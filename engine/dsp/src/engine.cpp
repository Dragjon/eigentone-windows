// Eigentone for Windows: the sound engine.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#include "eigentone/dsp/engine.h"

#include <algorithm>
#include <cfloat>
#include <cmath>
#include <cstring>
#include <numbers>

namespace eigentone::dsp {

namespace {

constexpr int kLineCount = 8;
// Reverb delay lines, in ms. Mutually prime-ish lengths, so their echoes don't line up.
constexpr double kLineMs[kLineCount] = {31.3, 37.9, 41.7, 47.3, 53.1, 59.7, 67.1, 73.9};
constexpr int kEarlyTaps = 6;
constexpr double kEarlyMs[kEarlyTaps] = {0.0, 3.1, 7.4, 11.9, 16.3, 21.7};
constexpr double kEarlyGain[kEarlyTaps] = {0.9, 0.8, 0.7, 0.6, 0.5, 0.4};
constexpr double kMaxPreDelayMs = 400.0;
constexpr double kMaxDiffuserMs = 10.0;
// Surround, after Brown and Duda's spherical head model (1998): speakers at 30 degrees either side, a head of
// 8.75 cm radius, and a few early reflections from a small room, which place the sound outside the head.
constexpr double kSpeakerDeg = 30.0;
constexpr double kHeadRadius = 0.0875;
constexpr double kSpeedOfSound = 343.0;
constexpr int kVirtReflections = 4;
constexpr double kVirtReflectionMs[kVirtReflections] = {3.7, 6.1, 9.3, 13.9};
constexpr float kVirtReflectionGain[kVirtReflections] = {0.35f, 0.28f, 0.22f, 0.17f};
constexpr double kVirtRingMs = 25.0;
constexpr float kVirtLevel = 0.665f;  // measured so music keeps its loudness with surround on (see the tests)

/// How much a head shades sound arriving at an angle from an ear (0 = straight at it): a high shelf that
/// lifts the near side a little and cuts the far side a lot. Brown and Duda's alpha, made digital.
Biquad headShadow(double incidenceDeg, double rate) {
    constexpr double alphaMin = 0.1, thetaMin = 150.0;
    const double alpha = (1 + alphaMin / 2) + (1 - alphaMin / 2) * std::cos(incidenceDeg / thetaMin * std::numbers::pi);
    const double tau = kHeadRadius / (2 * kSpeedOfSound);
    const double k = 2 * rate;
    const double a0 = 1 + tau * k;
    return {(1 + alpha * tau * k) / a0, (1 - alpha * tau * k) / a0, 0, (1 - tau * k) / a0, 0};
}

/// How long sound from that angle takes to reach the ear, beyond reaching the head's centre, in seconds.
double headDelay(double incidenceDeg) {
    const double theta = incidenceDeg * std::numbers::pi / 180.0;
    const double t = theta < std::numbers::pi / 2 ? -std::cos(theta) : theta - std::numbers::pi / 2;
    return (kHeadRadius / kSpeedOfSound) * (1 + t);
}
constexpr double kButterworthQ = std::numbers::sqrt2 / 2;

float dbToLin(double db) { return static_cast<float>(std::pow(10.0, db / 20.0)); }
float mbToLin(double mb) { return static_cast<float>(std::pow(10.0, mb / 2000.0)); }
double timeCoef(double ms, double rate) { return std::exp(-1.0 / (std::max(ms, 0.01) * 0.001 * rate)); }

/// A raised-cosine fade from 0 to 1 across a block, so crossfades are smooth at both ends.
float fadeIn(size_t i, size_t length) {
    return static_cast<float>(0.5 - 0.5 * std::cos(std::numbers::pi * (static_cast<double>(i) + 0.5) / static_cast<double>(length)));
}

/// The compressor's gain change, in dB, for a level in dB.
double gainComputer(double x, double thr, double ratio, double knee, double gate, double expander) {
    double y;
    const double over = x - thr;
    if (knee > 0 && std::fabs(over) <= knee / 2) {
        const double t = over + knee / 2;
        y = x + (1.0 / ratio - 1.0) * t * t / (2 * knee);
    } else if (over > 0) {
        y = thr + over / ratio;
    } else {
        y = x;
    }
    if (expander > 1 && x < gate) y -= (gate - x) * (expander - 1);
    return y - x;
}

/// Flushes denormal numbers to zero while it lives, so silent tails can't slow the audio thread down.
class FlushDenormals {
public:
    FlushDenormals() {
#ifdef _MSC_VER
        _controlfp_s(&saved_, 0, 0);
        unsigned int unused;
        _controlfp_s(&unused, _DN_FLUSH, _MCW_DN);
#endif
    }
    ~FlushDenormals() {
#ifdef _MSC_VER
        unsigned int unused;
        _controlfp_s(&unused, saved_, _MCW_DN);
#endif
    }

private:
    unsigned int saved_ = 0;
};

}  // namespace

struct Engine::Plan {
    enum State { kFree, kBuilding, kPending, kCurrent };
    std::atomic<int> state{kFree};

    float enabled = 1;
    float inGain[kCurveChannels] = {1, 1};

    bool eqActive = false;
    std::vector<std::complex<float>> eq[kCurveChannels];  // partitions x fftSize_

