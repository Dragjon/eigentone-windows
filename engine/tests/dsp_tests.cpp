// Tests for the sound engine: each stage against what it should do in theory, then the whole chain.
#include <algorithm>
#include <chrono>
#include <cmath>
#include <complex>
#include <cstdio>
#include <numbers>
#include <random>
#include <vector>

#include "eigentone/dsp/design.h"
#include "eigentone/dsp/engine.h"
#include "eigentone/dsp/fft.h"

using namespace eigentone::dsp;

namespace {

int failures = 0;
void check(bool ok, const char* what, double value = NAN) {
    if (std::isnan(value)) std::printf("%s  %s\n", ok ? "pass" : "FAIL", what);
    else std::printf("%s  %s  (%.4g)\n", ok ? "pass" : "FAIL", what, value);
    if (!ok) ++failures;
}

constexpr double kRate = 48000;
constexpr double kPi = std::numbers::pi;

/// A smooth test curve: a 6 dB bump at 1 kHz and a 4 dB bass cut.
double target(double hz) {
    const double o = std::log2(hz / 1000.0);
    return 6.0 * std::exp(-o * o / (2 * 0.5 * 0.5)) - 4.0 / (1.0 + (hz / 150.0) * (hz / 150.0));
}

/// Android-style bands: 128 of them, log-spaced from 20 Hz to 20 kHz, sampled from a curve at their centres.
void androidBands(EqParams& eq, double (*curve)(double)) {
    eq.bandCount = 128;
    for (uint32_t i = 0; i < 128; ++i) eq.cutoffHz[i] = static_cast<float>(20.0 * std::pow(1000.0, (i + 1) / 128.0));
    for (uint32_t i = 0; i < 128; ++i) {
        const double lo = i == 0 ? eq.cutoffHz[0] * 0.5 : eq.cutoffHz[i - 1];
        const double centre = std::sqrt(lo * eq.cutoffHz[i]);
        eq.gainDb[0][i] = eq.gainDb[1][i] = static_cast<float>(curve(centre));
    }
}

double firGainDb(const std::vector<float>& h, double hz) {
    std::complex<double> sum = 0;
    const double w = -2 * kPi * hz / kRate;
    for (size_t n = 0; n < h.size(); ++n) sum += static_cast<double>(h[n]) * std::polar(1.0, w * static_cast<double>(n));
    return 20 * std::log10(std::abs(sum));
}

/// Runs interleaved stereo through the engine in uneven chunks, as the audio engine might hand it over.
std::vector<float> run(Engine& e, const std::vector<float>& in) {
    std::vector<float> out(in.size());
    const uint32_t ch = e.channels();
    const uint32_t chunks[] = {480, 441, 1, 1000, 256, 37};
    size_t frame = 0, total = in.size() / ch;
    for (int i = 0; frame < total; ++i) {
        const uint32_t n = static_cast<uint32_t>(std::min<size_t>(chunks[i % 6], total - frame));
        e.process(in.data() + frame * ch, out.data() + frame * ch, n);
        frame += n;
    }
    return out;
}

std::vector<float> sine(double hz, double amp, double seconds, uint32_t ch = 2) {
    const size_t n = static_cast<size_t>(seconds * kRate);
    std::vector<float> v(n * ch);
    for (size_t i = 0; i < n; ++i) {
        for (uint32_t c = 0; c < ch; ++c) v[i * ch + c] = static_cast<float>(amp * std::sin(2 * kPi * hz * static_cast<double>(i) / kRate));
    }
    return v;
}

double rmsDb(const std::vector<float>& v, size_t fromFrame, uint32_t ch = 2, uint32_t channel = 0) {
    double sum = 0;
    size_t n = 0;
    for (size_t i = fromFrame; i < v.size() / ch; ++i, ++n) sum += v[i * ch + channel] * v[i * ch + channel];
    return 10 * std::log10(sum / static_cast<double>(n) + 1e-30);
}

double peak(const std::vector<float>& v, size_t fromFrame = 0, uint32_t ch = 2) {
    double p = 0;
    for (size_t i = fromFrame * ch; i < v.size(); ++i) p = std::max(p, static_cast<double>(std::fabs(v[i])));
    return p;
}

void testFft() {
    const size_t n = 64;
    Fft<double> fft(n);
    std::vector<std::complex<double>> x(n), naive(n);
    std::mt19937 rng(1);
    std::uniform_real_distribution<double> u(-1, 1);
    for (auto& v : x) v = {u(rng), u(rng)};
    for (size_t k = 0; k < n; ++k) {
        for (size_t j = 0; j < n; ++j) naive[k] += x[j] * std::polar(1.0, -2 * kPi * double(k * j) / double(n));
    }
    auto y = x;
    fft.forward(y.data());
    double err = 0;
    for (size_t k = 0; k < n; ++k) err = std::max(err, std::abs(y[k] - naive[k]));
    check(err < 1e-9, "FFT matches the textbook DFT", err);
    fft.inverse(y.data());
    err = 0;
    for (size_t k = 0; k < n; ++k) err = std::max(err, std::abs(y[k] - x[k]));
    check(err < 1e-12, "inverse FFT returns the input", err);
}

void testDesign() {
    EqParams eq{};
    androidBands(eq, target);
    const auto h = designMinimumPhase(eq.cutoffHz, eq.gainDb[0], eq.bandCount, kRate, eqTaps(kRate));
    double worst = 0;
    for (double hz = 30; hz < 16000; hz *= 1.05) worst = std::max(worst, std::fabs(firGainDb(h, hz) - target(hz)));
    check(worst < 0.35, "EQ filter follows the curve within 0.35 dB, 30 Hz to 16 kHz", worst);

    double energy = 0, early = 0;
    for (size_t n = 0; n < h.size(); ++n) {
        energy += h[n] * h[n];
        if (n < kRate * 0.005) early += h[n] * h[n];
    }
    check(early / energy > 0.95, "minimum phase: 95% of the energy in the first 5 ms", early / energy);

    EqParams flat{};
    flat.bandCount = 128;
    for (uint32_t i = 0; i < 128; ++i) flat.cutoffHz[i] = eq.cutoffHz[i];
    const auto d = designMinimumPhase(flat.cutoffHz, flat.gainDb[0], 128, kRate, 1024);
    double rest = 0;
    for (size_t n = 1; n < d.size(); ++n) rest = std::max(rest, static_cast<double>(std::fabs(d[n])));
    check(std::fabs(d[0] - 1) < 1e-4 && rest < 1e-4, "a flat curve gives a plain impulse");
}

void testPassThrough() {
    Engine e;
    e.prepare(kRate, 2);
    std::vector<float> noise(48000 * 2);
    std::mt19937 rng(2);
    std::uniform_real_distribution<float> u(-0.5f, 0.5f);
    for (auto& v : noise) v = u(rng);
    const auto out = run(e, noise);
    const size_t lat = e.latencyFrames();
    double err = 0;
    for (size_t i = lat * 2; i < noise.size(); ++i) err = std::max(err, static_cast<double>(std::fabs(out[i] - noise[i - lat * 2])));
    check(err < 1e-6, "neutral settings pass sound through untouched, only delayed", err);
    std::printf("      delay: %zu frames, %.1f ms\n", lat, 1000.0 * double(lat) / kRate);

    // In place, as Windows sometimes hands the same buffer in and out.
    Engine e2;
    e2.prepare(kRate, 2);
    auto buf = noise;
    e2.process(buf.data(), buf.data(), 48000);
    err = 0;
    for (size_t i = lat * 2; i < noise.size(); ++i) err = std::max(err, static_cast<double>(std::fabs(buf[i] - noise[i - lat * 2])));
    check(err < 1e-6, "works in place", err);
}

void testEq() {
    Engine e;
    e.prepare(kRate, 2);
    Params p = neutralParams();
    androidBands(p.eq, target);
    e.update(p);
    double worst = 0;
    for (double hz : {40.0, 100.0, 300.0, 700.0, 1000.0, 1500.0, 3000.0, 8000.0, 14000.0}) {
        const auto in = sine(hz, 0.25, 0.6);
        const auto out = run(e, in);
        const double gain = rmsDb(out, 12000) - rmsDb(in, 12000);
        worst = std::max(worst, std::fabs(gain - target(hz)));
    }
    check(worst < 0.35, "engine EQ matches the curve on sine tones", worst);

    // Inputs gains, left and right separately.
    p = neutralParams();
    p.inputGainDb[0] = -6;
    p.inputGainDb[1] = 3;
    Engine g;
    g.prepare(kRate, 2);
    g.update(p);
    const auto in = sine(1000, 0.25, 0.3);
    const auto out = run(g, in);
    check(std::fabs(rmsDb(out, 4800, 2, 0) - rmsDb(in, 4800, 2, 0) + 6) < 0.01 &&
              std::fabs(rmsDb(out, 4800, 2, 1) - rmsDb(in, 4800, 2, 1) - 3) < 0.01,
          "input gain, per channel");
}

void testSmoothSwitch() {
    // A 1 kHz tone, with the curve switched on halfway: no sample may jump more than the tone itself can.
    Engine e;
    e.prepare(kRate, 2);
    const auto in = sine(1000, 0.5, 1.0);
    std::vector<float> out(in.size());
    Params p = neutralParams();
    androidBands(p.eq, target);  // +6 dB at 1 kHz, so the amplitude doubles
    const size_t half = in.size() / 4;
    e.process(in.data(), out.data(), static_cast<uint32_t>(half));
    e.update(p);
    e.process(in.data() + half * 2, out.data() + half * 2, static_cast<uint32_t>(in.size() / 2 - half));
    double jump = 0;
    for (size_t i = 2; i < out.size(); i += 2) jump = std::max(jump, static_cast<double>(std::fabs(out[i] - out[i - 2])));
    const double limit = 2 * 0.5 * 2 * kPi * 1000 / kRate * 1.1;  // doubled tone's steepest slope, plus 10%
    check(jump < limit, "switching the EQ crossfades, with no click", jump / limit);
}

void testCrossovers() {
    Params p = neutralParams();
    p.mbc.enabled = 1;
    p.mbc.bandCount = 6;
    const float cuts[] = {110, 400, 1500, 4000, 9000, 24000};
    for (int b = 0; b < 6; ++b) p.mbc.bands[b] = {0, cuts[b], 5, 100, 1, 0, 0, -90, 1, 0, 0};
    double worst = 0;
    for (double hz : {50.0, 110.0, 250.0, 400.0, 900.0, 1500.0, 4000.0, 9000.0, 15000.0}) {
        Engine e;
        e.prepare(kRate, 2);
        e.update(p);
        const auto in = sine(hz, 0.25, 0.4);
        const auto out = run(e, in);
        worst = std::max(worst, std::fabs(rmsDb(out, 9600) - rmsDb(in, 9600)));
    }
    check(worst < 0.05, "six compressor bands add back to a flat response", worst);
}

void testCompressor() {
    Params p = neutralParams();
    p.mbc.enabled = 1;
    p.mbc.bandCount = 1;
    p.mbc.bands[0] = {1, 24000, 1, 50, 4, -20, 0, -90, 1, 0, 0};
    Engine e;
    e.prepare(kRate, 2);
    e.update(p);
    const double amp = std::pow(10.0, -10.0 / 20.0);
    const auto out = run(e, sine(1000, amp, 1.0));
    const double outPeakDb = 20 * std::log10(peak(out, 24000));
    check(std::fabs(outPeakDb - (-17.5)) < 1.0, "compressor: -10 dB peaks over -20 at 4:1 come out near -17.5 dB", outPeakDb);

    // Below the gate, with 2:1 expansion, -50 dB comes out at -60.
    p.mbc.bands[0] = {1, 24000, 1, 50, 1, 0, 0, -40, 2, 0, 0};
    Engine x;
    x.prepare(kRate, 2);
    x.update(p);
    const auto quiet = run(x, sine(1000, std::pow(10.0, -50.0 / 20.0), 1.0));
    const double quietDb = 20 * std::log10(peak(quiet, 24000));
    check(std::fabs(quietDb - (-60)) < 1.5, "expander: -50 dB below a -40 dB gate at 2:1 comes out near -60 dB", quietDb);
}

void testCompressorOff() {
    // Switched off with its bands still set up, as the app leaves it: once settled, the sound is untouched.
    Params p = neutralParams();
    p.mbc.enabled = 1;
    p.mbc.bandCount = 3;
    p.mbc.bands[0] = {1, 200, 20, 300, 2, -20, 6, -70, 1, 0, 4};
    p.mbc.bands[1] = {1, 2500, 10, 200, 2, -20, 6, -70, 1, 0, 4};
    p.mbc.bands[2] = {1, 24000, 5, 150, 2, -20, 6, -70, 1, 0, 4};
    Engine e;
    e.prepare(kRate, 2);
    e.update(p);
    const auto in = sine(440, 0.5, 4.0);
    std::vector<float> out(in.size());
    const size_t half = in.size() / 4;
    e.process(in.data(), out.data(), static_cast<uint32_t>(half));
    p.mbc.enabled = 0;
    e.update(p);
    e.process(in.data() + half * 2, out.data() + half * 2, static_cast<uint32_t>(in.size() / 2 - half));
    const size_t lat = e.latencyFrames();
    double err = 0, jump = 0;
    for (size_t i = static_cast<size_t>(kRate * 3) * 2; i < in.size(); ++i) err = std::max(err, static_cast<double>(std::fabs(out[i] - in[i - lat * 2])));
    for (size_t i = 2; i < out.size(); i += 2) jump = std::max(jump, static_cast<double>(std::fabs(out[i] - out[i - 2])));
    check(err < 1e-6, "compressor switched off steps out of the sound entirely", err);
    const double limit = 0.5 * std::pow(10.0, 4.0 / 20) * 2 * kPi * 440 / kRate * 1.2;
    check(jump < limit, "and switching it off doesn't click", jump / limit);
}

void testLimiter() {
    Params p = neutralParams();
    p.limiter = {1, 1.0f, 60.0f, 10.0f, -1.0f, 0.0f};
    const double ceiling = std::pow(10.0, (-1.0 + 7.0 / 10.0) / 20.0);  // +6 dB in, 7 dB over at 10:1

    Engine e;
    e.prepare(kRate, 2);
    e.update(p);
    const auto out = run(e, sine(1000, 2.0, 1.0));
    check(peak(out, 0) <= ceiling * 1.005, "limiter: +6 dB peaks never pass the ceiling, from the first one", 20 * std::log10(peak(out)));

    // A sudden burst from silence, the hardest case for a limiter without look-ahead.
    Engine b;
    b.prepare(kRate, 2);
    b.update(p);
    std::vector<float> burst(48000 * 2, 0.0f);
    for (size_t i = 24000 * 2; i < burst.size(); ++i) burst[i] = (i / 2) % 40 < 20 ? 4.0f : -4.0f;
    const auto hit = run(b, burst);
    const double burstCeiling = std::pow(10.0, (-1.0 + (20 * std::log10(4.0) + 1.0) / 10.0) / 20.0);
    check(peak(hit) <= burstCeiling * 1.005, "limiter: a +12 dB burst out of silence is caught too", 20 * std::log10(peak(hit)));
}

void testReverb() {
    Params p = neutralParams();
    p.reverb = {1, 0, 0, 1000, 1.0f, -10000, 0, 0, 0, 1, 1};  // no early reflections, 1 s decay
    Engine e;
    e.prepare(kRate, 2);
    e.update(p);
    std::vector<float> warm(4800 * 2, 0.0f);
    run(e, warm);  // let the settings take hold
    std::vector<float> impulse(static_cast<size_t>(kRate * 2.5) * 2, 0.0f);
    impulse[0] = impulse[1] = 1.0f;
    const auto out = run(e, impulse);
    // Energy in 50 ms windows, then the time to fall from -5 to -35 dB, times two.
    std::vector<double> env;
    for (size_t w = 0; w + 2400 <= out.size() / 2; w += 2400) {
        double s = 0;
        for (size_t i = w; i < w + 2400; ++i) s += (out[i * 2] - (i == e.latencyFrames() ? 1.0 : 0.0)) * out[i * 2];
        env.push_back(10 * std::log10(s + 1e-30));
    }
    const double top = *std::max_element(env.begin() + 1, env.end());
    double t5 = -1, t35 = -1;
    for (size_t k = 1; k < env.size(); ++k) {
        if (t5 < 0 && env[k] < top - 5) t5 = static_cast<double>(k) * 0.05;
        if (t35 < 0 && env[k] < top - 35) t35 = static_cast<double>(k) * 0.05;
    }
    const double t60 = (t35 - t5) * 2;
    check(t5 > 0 && t35 > 0 && std::fabs(t60 - 1.0) < 0.25, "reverb decays in about its set time (1 s)", t60);
}

void testVirtualizer() {
    // A left-only sound: in bass the head barely shades it, so the right ear hears it almost as loud; in
    // treble the head is in the way, so the right ear hears much less. That difference is what places it.
    Params p = neutralParams();
    p.virtualizer = {1, 1.0f};
    double cross[2];
    int k = 0;
    for (double hz : {200.0, 6000.0}) {
        Engine e;
        e.prepare(kRate, 2);
        e.update(p);
        auto in = sine(hz, 0.25, 0.5);
        for (size_t i = 1; i < in.size(); i += 2) in[i] = 0;
        const auto out = run(e, in);
        cross[k++] = rmsDb(out, 12000, 2, 1) - rmsDb(out, 12000, 2, 0);
    }
    check(cross[0] > -6 && cross[0] < 0, "surround: in bass, the far ear hears a left-only sound nearly as loud", cross[0]);
    check(cross[1] < cross[0] - 6, "surround: in treble, the head shades the far ear", cross[1]);

    p.virtualizer = {1, 0.0f};
    Engine off;
    off.prepare(kRate, 2);
    off.update(p);
    const auto in = sine(1000, 0.25, 0.2);
    const auto out = run(off, in);
    double err = 0;
    for (size_t i = off.latencyFrames() * 2; i < in.size(); ++i) err = std::max(err, static_cast<double>(std::fabs(out[i] - in[i - off.latencyFrames() * 2])));
    check(err < 1e-6, "surround at strength 0 changes nothing", err);
}

void testReverbKeepsLoudness() {
    // The app's rooms at a moderate and at the full amount: on or off, the music is equally loud.
    struct Room { const char* name; ReverbParams r; };
    const Room rooms[] = {
        {"room, -6 dB", {1, 0, -600, 1500, 0.83f, -1000, 10, -600, 20, 1, 1}},
        {"hall, -6 dB", {1, 0, -600, 4000, 0.7f, -1000, 20, -600, 30, 1, 1}},
        {"hall, full", {1, 0, -600, 4000, 0.7f, -1000, 20, 0, 30, 1, 1}},
    };
    for (const Room& room : rooms) {
        Params p = neutralParams();
        p.reverb = room.r;
        Engine e;
        e.prepare(kRate, 2);
        e.update(p);
        std::vector<float> in(static_cast<size_t>(kRate * 8) * 2);
        std::mt19937 rng(6);
        std::normal_distribution<float> g(0, 0.1f);
        float a = 0, b = 0;
        for (size_t i = 0; i < in.size(); i += 2) {
            a = 0.9f * a + 0.1f * g(rng);
            b = 0.9f * b + 0.1f * g(rng);
            in[i] = 0.7f * a + 0.3f * b;
            in[i + 1] = 0.7f * a - 0.3f * b;
        }
        const auto out = run(e, in);
        const size_t from = static_cast<size_t>(kRate * 5);
        const double change = (rmsDb(out, from, 2, 0) + rmsDb(out, from, 2, 1)) / 2 - (rmsDb(in, from, 2, 0) + rmsDb(in, from, 2, 1)) / 2;
        char what[96];
        std::snprintf(what, sizeof(what), "reverb keeps the loudness within 1 dB: %s", room.name);
        check(std::fabs(change) < 1.0, what, change);
    }
}

void testSurroundLevel() {
    // Music is partly the same in both channels and partly different. With half of each, surround should
    // keep the loudness, so switching it on doesn't sound like turning the volume down.
    Params p = neutralParams();
    p.virtualizer = {1, 1.0f};
    for (double shared : {0.2, 0.5, 0.8}) {
        Engine e;
        e.prepare(kRate, 2);
        e.update(p);
        std::vector<float> in(static_cast<size_t>(kRate * 3) * 2);
        std::mt19937 rng(5);
        std::normal_distribution<float> g(0, 0.1f);
        // Pink-ish: each source run through a gentle low-pass so treble doesn't dominate, as in music.
        float a = 0, b = 0, c = 0;
        for (size_t i = 0; i < in.size(); i += 2) {
            a = 0.9f * a + 0.1f * g(rng);
            b = 0.9f * b + 0.1f * g(rng);
            c = 0.9f * c + 0.1f * g(rng);
            in[i] = static_cast<float>(std::sqrt(shared) * a + std::sqrt(1 - shared) * b);
            in[i + 1] = static_cast<float>(std::sqrt(shared) * a + std::sqrt(1 - shared) * c);
        }
        const auto out = run(e, in);
        const double change = (rmsDb(out, 48000, 2, 0) + rmsDb(out, 48000, 2, 1)) / 2 - (rmsDb(in, 48000, 2, 0) + rmsDb(in, 48000, 2, 1)) / 2;
        char what[96];
        std::snprintf(what, sizeof(what), "surround keeps the loudness within 1 dB, %.0f%% shared between channels", shared * 100);
        check(std::fabs(change) < 1.0, what, change);
    }
}

void testReverbLevel() {
    // At a reverb level of 0 mB the reverb is as loud as the music, whether the decay is short or long.
    for (float decay : {1000.0f, 4000.0f}) {
        Params p = neutralParams();
        p.reverb = {1, 0, 0, decay, 1.0f, -10000, 0, 0, 0, 1, 1};
        Engine e;
        e.prepare(kRate, 2);
        e.update(p);
        std::vector<float> noise(static_cast<size_t>(kRate * 8) * 2);
        std::mt19937 rng(4);
        std::normal_distribution<float> g(0, 0.1f);
        for (size_t i = 0; i < noise.size(); i += 2) noise[i] = noise[i + 1] = g(rng);
        const auto out = run(e, noise);
        const size_t lat = e.latencyFrames();
        // The output is the music, turned down by the loudness trim, plus the echoes. Find how much of the
        // music is in it, take that away, and what's left is the echoes.
        double cross = 0, self = 0;
        for (size_t i = static_cast<size_t>(kRate * 4); i < noise.size() / 2; ++i) {
            const double d = noise[(i - lat) * 2];
            cross += out[i * 2] * d;
            self += d * d;
        }
        const double share = cross / self;
        double wet = 0, dry = 0;
        for (size_t i = static_cast<size_t>(kRate * 4); i < noise.size() / 2; ++i) {
            const double d = share * noise[(i - lat) * 2];
            const double w = out[i * 2] - d;
            wet += w * w;
            dry += d * d;
        }
        const double level = 10 * std::log10(wet / dry);
        char what[96];
        std::snprintf(what, sizeof(what), "reverb level 0 mB is as loud as the music (within 2 dB), %.0f s decay", decay / 1000);
        check(std::fabs(level) < 2, what, level);
    }
}

void testBypass() {
    Params p = neutralParams();
    androidBands(p.eq, target);
    p.limiter = {1, 1.0f, 60.0f, 10.0f, -6.0f, 0.0f};
    p.enabled = 0;
    Engine e;
    e.prepare(kRate, 2);
    e.update(p);
    const auto in = sine(1000, 0.9, 0.5);
    const auto out = run(e, in);
    const size_t lat = e.latencyFrames();
    double err = 0;
    for (size_t i = (lat + 1024) * 2; i < in.size(); ++i) err = std::max(err, static_cast<double>(std::fabs(out[i] - in[i - lat * 2])));
    check(err < 1e-6, "switched off, sound passes through untouched", err);
}

void testHandover() {
    // Settings changing constantly while sound plays: nothing may break or go silent.
    Engine e;
    e.prepare(kRate, 2);
    auto in = sine(440, 0.3, 0.02);
    std::vector<float> out(in.size());
    Params p = neutralParams();
    androidBands(p.eq, target);
    bool finite = true;
    int accepted = 0;
    for (int i = 0; i < 400; ++i) {
        p.inputGainDb[0] = p.inputGainDb[1] = static_cast<float>((i % 7) - 3);
        p.limiter.enabled = i & 1;
        accepted += e.update(p) ? 1 : 0;
        e.process(in.data(), out.data(), static_cast<uint32_t>(in.size() / 2));
        for (float v : out) finite &= std::isfinite(v);
    }
    check(finite && accepted > 300, "400 quick setting changes while playing", accepted);
}

double secondsPerSecond(const Params& p, double rate) {
    Engine e;
    e.prepare(rate, 2);
    e.update(p);
    const size_t frames = static_cast<size_t>(rate * 5);
    std::vector<float> buf(frames * 2);
    std::mt19937 rng(3);
    std::uniform_real_distribution<float> u(-0.3f, 0.3f);
    for (auto& v : buf) v = u(rng);
    const auto t0 = std::chrono::steady_clock::now();
    for (size_t f = 0; f < frames; f += 480) e.process(buf.data() + f * 2, buf.data() + f * 2, static_cast<uint32_t>(std::min<size_t>(480, frames - f)));
    return std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count() / 5.0;
}

void testSpeed() {
    Params eq = neutralParams();
    eq.eq.bandCount = 256;
    for (uint32_t i = 0; i < 256; ++i) {
        eq.eq.cutoffHz[i] = static_cast<float>(20.0 * std::pow(1000.0, (i + 1) / 256.0));
        eq.eq.gainDb[0][i] = static_cast<float>(target(eq.eq.cutoffHz[i]));
        eq.eq.gainDb[1][i] = eq.eq.gainDb[0][i] * 0.8f;
    }
    Params mbc = neutralParams();
    mbc.mbc.enabled = 1;
    mbc.mbc.bandCount = 6;
    const float cuts[] = {110, 400, 1500, 4000, 9000, 24000};
    for (int b = 0; b < 6; ++b) mbc.mbc.bands[b] = {1, cuts[b], 5, 100, 2, -24, 6, -60, 1.5f, 0, 2};
    Params rest = neutralParams();
    rest.virtualizer = {1, 0.7f};
    rest.reverb.enabled = 1;
    rest.limiter.enabled = 1;
    Params all = eq;
    all.mbc = mbc.mbc;
    all.virtualizer = rest.virtualizer;
    all.reverb = rest.reverb;
    all.limiter = rest.limiter;

    for (double rate : {48000.0, 192000.0}) {
        const double cost = secondsPerSecond(all, rate);
        std::printf("      %.0f kHz, share of one core: EQ %.1f%%, compressor %.1f%%, surround+reverb+limiter %.1f%%, all %.1f%%\n",
                    rate / 1000, 100 * secondsPerSecond(eq, rate), 100 * secondsPerSecond(mbc, rate),
                    100 * secondsPerSecond(rest, rate), 100 * cost);
        char what[96];
        std::snprintf(what, sizeof(what), "the full chain at %.0f kHz takes under %d%% of one core", rate / 1000, rate > 96000 ? 40 : 10);
        // Generous, as CI machines are slower and busier than a desktop. The figures above are the real measure.
        check(cost < (rate > 96000 ? 0.40 : 0.10), what, 100 * cost);
    }
}

}  // namespace

int main() {
    testFft();
    testDesign();
    testPassThrough();
    testEq();
    testSmoothSwitch();
    testCrossovers();
    testCompressor();
    testCompressorOff();
    testLimiter();
    testReverb();
    testVirtualizer();
    testReverbLevel();
    testSurroundLevel();
    testReverbKeepsLoudness();
    testBypass();
    testHandover();
    testSpeed();
    std::printf("\n%s (%d failed)\n", failures ? "FAILED" : "all passed", failures);
    return failures ? 1 : 0;
}
