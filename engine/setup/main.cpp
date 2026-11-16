// Eigentone for Windows: installs the audio plug-in, hooks it into outputs, and puts everything back.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
//
//   eigentone-setup status                  what is installed, which outputs are hooked, live meters
//   eigentone-setup install [endpoint-guid]  copy and register the plug-in, hook the default (or given) output
//   eigentone-setup unhook  [endpoint-guid]  restore one output (or all) exactly as it was
//   eigentone-setup uninstall               unhook every output, unregister, remove the files
//   eigentone-setup restart-audio           restart Windows Audio so hooks take effect
//   eigentone-setup gain <dB> | on | off    change the live level, to hear that the chain works
//
// Every output Eigentone hooks has its original setting saved first under HKLM\SOFTWARE\Eigentone\Hooks,
// and unhook writes exactly that back, or removes the value if it wasn't there.
#include <windows.h>
#include <mmdeviceapi.h>
#include <initguid.h>
#include <functiondiscoverykeys_devpkey.h>
#include <knownfolders.h>
#include <sddl.h>
#include <shlobj.h>

#include <algorithm>
#include <cwctype>
#include <optional>
#include <string>
#include <vector>

#include "eigentone/shared.h"

using namespace eigentone;

namespace {

constexpr wchar_t kRenderRoot[] = L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\MMDevices\\Audio\\Render";
constexpr wchar_t kHooksRoot[] = L"SOFTWARE\\Eigentone\\Hooks";
constexpr wchar_t kEndpointFxComposite[] = L"{d04e05a6-594b-4fb6-a80d-01af5eed7d1d},15";
constexpr wchar_t kEndpointFxLegacy[] = L"{d04e05a6-594b-4fb6-a80d-01af5eed7d1d},7";
constexpr wchar_t kApoInterface[] = L"{FD7F2B29-24D0-4B5C-B177-592C39F9CA10}";  // IID_IAudioProcessingObject

int fail(const wchar_t* what, LONG code = 0) {
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

struct Output {
    std::wstring guid;  // the registry key name, e.g. {f51e4c16-...}
    std::wstring name;
    DWORD state = 0;
    bool isDefault = false;
};

std::wstring guidOf(const std::wstring& id) {
    const auto dot = id.rfind(L'.');
    return dot == std::wstring::npos ? id : id.substr(dot + 1);
}

std::vector<Output> listOutputs() {
    std::vector<Output> out;
    IMMDeviceEnumerator* en = nullptr;
    if (FAILED(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_ALL, IID_PPV_ARGS(&en)))) return out;
    std::wstring defaultGuid;
    IMMDevice* def = nullptr;
    if (SUCCEEDED(en->GetDefaultAudioEndpoint(eRender, eConsole, &def))) {
        LPWSTR id = nullptr;
        if (SUCCEEDED(def->GetId(&id))) { defaultGuid = lower(guidOf(id)); CoTaskMemFree(id); }
        def->Release();
    }
    IMMDeviceCollection* all = nullptr;
    if (SUCCEEDED(en->EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE | DEVICE_STATE_UNPLUGGED, &all))) {
        UINT n = 0;
        all->GetCount(&n);
        for (UINT i = 0; i < n; ++i) {
            IMMDevice* d = nullptr;
            if (FAILED(all->Item(i, &d))) continue;
            Output o;
            LPWSTR id = nullptr;
            if (SUCCEEDED(d->GetId(&id))) { o.guid = lower(guidOf(id)); CoTaskMemFree(id); }
            d->GetState(&o.state);
            IPropertyStore* ps = nullptr;
            if (SUCCEEDED(d->OpenPropertyStore(STGM_READ, &ps))) {
                PROPVARIANT v;
                PropVariantInit(&v);
                if (SUCCEEDED(ps->GetValue(PKEY_Device_FriendlyName, &v)) && v.vt == VT_LPWSTR) o.name = v.pwszVal;
                PropVariantClear(&v);
                ps->Release();
            }
            o.isDefault = o.guid == defaultGuid;
            out.push_back(o);
            d->Release();
        }
        all->Release();
    }
    en->Release();
    return out;
}

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