    bool mbcOn = false;
    size_t bands = 0;
    Biquad lp[kMaxMbcBands], hp[kMaxMbcBands], ap[kMaxMbcBands];
    struct Band {
        bool on;
        double attack, release, thr, ratio, knee, gate, expander, pre, post;  // attack and release per hop
    } band[kMaxMbcBands] = {};
    double envDecay = 0;  // how fast a band's level reading falls back after a peak
    double offGlide = 0;  // per hop, how fast a switched-off band returns to unity

    float virtAmount = 0;
    Biquad virtNear, virtFar, virtRoom;
    float virtNearDelay = 0, virtFarDelay = 0;  // in samples, fractional
    size_t virtReflection[kVirtReflections] = {};

    bool reverbOn = false;
    size_t lineLen[kLineCount] = {};
    float lineGain[kLineCount] = {}, lineB[kLineCount] = {};
    size_t preDelay = 1;
    size_t earlyTap[kEarlyTaps] = {};
    float earlyGain[kEarlyTaps] = {};
    float lateGain = 0, roomGain = 0, wetHf = 1, diffusion = 0.5f;
    float reverbTrim = 1;
    size_t diffLen[2] = {1, 1};
    Biquad wetLow;

    bool limOn = false;
    double limThrDb = -1, limThr = 1, limRatio = 10, limRelease = 0, limPost = 1;
    size_t limLen = 1;
};

/// The reverb's memory, and one step of it. Shared by the reverb that plays and the settings thread's
/// measurement of how much it adds, so the two can't drift apart.
struct Engine::ReverbState {
    std::vector<float> preDelay;
    size_t preDelayPos = 0;
    std::vector<float> diffuser[2];
    size_t diffuserPos[2] = {};
    std::vector<float> lines[kLineCount];
    size_t linePos[kLineCount] = {};
    float lineDamp[kLineCount] = {};
    BiquadState wetTone[2];

    void allocate(double rate) {
        preDelay.assign(static_cast<size_t>(kMaxPreDelayMs * 0.001 * rate) + 2, 0);
        for (auto& d : diffuser) d.assign(static_cast<size_t>(kMaxDiffuserMs * 0.001 * rate) + 2, 0);
        for (auto& l : lines) l.assign(static_cast<size_t>(kLineMs[kLineCount - 1] * 0.001 * rate) + 2, 0);
        clear();
    }

    void clear() {
        std::fill(preDelay.begin(), preDelay.end(), 0.0f);
        for (auto& d : diffuser) std::fill(d.begin(), d.end(), 0.0f);
        for (auto& l : lines) std::fill(l.begin(), l.end(), 0.0f);
        std::fill(std::begin(lineDamp), std::end(lineDamp), 0.0f);
        wetTone[0] = wetTone[1] = {};
        preDelayPos = diffuserPos[0] = diffuserPos[1] = 0;
        std::fill(std::begin(linePos), std::end(linePos), size_t{0});
    }

