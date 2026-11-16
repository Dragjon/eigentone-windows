// Eigentone's agent: lists the apps playing on the default output, for per-app presets.
//
// Windows keeps audio sessions per signed-in user, so the background service, which runs as the system,
// can't see them. The app starts this in the user's session instead. It writes one JSON line whenever the
// list changes, and quits when the app closes its end of the pipe.

#include <windows.h>
#include <audiopolicy.h>
#include <mmdeviceapi.h>

#include <algorithm>
#include <cstdio>
#include <map>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>

using json = nlohmann::json;

namespace {

HANDLE gStopEvent = nullptr;

std::string narrow(const std::wstring& w) {
    if (w.empty()) return {};
    const int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), static_cast<int>(w.size()), nullptr, 0, nullptr, nullptr);
    std::string s(static_cast<size_t>(n), '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), static_cast<int>(w.size()), s.data(), n, nullptr, nullptr);
    return s;
}

std::wstring lower(std::wstring s) {
    CharLowerBuffW(s.data(), static_cast<DWORD>(s.size()));
    return s;
}

/// An app's own name, from its program file's description, such as "Spotify" for Spotify.exe.
std::wstring describe(const std::wstring& path) {
    DWORD ignored = 0;
    const DWORD size = GetFileVersionInfoSizeW(path.c_str(), &ignored);
    if (size == 0) return {};
    std::vector<BYTE> info(size);
    if (!GetFileVersionInfoW(path.c_str(), 0, size, info.data())) return {};
    struct Translation { WORD language, codePage; }* translations = nullptr;
    UINT bytes = 0;
    if (!VerQueryValueW(info.data(), L"\\VarFileInfo\\Translation", reinterpret_cast<void**>(&translations), &bytes) || bytes < sizeof(Translation)) {
        return {};
    }
    wchar_t query[64];
    swprintf_s(query, L"\\StringFileInfo\\%04x%04x\\FileDescription", translations[0].language, translations[0].codePage);
    wchar_t* text = nullptr;
    UINT length = 0;
    if (!VerQueryValueW(info.data(), query, reinterpret_cast<void**>(&text), &length) || length == 0) return {};
    return std::wstring(text);
}

/// A process's program file, or empty if it can't be read.
std::wstring programOf(DWORD pid) {
    HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    if (!process) return {};
    wchar_t path[MAX_PATH];
    DWORD length = MAX_PATH;
    const bool ok = QueryFullProcessImageNameW(process, 0, path, &length) != FALSE;
    CloseHandle(process);
    return ok ? std::wstring(path, length) : std::wstring();
}

struct PlayingApp {
    std::wstring exe;   // lower case, such as "spotify.exe": what a preset is assigned to
    std::wstring name;  // what it's called, such as "Spotify"
    ULONGLONG since;    // when it started playing, in milliseconds since Windows started
};

/// Eigentone plays its own test tones, which aren't an app's sound, and Windows' audio engine isn't an app.
bool ignoredProgram(const std::wstring& exe) {
    return exe == L"eigentone.exe" || exe == L"java.exe" || exe == L"javaw.exe" || exe == L"audiodg.exe";
}

/// Lists the apps playing on the default output once a second and tells apps whenever the list changes.
void watchApps() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    IMMDeviceEnumerator* en = nullptr;
    CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_ALL, IID_PPV_ARGS(&en));
    std::vector<PlayingApp> playing;  // in the order they started
    std::map<DWORD, std::pair<std::wstring, std::wstring>> known;  // pid: program and name, read once
    while (WaitForSingleObject(gStopEvent, 1000) == WAIT_TIMEOUT) {
        std::vector<std::pair<std::wstring, std::wstring>> now;
        IMMDevice* def = nullptr;
        IAudioSessionManager2* manager = nullptr;
        IAudioSessionEnumerator* sessions = nullptr;
        if (en && SUCCEEDED(en->GetDefaultAudioEndpoint(eRender, eConsole, &def)) &&
            SUCCEEDED(def->Activate(__uuidof(IAudioSessionManager2), CLSCTX_ALL, nullptr, reinterpret_cast<void**>(&manager))) &&
            SUCCEEDED(manager->GetSessionEnumerator(&sessions))) {
            int count = 0;
            sessions->GetCount(&count);
            for (int i = 0; i < count; ++i) {
                IAudioSessionControl* control = nullptr;
                IAudioSessionControl2* control2 = nullptr;
                if (FAILED(sessions->GetSession(i, &control))) continue;
                AudioSessionState state = AudioSessionStateInactive;
                DWORD pid = 0;
                if (SUCCEEDED(control->GetState(&state)) && state == AudioSessionStateActive &&
                    SUCCEEDED(control->QueryInterface(IID_PPV_ARGS(&control2))) && control2->IsSystemSoundsSession() != S_OK &&
                    SUCCEEDED(control2->GetProcessId(&pid)) && pid != 0) {
                    auto found = known.find(pid);
                    if (found == known.end()) {
                        const std::wstring path = programOf(pid);
                        std::wstring exe = lower(path.substr(path.find_last_of(L'\\') + 1));
                        std::wstring name = describe(path);
                        if (name.empty()) name = exe.substr(0, exe.rfind(L'.'));
                        found = known.emplace(pid, std::make_pair(exe, name)).first;
                    }
                    const auto& [exe, name] = found->second;
                    if (!exe.empty() && !ignoredProgram(exe) &&
                        std::none_of(now.begin(), now.end(), [&](const auto& n) { return n.first == exe; })) {
                        now.emplace_back(exe, name);
                    }
                }
                if (control2) control2->Release();
                control->Release();
            }
        }
        if (sessions) sessions->Release();
        if (manager) manager->Release();
        if (def) def->Release();

        // Keeps when each started, so the app can tell which began most recently.
        std::vector<PlayingApp> next;
        for (const auto& p : playing) {
            if (std::any_of(now.begin(), now.end(), [&](const auto& n) { return n.first == p.exe; })) next.push_back(p);
        }
        for (const auto& [exe, name] : now) {
            if (std::none_of(next.begin(), next.end(), [&](const PlayingApp& p) { return p.exe == exe; })) {
                next.push_back({exe, name, GetTickCount64()});
            }
        }
        const bool changed = next.size() != playing.size() ||
                             !std::equal(next.begin(), next.end(), playing.begin(), [](const auto& a, const auto& b) { return a.exe == b.exe; });
        playing = std::move(next);
        if (known.size() > 512) known.clear();  // processes come and go; names are read again as needed
        if (changed) {
            json list = json::array();
            for (const auto& p : playing) list.push_back({{"exe", narrow(p.exe)}, {"name", narrow(p.name)}, {"since", p.since}});
            const std::string line = json{{"op", "apps"}, {"apps", list}}.dump() + "\n";
            std::fwrite(line.data(), 1, line.size(), stdout);
            std::fflush(stdout);
        }
    }
    if (en) en->Release();
    CoUninitialize();
}

}  // namespace

int wmain() {
    gStopEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    // The app holds the other end of standard input; when it closes, or the app goes, this stops too.
    std::thread([] {
        char buffer[256];
        while (std::fgets(buffer, sizeof(buffer), stdin)) {
        }
        SetEvent(gStopEvent);
    }).detach();
    watchApps();
    return 0;
}
