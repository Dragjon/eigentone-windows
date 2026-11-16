// Eigentone for Windows: hooking the plug-in into outputs, registering it, and Windows Audio itself.
// Shared by the setup tool and the service.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
//
// Every output Eigentone hooks has its original setting saved first under HKLM\SOFTWARE\Eigentone\Hooks,
// and unhook writes exactly that back, or removes the value if it wasn't there.
#include "eigentone/system.h"

#include <mmdeviceapi.h>
#include <initguid.h>
#include <functiondiscoverykeys_devpkey.h>
#include <knownfolders.h>
#include <shlobj.h>

#include <algorithm>
#include <cstring>
#include <cwctype>
#include <optional>

#include "eigentone/shared.h"

namespace eigentone::sys {

namespace {
constexpr wchar_t kRenderRoot[] = L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\MMDevices\\Audio\\Render";
constexpr wchar_t kHooksRoot[] = L"SOFTWARE\\Eigentone\\Hooks";
constexpr wchar_t kEndpointFxComposite[] = L"{d04e05a6-594b-4fb6-a80d-01af5eed7d1d},15";
constexpr wchar_t kEndpointFxLegacy[] = L"{d04e05a6-594b-4fb6-a80d-01af5eed7d1d},7";
constexpr wchar_t kApoInterface[] = L"{FD7F2B29-24D0-4B5C-B177-592C39F9CA10}";  // IID_IAudioProcessingObject
}  // namespace


int fail(const wchar_t* what, LONG code) {
    if (code) fwprintf(stderr, L"error: %ls (code %ld)\n", what, code);
    else fwprintf(stderr, L"error: %ls\n", what);
    return 1;
}

std::wstring lower(std::wstring s) {
    std::transform(s.begin(), s.end(), s.begin(), [](wchar_t c) { return static_cast<wchar_t>(std::towlower(c)); });
    return s;
}

bool isAdmin() {
    BOOL admin = FALSE;
    PSID group = nullptr;
    SID_IDENTIFIER_AUTHORITY nt = SECURITY_NT_AUTHORITY;
    if (AllocateAndInitializeSid(&nt, 2, SECURITY_BUILTIN_DOMAIN_RID, DOMAIN_ALIAS_RID_ADMINS, 0, 0, 0, 0, 0, 0, &group)) {
        CheckTokenMembership(nullptr, group, &admin);
        FreeSid(group);
    }
    return admin;
}

bool enablePrivilege(const wchar_t* name) {
    HANDLE token;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, &token)) return false;
    TOKEN_PRIVILEGES tp{1};
    tp.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;
    const bool ok = LookupPrivilegeValueW(nullptr, name, &tp.Privileges[0].Luid) &&
                    AdjustTokenPrivileges(token, FALSE, &tp, 0, nullptr, nullptr) && GetLastError() == ERROR_SUCCESS;
    CloseHandle(token);
    return ok;
}

// ---------------------------------------------------------------------------------------------------------
// Registry helpers

std::vector<std::wstring> splitMulti(const std::vector<wchar_t>& data) {
    std::vector<std::wstring> out;
    const wchar_t* p = data.data();
    const wchar_t* end = p + data.size();
    while (p < end && *p) {
        std::wstring s(p);
        out.push_back(s);
        p += s.size() + 1;
    }
    return out;
}

std::vector<wchar_t> joinMulti(const std::vector<std::wstring>& items) {
    std::vector<wchar_t> out;
    for (const auto& s : items) {
        out.insert(out.end(), s.begin(), s.end());
        out.push_back(0);
    }
    out.push_back(0);
    return out;
}

struct RegValue {
    DWORD type = 0;
    std::vector<BYTE> data;
};

std::optional<RegValue> readValue(HKEY key, const wchar_t* name) {
    DWORD type = 0, size = 0;
    if (RegQueryValueExW(key, name, nullptr, &type, nullptr, &size) != ERROR_SUCCESS) return std::nullopt;
    RegValue v{type, std::vector<BYTE>(size)};
    if (RegQueryValueExW(key, name, nullptr, &type, v.data.data(), &size) != ERROR_SUCCESS) return std::nullopt;
    v.data.resize(size);
    return v;
}

