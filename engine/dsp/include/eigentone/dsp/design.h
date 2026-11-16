// Eigentone for Windows: turning settings into filters. Runs off the audio thread.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#pragma once

#include <cstddef>
#include <vector>

namespace eigentone::dsp {

/// The EQ filter's length at a sample rate: about 170 ms, enough to shape the lowest bass precisely.
size_t eqTaps(double sampleRate);

/**
 * The curve's gain at a frequency, from Android-style bands (band i reaches up to cutoffs[i]).
 * Each band's gain sits at its centre, and the curve runs straight between centres on a log
 * frequency axis, so it follows the shape the bands were sampled from rather than stepping.
 */
double curveGainDb(const float* cutoffs, const float* gainsDb, size_t count, double hz);

/**
 * A minimum-phase filter with the curve's magnitude: all the delay a filter must have, and no more,
 * so the EQ adds no pre-echo and almost no latency. Made by the cepstrum method.
 */
std::vector<float> designMinimumPhase(const float* cutoffs, const float* gainsDb, size_t count, double sampleRate,
                                      size_t taps);

/// A second-order section. Kept in double: at 192 kHz, low crossovers lose precision in float.
struct Biquad {
    double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;

    static Biquad lowPass(double hz, double q, double sampleRate);
    static Biquad highPass(double hz, double q, double sampleRate);
    static Biquad allPass(double hz, double q, double sampleRate);
    static Biquad lowShelf(double hz, double gainDb, double q, double sampleRate);
    static Biquad highShelf(double hz, double gainDb, double q, double sampleRate);
};

/// Running state for one Biquad on one signal.
struct BiquadState {
    double z1 = 0, z2 = 0;
    double process(const Biquad& f, double x) {
        const double y = f.b0 * x + z1;
        z1 = f.b1 * x - f.a1 * y + z2;
        z2 = f.b2 * x - f.a2 * y;
        return y;
    }
};

/// Magnitude in dB of a Biquad at a frequency, for tests and drawing.
double biquadGainDb(const Biquad& f, double hz, double sampleRate);

}  // namespace eigentone::dsp
