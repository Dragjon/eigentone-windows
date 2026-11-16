// Eigentone for Windows: the background service.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
//
// Runs as the system from startup, so Eigentone shapes the sound before anyone opens the app:
//   - owns the shared settings block and puts back the last settings applied
//   - watches outputs: hooks new ones, and tells the app when the output or its jack changes
//   - serves the app over \\.\pipe\eigentone, one JSON message per line
//   - sends level and spectrum to apps that ask, about 30 times a second
//
//   eigentone-service             run as a Windows service (started by the service manager)
//   eigentone-service --console   run in this window, for testing
#include <windows.h>
#include <mmdeviceapi.h>
#include <endpointvolume.h>
#include <objbase.h>
#include <sddl.h>
#include <shlobj.h>
#include <knownfolders.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <complex>
#include <condition_variable>
#include <cstdio>
#include <fstream>
#include <memory>
#include <mutex>
#include <regex>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>

#include "eigentone/dsp/fft.h"
#include "eigentone/shared.h"
#include "eigentone/system.h"
#include "params_json.h"

using nlohmann::json;
using namespace eigentone;

namespace {

constexpr wchar_t kServiceName[] = L"EigentoneService";
constexpr wchar_t kPipeName[] = L"\\\\.\\pipe\\eigentone";
// The system and administrators fully; signed-in users may connect and talk to it.
constexpr wchar_t kPipeSddl[] = L"D:(A;;GA;;;SY)(A;;GA;;;BA)(A;;GRGW;;;AU)";
constexpr char kServiceVersion[] = "0.1.0";
constexpr int kMetersPerSecond = 30;
constexpr int kSpectrumBands = 60;  // sixth-octave bands from 20 Hz to 20 kHz

std::atomic<bool> gStopping{false};
HANDLE gStopEvent = nullptr;
bool gConsole = false;

void log(const char* fmt, ...) {
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    if (gConsole) std::printf("%s\n", buf);
    OutputDebugStringA(buf);
    OutputDebugStringA("\n");
}

std::string narrow(const std::wstring& w) {
    if (w.empty()) return {};
    const int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), static_cast<int>(w.size()), nullptr, 0, nullptr, nullptr);
    std::string s(static_cast<size_t>(n), '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), static_cast<int>(w.size()), s.data(), n, nullptr, nullptr);
    return s;
}

std::wstring widen(const std::string& s) {
    if (s.empty()) return {};
    const int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), static_cast<int>(s.size()), nullptr, 0);
    std::wstring w(static_cast<size_t>(n), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), static_cast<int>(s.size()), w.data(), n);
    return w;
}

// ---------------------------------------------------------------------------------------------------------
// Settings: shared with the plug-ins, and kept on disk

class Settings {
public:
    bool open() {
        PSECURITY_DESCRIPTOR sd = nullptr;
        if (!ConvertStringSecurityDescriptorToSecurityDescriptorW(kSectionSddl, SDDL_REVISION_1, &sd, nullptr)) return false;
        SECURITY_ATTRIBUTES sa{sizeof(sa), sd, FALSE};
        mapping_ = CreateFileMappingW(INVALID_HANDLE_VALUE, &sa, PAGE_READWRITE, 0, sizeof(SharedBlock), kSectionName);
        const bool existed = GetLastError() == ERROR_ALREADY_EXISTS;
        LocalFree(sd);
        if (!mapping_) {
            // Run in a console without the right to create shared memory: use the plug-in's, if it's running.
            mapping_ = OpenFileMappingW(FILE_MAP_READ | FILE_MAP_WRITE, FALSE, kSectionName);
            if (!mapping_) return false;
        }
        block_ = static_cast<SharedBlock*>(MapViewOfFile(mapping_, FILE_MAP_READ | FILE_MAP_WRITE, 0, 0, sizeof(SharedBlock)));
        if (!block_) return false;
        const dsp::Params saved = load();
        if (!existed || block_->magic != kMagic) {
            block_->version = kVersion;
            writeParams(block_, saved);
            MemoryBarrier();
            block_->magic = kMagic;
        } else {
            writeParams(block_, saved);
        }
        log("settings: %s, last settings put back", existed ? "joined the plug-ins' block" : "made the shared block");
        return true;
    }