std::vector<std::wstring> asList(const RegValue& v) {
    std::vector<wchar_t> chars(v.data.size() / sizeof(wchar_t) + 1, 0);
    std::memcpy(chars.data(), v.data.data(), v.data.size());
    if (v.type == REG_MULTI_SZ) return splitMulti(chars);
    if (v.type == REG_SZ && chars[0]) return {std::wstring(chars.data())};
    return {};
}

/// Opens an output's FxProperties key. Windows protects these keys, so it is opened with the backup and
/// restore privileges that administrators hold, instead of changing who owns it.
HKEY openFx(const std::wstring& endpoint, bool write) {
    const std::wstring path = std::wstring(kRenderRoot) + L"\\" + endpoint + L"\\FxProperties";
    HKEY key = nullptr;
    LONG r;
    if (write) {
        r = RegCreateKeyExW(HKEY_LOCAL_MACHINE, path.c_str(), 0, nullptr, REG_OPTION_BACKUP_RESTORE,
                            KEY_ALL_ACCESS | KEY_WOW64_64KEY, nullptr, &key, nullptr);
    } else {
        r = RegOpenKeyExW(HKEY_LOCAL_MACHINE, path.c_str(), 0, KEY_READ | KEY_WOW64_64KEY, &key);
    }
    return r == ERROR_SUCCESS ? key : nullptr;
}

bool isHooked(const std::wstring& endpoint) {
    HKEY fx = openFx(endpoint, false);
    if (!fx) return false;
    bool hooked = false;
    if (auto v = readValue(fx, kEndpointFxComposite)) {
        for (const auto& s : asList(*v)) hooked |= lower(s) == lower(kApoClsidText);
    }
    RegCloseKey(fx);
    return hooked;
}

// ---------------------------------------------------------------------------------------------------------
// Outputs


// ---------------------------------------------------------------------------------------------------------
// Hooking

int hook(const std::wstring& endpoint) {
    HKEY fx = openFx(endpoint, true);
    if (!fx) return fail(L"couldn't open the output's effect settings");

    auto composite = readValue(fx, kEndpointFxComposite);
    std::vector<std::wstring> list = composite ? asList(*composite) : std::vector<std::wstring>{};
    for (const auto& s : list) {
        if (lower(s) == lower(kApoClsidText)) { RegCloseKey(fx); wprintf(L"Already hooked: %ls\n", endpoint.c_str()); return 0; }
    }

    // Save exactly what was there, before touching it.
    HKEY backup = nullptr;
    const std::wstring backupPath = std::wstring(kHooksRoot) + L"\\" + endpoint;
    LONG r = RegCreateKeyExW(HKEY_LOCAL_MACHINE, backupPath.c_str(), 0, nullptr, 0, KEY_ALL_ACCESS | KEY_WOW64_64KEY,
                             nullptr, &backup, nullptr);
    if (r != ERROR_SUCCESS) { RegCloseKey(fx); return fail(L"couldn't save the output's original setting", r); }
    const DWORD existed = composite ? 1 : 0;
    RegSetValueExW(backup, L"Existed", 0, REG_DWORD, reinterpret_cast<const BYTE*>(&existed), sizeof(existed));
    if (composite) {
        RegSetValueExW(backup, L"Original", 0, composite->type, composite->data.data(), static_cast<DWORD>(composite->data.size()));
    }
    RegCloseKey(backup);

    // With no list yet, carry over a single older-style endpoint effect so the driver's own one still runs.
    if (!composite) {
        if (auto legacy = readValue(fx, kEndpointFxLegacy)) list = asList(*legacy);
    }
    // First in the chain, so the driver's own effects (speaker protection and the like) still come last.
    list.insert(list.begin(), kApoClsidText);
    const auto data = joinMulti(list);
    r = RegSetValueExW(fx, kEndpointFxComposite, 0, REG_MULTI_SZ, reinterpret_cast<const BYTE*>(data.data()),
                       static_cast<DWORD>(data.size() * sizeof(wchar_t)));
    RegCloseKey(fx);
    if (r != ERROR_SUCCESS) return fail(L"couldn't hook the output", r);
    wprintf(L"Hooked: %ls\n", endpoint.c_str());
    return 0;
}