    /// Takes one sample of the mono mix and gives the echoes for the left and right ear, before the room level.
    void tick(const Plan& plan, float mono, float wet[2]) {
        const size_t preSize = preDelay.size();
        preDelay[preDelayPos] = mono;

        float early[2] = {0, 0};
        for (int t = 0; t < kEarlyTaps; ++t) {
            early[t & 1] += preDelay[(preDelayPos + preSize - plan.earlyTap[t]) % preSize] * plan.earlyGain[t];
        }

        // Two all-pass diffusers smear the input into a dense wash before the network.
        float x = preDelay[(preDelayPos + preSize - plan.preDelay) % preSize];
        for (int d = 0; d < 2; ++d) {
            std::vector<float>& buf = diffuser[d];
            size_t& p = diffuserPos[d];
            const float v = buf[p];
            const float y = -plan.diffusion * x + v;
            buf[p] = x + plan.diffusion * y;
            p = (p + 1) % plan.diffLen[d];
            x = y;
        }

        float s[kLineCount];
        float sum = 0;
        for (int k = 0; k < kLineCount; ++k) {
            s[k] = lines[k][linePos[k]];
            sum += s[k];
        }
        const float house = sum * (2.0f / kLineCount);  // Householder feedback: lossless, and mixes every line
        float late[2] = {0, 0};
        for (int k = 0; k < kLineCount; ++k) {
            late[k & 1] += s[k];
            const float y = s[k] - house;
            lineDamp[k] = plan.lineGain[k] * (1 - plan.lineB[k]) * y + plan.lineB[k] * lineDamp[k];
            lines[k][linePos[k]] = lineDamp[k] + ((k & 1) ? -x : x);
            if (++linePos[k] >= plan.lineLen[k]) linePos[k] = 0;
        }

        for (int side = 0; side < 2; ++side) {
            const float w = early[side] + late[side] * plan.lateGain * 0.5f;
            const float low = static_cast<float>(wetTone[side].process(plan.wetLow, w));
            wet[side] = low + plan.wetHf * (w - low);
        }
        preDelayPos = (preDelayPos + 1) % preSize;
    }
};

Engine::Engine() = default;
Engine::~Engine() = default;

void Engine::prepare(double sampleRate, uint32_t channels) {
    rate_ = sampleRate;
    channels_ = std::max<uint32_t>(channels, 1);
    block_ = kBaseBlock;
    while (static_cast<double>(block_) < rate_ * (static_cast<double>(kBaseBlock) / 50000.0)) block_ <<= 1;
    fftSize_ = 2 * block_;
    bins_ = block_ + 1;
    taps_ = eqTaps(rate_);
    partitions_ = std::max<size_t>(1, taps_ / block_);
    lookahead_ = std::max<size_t>(1, static_cast<size_t>(std::lround(kLookaheadMs * 0.001 * rate_)));
    fft_ = std::make_unique<Fft<float>>(fftSize_);

    for (auto& p : plans_) {
        p = std::make_unique<Plan>();
        for (auto& e : p->eq) e.assign(partitions_ * bins_, {});
    }
    pending_.store(nullptr);

    auto planar = [&](size_t len) { return std::vector<std::vector<float>>(channels_, std::vector<float>(len)); };
    in_ = planar(block_);
    out_ = planar(block_);
    work_ = planar(block_);
    dry_ = planar(block_);
    dryDelay_ = planar(lookahead_);
    mbcDry_ = planar(block_);
    limDelay_ = planar(lookahead_);

    eq_.assign(channels_, {});
    for (auto& e : eq_) {
        e.history.assign(fftSize_, 0);
        e.spectra.assign(partitions_ * bins_, {});
        e.acc.assign(fftSize_, {});
        e.accOld.assign(fftSize_, {});
    }
    mbc_.assign(channels_, {});
    bandScratch_.assign(kHop * channels_ * kMaxMbcBands, 0);

    for (auto& v : virtDelay_) v.assign(static_cast<size_t>(kVirtRingMs * 0.001 * rate_) + 4, 0);
    reverb_ = std::make_unique<ReverbState>();
    reverb_->allocate(rate_);
    reverbProbe_ = std::make_unique<ReverbState>();
    reverbProbe_->allocate(rate_);
    probedFor_ = {};
    probedTrim_ = 1;

    limHeldValue_.assign(lookahead_ + 2, 1.0f);
    limHeldIndex_.assign(lookahead_ + 2, 0);
    limBox_.assign(lookahead_ + 1, 1.0);

    build(*plans_[0], neutralParams());
    plans_[0]->state = Plan::kCurrent;
    current_ = plans_[0].get();
    reset();
}

void Engine::reset() {
    pos_ = 0;
    for (auto* set : {&in_, &out_, &work_, &dry_, &dryDelay_, &limDelay_}) {
        for (auto& v : *set) std::fill(v.begin(), v.end(), 0.0f);
    }
    for (auto& e : eq_) {
        std::fill(e.history.begin(), e.history.end(), 0.0f);
        std::fill(e.spectra.begin(), e.spectra.end(), std::complex<float>{});
        e.head = 0;
    }
    for (auto& m : mbc_) m = MbcChannel{};
    std::fill(std::begin(bandGainDb_), std::end(bandGainDb_), 0.0);
    std::fill(std::begin(bandEnv_), std::end(bandEnv_), 0.0);
    std::fill(std::begin(bandLin_), std::end(bandLin_), 1.0f);
    mbcBlend_ = 0;
    for (auto& v : virtDelay_) std::fill(v.begin(), v.end(), 0.0f);
    for (auto& speaker : virtHead_) speaker[0] = speaker[1] = {};
    virtRoom_[0] = virtRoom_[1] = {};
    virtPos_ = 0;
    reverbClear_ = true;
    limHeldFront_ = limHeldBack_ = 0;
    limCount_ = 0;
    limPos_ = dryPos_ = 0;
    limBoxLen_ = 1;
    std::fill(limBox_.begin(), limBox_.end(), 1.0);
    limBoxSum_ = 1.0;
    limGain_ = 1;
    if (current_) {
        gain_[0] = current_->inGain[0];
        gain_[1] = current_->inGain[1];
        wetMix_ = current_->enabled;
        virtAmount_ = current_->virtAmount;
        reverbMix_ = current_->reverbOn ? 1.0f : 0.0f;
        reverbTrim_ = current_->reverbTrim;
    }
}

// ---------------------------------------------------------------------------------------------------------
// Settings thread

bool Engine::update(const Params& params) {
    Plan* plan = nullptr;
    for (auto& p : plans_) {
        int expected = Plan::kFree;
        if (p->state.compare_exchange_strong(expected, Plan::kBuilding, std::memory_order_acquire)) {
            plan = p.get();
            break;
        }
    }
    if (!plan) return false;
    build(*plan, params);
    plan->state.store(Plan::kPending, std::memory_order_release);
    // A newer plan replaces one the audio thread hasn't picked up yet.
    if (Plan* old = pending_.exchange(plan, std::memory_order_acq_rel)) old->state.store(Plan::kFree, std::memory_order_release);
    return true;
}

void Engine::build(Plan& plan, const Params& p) {
    plan.enabled = p.enabled ? 1.0f : 0.0f;
    for (uint32_t c = 0; c < kCurveChannels; ++c) plan.inGain[c] = dbToLin(std::clamp<double>(p.inputGainDb[c], -60, 24));

    // EQ
    const size_t count = std::min<size_t>(p.eq.bandCount, kMaxEqBands);
    plan.eqActive = false;
    for (size_t i = 0; i < count; ++i) {
        for (uint32_t c = 0; c < kCurveChannels; ++c) plan.eqActive |= std::fabs(p.eq.gainDb[c][i]) > 0.005f;
    }
    if (plan.eqActive) {
        std::vector<std::complex<float>> buf(fftSize_);
        for (uint32_t c = 0; c < kCurveChannels; ++c) {
            if (c > 0 && std::equal(p.eq.gainDb[c], p.eq.gainDb[c] + count, p.eq.gainDb[0])) {
                plan.eq[c] = plan.eq[0];  // same size, so no allocation beyond the copy's own storage
                continue;
            }
            const auto h = designMinimumPhase(p.eq.cutoffHz, p.eq.gainDb[c], count, rate_, taps_);
            for (size_t part = 0; part < partitions_; ++part) {
                for (size_t k = 0; k < fftSize_; ++k) buf[k] = k < block_ ? h[part * block_ + k] : 0.0f;
                fft_->forward(buf.data());
                std::copy(buf.begin(), buf.begin() + static_cast<ptrdiff_t>(bins_),
                          plan.eq[c].begin() + static_cast<ptrdiff_t>(part * bins_));
            }
        }
    }

    // Compressor. Levels are read as peaks that fall back over 30 ms, so a band's reading holds steady
    // across a waveform's cycle instead of dipping at every zero crossing.
    plan.envDecay = timeCoef(30.0, rate_);
    plan.offGlide = std::pow(timeCoef(30.0, rate_), static_cast<double>(kHop));
    plan.bands = std::min<size_t>(p.mbc.bandCount, kMaxMbcBands);
    plan.mbcOn = p.enabled && p.mbc.enabled && plan.bands > 0;
    double prev = 10.0;
    for (size_t k = 0; k < plan.bands; ++k) {
        const MbcBand& b = p.mbc.bands[k];
        const double f = std::clamp(std::max<double>(b.cutoffHz, prev * 1.01), 20.0, rate_ * 0.45);
        prev = f;
        plan.lp[k] = Biquad::lowPass(f, kButterworthQ, rate_);
        plan.hp[k] = Biquad::highPass(f, kButterworthQ, rate_);
        plan.ap[k] = Biquad::allPass(f, kButterworthQ, rate_);
        plan.band[k] = {b.enabled != 0,
                        std::pow(timeCoef(b.attackMs, rate_), static_cast<double>(kHop)),
                        std::pow(timeCoef(b.releaseMs, rate_), static_cast<double>(kHop)),
                        b.thresholdDb,
                        std::max(1.0, static_cast<double>(b.ratio)),
                        std::max(0.0, static_cast<double>(b.kneeDb)),
                        b.noiseGateDb,
                        std::max(1.0, static_cast<double>(b.expanderRatio)),
                        b.preGainDb,
                        b.postGainDb};
    }

    // Surround
    plan.virtAmount = p.enabled && p.virtualizer.enabled ? std::clamp(p.virtualizer.strength, 0.0f, 1.0f) : 0.0f;
    // A speaker 30 degrees to one side reaches the near ear at 60 degrees from it, the far ear at 120.
    plan.virtNear = headShadow(90 - kSpeakerDeg, rate_);
    plan.virtFar = headShadow(90 + kSpeakerDeg, rate_);
    plan.virtNearDelay = static_cast<float>(headDelay(90 - kSpeakerDeg) * rate_);
    plan.virtFarDelay = static_cast<float>(headDelay(90 + kSpeakerDeg) * rate_);
    plan.virtRoom = Biquad::lowPass(4000, kButterworthQ, rate_);
    for (int i = 0; i < kVirtReflections; ++i) {
        plan.virtReflection[i] = std::min(virtDelay_[0].size() - 2, static_cast<size_t>(kVirtReflectionMs[i] * 0.001 * rate_));
    }

    // Reverb, after Jot: each line loses the same dB per second, and loses it faster at high frequencies.
    const ReverbParams& r = p.reverb;
    plan.reverbOn = p.enabled && r.enabled;
    const double t60 = std::clamp<double>(r.decayTimeMs, 100, 20000) * 0.001;
    const double hf = std::clamp<double>(r.decayHfRatio, 0.1, 2.0);
    const double scale = 0.6 + 0.4 * std::clamp<double>(r.density, 0, 1);
    for (int i = 0; i < kLineCount; ++i) {
        const size_t len = std::max<size_t>(1, static_cast<size_t>(kLineMs[i] * scale * 0.001 * rate_));
        plan.lineLen[i] = std::min(len, reverb_->lines[i].size() - 1);
        const double g = std::pow(10.0, -3.0 * static_cast<double>(len) / (rate_ * t60));
        const double gHf = std::pow(10.0, -3.0 * static_cast<double>(len) / (rate_ * t60 * hf));
        plan.lineGain[i] = static_cast<float>(g);
        plan.lineB[i] = gHf < g ? static_cast<float>((g - gHf) / (g + gHf)) : 0.0f;
    }
    const double reflections = std::clamp<double>(r.reflectionsDelayMs, 0, 300);
    const double preMs = std::clamp<double>(reflections + std::clamp<double>(r.reverbDelayMs, 0, 100), 0, kMaxPreDelayMs - 30);
    plan.preDelay = std::max<size_t>(1, static_cast<size_t>(preMs * 0.001 * rate_));
    for (int i = 0; i < kEarlyTaps; ++i) {
        plan.earlyTap[i] = std::min(reverb_->preDelay.size() - 1,
                                    static_cast<size_t>((reflections + kEarlyMs[i] * scale) * 0.001 * rate_) + 1);
        plan.earlyGain[i] = mbToLin(r.reflectionsLevelMb) * static_cast<float>(kEarlyGain[i]) * 0.5f;
    }
    // A network of lines that each lose g per pass holds about 1 / (1 - g^2) times the energy put in. Dividing
    // that out makes a reverb level of 0 mB as loud as the music, however long the decay.
    double held = 0;
    for (int i = 0; i < kLineCount; ++i) held += 1.0 / (1.0 - static_cast<double>(plan.lineGain[i]) * plan.lineGain[i]);
    held /= kLineCount;
    plan.lateGain = mbToLin(r.reverbLevelMb) / static_cast<float>(std::sqrt(held));
    plan.roomGain = mbToLin(std::min(0.0f, r.roomLevelMb));
    plan.wetHf = mbToLin(std::min(0.0f, r.roomHfLevelMb));
    plan.wetLow = Biquad::lowPass(5000, kButterworthQ, rate_);
    plan.diffusion = static_cast<float>(0.3 + 0.4 * std::clamp<double>(r.diffusion, 0, 1));
    plan.diffLen[0] = std::clamp<size_t>(static_cast<size_t>(4.7e-3 * rate_), 1, reverb_->diffuser[0].size() - 1);
    plan.diffLen[1] = std::clamp<size_t>(static_cast<size_t>(3.3e-3 * rate_), 1, reverb_->diffuser[1].size() - 1);

    // The echoes add energy on top of the music. Turning everything down by that much keeps switching the
    // reverb on from sounding louder, and so better, than it is. How much they add depends on the room
    // and on the music's balance, so it's measured: a burst of music-like sound (weighted towards bass,
    // as most music is) is played through this very reverb, and its echoes' energy summed. Remembered
    // per room, as the EQ changes far more often than the reverb.
    plan.reverbTrim = 1;
    if (plan.reverbOn) {
        if (std::memcmp(&probedFor_, &r, sizeof(r)) != 0) {
            ReverbState& probe = *reverbProbe_;
            probe.clear();
            const double pole = std::exp(-2 * std::numbers::pi * 800.0 / rate_);  // music's tilt, roughly
            const size_t length = static_cast<size_t>(rate_ * std::clamp(t60 * 0.8, 0.5, 8.0));
            double in = 1 - pole, dryEnergy = 0, wetEnergy = 0;
            for (size_t n = 0; n < length; ++n) {
                float wet[2];
                probe.tick(plan, static_cast<float>(in), wet);
                dryEnergy += in * in;
                wetEnergy += 0.5 * (static_cast<double>(wet[0]) * wet[0] + static_cast<double>(wet[1]) * wet[1]);
                in *= pole;
            }
            const double added = wetEnergy / dryEnergy * plan.roomGain * plan.roomGain;
            probedTrim_ = static_cast<float>(1.0 / std::sqrt(1.0 + added));
            probedFor_ = r;
        }
        plan.reverbTrim = probedTrim_;
    }

    // Limiter
    plan.limOn = p.enabled && p.limiter.enabled;
    plan.limThrDb = std::clamp<double>(p.limiter.thresholdDb, -60, 0);
    plan.limThr = std::pow(10.0, plan.limThrDb / 20.0);
    plan.limRatio = std::max(1.0, static_cast<double>(p.limiter.ratio));
    plan.limRelease = timeCoef(p.limiter.releaseMs, rate_);
    plan.limPost = plan.limOn ? std::pow(10.0, std::clamp<double>(p.limiter.postGainDb, -24, 24) / 20.0) : 1.0;
    plan.limLen = std::clamp<size_t>(static_cast<size_t>(std::lround(p.limiter.attackMs * 0.001 * rate_)), 1, lookahead_ + 1);
}

// ---------------------------------------------------------------------------------------------------------
// Audio thread

void Engine::process(const float* in, float* out, uint32_t frames) {
    const uint32_t ch = channels_;
    for (uint32_t f = 0; f < frames; ++f) {
        const size_t base = static_cast<size_t>(f) * ch;
        for (uint32_t c = 0; c < ch; ++c) {
            const float x = in[base + c];  // read before writing, as `out` may be `in`
            in_[c][pos_] = x;
            out[base + c] = out_[c][pos_];
        }
        if (++pos_ == block_) {
            runBlock();
            pos_ = 0;
        }
    }
}

void Engine::runBlock() {
    FlushDenormals flush;

    Plan* before = nullptr;
    if (Plan* next = pending_.exchange(nullptr, std::memory_order_acq_rel)) {
        next->state.store(Plan::kCurrent, std::memory_order_relaxed);
        before = current_;
        current_ = next;
    }
    const Plan& plan = *current_;

    // Input gain, eased from the last block's level to this one's.
    for (uint32_t c = 0; c < channels_; ++c) {
        const uint32_t curve = c % kCurveChannels;
        const float from = gain_[curve], to = plan.inGain[curve];
        for (size_t i = 0; i < block_; ++i) {
            const float x = in_[c][i];
            dry_[c][i] = x;
            work_[c][i] = x * (from + (to - from) * (static_cast<float>(i) + 1) / block_);
        }
    }
    for (uint32_t c = 0; c < kCurveChannels; ++c) gain_[c] = plan.inGain[c];

    eqBlock(current_, before);
    mbcBlock(plan);
    virtualizerBlock(plan);
    reverbBlock(plan);
    limiterBlock(plan);

    // On or off: the dry sound waits as long as the processed sound, so the crossfade lines up.
    const float fromMix = wetMix_, toMix = plan.enabled;
    for (size_t i = 0; i < block_; ++i) {
        const float mix = fromMix + (toMix - fromMix) * fadeIn(i, block_);
        for (uint32_t c = 0; c < channels_; ++c) {
            float& slot = dryDelay_[c][dryPos_];
            const float dry = slot;
            slot = dry_[c][i];
            out_[c][i] = mix == 1.0f ? work_[c][i] : work_[c][i] * mix + dry * (1.0f - mix);
        }
        if (++dryPos_ == lookahead_) dryPos_ = 0;
    }
    wetMix_ = toMix;

    if (before) before->state.store(Plan::kFree, std::memory_order_release);
}

void Engine::eqBlock(Plan* now, Plan* before) {
    const bool fading = before && (before->eqActive || now->eqActive);
    for (uint32_t c = 0; c < channels_; ++c) {
        EqChannel& e = eq_[c];
        const uint32_t curve = c % kCurveChannels;
        // Overlap-save: transform the last two blocks and keep the newest spectrum. The input is real, so
        // its spectrum is symmetric and only the first half is kept and multiplied.
        std::memmove(e.history.data(), e.history.data() + block_, block_ * sizeof(float));
        std::memcpy(e.history.data() + block_, work_[c].data(), block_ * sizeof(float));
        e.head = (e.head + 1) % partitions_;
        for (size_t k = 0; k < fftSize_; ++k) e.acc[k] = e.history[k];
        fft_->forward(e.acc.data());
        std::copy(e.acc.begin(), e.acc.begin() + static_cast<ptrdiff_t>(bins_), e.spectra.begin() + static_cast<ptrdiff_t>(e.head * bins_));
        if (!now->eqActive && !fading) continue;  // work_ already holds the block, unchanged

        auto convolve = [&](const Plan& plan, std::vector<std::complex<float>>& acc) {
            std::fill(acc.begin(), acc.begin() + static_cast<ptrdiff_t>(bins_), std::complex<float>{});
            const std::complex<float>* h = plan.eq[curve].data();
            for (size_t p = 0; p < partitions_; ++p) {
                const std::complex<float>* xs = &e.spectra[((e.head + partitions_ - p) % partitions_) * bins_];
                const std::complex<float>* hs = h + p * bins_;
                for (size_t k = 0; k < bins_; ++k) acc[k] += xs[k] * hs[k];
            }
            for (size_t k = 1; k < block_; ++k) acc[fftSize_ - k] = std::conj(acc[k]);
            fft_->inverse(acc.data());
        };
        // A flat filter is just the block itself, written out as the second half of `acc`.
        auto output = [&](const Plan& plan, std::vector<std::complex<float>>& acc) {
            if (plan.eqActive) {
                convolve(plan, acc);
            } else {
                for (size_t i = 0; i < block_; ++i) acc[block_ + i] = e.history[block_ + i];
            }
        };

        output(*now, e.acc);
        if (fading) {
            output(*before, e.accOld);
            for (size_t i = 0; i < block_; ++i) {
                const float w = fadeIn(i, block_);
                work_[c][i] = e.acc[block_ + i].real() * w + e.accOld[block_ + i].real() * (1.0f - w);
            }
        } else {
            for (size_t i = 0; i < block_; ++i) work_[c][i] = e.acc[block_ + i].real();
        }
    }
}

void Engine::mbcBlock(const Plan& plan) {
    const size_t n = plan.bands;
    bool settled = !plan.mbcOn;
    for (size_t b = 0; b < n && settled; ++b) settled = std::fabs(bandGainDb_[b]) < 1e-3;
    const float blendTarget = n > 0 && !settled ? 1.0f : 0.0f;
    if (n == 0) mbcBlend_ = 0;  // no bands to fade from; the app steps band counts down gently
    if (mbcBlend_ == 0 && blendTarget == 0) return;
    if (mbcBlend_ == 0) {
        // Starting afresh: clear what the filters and meters remember from last time.
        for (auto& m : mbc_) m = MbcChannel{};
        std::fill(std::begin(bandGainDb_), std::end(bandGainDb_), 0.0);
        std::fill(std::begin(bandEnv_), std::end(bandEnv_), 0.0);
        std::fill(std::begin(bandLin_), std::end(bandLin_), 1.0f);
    }
    for (uint32_t c = 0; c < channels_; ++c) std::copy(work_[c].begin(), work_[c].end(), mbcDry_[c].begin());

    const size_t stride = static_cast<size_t>(channels_) * kMaxMbcBands;
    for (size_t start = 0; start < block_; start += kHop) {
        // Split each frame into bands, and follow each band's peaks, linked across channels so the
        // stereo image doesn't lean.
        for (size_t h = 0; h < kHop; ++h) {
            for (uint32_t c = 0; c < channels_; ++c) {
                MbcChannel& m = mbc_[c];
                double* bands = &bandScratch_[h * stride + c * kMaxMbcBands];
                double rest = work_[c][start + h];
                // Low off the bottom of what's left, one crossover at a time.
                for (size_t k = 0; k + 1 < n; ++k) {
                    bands[k] = m.lp[k][1].process(plan.lp[k], m.lp[k][0].process(plan.lp[k], rest));
                    rest = m.hp[k][1].process(plan.hp[k], m.hp[k][0].process(plan.hp[k], rest));
                }
                bands[n - 1] = rest;
                // Lower bands pass through the higher crossovers' all-pass, so every band lines up in phase
                // and they add back to a flat response.
                for (size_t k = 0; k + 2 < n; ++k) {
                    for (size_t j = k + 1; j + 1 < n; ++j) bands[k] = m.ap[k][j].process(plan.ap[j], bands[k]);
                }
            }
            for (size_t b = 0; b < n; ++b) {
                double level = 0;
                for (uint32_t c = 0; c < channels_; ++c) level = std::max(level, std::fabs(bandScratch_[h * stride + c * kMaxMbcBands + b]));
                bandEnv_[b] = std::max(level, bandEnv_[b] * plan.envDecay);
            }
        }
        // Each band's gain, worked out once per hop and eased across it.
        float from[kMaxMbcBands], to[kMaxMbcBands];
        for (size_t b = 0; b < n; ++b) {
            const auto& band = plan.band[b];
            double target = 0;
            if (plan.mbcOn && band.on) {
                const double x = 20.0 * std::log10(bandEnv_[b] + 1e-12) + band.pre;
                target = gainComputer(x, band.thr, band.ratio, band.knee, band.gate, band.expander) + band.pre + band.post;
            }
            double& g = bandGainDb_[b];
            // Switched off, a band glides back to unity quickly, rather than at the compressor's own release.
            const bool working = plan.mbcOn && band.on;
            const double coef = !working ? plan.offGlide : target < g ? band.attack : band.release;
            g = coef * g + (1 - coef) * target;
            from[b] = bandLin_[b];
            to[b] = std::fabs(g) < 1e-6 ? 1.0f : static_cast<float>(std::pow(10.0, g / 20.0));
            bandLin_[b] = to[b];
        }
        for (size_t h = 0; h < kHop; ++h) {
            const float t = static_cast<float>(h + 1) / kHop;
            for (uint32_t c = 0; c < channels_; ++c) {
                const double* bands = &bandScratch_[h * stride + c * kMaxMbcBands];
                double sum = 0;
                for (size_t b = 0; b < n; ++b) sum += bands[b] * (from[b] + (to[b] - from[b]) * t);
                work_[c][start + h] = static_cast<float>(sum);
            }
        }
    }
    // Crossfade between the bands and the untouched sound over about 20 ms.
    const float step = static_cast<float>(block_) / static_cast<float>(0.02 * rate_);
    const float from = mbcBlend_;
    const float to = blendTarget > from ? std::min(blendTarget, from + step) : std::max(blendTarget, from - step);
    if (from != 1.0f || to != 1.0f) {
        for (size_t i = 0; i < block_; ++i) {
            const float w = from + (to - from) * (static_cast<float>(i) + 1) / static_cast<float>(block_);
            for (uint32_t c = 0; c < channels_; ++c) work_[c][i] = mbcDry_[c][i] + w * (work_[c][i] - mbcDry_[c][i]);
        }
    }
    mbcBlend_ = to;
}

void Engine::virtualizerBlock(const Plan& plan) {
    if (channels_ < 2) return;
    const float from = virtAmount_, to = plan.virtAmount;
    if (from == 0 && to == 0) return;
    if (from == 0) {
        for (auto& v : virtDelay_) std::fill(v.begin(), v.end(), 0.0f);
        for (auto& speaker : virtHead_) speaker[0] = speaker[1] = {};
        virtRoom_[0] = virtRoom_[1] = {};
    }
    const size_t size = virtDelay_[0].size();
    // Reads a channel's past at a fractional delay, by straight-line interpolation.
    auto past = [&](int ch, float delay) {
        const float whole = std::floor(delay);
        const float frac = delay - whole;
        const size_t back = static_cast<size_t>(whole);
        const float a = virtDelay_[ch][(virtPos_ + size - back) % size];
        const float b = virtDelay_[ch][(virtPos_ + size - back - 1) % size];
        return a + (b - a) * frac;
    };
    for (size_t i = 0; i < block_; ++i) {
        const float dryL = work_[0][i], dryR = work_[1][i];
        virtDelay_[0][virtPos_] = dryL;
        virtDelay_[1][virtPos_] = dryR;
        float ear[2];
        for (int e = 0; e < 2; ++e) {
            const int nearSpeaker = e, farSpeaker = 1 - e;
            const double direct = virtHead_[nearSpeaker][e].process(plan.virtNear, past(nearSpeaker, plan.virtNearDelay)) +
                                  virtHead_[farSpeaker][e].process(plan.virtFar, past(farSpeaker, plan.virtFarDelay));
            // Reflections arrive from both speakers, alternating which side they favour.
            float room = 0;
            for (int r = 0; r < kVirtReflections; ++r) {
                const int source = (r & 1) ? farSpeaker : nearSpeaker;
                room += virtDelay_[source][(virtPos_ + size - plan.virtReflection[r]) % size] * kVirtReflectionGain[r];
            }
            ear[e] = static_cast<float>(direct + virtRoom_[e].process(plan.virtRoom, room));
        }
        const float k = from + (to - from) * fadeIn(i, block_);
        work_[0][i] = dryL + k * (ear[0] * kVirtLevel - dryL);
        work_[1][i] = dryR + k * (ear[1] * kVirtLevel - dryR);
        virtPos_ = (virtPos_ + 1) % size;
    }
    virtAmount_ = to;
}

void Engine::reverbBlock(const Plan& plan) {
    const float from = reverbMix_, to = plan.reverbOn ? 1.0f : 0.0f;
    const float trimFrom = reverbTrim_;
    if (from == 0 && to == 0) {
        reverbClear_ = true;
        reverbTrim_ = 1;
        return;
    }
    if (reverbClear_) {
        reverb_->clear();
        reverbClear_ = false;
    }
    const bool stereo = channels_ >= 2;
    for (size_t i = 0; i < block_; ++i) {
        const float l = work_[0][i], r = stereo ? work_[1][i] : l;
        float wet[2];
        reverb_->tick(plan, 0.5f * (l + r), wet);
        const float fade = fadeIn(i, block_);
        const float mix = (from + (to - from) * fade) * plan.roomGain;
        const float trim = trimFrom + (plan.reverbTrim - trimFrom) * fade;
        for (uint32_t c = 0; c < channels_; ++c) {
            const float echo = c < 2 ? wet[stereo ? c : 0] * mix : 0.0f;
            work_[c][i] = (work_[c][i] + echo) * trim;
        }
    }
    reverbMix_ = to;
    reverbTrim_ = plan.reverbTrim;
}

void Engine::limiterBlock(const Plan& plan) {
    // Look-ahead: the sound waits `lookahead_` samples while the gain it needs is worked out. The lowest
    // gain needed in that window is held, then averaged over the attack time, so the gain has reached it
    // exactly when the peak plays, and gets there smoothly.
    const size_t window = lookahead_ + 1;
    const size_t cap = limHeldValue_.size();
    if (plan.limLen != limBoxLen_) {
        const double held = limHeldFront_ != limHeldBack_ ? limHeldValue_[limHeldFront_ % cap] : 1.0;
        limBoxLen_ = plan.limLen;
        std::fill(limBox_.begin(), limBox_.begin() + static_cast<ptrdiff_t>(limBoxLen_), held);
        limBoxSum_ = held * static_cast<double>(limBoxLen_);
        limPos_ %= lookahead_;
    }
    size_t boxPos = limCount_ % limBoxLen_;
    for (size_t i = 0; i < block_; ++i) {
        float peak = 0;
        for (uint32_t c = 0; c < channels_; ++c) peak = std::max(peak, std::fabs(work_[c][i]));
        float need = 1;
        if (plan.limOn && peak > plan.limThr) {
            const double level = 20.0 * std::log10(peak);
            const double outDb = plan.limThrDb + (level - plan.limThrDb) / plan.limRatio;
            need = static_cast<float>(std::pow(10.0, (outDb - level) / 20.0));
        }

        // Sliding minimum over the window, kept as a queue of rising values.
        while (limHeldBack_ > limHeldFront_ && limHeldValue_[(limHeldBack_ - 1) % cap] >= need) --limHeldBack_;
        limHeldValue_[limHeldBack_ % cap] = need;
        limHeldIndex_[limHeldBack_ % cap] = limCount_;
        ++limHeldBack_;
        while (limHeldIndex_[limHeldFront_ % cap] + window <= limCount_) ++limHeldFront_;
        const double held = limHeldValue_[limHeldFront_ % cap];

        limBoxSum_ += held - limBox_[boxPos];
        limBox_[boxPos] = held;
        if (++boxPos == limBoxLen_) boxPos = 0;
        const double smooth = std::min(1.0, limBoxSum_ / static_cast<double>(limBoxLen_));
        limGain_ = smooth < limGain_ ? smooth : limGain_ + (1 - plan.limRelease) * (smooth - limGain_);
        ++limCount_;

        const float g = static_cast<float>(limGain_ * plan.limPost);
        for (uint32_t c = 0; c < channels_; ++c) {
            float& slot = limDelay_[c][limPos_];
            const float delayed = slot;
            slot = work_[c][i];
            work_[c][i] = delayed * g;
        }
        if (++limPos_ == lookahead_) limPos_ = 0;
    }
    // Keep the running sum honest: rounding drifts over hours.
    if ((limCount_ & 0xFFFF) < block_) {
        double sum = 0;
        for (size_t k = 0; k < limBoxLen_; ++k) sum += limBox_[k];
        limBoxSum_ = sum;
    }
}

}  // namespace eigentone::dsp