    SharedBlock* block() const { return block_; }

    LONG64 apply(const dsp::Params& p) {
        std::lock_guard lock(mutex_);
        writeParams(block_, p);
        save(p);
        return block_->seq;
    }

    dsp::Params current() const {
        dsp::Params p{};
        while (readParams(block_, p) < 0) Sleep(1);
        return p;
    }

private:
    static std::wstring path() {
        PWSTR data = nullptr;
        std::wstring dir;
        if (SUCCEEDED(SHGetKnownFolderPath(FOLDERID_ProgramData, 0, nullptr, &data))) dir = std::wstring(data) + L"\\Eigentone";
        CoTaskMemFree(data);
        CreateDirectoryW(dir.c_str(), nullptr);
        return dir + L"\\settings.json";
    }

    static dsp::Params load() {
        try {
            std::ifstream in(path());
            if (in) return service::paramsFromJson(json::parse(in));
        } catch (const std::exception& e) {
            log("settings: couldn't read the saved settings (%s); starting neutral", e.what());
        }
        return dsp::neutralParams();
    }

    static void save(const dsp::Params& p) {
        // Written beside, then moved over, so a crash mid-write never leaves half a file.
        const std::wstring target = path();
        const std::wstring temp = target + L".new";
        {
            std::ofstream out(temp, std::ios::trunc);
            out << service::paramsToJson(p).dump();
            if (!out) return;
        }
        MoveFileExW(temp.c_str(), target.c_str(), MOVEFILE_REPLACE_EXISTING);
    }

    HANDLE mapping_ = nullptr;
    SharedBlock* block_ = nullptr;
    std::mutex mutex_;
};

Settings gSettings;

// ---------------------------------------------------------------------------------------------------------
// Clients: each connected app, written to from several threads

class Client {
public:
    explicit Client(HANDLE pipe)
        : pipe_(pipe), readDone_(CreateEventW(nullptr, TRUE, FALSE, nullptr)), writeDone_(CreateEventW(nullptr, TRUE, FALSE, nullptr)) {}
    ~Client() {
        CloseHandle(readDone_);
        CloseHandle(writeDone_);
        CloseHandle(pipe_);
    }

    /// Writes one message as a line. The pipe is opened for overlapped use, so each write waits for itself.
    bool send(const json& message) {
        const std::string line = message.dump() + "\n";
        std::lock_guard lock(writeMutex_);
        OVERLAPPED ov{};
        ov.hEvent = writeDone_;
        DWORD written = 0;
        if (!WriteFile(pipe_, line.data(), static_cast<DWORD>(line.size()), nullptr, &ov) && GetLastError() != ERROR_IO_PENDING) {
            return false;
        }
        return GetOverlappedResult(pipe_, &ov, &written, TRUE) && written == line.size();
    }

    /// Reads what's arrived, waking early if the service is stopping. Returns 0 when the app has gone.
    DWORD read(char* buf, DWORD size) {
        OVERLAPPED ov{};
        ov.hEvent = readDone_;
        DWORD got = 0;
        if (!ReadFile(pipe_, buf, size, nullptr, &ov) && GetLastError() != ERROR_IO_PENDING) return 0;
        HANDLE waits[] = {gStopEvent, readDone_};
        if (WaitForMultipleObjects(2, waits, FALSE, INFINITE) != WAIT_OBJECT_0 + 1) {
            CancelIoEx(pipe_, &ov);
            GetOverlappedResult(pipe_, &ov, &got, TRUE);
            return 0;
        }
        return GetOverlappedResult(pipe_, &ov, &got, FALSE) ? got : 0;
    }

    HANDLE pipe() const { return pipe_; }
    std::atomic<bool> wantsMeters{false};
    std::atomic<bool> closed{false};

private:
    HANDLE pipe_;
    HANDLE readDone_, writeDone_;
    std::mutex writeMutex_;
};

