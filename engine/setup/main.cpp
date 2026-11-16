// Eigentone for Windows: installs the audio plug-in, hooks it into outputs, and puts everything back.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
//
//   eigentone-setup status                  what is installed, which outputs are hooked, live meters
//   eigentone-setup devices                 every output, what Eigentone takes it to be, and why
//   eigentone-setup install [endpoint-guid]  copy and register the plug-in, hook the default (or given) output
//   eigentone-setup unhook  [endpoint-guid]  restore one output (or all) exactly as it was
//   eigentone-setup uninstall               unhook every output, unregister, remove the files
//   eigentone-setup restart-audio           restart Windows Audio so hooks take effect
//   eigentone-setup set ...                 change the live sound, to hear each stage work:
//       on | off | gain <dB> | tone <bass dB> <treble dB> | flat | compressor on|off
//       limiter on|off | surround <0-1> | reverb room|hall|plate|off
#include <windows.h>
#include <objbase.h>
#include <sddl.h>

#include <algorithm>
#include <cmath>
#include <cwctype>
#include <optional>
#include <string>
#include <vector>

#include "eigentone/dsp/design.h"
#include "eigentone/system.h"
#include "eigentone/shared.h"

using namespace eigentone;

namespace {

using namespace eigentone::sys;

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
    dsp::Params p{};
    readParams(b, p);
    wprintf(L"\nEngine: %ls, level %+.1f dB, EQ %u bands, compressor %ls, surround %.2f, reverb %ls, limiter %ls\n",
            p.enabled ? L"on" : L"off (passing sound through)", p.inputGainDb[0], p.eq.bandCount,
            p.mbc.enabled ? L"on" : L"off", p.virtualizer.enabled ? p.virtualizer.strength : 0.0f,
            p.reverb.enabled ? L"on" : L"off", p.limiter.enabled ? L"on" : L"off");
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

    const std::wstring here = source.substr(0, source.rfind(L'\\'));
    const std::wstring dir = installDir();
    const std::wstring dll = dir + L"\\EigentoneApo.dll";
    const std::wstring service = dir + L"\\eigentone-service.exe";
    CreateDirectoryW(dir.c_str(), nullptr);

    // The service goes first, so its file isn't in use while it's replaced.
    stopEigentoneService();
    wprintf(L"Stopping Windows Audio...\n");
    if (!stopAudio()) return fail(L"couldn't stop Windows Audio");
    const bool copied = CopyFileW(source.c_str(), dll.c_str(), FALSE);
    int rc = copied ? registerApo(dll) : fail(L"couldn't copy the plug-in", static_cast<LONG>(GetLastError()));
    if (rc == 0) rc = hook(*endpoint);
    if (rc == 0 && !CopyFileW((here + L"\\eigentone-service.exe").c_str(), service.c_str(), FALSE)) {
        rc = fail(L"couldn't copy the service", static_cast<LONG>(GetLastError()));
    }
    wprintf(L"Starting Windows Audio...\n");
    if (!startAudio()) fail(L"couldn't start Windows Audio; restart the PC");
    if (rc == 0) {
        wprintf(L"Starting Eigentone's service...\n");
        rc = installEigentoneService(service);
    }
    return rc;
}