int unhook(const std::wstring& endpoint) {
    const std::wstring backupPath = std::wstring(kHooksRoot) + L"\\" + endpoint;
    HKEY backup = nullptr;
    if (RegOpenKeyExW(HKEY_LOCAL_MACHINE, backupPath.c_str(), 0, KEY_READ | KEY_WOW64_64KEY, &backup) != ERROR_SUCCESS) {
        return fail(L"no saved setting for this output, so it was never hooked by Eigentone");
    }
    auto existed = readValue(backup, L"Existed");
    auto original = readValue(backup, L"Original");
    RegCloseKey(backup);

    HKEY fx = openFx(endpoint, true);
    if (!fx) return fail(L"couldn't open the output's effect settings");
    LONG r;
    if (existed && existed->data.size() >= 4 && *reinterpret_cast<DWORD*>(existed->data.data()) == 1 && original) {
        r = RegSetValueExW(fx, kEndpointFxComposite, 0, original->type, original->data.data(),
                           static_cast<DWORD>(original->data.size()));
    } else {
        r = RegDeleteValueW(fx, kEndpointFxComposite);
        if (r == ERROR_FILE_NOT_FOUND) r = ERROR_SUCCESS;
    }
    RegCloseKey(fx);
    if (r != ERROR_SUCCESS) return fail(L"couldn't restore the output", r);
    RegDeleteKeyExW(HKEY_LOCAL_MACHINE, backupPath.c_str(), KEY_WOW64_64KEY, 0);
    wprintf(L"Restored: %ls\n", endpoint.c_str());
    return 0;
}

std::vector<std::wstring> hookedOutputs() {
    std::vector<std::wstring> out;
    HKEY hooks = nullptr;
    if (RegOpenKeyExW(HKEY_LOCAL_MACHINE, kHooksRoot, 0, KEY_READ | KEY_WOW64_64KEY, &hooks) != ERROR_SUCCESS) return out;
    wchar_t name[128];
    for (DWORD i = 0;; ++i) {
        DWORD len = 128;
        if (RegEnumKeyExW(hooks, i, name, &len, nullptr, nullptr, nullptr, nullptr) != ERROR_SUCCESS) break;
        out.emplace_back(name);
    }
    RegCloseKey(hooks);
    return out;
}

// ---------------------------------------------------------------------------------------------------------
// Registration

std::wstring installDir() {
    PWSTR pf = nullptr;
    std::wstring dir;
    if (SUCCEEDED(SHGetKnownFolderPath(FOLDERID_ProgramFiles, 0, nullptr, &pf))) dir = std::wstring(pf) + L"\\Eigentone";
    CoTaskMemFree(pf);
    return dir;
}

bool setString(HKEY key, const wchar_t* name, const std::wstring& value) {
    return RegSetValueExW(key, name, 0, REG_SZ, reinterpret_cast<const BYTE*>(value.c_str()),
                          static_cast<DWORD>((value.size() + 1) * sizeof(wchar_t))) == ERROR_SUCCESS;
}

bool setDword(HKEY key, const wchar_t* name, DWORD value) {
    return RegSetValueExW(key, name, 0, REG_DWORD, reinterpret_cast<const BYTE*>(&value), sizeof(value)) == ERROR_SUCCESS;
}

int registerApo(const std::wstring& dll) {
    HKEY key = nullptr;
    const std::wstring clsidPath = std::wstring(L"SOFTWARE\\Classes\\CLSID\\") + kApoClsidText;
    if (RegCreateKeyExW(HKEY_LOCAL_MACHINE, clsidPath.c_str(), 0, nullptr, 0, KEY_ALL_ACCESS | KEY_WOW64_64KEY, nullptr, &key, nullptr)) {
        return fail(L"couldn't register the plug-in");
    }
    setString(key, nullptr, L"Eigentone");
    HKEY server = nullptr;
    RegCreateKeyExW(key, L"InprocServer32", 0, nullptr, 0, KEY_ALL_ACCESS | KEY_WOW64_64KEY, nullptr, &server, nullptr);
    setString(server, nullptr, dll);
    setString(server, L"ThreadingModel", L"Both");
    RegCloseKey(server);
    RegCloseKey(key);

    // What audiodg reads to accept the plug-in as an audio processing object.
    const std::wstring apoPath = std::wstring(L"SOFTWARE\\Classes\\AudioEngine\\AudioProcessingObjects\\") + kApoClsidText;
    if (RegCreateKeyExW(HKEY_LOCAL_MACHINE, apoPath.c_str(), 0, nullptr, 0, KEY_ALL_ACCESS | KEY_WOW64_64KEY, nullptr, &key, nullptr)) {
        return fail(L"couldn't register the plug-in as an audio effect");
    }
    setString(key, L"FriendlyName", L"Eigentone");
    setString(key, L"Copyright", L"Copyright (C) 2026 Dragjon. GPL-3.0-or-later.");
    setDword(key, L"MajorVersion", 0);
    setDword(key, L"MinorVersion", 1);
    setDword(key, L"Flags", 0x0000000e);  // APO_FLAG_DEFAULT
    setDword(key, L"MinInputConnections", 1);
    setDword(key, L"MaxInputConnections", 1);
    setDword(key, L"MinOutputConnections", 1);
    setDword(key, L"MaxOutputConnections", 1);
    setDword(key, L"MaxInstances", 0xffffffff);
    setDword(key, L"NumAPOInterfaces", 1);
    setString(key, L"APOInterface0", kApoInterface);
    RegCloseKey(key);
    return 0;
}