std::mutex gClientsMutex;
std::vector<std::shared_ptr<Client>> gClients;

void broadcast(const json& message, bool metersOnly = false) {
    std::vector<std::shared_ptr<Client>> clients;
    {
        std::lock_guard lock(gClientsMutex);
        clients = gClients;
    }
    for (auto& c : clients) {
        if (!c->closed && (!metersOnly || c->wantsMeters)) c->send(message);
    }
}

// ---------------------------------------------------------------------------------------------------------
// Outputs

json outputsJson(const std::vector<sys::Output>& outputs) {
    json list = json::array();
    for (const auto& o : outputs) {
        list.push_back({{"id", narrow(o.id)},
                        {"guid", narrow(o.guid)},
                        {"name", narrow(o.name)},
                        {"device", narrow(o.device)},
                        {"adapter", narrow(o.adapter)},
                        {"bus", narrow(o.bus)},
                        {"kind", narrow(sys::kindName(o.kind))},
                        {"active", o.state == DEVICE_STATE_ACTIVE},
                        {"default", o.isDefault},
                        {"hooked", o.hooked}});
    }
    return list;
}

bool autoHookEnabled() {
    DWORD value = 1, size = sizeof(value);
    RegGetValueW(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Eigentone", L"AutoHook", RRF_RT_REG_DWORD, nullptr, &value, &size);
    return value != 0;
}

/// Tells the watcher something about outputs changed. Windows calls it on its own threads, so it only
/// raises a flag; the watcher does the work.
class Notifications final : public IMMNotificationClient {
public:
    explicit Notifications(HANDLE changed) : changed_(changed) {}
    STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override {
        if (riid == __uuidof(IUnknown) || riid == __uuidof(IMMNotificationClient)) {
            *ppv = static_cast<IMMNotificationClient*>(this);
            AddRef();
            return S_OK;
        }
        *ppv = nullptr;
        return E_NOINTERFACE;
    }
    STDMETHODIMP_(ULONG) AddRef() override { return ++refs_; }
    STDMETHODIMP_(ULONG) Release() override { return --refs_; }  // owned by the watcher, which outlives it
    STDMETHODIMP OnDeviceStateChanged(LPCWSTR, DWORD) override { return SetEvent(changed_), S_OK; }
    STDMETHODIMP OnDeviceAdded(LPCWSTR) override { return SetEvent(changed_), S_OK; }
    STDMETHODIMP OnDeviceRemoved(LPCWSTR) override { return SetEvent(changed_), S_OK; }
    STDMETHODIMP OnDefaultDeviceChanged(EDataFlow flow, ERole role, LPCWSTR) override {
        if (flow == eRender && role == eConsole) SetEvent(changed_);
        return S_OK;
    }
    STDMETHODIMP OnPropertyValueChanged(LPCWSTR, const PROPERTYKEY) override { return S_OK; }

private:
    HANDLE changed_;
    std::atomic<ULONG> refs_{1};
};

/// Keeps the list of outputs fresh, hooks new ones, and tells apps when anything they show would change:
/// an output coming or going, the default changing, or headphones going into a laptop's jack.
void watchOutputs() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    HANDLE changed = CreateEventW(nullptr, FALSE, TRUE, nullptr);
    Notifications notifications(changed);
    IMMDeviceEnumerator* en = nullptr;
    if (SUCCEEDED(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_ALL, IID_PPV_ARGS(&en)))) {
        en->RegisterEndpointNotificationCallback(&notifications);
    }
    json last;
    HANDLE waits[] = {gStopEvent, changed};
    while (true) {
        // Jacks don't announce themselves, so the list is also checked every second.
        const DWORD r = WaitForMultipleObjects(2, waits, FALSE, 1000);
        if (r == WAIT_OBJECT_0) break;
        if (r == WAIT_OBJECT_0 + 1) Sleep(300);  // let a burst of changes settle
        auto outputs = sys::listOutputs();
        if (autoHookEnabled() && sys::isAdmin()) {
            for (auto& o : outputs) {
                if (o.state == DEVICE_STATE_ACTIVE && !o.hooked && sys::hook(o.guid) == 0) {
                    o.hooked = true;
                    log("outputs: hooked %s; it takes effect the next time Windows Audio starts", narrow(o.name).c_str());
                }
            }
        }
        json now = outputsJson(outputs);
        if (now != last) {
            broadcast({{"op", "outputs"}, {"outputs", now}});
            for (const auto& o : outputs) {
                if (o.isDefault) log("outputs: playing on %s, %s", narrow(o.name).c_str(), narrow(sys::kindName(o.kind)).c_str());
            }
            last = std::move(now);
        }
    }
    if (en) {
        en->UnregisterEndpointNotificationCallback(&notifications);
        en->Release();
    }
    CloseHandle(changed);
    CoUninitialize();
}