bool stopAudio() {
    SC_HANDLE scm = OpenSCManagerW(nullptr, nullptr, SC_MANAGER_CONNECT);
    SC_HANDLE svc = scm ? OpenServiceW(scm, L"Audiosrv", SERVICE_STOP | SERVICE_QUERY_STATUS) : nullptr;
    SERVICE_STATUS st{};
    bool ok = svc && (ControlService(svc, SERVICE_CONTROL_STOP, &st) || GetLastError() == ERROR_SERVICE_NOT_ACTIVE) &&
              waitFor(svc, SERVICE_STOPPED);
    if (svc) CloseServiceHandle(svc);
    if (scm) CloseServiceHandle(scm);
    return ok;
}

bool startAudio() {
    SC_HANDLE scm = OpenSCManagerW(nullptr, nullptr, SC_MANAGER_CONNECT);
    SC_HANDLE svc = scm ? OpenServiceW(scm, L"Audiosrv", SERVICE_START | SERVICE_QUERY_STATUS) : nullptr;
    bool ok = svc && (StartServiceW(svc, 0, nullptr) || GetLastError() == ERROR_SERVICE_ALREADY_RUNNING) &&
              waitFor(svc, SERVICE_RUNNING);
    if (svc) CloseServiceHandle(svc);
    if (scm) CloseServiceHandle(scm);
    return ok;
}

// ---------------------------------------------------------------------------------------------------------
// Commands

SharedBlock* openShared(bool write) {
    HANDLE m = OpenFileMappingW(write ? FILE_MAP_READ | FILE_MAP_WRITE : FILE_MAP_READ, FALSE, kSectionName);
    if (!m) return nullptr;
    auto* b = static_cast<SharedBlock*>(MapViewOfFile(m, write ? FILE_MAP_READ | FILE_MAP_WRITE : FILE_MAP_READ, 0, 0, sizeof(SharedBlock)));
    return b && b->magic == kMagic ? b : nullptr;  // the mapping stays open for the life of this short process
}

int status() {
    const std::wstring dll = installDir() + L"\\EigentoneApo.dll";
    wprintf(L"Plug-in file: %ls\n", GetFileAttributesW(dll.c_str()) != INVALID_FILE_ATTRIBUTES ? dll.c_str() : L"(not installed)");
    wprintf(L"\nOutputs:\n");
    for (const auto& o : listOutputs()) {
        wprintf(L"  %ls %ls  %-40ls %ls\n", o.isDefault ? L"*" : L" ", isHooked(o.guid) ? L"[hooked]" : L"        ",
                o.name.c_str(), o.guid.c_str());
    }
    SharedBlock* b = openShared(false);
    if (!b) {
        wprintf(L"\nEngine: not running (no output has loaded the plug-in yet)\n");
        return 0;
    }
    LONG64 before[kMaxSlots];
    for (UINT i = 0; i < kMaxSlots; ++i) before[i] = b->slots[i].heartbeat;
    Sleep(300);
    wprintf(L"\nEngine: %ls, level %+.1f dB\n", b->enabled ? L"on" : L"off (passing sound through)", b->gainDb);
    for (UINT i = 0; i < kMaxSlots; ++i) {
        const EndpointSlot& s = b->slots[i];
        if (!s.owner) continue;
        const bool live = s.heartbeat != before[i];
        wprintf(L"  %ls  %u Hz, %u ch, %ls, peak in %.3f out %.3f\n", s.endpoint, s.sampleRate, s.channels,
                live ? L"processing" : L"idle", s.peakIn, s.peakOut);
    }
    return 0;
}

std::optional<std::wstring> pickEndpoint(int argc, wchar_t** argv, int index) {
    if (argc > index) return lower(argv[index]);
    for (const auto& o : listOutputs()) {
        if (o.isDefault) return o.guid;
    }
    return std::nullopt;
}