void unregisterApo() {
    RegDeleteTreeW(HKEY_LOCAL_MACHINE, (std::wstring(L"SOFTWARE\\Classes\\CLSID\\") + kApoClsidText).c_str());
    RegDeleteTreeW(HKEY_LOCAL_MACHINE, (std::wstring(L"SOFTWARE\\Classes\\AudioEngine\\AudioProcessingObjects\\") + kApoClsidText).c_str());
}

// ---------------------------------------------------------------------------------------------------------
// Windows Audio

bool waitFor(SC_HANDLE svc, DWORD state) {
    SERVICE_STATUS st{};
    for (int i = 0; i < 100; ++i) {
        if (!QueryServiceStatus(svc, &st)) return false;
        if (st.dwCurrentState == state) return true;
        Sleep(100);
    }
    return false;
}

/// Services that need Windows Audio and were running when it was stopped, to start again afterwards.
/// Windows won't stop Audiosrv while any of them run (a laptop maker's hotkey service, for example).
std::vector<std::wstring> gStoppedDependents;

bool stopService(SC_HANDLE scm, const wchar_t* name) {
    SC_HANDLE svc = OpenServiceW(scm, name, SERVICE_STOP | SERVICE_QUERY_STATUS);
    if (!svc) return fail(name, static_cast<LONG>(GetLastError())), false;
    SERVICE_STATUS st{};
    bool ok = ControlService(svc, SERVICE_CONTROL_STOP, &st) || GetLastError() == ERROR_SERVICE_NOT_ACTIVE;
    if (!ok) fail(name, static_cast<LONG>(GetLastError()));
    ok = ok && waitFor(svc, SERVICE_STOPPED);
    CloseServiceHandle(svc);
    return ok;
}

bool startService(SC_HANDLE scm, const wchar_t* name) {
    SC_HANDLE svc = OpenServiceW(scm, name, SERVICE_START | SERVICE_QUERY_STATUS);
    if (!svc) return false;
    bool ok = (StartServiceW(svc, 0, nullptr) || GetLastError() == ERROR_SERVICE_ALREADY_RUNNING) &&
              waitFor(svc, SERVICE_RUNNING);
    CloseServiceHandle(svc);
    return ok;
}

bool stopAudio() {
    SC_HANDLE scm = OpenSCManagerW(nullptr, nullptr, SC_MANAGER_CONNECT);
    if (!scm) return false;
    gStoppedDependents.clear();
    if (SC_HANDLE audio = OpenServiceW(scm, L"Audiosrv", SERVICE_ENUMERATE_DEPENDENTS)) {
        DWORD needed = 0, count = 0;
        EnumDependentServicesW(audio, SERVICE_ACTIVE, nullptr, 0, &needed, &count);
        std::vector<BYTE> buf(needed);
        auto* deps = reinterpret_cast<ENUM_SERVICE_STATUSW*>(buf.data());
        if (needed && EnumDependentServicesW(audio, SERVICE_ACTIVE, deps, needed, &needed, &count)) {
            for (DWORD i = 0; i < count; ++i) {
                if (stopService(scm, deps[i].lpServiceName)) gStoppedDependents.emplace_back(deps[i].lpServiceName);
            }
        }
        CloseServiceHandle(audio);
    }
    const bool ok = stopService(scm, L"Audiosrv");
    CloseServiceHandle(scm);
    return ok;
}