// ---------------------------------------------------------------------------------------------------------
// Volume: where the playing output's volume is, in dB, for quiet listening (Android reads the phone's volume)

/// Raised by Windows on its own threads when the volume or mute changes; the watcher does the reading.
class VolumeNotifications final : public IAudioEndpointVolumeCallback {
public:
    explicit VolumeNotifications(HANDLE changed) : changed_(changed) {}
    STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override {
        if (riid == __uuidof(IUnknown) || riid == __uuidof(IAudioEndpointVolumeCallback)) {
            *ppv = static_cast<IAudioEndpointVolumeCallback*>(this);
            AddRef();
            return S_OK;
        }
        *ppv = nullptr;
        return E_NOINTERFACE;
    }
    STDMETHODIMP_(ULONG) AddRef() override { return ++refs_; }
    STDMETHODIMP_(ULONG) Release() override { return --refs_; }  // owned by the watcher, which outlives it
    STDMETHODIMP OnNotify(PAUDIO_VOLUME_NOTIFICATION_DATA) override { return SetEvent(changed_), S_OK; }

private:
    HANDLE changed_;
    std::atomic<ULONG> refs_{1};
};

std::mutex gVolumeMutex;
json gVolume;  // the last reading, sent to each app as it asks

double tenth(double v) { return std::round(v * 10) / 10; }

/// Follows the default output's volume and tells apps whenever it moves, or the default output changes.
void watchVolume() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    HANDLE changed = CreateEventW(nullptr, FALSE, TRUE, nullptr);
    VolumeNotifications notifications(changed);
    IMMDeviceEnumerator* en = nullptr;
    CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_ALL, IID_PPV_ARGS(&en));
    IAudioEndpointVolume* volume = nullptr;
    std::wstring guid;
    HANDLE waits[] = {gStopEvent, changed};
    while (true) {
        // A change of default output has no volume notification, so it's also checked every second.
        if (WaitForMultipleObjects(2, waits, FALSE, 1000) == WAIT_OBJECT_0) break;
        IMMDevice* def = nullptr;
        std::wstring now;
        if (en && SUCCEEDED(en->GetDefaultAudioEndpoint(eRender, eConsole, &def))) {
            LPWSTR id = nullptr;
            if (SUCCEEDED(def->GetId(&id))) {
                now = sys::guidOf(id);
                CoTaskMemFree(id);
            }
        }
        if (now != guid) {
            if (volume) {
                volume->UnregisterControlChangeNotify(&notifications);
                volume->Release();
                volume = nullptr;
            }
            guid = now;
            if (def && SUCCEEDED(def->Activate(__uuidof(IAudioEndpointVolume), CLSCTX_ALL, nullptr, reinterpret_cast<void**>(&volume)))) {
                volume->RegisterControlChangeNotify(&notifications);
            }
        }
        if (def) def->Release();
        if (!volume) continue;
        float db = 0, minDb = 0, maxDb = 0, step = 0;
        BOOL muted = FALSE;
        if (FAILED(volume->GetMasterVolumeLevel(&db)) || FAILED(volume->GetVolumeRange(&minDb, &maxDb, &step))) continue;
        volume->GetMute(&muted);
        json reading{{"op", "volume"},
                     {"output", narrow(guid)},
                     {"db", tenth(db)},
                     {"minDb", tenth(minDb)},
                     {"maxDb", tenth(maxDb)},
                     {"muted", muted != FALSE}};
        bool moved;
        {
            std::lock_guard lock(gVolumeMutex);
            moved = reading != gVolume;
            if (moved) gVolume = reading;
        }
        if (moved) broadcast(reading);
    }
    if (volume) {
        volume->UnregisterControlChangeNotify(&notifications);
        volume->Release();
    }
    if (en) en->Release();
    CloseHandle(changed);
    CoUninitialize();
}

