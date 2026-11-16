// Eigentone for Windows: the memory the audio plug-in shares with the rest of Eigentone.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
//
// The plug-in runs inside Windows' audio engine (audiodg.exe), where it may never wait on a lock or a
// file. Settings come in and meters go out through this one block of shared memory instead: plain
// numbers that the plug-in reads at the start of each audio buffer and writes at the end.
#pragma once

#include <windows.h>
#include <cstdint>

#pragma warning(push)
#pragma warning(disable : 4324)  // padding from alignas is the point: one slot per cache line

namespace eigentone {

// {92B47D94-3398-48AE-B291-9A994AE356CB}
inline constexpr GUID kApoClsid = {0x92b47d94, 0x3398, 0x48ae, {0xb2, 0x91, 0x9a, 0x99, 0x4a, 0xe3, 0x56, 0xcb}};
inline constexpr wchar_t kApoClsidText[] = L"{92B47D94-3398-48AE-B291-9A994AE356CB}";

inline constexpr wchar_t kSectionName[] = L"Global\\EigentoneEngine.1";
inline constexpr uint32_t kMagic = 0x45474E54;  // "EGNT"
inline constexpr uint32_t kVersion = 1;
inline constexpr uint32_t kMaxSlots = 8;

/// One output's plug-in reports here. A plug-in claims a free slot when it starts processing.
struct alignas(64) EndpointSlot {
    volatile LONG owner;           // 0 when free, otherwise the claiming process ID
    volatile LONG active;          // 1 while processing
    volatile LONG64 heartbeat;     // counts processed buffers, so a reader can tell it is alive
    volatile LONG64 frames;        // frames processed since it started
    volatile uint32_t sampleRate;
    volatile uint32_t channels;
    volatile float peakIn;         // linear, the loudest sample of the last buffer before processing
    volatile float peakOut;        // and after
    wchar_t endpoint[64];          // the output's ID, for matching to a device in the app
};

struct SharedBlock {
    volatile uint32_t magic;
    volatile uint32_t version;
    volatile LONG enabled;         // 0 passes sound through untouched
    volatile float gainDb;         // M1: a plain level change, to prove the chain works
    uint8_t reserved[48];
    EndpointSlot slots[kMaxSlots];
};

/// Who may open the block: the system, administrators and the audio service fully, signed-in users to
/// read meters and change settings.
inline constexpr wchar_t kSectionSddl[] = L"D:(A;;GA;;;SY)(A;;GA;;;BA)(A;;GA;;;LS)(A;;GRGW;;;AU)S:(ML;;NW;;;LW)";

}  // namespace eigentone

#pragma warning(pop)