bool startAudio() {
    SC_HANDLE scm = OpenSCManagerW(nullptr, nullptr, SC_MANAGER_CONNECT);
    if (!scm) return false;
    const bool ok = startService(scm, L"Audiosrv");
    // Dependents go back in the reverse of the order they were stopped in.
    for (auto it = gStoppedDependents.rbegin(); it != gStoppedDependents.rend(); ++it) startService(scm, it->c_str());
    gStoppedDependents.clear();
    CloseServiceHandle(scm);
    return ok;
}


}  // namespace eigentone::sys

// ---------------------------------------------------------------------------------------------------------
// Eigentone's service

namespace eigentone::sys {

namespace {
constexpr wchar_t kServiceName[] = L"EigentoneService";
// Deliberately not dependent on Windows Audio: stopping Audio must not stop the service, which holds the
// settings so the plug-ins find them again when Audio comes back. An empty list clears any old one.
constexpr wchar_t kNoDependencies[] = {0, 0};
}

int installEigentoneService(const std::wstring& exe) {
    SC_HANDLE scm = OpenSCManagerW(nullptr, nullptr, SC_MANAGER_CREATE_SERVICE | SC_MANAGER_CONNECT);
    if (!scm) return fail(L"couldn't reach the service manager", static_cast<LONG>(GetLastError()));
    const std::wstring command = L"\"" + exe + L"\"";
    SC_HANDLE svc = CreateServiceW(scm, kServiceName, L"Eigentone", SERVICE_ALL_ACCESS, SERVICE_WIN32_OWN_PROCESS,
                                   SERVICE_AUTO_START, SERVICE_ERROR_NORMAL, command.c_str(), nullptr, nullptr,
                                   kNoDependencies, nullptr, nullptr);
    if (!svc && GetLastError() == ERROR_SERVICE_EXISTS) {
        svc = OpenServiceW(scm, kServiceName, SERVICE_ALL_ACCESS);
        if (svc) {
            ChangeServiceConfigW(svc, SERVICE_WIN32_OWN_PROCESS, SERVICE_AUTO_START, SERVICE_ERROR_NORMAL, command.c_str(),
                                 nullptr, nullptr, kNoDependencies, nullptr, nullptr, L"Eigentone");
        }
    }
    if (!svc) {
        const LONG err = static_cast<LONG>(GetLastError());
        CloseServiceHandle(scm);
        return fail(L"couldn't add the service", err);
    }
    wchar_t text[] = L"Keeps Eigentone's sound settings and tells the app what's playing.";
    SERVICE_DESCRIPTIONW description{text};
    ChangeServiceConfig2W(svc, SERVICE_CONFIG_DESCRIPTION, &description);
    // If it ever stops unexpectedly, start it again after a few seconds.
    SC_ACTION actions[] = {{SC_ACTION_RESTART, 3000}, {SC_ACTION_RESTART, 10000}, {SC_ACTION_NONE, 0}};
    SERVICE_FAILURE_ACTIONSW failure{86400, nullptr, nullptr, 3, actions};
    ChangeServiceConfig2W(svc, SERVICE_CONFIG_FAILURE_ACTIONS, &failure);
    const bool started = StartServiceW(svc, 0, nullptr) || GetLastError() == ERROR_SERVICE_ALREADY_RUNNING;
    const LONG err = static_cast<LONG>(GetLastError());
    CloseServiceHandle(svc);
    CloseServiceHandle(scm);
    return started ? 0 : fail(L"couldn't start the service", err);
}

void stopEigentoneService() {
    SC_HANDLE scm = OpenSCManagerW(nullptr, nullptr, SC_MANAGER_CONNECT);
    if (!scm) return;
    if (SC_HANDLE svc = OpenServiceW(scm, kServiceName, SERVICE_STOP | SERVICE_QUERY_STATUS)) {
        SERVICE_STATUS st{};
        if (ControlService(svc, SERVICE_CONTROL_STOP, &st)) waitFor(svc, SERVICE_STOPPED);
        CloseServiceHandle(svc);
    }
    CloseServiceHandle(scm);
}

void removeEigentoneService() {
    stopEigentoneService();
    SC_HANDLE scm = OpenSCManagerW(nullptr, nullptr, SC_MANAGER_CONNECT);
    if (!scm) return;
    if (SC_HANDLE svc = OpenServiceW(scm, kServiceName, DELETE)) {
        DeleteService(svc);
        CloseServiceHandle(svc);
    }
    CloseServiceHandle(scm);
}

}  // namespace eigentone::sys