// ---------------------------------------------------------------------------------------------------------
// Meters: level and spectrum of what's playing on the default output

std::wstring defaultGuid() {
    for (const auto& o : sys::listOutputs()) {
        if (o.isDefault) return o.guid;
    }
    return {};
}

void sendMeters() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    dsp::Fft<float> fft(kScopeFrames);
    std::vector<std::complex<float>> buf(kScopeFrames);
    std::vector<float> window(kScopeFrames);
    for (uint32_t i = 0; i < kScopeFrames; ++i) {
        window[i] = static_cast<float>(0.5 - 0.5 * std::cos(2 * 3.14159265358979 * i / (kScopeFrames - 1)));
    }
    std::wstring guid = defaultGuid();
    int tick = 0;
    while (WaitForSingleObject(gStopEvent, 1000 / kMetersPerSecond) == WAIT_TIMEOUT) {
        bool anyone = false;
        {
            std::lock_guard lock(gClientsMutex);
            for (auto& c : gClients) anyone |= c->wantsMeters && !c->closed;
        }
        if (!anyone || !gSettings.block()) continue;
        if (++tick % kMetersPerSecond == 0) guid = defaultGuid();  // follow the default output, once a second

        const SharedBlock* b = gSettings.block();
        const EndpointSlot* slot = nullptr;
        for (const auto& s : b->slots) {
            if (s.owner && s.active && _wcsicmp(s.endpoint, guid.c_str()) == 0) slot = &s;
        }
        if (!slot) {
            broadcast({{"op", "meters"}, {"playing", false}}, true);
            continue;
        }
        const LONG end = slot->scopePos;
        MemoryBarrier();
        for (uint32_t i = 0; i < kScopeFrames; ++i) {
            buf[i] = slot->scope[static_cast<uint32_t>(end + static_cast<LONG>(i)) % kScopeFrames] * window[i];
        }
        fft.forward(buf.data());
        // Power in sixth-octave bands, in dB relative to a full-scale sine.
        const double rate = slot->sampleRate ? slot->sampleRate : 48000.0;
        const double binHz = rate / kScopeFrames;
        const double fullScale = (kScopeFrames / 4.0) * (kScopeFrames / 4.0);  // a full-scale sine through a Hann window
        json bands = json::array();
        for (int k = 0; k < kSpectrumBands; ++k) {
            const double lo = 20.0 * std::pow(1000.0, static_cast<double>(k) / kSpectrumBands);
            const double hi = 20.0 * std::pow(1000.0, static_cast<double>(k + 1) / kSpectrumBands);
            size_t from = static_cast<size_t>(std::floor(lo / binHz)), to = static_cast<size_t>(std::ceil(hi / binHz));
            to = std::min<size_t>(std::max(to, from + 1), kScopeFrames / 2);
            double power = 0;
            for (size_t i = from; i < to; ++i) power = std::max(power, static_cast<double>(std::norm(buf[i])));
            bands.push_back(std::round(10 * std::log10(power / fullScale + 1e-12) * 10) / 10);
        }
        const double peak = slot->peakOut;
        broadcast({{"op", "meters"},
                   {"playing", true},
                   {"peakDb", std::round(20 * std::log10(peak + 1e-9) * 10) / 10},
                   {"spectrumDb", bands}},
                  true);
    }
    CoUninitialize();
}

// ---------------------------------------------------------------------------------------------------------
// Requests

const std::regex kGuid(R"(^\{[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\}$)");

