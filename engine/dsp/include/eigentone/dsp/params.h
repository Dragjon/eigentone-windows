// Eigentone for Windows: everything the sound engine can be told, as plain numbers.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
//
// The stages and their parameters copy Android's DynamicsProcessing, which Eigentone for Android drives,
// so the app's planning code (curves added into one EQ, dynamics planned into compressor bands) carries
// over unchanged. Fixed-size and free of pointers, so it can sit in shared memory as it is.
#pragma once

#include <cstdint>

namespace eigentone::dsp {

inline constexpr uint32_t kMaxEqBands = 256;
inline constexpr uint32_t kMaxMbcBands = 8;
inline constexpr uint32_t kCurveChannels = 2;  // left and right; further channels follow left or right in turn

/// The combined curve, as Android's pre-EQ takes it: band i reaches up to cutoffHz[i] and has gainDb there.
struct EqParams {
    uint32_t bandCount;  // 0 means flat
    float cutoffHz[kMaxEqBands];
    float gainDb[kCurveChannels][kMaxEqBands];
};

/// One compressor band, covering from the previous band's cutoff up to its own.
struct MbcBand {
    uint32_t enabled;
    float cutoffHz;
    float attackMs;
    float releaseMs;
    float ratio;
    float thresholdDb;
    float kneeDb;
    float noiseGateDb;    // below this the band is expanded downward
    float expanderRatio;  // 1 means no expansion
    float preGainDb;
    float postGainDb;
};

struct MbcParams {
    uint32_t enabled;
    uint32_t bandCount;
    MbcBand bands[kMaxMbcBands];
};

struct LimiterParams {
    uint32_t enabled;
    float attackMs;  // also how far ahead it looks, so peaks are caught before they arrive
    float releaseMs;
    float ratio;
    float thresholdDb;
    float postGainDb;
};

/// Headphone surround: each ear hears a little of the other side, delayed and softened, as with speakers.
struct VirtualizerParams {
    uint32_t enabled;
    float strength;  // 0 to 1
};

/// The same parameters as Android's EnvironmentalReverb, in the same units (millibels and milliseconds),
/// except diffusion and density, which run from 0 to 1 rather than 0 to 1000.
struct ReverbParams {
    uint32_t enabled;
    float roomLevelMb;
    float roomHfLevelMb;
    float decayTimeMs;
    float decayHfRatio;
    float reflectionsLevelMb;
    float reflectionsDelayMs;
    float reverbLevelMb;
    float reverbDelayMs;
    float diffusion;
    float density;
};

struct Params {
    uint32_t enabled;  // 0 passes the sound through, after the same delay, so switching never jumps
    float inputGainDb[kCurveChannels];
    EqParams eq;
    MbcParams mbc;
    VirtualizerParams virtualizer;
    ReverbParams reverb;
    LimiterParams limiter;
};

/// Settings that leave the sound unchanged.
inline Params neutralParams() {
    Params p{};
    p.enabled = 1;
    p.limiter = {0, 1.0f, 60.0f, 10.0f, -1.0f, 0.0f};
    p.reverb = {0, -1000.0f, -100.0f, 1490.0f, 0.83f, -2602.0f, 7.0f, 200.0f, 11.0f, 1.0f, 1.0f};
    return p;
}

}  // namespace eigentone::dsp
