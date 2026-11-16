// Eigentone for Windows: the sound engine's settings as JSON.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
//
// {
//   "enabled": true,
//   "inputGainDb": [0, 0],
//   "eq": {"cutoffHz": [...], "gainDb": [[left...], [right...]]},
//   "mbc": {"enabled": false, "bands": [{"enabled": true, "cutoffHz": 200, "attackMs": 20, ...}]},
//   "virtualizer": {"enabled": false, "strength": 0},
//   "reverb": {"enabled": false, "roomLevelMb": 0, "roomHfLevelMb": -600, "decayTimeMs": 1500, ...},
//   "limiter": {"enabled": true, "attackMs": 1, "releaseMs": 60, "ratio": 10, "thresholdDb": -1, "postGainDb": 0}
// }
#include "params_json.h"

#include <algorithm>

namespace eigentone::service {

using nlohmann::json;

namespace {

template <class T>
void get(const json& j, const char* key, T& out) {
    if (const auto it = j.find(key); it != j.end() && !it->is_null()) out = it->get<T>();
}

void getFlag(const json& j, const char* key, uint32_t& out) {
    if (const auto it = j.find(key); it != j.end() && !it->is_null()) out = it->get<bool>() ? 1u : 0u;
}

}  // namespace

dsp::Params paramsFromJson(const json& j) {
    dsp::Params p = dsp::neutralParams();
    getFlag(j, "enabled", p.enabled);
    if (const auto it = j.find("inputGainDb"); it != j.end()) {
        for (size_t c = 0; c < dsp::kCurveChannels && c < it->size(); ++c) p.inputGainDb[c] = (*it)[c].get<float>();
    }

    if (const auto eq = j.find("eq"); eq != j.end()) {
        const json& cut = eq->at("cutoffHz");
        const json& gain = eq->at("gainDb");
        const size_t n = std::min<size_t>(cut.size(), dsp::kMaxEqBands);
        p.eq.bandCount = static_cast<uint32_t>(n);
        for (size_t i = 0; i < n; ++i) p.eq.cutoffHz[i] = cut[i].get<float>();
        for (size_t c = 0; c < dsp::kCurveChannels; ++c) {
            // One curve for both channels is allowed; it's the common case.
            const json& curve = gain.size() > c ? gain[c] : gain[0];
            for (size_t i = 0; i < n; ++i) p.eq.gainDb[c][i] = i < curve.size() ? curve[i].get<float>() : 0.0f;
        }
    }

    if (const auto mbc = j.find("mbc"); mbc != j.end()) {
        getFlag(*mbc, "enabled", p.mbc.enabled);
        const json& bands = mbc->at("bands");
        p.mbc.bandCount = static_cast<uint32_t>(std::min<size_t>(bands.size(), dsp::kMaxMbcBands));
        for (size_t b = 0; b < p.mbc.bandCount; ++b) {
            const json& in = bands[b];
            dsp::MbcBand& out = p.mbc.bands[b];
            out = {1, 24000, 20, 200, 1, 0, 0, -90, 1, 0, 0};
            getFlag(in, "enabled", out.enabled);
            get(in, "cutoffHz", out.cutoffHz);
            get(in, "attackMs", out.attackMs);
            get(in, "releaseMs", out.releaseMs);
            get(in, "ratio", out.ratio);
            get(in, "thresholdDb", out.thresholdDb);
            get(in, "kneeDb", out.kneeDb);
            get(in, "noiseGateDb", out.noiseGateDb);
            get(in, "expanderRatio", out.expanderRatio);
            get(in, "preGainDb", out.preGainDb);
            get(in, "postGainDb", out.postGainDb);
        }
    }

    if (const auto v = j.find("virtualizer"); v != j.end()) {
        getFlag(*v, "enabled", p.virtualizer.enabled);
        get(*v, "strength", p.virtualizer.strength);
    }

    if (const auto r = j.find("reverb"); r != j.end()) {
        getFlag(*r, "enabled", p.reverb.enabled);
        get(*r, "roomLevelMb", p.reverb.roomLevelMb);
        get(*r, "roomHfLevelMb", p.reverb.roomHfLevelMb);
        get(*r, "decayTimeMs", p.reverb.decayTimeMs);
        get(*r, "decayHfRatio", p.reverb.decayHfRatio);
        get(*r, "reflectionsLevelMb", p.reverb.reflectionsLevelMb);
        get(*r, "reflectionsDelayMs", p.reverb.reflectionsDelayMs);
        get(*r, "reverbLevelMb", p.reverb.reverbLevelMb);
        get(*r, "reverbDelayMs", p.reverb.reverbDelayMs);
        get(*r, "diffusion", p.reverb.diffusion);
        get(*r, "density", p.reverb.density);
    }

    if (const auto l = j.find("limiter"); l != j.end()) {
        getFlag(*l, "enabled", p.limiter.enabled);
        get(*l, "attackMs", p.limiter.attackMs);
        get(*l, "releaseMs", p.limiter.releaseMs);
        get(*l, "ratio", p.limiter.ratio);
        get(*l, "thresholdDb", p.limiter.thresholdDb);
        get(*l, "postGainDb", p.limiter.postGainDb);
    }
    return p;
}

json paramsToJson(const dsp::Params& p) {
    json j;
    j["enabled"] = p.enabled != 0;
    j["inputGainDb"] = {p.inputGainDb[0], p.inputGainDb[1]};

    json cut = json::array(), left = json::array(), right = json::array();
    for (uint32_t i = 0; i < std::min(p.eq.bandCount, dsp::kMaxEqBands); ++i) {
        cut.push_back(p.eq.cutoffHz[i]);
        left.push_back(p.eq.gainDb[0][i]);
        right.push_back(p.eq.gainDb[1][i]);
    }
    j["eq"] = {{"cutoffHz", cut}, {"gainDb", {left, right}}};

    json bands = json::array();
    for (uint32_t b = 0; b < std::min(p.mbc.bandCount, dsp::kMaxMbcBands); ++b) {
        const dsp::MbcBand& in = p.mbc.bands[b];
        bands.push_back({{"enabled", in.enabled != 0}, {"cutoffHz", in.cutoffHz}, {"attackMs", in.attackMs},
                         {"releaseMs", in.releaseMs}, {"ratio", in.ratio}, {"thresholdDb", in.thresholdDb},
                         {"kneeDb", in.kneeDb}, {"noiseGateDb", in.noiseGateDb}, {"expanderRatio", in.expanderRatio},
                         {"preGainDb", in.preGainDb}, {"postGainDb", in.postGainDb}});
    }
    j["mbc"] = {{"enabled", p.mbc.enabled != 0}, {"bands", bands}};
    j["virtualizer"] = {{"enabled", p.virtualizer.enabled != 0}, {"strength", p.virtualizer.strength}};
    const dsp::ReverbParams& r = p.reverb;
    j["reverb"] = {{"enabled", r.enabled != 0}, {"roomLevelMb", r.roomLevelMb}, {"roomHfLevelMb", r.roomHfLevelMb},
                   {"decayTimeMs", r.decayTimeMs}, {"decayHfRatio", r.decayHfRatio},
                   {"reflectionsLevelMb", r.reflectionsLevelMb}, {"reflectionsDelayMs", r.reflectionsDelayMs},
                   {"reverbLevelMb", r.reverbLevelMb}, {"reverbDelayMs", r.reverbDelayMs},
                   {"diffusion", r.diffusion}, {"density", r.density}};
    const dsp::LimiterParams& l = p.limiter;
    j["limiter"] = {{"enabled", l.enabled != 0}, {"attackMs", l.attackMs}, {"releaseMs", l.releaseMs},
                    {"ratio", l.ratio}, {"thresholdDb", l.thresholdDb}, {"postGainDb", l.postGainDb}};
    return j;
}

}  // namespace eigentone::service