int install(int argc, wchar_t** argv) {
    wchar_t self[MAX_PATH];
    GetModuleFileNameW(nullptr, self, MAX_PATH);
    std::wstring source(self);
    source = source.substr(0, source.rfind(L'\\')) + L"\\EigentoneApo.dll";
    if (GetFileAttributesW(source.c_str()) == INVALID_FILE_ATTRIBUTES) return fail(L"EigentoneApo.dll must sit next to this program");

    auto endpoint = pickEndpoint(argc, argv, 2);
    if (!endpoint) return fail(L"no default output found");

    const std::wstring dir = installDir();
    const std::wstring dll = dir + L"\\EigentoneApo.dll";
    CreateDirectoryW(dir.c_str(), nullptr);

    wprintf(L"Stopping Windows Audio...\n");
    if (!stopAudio()) return fail(L"couldn't stop Windows Audio");
    const bool copied = CopyFileW(source.c_str(), dll.c_str(), FALSE);
    int rc = copied ? registerApo(dll) : fail(L"couldn't copy the plug-in", static_cast<LONG>(GetLastError()));
    if (rc == 0) rc = hook(*endpoint);
    wprintf(L"Starting Windows Audio...\n");
    if (!startAudio()) fail(L"couldn't start Windows Audio; restart the PC");
    return rc;
}

int uninstall() {
    int rc = 0;
    for (const auto& e : hookedOutputs()) rc |= unhook(e);
    wprintf(L"Stopping Windows Audio...\n");
    stopAudio();
    unregisterApo();
    const std::wstring dir = installDir();
    const std::wstring dll = dir + L"\\EigentoneApo.dll";
    if (!DeleteFileW(dll.c_str()) && GetLastError() != ERROR_FILE_NOT_FOUND) {
        MoveFileExW(dll.c_str(), nullptr, MOVEFILE_DELAY_UNTIL_REBOOT);
        wprintf(L"The plug-in file is in use; it will be removed when the PC restarts.\n");
    }
    RemoveDirectoryW(dir.c_str());
    RegDeleteTreeW(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Eigentone");
    wprintf(L"Starting Windows Audio...\n");
    if (!startAudio()) fail(L"couldn't start Windows Audio; restart the PC");
    wprintf(rc ? L"Uninstalled, with errors above.\n" : L"Uninstalled. Every output is as it was.\n");
    return rc;
}

int setLevel(const wchar_t* arg) {
    SharedBlock* b = openShared(true);
    if (!b) return fail(L"the engine isn't running");
    if (lstrcmpiW(arg, L"on") == 0) b->enabled = 1;
    else if (lstrcmpiW(arg, L"off") == 0) b->enabled = 0;
    else b->gainDb = static_cast<float>(_wtof(arg));
    wprintf(L"Engine %ls, level %+.1f dB\n", b->enabled ? L"on" : L"off", b->gainDb);
    return 0;
}

int usage() {
    fwprintf(stderr,
             L"usage: eigentone-setup status | install [endpoint] | unhook [endpoint] | uninstall | restart-audio | gain <dB>|on|off\n");
    return 2;
}

}  // namespace

int wmain(int argc, wchar_t** argv) {
    if (argc < 2) return usage();
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const std::wstring cmd = lower(argv[1]);
    if (cmd == L"status") return status();
    if (cmd == L"gain") return argc > 2 ? setLevel(argv[2]) : usage();

    if (!isAdmin()) return fail(L"run this as administrator");
    enablePrivilege(SE_BACKUP_NAME);
    enablePrivilege(SE_RESTORE_NAME);
    if (cmd == L"install") return install(argc, argv);
    if (cmd == L"uninstall") return uninstall();
    if (cmd == L"unhook") {
        if (argc > 2) return unhook(lower(argv[2]));
        int rc = 0;
        for (const auto& e : hookedOutputs()) rc |= unhook(e);
        return rc;
    }
    if (cmd == L"restart-audio") return stopAudio() && startAudio() ? 0 : fail(L"couldn't restart Windows Audio");
    return usage();
}