json handle(Client& client, const json& request) {
    const std::string op = request.value("op", "");
    json reply{{"op", op}};
    if (request.contains("id")) reply["id"] = request["id"];
    const bool needsEngine = op == "params" || op == "apply" || op == "status";
    if (needsEngine && !gSettings.block()) {
        reply["error"] = "the sound engine isn't available; reinstall Eigentone";
        return reply;
    }

    if (op == "hello") {
        reply["version"] = kServiceVersion;
    } else if (op == "outputs") {
        reply["outputs"] = outputsJson(sys::listOutputs());
    } else if (op == "params") {
        reply["params"] = service::paramsToJson(gSettings.current());
    } else if (op == "apply") {
        reply["seq"] = gSettings.apply(service::paramsFromJson(request.at("params")));
    } else if (op == "status") {
        json engines = json::array();
        for (const auto& s : gSettings.block()->slots) {
            if (!s.owner) continue;
            engines.push_back({{"output", narrow(sys::lower(s.endpoint))},
                               {"sampleRate", s.sampleRate},
                               {"channels", s.channels},
                               {"latencyMs", s.sampleRate ? 1000.0 * s.latencyFrames / s.sampleRate : 0.0},
                               {"appliedSeq", s.appliedSeq},
                               {"active", s.active != 0}});
        }
        reply["engines"] = engines;
        reply["seq"] = gSettings.block()->seq;
    } else if (op == "meters") {
        client.wantsMeters = request.value("on", true);
    } else if (op == "volume") {
        std::lock_guard lock(gVolumeMutex);
        if (gVolume.is_object()) {
            for (const auto& [key, value] : gVolume.items()) {
                if (key != "op") reply[key] = value;
            }
        }
    } else if (op == "hook" || op == "unhook") {
        const std::string guid = request.at("guid").get<std::string>();
        if (!std::regex_match(guid, kGuid)) throw std::invalid_argument("not an output ID");
        const int rc = op == "hook" ? sys::hook(widen(guid)) : sys::unhook(widen(guid));
        reply["ok"] = rc == 0;
        reply["restartNeeded"] = rc == 0;
    } else if (op == "restartAudio") {
        reply["ok"] = sys::stopAudio() && sys::startAudio();
    } else {
        reply["error"] = "unknown request";
    }
    return reply;
}

void serveClient(std::shared_ptr<Client> client) {
    std::string pending;
    char chunk[4096];
    while (!gStopping) {
        const DWORD read = client->read(chunk, sizeof(chunk));
        if (read == 0) break;
        pending.append(chunk, read);
        if (pending.size() > (1u << 20)) break;  // no request is anywhere near a megabyte
        size_t nl;
        while ((nl = pending.find('\n')) != std::string::npos) {
            const std::string line = pending.substr(0, nl);
            pending.erase(0, nl + 1);
            if (line.find_first_not_of(" \r\t") == std::string::npos) continue;
            json reply;
            try {
                reply = handle(*client, json::parse(line));
            } catch (const std::exception& e) {
                reply = {{"op", "error"}, {"error", e.what()}};
            }
            client->send(reply);
        }
    }
    client->closed = true;
    std::lock_guard lock(gClientsMutex);
    std::erase(gClients, client);
}

