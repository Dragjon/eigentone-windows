// Eigentone for Windows: the memory the audio plug-in shares with the rest of Eigentone.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
//
// The plug-in runs inside Windows' audio engine (audiodg.exe), where it may never wait on a lock or a
// file. Settings come in and meters go out through this one block of shared memory instead.
//
// Settings are guarded by a sequence number: a writer makes it odd, writes, then makes it even again.
// A reader copies the settings and keeps the copy only if the number was even and unchanged throughout,
// so it never acts on a half-written change, and a writer never waits.
#pragma once

#include <windows.h>
#include <cstdint>

#include "eigentone/dsp/params.h"

#pragma warning(push)
#pragma warning(disable : 4324)  // padding from alignas is the point: one slot per cache line

namespace eigentone {

// {92B47D94-3398-48AE-B291-9A994AE356CB}
inline constexpr GUID kApoClsid = {0x92b47d94, 0x3398, 0x48ae, {0xb2, 0x91, 0x9a, 0x99, 0x4a, 0xe3, 0x56, 0xcb}};
inline constexpr wchar_t kApoClsidText[] = L"{92B47D94-3398-48AE-B291-9A994AE356CB}";

// The version is in the name, so a plug-in and a tool from different versions never read each other's layout.
inline constexpr wchar_t kSectionName[] = L"Global\\EigentoneEngine.2";
inline constexpr uint32_t kMagic = 0x45474E54;  // "EGNT"
inline constexpr uint32_t kVersion = 2;
inline constexpr uint32_t kMaxSlots = 8;

/// One output's plug-in reports here. A plug-in claims a free slot when it starts processing.
struct alignas(64) EndpointSlot {
    volatile LONG owner;        // 0 when free, otherwise the claiming process ID
    volatile LONG active;       // 1 while processing
    volatile LONG64 heartbeat;  // counts processed buffers, so a reader can tell it is alive
    volatile LONG64 frames;     // frames processed since it started
    volatile uint32_t sampleRate;
    volatile uint32_t channels;
    volatile uint32_t latencyFrames;
    volatile LONG64 appliedSeq; // the settings it is playing, so a writer can tell they've arrived
    volatile float peakIn;      // linear, the loudest sample of the last buffer before processing
    volatile float peakOut;     // and after
    wchar_t endpoint[64];       // the output's ID, for matching to a device in the app
};

struct alignas(64) SharedBlock {
    volatile uint32_t magic;
    volatile uint32_t version;
    volatile LONG64 seq;        // odd while settings are being written
    dsp::Params params;
    EndpointSlot slots[kMaxSlots];
};

/// Who may open the block: the system, administrators and the audio service fully, signed-in users to
/// read meters and change settings.
inline constexpr wchar_t kSectionSddl[] = L"D:(A;;GA;;;SY)(A;;GA;;;BA)(A;;GA;;;LS)(A;;GRGW;;;AU)S:(ML;;NW;;;LW)";

/// Writes new settings. Only one writer at a time; the service will be that writer.
inline void writeParams(SharedBlock* block, const dsp::Params& params) {
    InterlockedIncrement64(&block->seq);
    MemoryBarrier();
    *const_cast<dsp::Params*>(&block->params) = params;
    MemoryBarrier();
    InterlockedIncrement64(&block->seq);
}

/// Copies the settings if they're whole. Returns the sequence number they were read at, or -1 if a
/// write was under way; try again shortly.
inline LONG64 readParams(const SharedBlock* block, dsp::Params& out) {
    const LONG64 before = block->seq;
    if (before & 1) return -1;
    MemoryBarrier();
    out = *const_cast<const dsp::Params*>(&block->params);
    MemoryBarrier();
    return block->seq == before ? before : -1;
}

}  // namespace eigentone

#pragma warning(pop)
