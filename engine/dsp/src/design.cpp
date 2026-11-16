// Eigentone for Windows: turning settings into filters.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#include "eigentone/dsp/design.h"

#include <algorithm>
#include <cmath>
#include <complex>
#include <numbers>

#include "eigentone/dsp/fft.h"

namespace eigentone::dsp {

namespace {
constexpr double kPi = std::numbers::pi;
constexpr double kEqLengthSeconds = 0.17;
constexpr size_t kCepstrumOversample = 4;  // keeps the cepstrum from wrapping round on sharp curves
constexpr size_t kTaperFraction = 8;       // the last eighth of the filter fades out, so it ends at silence
}  // namespace

size_t eqTaps(double sampleRate) {
    size_t taps = 1024;
    while (static_cast<double>(taps) < sampleRate * kEqLengthSeconds) taps <<= 1;
    return taps;
}

double curveGainDb(const float* cutoffs, const float* gainsDb, size_t count, double hz) {
    if (count == 0) return 0.0;
    if (count == 1) return gainsDb[0];
    const double x = std::log2(std::max(hz, 1.0));
    // Band i's centre, on a log axis, between its lower and upper edge. The first band's lower edge is
    // taken an octave below its top.
    auto centre = [&](size_t i) {
        const double lo = i == 0 ? cutoffs[0] * 0.5 : cutoffs[i - 1];
        return 0.5 * (std::log2(std::max(lo, 1.0)) + std::log2(std::max<double>(cutoffs[i], 1.0)));
    };
    if (x <= centre(0)) return gainsDb[0];
    if (x >= centre(count - 1)) return gainsDb[count - 1];
    size_t lo = 0, hi = count - 1;
    while (hi - lo > 1) {
        const size_t mid = (lo + hi) / 2;
        (centre(mid) <= x ? lo : hi) = mid;
    }
    const double c0 = centre(lo), c1 = centre(hi);
    const double t = c1 > c0 ? (x - c0) / (c1 - c0) : 0.0;
    return gainsDb[lo] + t * (gainsDb[hi] - gainsDb[lo]);
}

std::vector<float> designMinimumPhase(const float* cutoffs, const float* gainsDb, size_t count, double sampleRate,
                                      size_t taps) {
    const size_t m = taps * kCepstrumOversample;
    Fft<double> fft(m);
    std::vector<std::complex<double>> buf(m);

    // The log of the wanted magnitude, symmetric so its transform is real.
    const double toNeper = std::log(10.0) / 20.0;
    for (size_t k = 0; k <= m / 2; ++k) {
        const double hz = static_cast<double>(k) * sampleRate / static_cast<double>(m);
        const double v = curveGainDb(cutoffs, gainsDb, count, hz) * toNeper;
        buf[k] = v;
        if (k > 0 && k < m / 2) buf[m - k] = v;
    }

    // Real cepstrum, folded onto positive time: that makes the phase the minimum the magnitude allows.
    fft.inverse(buf.data());
    for (size_t n = 1; n < m / 2; ++n) buf[n] = 2.0 * buf[n].real();
    buf[0] = buf[0].real();
    buf[m / 2] = buf[m / 2].real();
    for (size_t n = m / 2 + 1; n < m; ++n) buf[n] = 0.0;

    fft.forward(buf.data());
    for (auto& v : buf) v = std::exp(v);
    fft.inverse(buf.data());

    std::vector<float> out(taps);
    const size_t taper = taps / kTaperFraction;
    for (size_t n = 0; n < taps; ++n) {
        double w = 1.0;
        if (n >= taps - taper) {
            const double t = static_cast<double>(n - (taps - taper)) / static_cast<double>(taper);
            w = 0.5 * (1.0 + std::cos(kPi * t));
        }
        out[n] = static_cast<float>(buf[n].real() * w);
    }
    return out;
}

// Robert Bristow-Johnson's Audio EQ Cookbook.
namespace {
struct Rbj {
    double w0, cosw, alpha;
    Rbj(double hz, double q, double sampleRate) {
        w0 = 2.0 * kPi * std::clamp(hz, 1.0, sampleRate * 0.49) / sampleRate;
        cosw = std::cos(w0);
        alpha = std::sin(w0) / (2.0 * q);
    }
};
Biquad normalise(double b0, double b1, double b2, double a0, double a1, double a2) {
    return {b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
}
}  // namespace

Biquad Biquad::lowPass(double hz, double q, double sampleRate) {
    const Rbj r(hz, q, sampleRate);
    return normalise((1 - r.cosw) / 2, 1 - r.cosw, (1 - r.cosw) / 2, 1 + r.alpha, -2 * r.cosw, 1 - r.alpha);
}

Biquad Biquad::highPass(double hz, double q, double sampleRate) {
    const Rbj r(hz, q, sampleRate);
    return normalise((1 + r.cosw) / 2, -(1 + r.cosw), (1 + r.cosw) / 2, 1 + r.alpha, -2 * r.cosw, 1 - r.alpha);
}

Biquad Biquad::allPass(double hz, double q, double sampleRate) {
    const Rbj r(hz, q, sampleRate);
    return normalise(1 - r.alpha, -2 * r.cosw, 1 + r.alpha, 1 + r.alpha, -2 * r.cosw, 1 - r.alpha);
}

Biquad Biquad::lowShelf(double hz, double gainDb, double q, double sampleRate) {
    const Rbj r(hz, q, sampleRate);
    const double a = std::pow(10.0, gainDb / 40.0), s = 2 * std::sqrt(a) * r.alpha;
    return normalise(a * ((a + 1) - (a - 1) * r.cosw + s), 2 * a * ((a - 1) - (a + 1) * r.cosw),
                     a * ((a + 1) - (a - 1) * r.cosw - s), (a + 1) + (a - 1) * r.cosw + s,
                     -2 * ((a - 1) + (a + 1) * r.cosw), (a + 1) + (a - 1) * r.cosw - s);
}

Biquad Biquad::highShelf(double hz, double gainDb, double q, double sampleRate) {
    const Rbj r(hz, q, sampleRate);
    const double a = std::pow(10.0, gainDb / 40.0), s = 2 * std::sqrt(a) * r.alpha;
    return normalise(a * ((a + 1) + (a - 1) * r.cosw + s), -2 * a * ((a - 1) + (a + 1) * r.cosw),
                     a * ((a + 1) + (a - 1) * r.cosw - s), (a + 1) - (a - 1) * r.cosw + s,
                     2 * ((a - 1) - (a + 1) * r.cosw), (a + 1) - (a - 1) * r.cosw - s);
}

double biquadGainDb(const Biquad& f, double hz, double sampleRate) {
    const std::complex<double> z = std::polar(1.0, -2.0 * kPi * hz / sampleRate);
    const std::complex<double> num = f.b0 + f.b1 * z + f.b2 * z * z;
    const std::complex<double> den = 1.0 + f.a1 * z + f.a2 * z * z;
    return 20.0 * std::log10(std::abs(num / den));
}

}  // namespace eigentone::dsp