void servePipe() {
    PSECURITY_DESCRIPTOR sd = nullptr;
    ConvertStringSecurityDescriptorToSecurityDescriptorW(kPipeSddl, SDDL_REVISION_1, &sd, nullptr);
    SECURITY_ATTRIBUTES sa{sizeof(sa), sd, FALSE};
    HANDLE connected = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    // EIGENTONE_PIPE serves a test copy on a pipe of its own, beside the installed service.
    wchar_t custom[128] = {};
    const std::wstring name = GetEnvironmentVariableW(L"EIGENTONE_PIPE", custom, 128) ? std::wstring(L"\\\\.\\pipe\\") + custom : kPipeName;
    while (!gStopping) {
        HANDLE pipe = CreateNamedPipeW(name.c_str(), PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED,
                                       PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
                                       PIPE_UNLIMITED_INSTANCES, 64 * 1024, 64 * 1024, 0, &sa);
        if (pipe == INVALID_HANDLE_VALUE) {
            log("pipe: couldn't create it (%lu)", GetLastError());
            WaitForSingleObject(gStopEvent, 1000);
            continue;
        }
        OVERLAPPED ov{};
        ov.hEvent = connected;
        ResetEvent(connected);
        bool ok = ConnectNamedPipe(pipe, &ov) != FALSE;
        if (!ok) {
            const DWORD err = GetLastError();
            if (err == ERROR_IO_PENDING) {
                HANDLE waits[] = {gStopEvent, connected};
                ok = WaitForMultipleObjects(2, waits, FALSE, INFINITE) == WAIT_OBJECT_0 + 1;
                DWORD unused;
                ok = ok && GetOverlappedResult(pipe, &ov, &unused, FALSE);
            } else {
                ok = err == ERROR_PIPE_CONNECTED;
            }
        }
        if (!ok) {
            CancelIo(pipe);
            CloseHandle(pipe);
            continue;
        }
        auto client = std::make_shared<Client>(pipe);
        {
            std::lock_guard lock(gClientsMutex);
            gClients.push_back(client);
        }
        std::thread(serveClient, client).detach();
    }
    CloseHandle(connected);
    LocalFree(sd);
}

// ---------------------------------------------------------------------------------------------------------
// Running

void run() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    if (!gSettings.open()) {
        log("settings: couldn't open the shared block; is the plug-in installed?");
    }
    std::thread watcher(watchOutputs);
    std::thread meters(sendMeters);
    std::thread volume(watchVolume);
    std::thread pipe(servePipe);
    log("service: running");
    WaitForSingleObject(gStopEvent, INFINITE);
    gStopping = true;
    pipe.join();
    meters.join();
    volume.join();
    watcher.join();
    log("service: stopped");
    CoUninitialize();
}

SERVICE_STATUS_HANDLE gStatusHandle = nullptr;
SERVICE_STATUS gStatus{SERVICE_WIN32_OWN_PROCESS, SERVICE_START_PENDING, 0, NO_ERROR, 0, 0, 0};

void report(DWORD state) {
    gStatus.dwCurrentState = state;
    gStatus.dwControlsAccepted = state == SERVICE_RUNNING ? SERVICE_ACCEPT_STOP | SERVICE_ACCEPT_SHUTDOWN : 0;
    SetServiceStatus(gStatusHandle, &gStatus);
}

DWORD WINAPI control(DWORD code, DWORD, LPVOID, LPVOID) {
    if (code == SERVICE_CONTROL_STOP || code == SERVICE_CONTROL_SHUTDOWN) {
        report(SERVICE_STOP_PENDING);
        SetEvent(gStopEvent);
        return NO_ERROR;
    }
    return code == SERVICE_CONTROL_INTERROGATE ? NO_ERROR : ERROR_CALL_NOT_IMPLEMENTED;
}

void WINAPI serviceMain(DWORD, LPWSTR*) {
    gStatusHandle = RegisterServiceCtrlHandlerExW(kServiceName, control, nullptr);
    if (!gStatusHandle) return;
    report(SERVICE_RUNNING);
    run();
    report(SERVICE_STOPPED);
}

BOOL WINAPI onConsoleClose(DWORD) {
    SetEvent(gStopEvent);
    return TRUE;
}

}  // namespace

int wmain(int argc, wchar_t** argv) {
    gStopEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (argc > 1 && _wcsicmp(argv[1], L"--console") == 0) {
        gConsole = true;
        SetConsoleCtrlHandler(onConsoleClose, TRUE);
        run();
        return 0;
    }
    SERVICE_TABLE_ENTRYW table[] = {{const_cast<LPWSTR>(kServiceName), serviceMain}, {nullptr, nullptr}};
    if (!StartServiceCtrlDispatcherW(table)) {
        std::fprintf(stderr, "This is Eigentone's background service. Run it with --console to test it here.\n");
        return 1;
    }
    return 0;
}
