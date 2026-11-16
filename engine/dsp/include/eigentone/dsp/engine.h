// Eigentone for Windows: the sound engine. Free of Windows audio types, so it can be tested on its own.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#pragma once

#include <atomic>
#include <complex>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

#include "eigentone/dsp/design.h"
#include "eigentone/dsp/fft.h"
#include "eigentone/dsp/params.h"

namespace eigentone::dsp {

/**
 * The chain, as on Android: input gain, EQ, multiband compressor, then (Windows only) surround and reverb,
 * then the limiter, so the limiter catches everything.
 *
 * Two threads use it. A settings thread calls update(), which does all the slow work (designing filters,
 * working out coefficients) into a spare Plan and hands it over. The audio thread calls process(), which
 * picks up the newest Plan at the start of a block and never allocates, locks or waits. Sound is processed
 * in blocks, so the delay is fixed: one block plus the limiter's look-ahead.
 */
class Engine {
public:
    /// Frames per block at 44.1 and 48 kHz, doubled at 96 kHz and again at 192 kHz, so the delay stays about 5 ms.
    static constexpr size_t kBaseBlock = 256;
    /// Compressor gains are worked out every this many frames, and eased between.
    static constexpr size_t kHop = 16;
    static constexpr double kLookaheadMs = 2.0;

    Engine();
    ~Engine();
    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    /// Sizes everything for a format and starts from neutral settings. Not for the audio thread.
    void prepare(double sampleRate, uint32_t channels);
    /// Works out new settings and queues them. Returns false if every spare plan is still in use; try again shortly.
    bool update(const Params& params);
    /// Processes interleaved float frames. `in` and `out` may be the same buffer. Audio thread only.
    void process(const float* in, float* out, uint32_t frames);
    /// Clears all sound held inside (EQ history, reverb tail, compressor state). Not during process().
    void reset();

    size_t latencyFrames() const { return block_ + lookahead_; }
    size_t blockFrames() const { return block_; }
    double sampleRate() const { return rate_; }
    uint32_t channels() const { return channels_; }

    struct Plan;

private:
    void runBlock();
    void eqBlock(Plan* now, Plan* before);
    void mbcBlock(const Plan& plan);
    void virtualizerBlock(const Plan& plan);
    void reverbBlock(const Plan& plan);
    void limiterBlock(const Plan& plan);
    void build(Plan& plan, const Params& params);

    double rate_ = 48000;
    uint32_t channels_ = 0;
    size_t block_ = kBaseBlock;
    size_t fftSize_ = 2 * kBaseBlock;
    size_t bins_ = kBaseBlock + 1;  // a real signal's spectrum is symmetric, so only half of it is kept
    size_t taps_ = 0;
    size_t partitions_ = 0;
    size_t lookahead_ = 0;
    std::unique_ptr<Fft<float>> fft_;  // size 2 * block_; its tables are read-only, so both threads share it

    static constexpr int kPlans = 3;
    std::unique_ptr<Plan> plans_[kPlans];
    std::atomic<Plan*> pending_{nullptr};
    Plan* current_ = nullptr;

    // The block being filled and the block being played, one per channel.
    std::vector<std::vector<float>> in_, out_, work_, dry_;
    size_t pos_ = 0;

    // Input gain and the on/off crossfade, eased across each block. Switched off, the sound still takes the
    // same delay, so switching never jumps.
    float gain_[kCurveChannels] = {1, 1};
    float wetMix_ = 1;
    std::vector<std::vector<float>> dryDelay_;
    size_t dryPos_ = 0;

    // EQ: overlap-save convolution, split into block-long partitions.
    struct EqChannel {
        std::vector<float> history;                    // the last two blocks of input
        std::vector<std::complex<float>> spectra;      // partitions_ past input spectra, newest at `head`
        std::vector<std::complex<float>> acc, accOld;  // scratch for this block's output, old and new filter
        size_t head = 0;
    };
    std::vector<EqChannel> eq_;

    // Compressor: Linkwitz-Riley crossovers, kept in double.
    struct MbcChannel {
        BiquadState lp[kMaxMbcBands][2], hp[kMaxMbcBands][2];
        BiquadState ap[kMaxMbcBands][kMaxMbcBands];
    };
    std::vector<MbcChannel> mbc_;
    double bandGainDb_[kMaxMbcBands] = {};
    std::vector<double> bandScratch_;  // kHop x channels x bands
    double bandEnv_[kMaxMbcBands] = {};
    float bandLin_[kMaxMbcBands] = {};
    // Off and settled, the compressor steps out of the way entirely: its band filters keep the level flat
    // but still shift the waveform. It crossfades out and back in, so switching never clicks.
    float mbcBlend_ = 0;  // 1 when the sound goes through the bands
    std::vector<std::vector<float>> mbcDry_;

    // Surround.
    // Surround: the two channels played from virtual speakers in front, as each ear would hear them.
    std::vector<float> virtDelay_[2];   // each channel's recent past, for the delays to each ear
    size_t virtPos_ = 0;
    BiquadState virtHead_[2][2];        // [speaker][ear]: the head's shading of that path
    BiquadState virtRoom_[2];           // per ear: softening of the room's reflections
    float virtAmount_ = 0;

    // Reverb: a feedback delay network.
    struct ReverbState;
    std::unique_ptr<ReverbState> reverb_;      // the reverb that plays
    std::unique_ptr<ReverbState> reverbProbe_; // settings thread only: measures what a reverb adds
    ReverbParams probedFor_{};
    float probedTrim_ = 1;
    float reverbMix_ = 0;
    float reverbTrim_ = 1;  // turns the whole sound down by what the echoes add, so loudness stays the same
    bool reverbClear_ = true;

    // Limiter: look-ahead, so it reaches the right level by the time a peak plays.
    std::vector<std::vector<float>> limDelay_;
    std::vector<float> limHeldValue_;
    std::vector<size_t> limHeldIndex_;
    size_t limHeldFront_ = 0, limHeldBack_ = 0;
    std::vector<double> limBox_;
    double limBoxSum_ = 0;
    size_t limBoxLen_ = 1;
    size_t limPos_ = 0;
    size_t limCount_ = 0;
    double limGain_ = 1;
};

}  // namespace eigentone::dsp