int uninstall() {
    int rc = 0;
    removeEigentoneService();
    for (const auto& e : hookedOutputs()) rc |= unhook(e);
    wprintf(L"Stopping Windows Audio...\n");
    stopAudio();
    unregisterApo();
    const std::wstring dir = installDir();
    const std::wstring dll = dir + L"\\EigentoneApo.dll";
    for (const std::wstring& file : {dll, dir + L"\\eigentone-service.exe"}) {
        if (!DeleteFileW(file.c_str()) && GetLastError() != ERROR_FILE_NOT_FOUND) {
            MoveFileExW(file.c_str(), nullptr, MOVEFILE_DELAY_UNTIL_REBOOT);
            wprintf(L"%ls is in use; it will be removed when the PC restarts.\n", file.c_str());
        }
    }
    RemoveDirectoryW(dir.c_str());
    RegDeleteTreeW(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Eigentone");
    wprintf(L"Starting Windows Audio...\n");
    if (!startAudio()) fail(L"couldn't start Windows Audio; restart the PC");
    wprintf(rc ? L"Uninstalled, with errors above.\n" : L"Uninstalled. Every output is as it was.\n");
    return rc;
}

/// A test curve on Android's 128-band layout: a bass shelf at 120 Hz and a treble shelf at 6 kHz.
void toneCurve(dsp::EqParams& eq, double bassDb, double trebleDb, double rate) {
    const dsp::Biquad bass = dsp::Biquad::lowShelf(120, bassDb, 0.7071, rate);
    const dsp::Biquad treble = dsp::Biquad::highShelf(6000, trebleDb, 0.7071, rate);
    eq.bandCount = 128;
    for (uint32_t i = 0; i < 128; ++i) eq.cutoffHz[i] = static_cast<float>(20.0 * std::pow(1000.0, (i + 1) / 128.0));
    for (uint32_t i = 0; i < 128; ++i) {
        const double lo = i == 0 ? eq.cutoffHz[0] * 0.5 : eq.cutoffHz[i - 1];
        const double centre = std::sqrt(lo * eq.cutoffHz[i]);
        const double g = dsp::biquadGainDb(bass, centre, rate) + dsp::biquadGainDb(treble, centre, rate);
        eq.gainDb[0][i] = eq.gainDb[1][i] = static_cast<float>(g);
    }
}

bool isOn(const wchar_t* v) { return lstrcmpiW(v, L"on") == 0; }

int set(int argc, wchar_t** argv) {
    SharedBlock* b = openShared(true);
    if (!b) return fail(L"the engine isn't running; play something on a hooked output first");
    if (argc < 3) return 2;
    dsp::Params p{};
    while (readParams(b, p) < 0) Sleep(1);
    const std::wstring what = lower(argv[2]);
    const wchar_t* v = argc > 3 ? argv[3] : L"";
    if (what == L"on" || what == L"off") {
        p.enabled = what == L"on";
    } else if (what == L"gain") {
        p.inputGainDb[0] = p.inputGainDb[1] = static_cast<float>(_wtof(v));
    } else if (what == L"tone") {
        toneCurve(p.eq, _wtof(v), argc > 4 ? _wtof(argv[4]) : 0.0, 48000);
    } else if (what == L"flat") {
        p.eq.bandCount = 0;
    } else if (what == L"compressor") {
        // A gentle three-band levelling style, as night mode might use.
        p.mbc.enabled = isOn(v);
        p.mbc.bandCount = 3;
        p.mbc.bands[0] = {1, 200, 20, 300, 2, -20, 6, -70, 1, 0, 4};
        p.mbc.bands[1] = {1, 2500, 10, 200, 2, -20, 6, -70, 1, 0, 4};
        p.mbc.bands[2] = {1, 24000, 5, 150, 2, -20, 6, -70, 1, 0, 4};
    } else if (what == L"limiter") {
        p.limiter = {isOn(v) ? 1u : 0u, 1.0f, 60.0f, 10.0f, -1.0f, 0.0f};
    } else if (what == L"surround") {
        p.virtualizer = {_wtof(v) > 0 ? 1u : 0u, static_cast<float>(_wtof(v))};
    } else if (what == L"reverb") {
        // The Android app's rooms (ReverbRoom), at a moderate amount: the reverb 6 dB below the music.
        const std::wstring kind = lower(v);
        if (kind == L"room") p.reverb = {1, 0, -600, 1500, 0.83f, -1000, 10, -600, 20, 1, 1};
        else if (kind == L"hall") p.reverb = {1, 0, -600, 4000, 0.7f, -1000, 20, -600, 30, 1, 1};
        else if (kind == L"plate") p.reverb = {1, 0, -200, 2200, 0.9f, -1000, 5, -600, 10, 1, 0.75f};
        else p.reverb.enabled = 0;
    } else {
        return 2;
    }
    writeParams(b, p);
    // Wait for every running plug-in to report the change, so the result is known when this returns.
    const LONG64 want = b->seq;
    for (int i = 0; i < 100; ++i) {
        bool all = true;
        for (const EndpointSlot& s : b->slots) all &= !s.owner || s.appliedSeq >= want;
        if (all) {
            wprintf(L"Applied.\n");
            return 0;
        }
        Sleep(10);
    }
    wprintf(L"Written, but no plug-in has picked it up yet (is anything playing?).\n");
    return 0;
}

int devices() {
    for (const auto& o : listOutputs()) {
        wprintf(L"%ls%ls\n  kind: %ls\n  device: %ls | adapter: %ls | bus: %ls | form factor: %d | state: %lu%ls\n",
                o.name.c_str(), o.isDefault ? L"  (default)" : L"", kindName(o.kind), o.device.c_str(), o.adapter.c_str(),
                o.bus.c_str(), o.formFactor, o.state, o.hooked ? L" | hooked" : L"");
        for (const auto& j : o.jacks) {
            wprintf(L"  jack: connection %d, location %d, port %d, %ls\n", j.connection, j.location, j.port,
                    j.connected ? L"connected" : L"not connected");
        }
    }
    return 0;
}

int usage() {
    fwprintf(stderr,
             L"usage: eigentone-setup status | devices | install [endpoint] | unhook [endpoint] | uninstall | restart-audio | gain <dB>|on|off\n");
    return 2;
}

}  // namespace

int wmain(int argc, wchar_t** argv) {
    if (argc < 2) return usage();
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const std::wstring cmd = lower(argv[1]);
    if (cmd == L"status") return status();
    if (cmd == L"devices") return devices();
    if (cmd == L"set") return set(argc, argv) == 2 ? usage() : 0;

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
