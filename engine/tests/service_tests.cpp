// Tests for the service's pieces that don't need Windows Audio: settings as JSON, and output kinds.
#include <cmath>
#include <cstdio>
#include <cstring>

#include "../service/params_json.h"
#include "eigentone/system.h"

using namespace eigentone;
using nlohmann::json;

namespace {

int failures = 0;
void check(bool ok, const char* what) {
    std::printf("%s  %s\n", ok ? "pass" : "FAIL", what);
    if (!ok) ++failures;
}

void testRoundTrip() {
    dsp::Params p = dsp::neutralParams();
    p.enabled = 1;
    p.inputGainDb[0] = -3.5f;
    p.inputGainDb[1] = -2.0f;
    p.eq.bandCount = 3;
    const float cut[] = {100, 1000, 20000}, left[] = {1, -2, 3}, right[] = {0.5f, -1, 2};
    for (int i = 0; i < 3; ++i) {
        p.eq.cutoffHz[i] = cut[i];
        p.eq.gainDb[0][i] = left[i];
        p.eq.gainDb[1][i] = right[i];
    }
    p.mbc.enabled = 1;
    p.mbc.bandCount = 2;
    p.mbc.bands[0] = {1, 200, 20, 300, 2, -20, 6, -70, 1.5f, 1, 4};
    p.mbc.bands[1] = {0, 24000, 5, 150, 3, -25, 3, -60, 1, 0, 2};
    p.virtualizer = {1, 0.7f};
    p.reverb = {1, 0, -600, 4000, 0.7f, -1000, 20, -600, 30, 1, 0.8f};
    p.limiter = {1, 2, 80, 12, -1.5f, 0.5f};

    const dsp::Params back = service::paramsFromJson(json::parse(service::paramsToJson(p).dump()));
    // Compare field by field: the struct has padding, so memcmp would compare junk.
    bool same = back.enabled == p.enabled && back.inputGainDb[0] == p.inputGainDb[0] && back.inputGainDb[1] == p.inputGainDb[1] &&
                back.eq.bandCount == 3 && back.mbc.bandCount == 2 && back.mbc.enabled == 1;
    for (int i = 0; i < 3; ++i) {
        same &= back.eq.cutoffHz[i] == cut[i] && back.eq.gainDb[0][i] == left[i] && back.eq.gainDb[1][i] == right[i];
    }
    same &= std::memcmp(&back.mbc.bands[0], &p.mbc.bands[0], sizeof(dsp::MbcBand) * 2) == 0;
    same &= std::memcmp(&back.virtualizer, &p.virtualizer, sizeof(p.virtualizer)) == 0;
    same &= std::memcmp(&back.reverb, &p.reverb, sizeof(p.reverb)) == 0;
    same &= std::memcmp(&back.limiter, &p.limiter, sizeof(p.limiter)) == 0;
    check(same, "settings survive the trip to JSON and back");
}

void testDefaults() {
    const dsp::Params p = service::paramsFromJson(json::parse(R"({"eq": {"cutoffHz": [100, 200], "gainDb": [[3, 4]]}})"));
    const dsp::Params n = dsp::neutralParams();
    check(p.enabled == 1 && p.mbc.enabled == 0 && p.limiter.enabled == n.limiter.enabled && p.reverb.enabled == 0,
          "anything not sent stays neutral");
    check(p.eq.bandCount == 2 && p.eq.gainDb[1][0] == 3 && p.eq.gainDb[1][1] == 4, "one curve is used for both channels");

    json many = {{"eq", {{"cutoffHz", json::array()}, {"gainDb", {json::array()}}}}};
    for (int i = 0; i < 400; ++i) {
        many["eq"]["cutoffHz"].push_back(20 + i);
        many["eq"]["gainDb"][0].push_back(0);
    }
    check(service::paramsFromJson(many).eq.bandCount == dsp::kMaxEqBands, "a curve with too many bands is cut to what the engine takes");

    bool threw = false;
    try {
        service::paramsFromJson(json::parse(R"({"limiter": {"thresholdDb": "loud"}})"));
    } catch (const json::exception&) {
        threw = true;
    }
    check(threw, "a wrong type is refused");
}

void testKinds() {
    using sys::Kind;
    sys::Output laptop;
    laptop.bus = L"HDAUDIO";
    laptop.formFactor = 1;
    laptop.name = L"Speaker/HP (Realtek(R) Audio)";
    laptop.jacks = {{7, 0, 1, true}, {1, 0, 0, false}};
    check(sys::classify(laptop) == Kind::BuiltIn, "a laptop's speakers, with nothing in the jack, are built-in speakers");
    laptop.jacks[1].connected = true;
    check(sys::classify(laptop) == Kind::Headphones, "the same output with headphones in the jack is headphones");

    sys::Output lineOut;
    lineOut.bus = L"HDAUDIO";
    lineOut.formFactor = 1;
    lineOut.name = L"Speakers (Realtek(R) Audio)";
    lineOut.jacks = {{1, 0, 0, true}};
    check(sys::classify(lineOut) == Kind::Speakers, "a desktop's line-out with speakers plugged in is speakers");

    sys::Output bt;
    bt.bus = L"BTHENUM";
    bt.formFactor = 3;
    bt.name = L"Headphones (Galaxy Buds2 Pro)";
    check(sys::classify(bt) == Kind::Earbuds, "Bluetooth buds are earbuds");
    bt.name = L"Headphones (WH-1000XM5)";
    check(sys::classify(bt) == Kind::Headphones, "Bluetooth headphones are headphones");
    bt.name = L"Speakers (JBL Flip 6)";
    bt.formFactor = 1;
    check(sys::classify(bt) == Kind::Speakers, "a Bluetooth speaker is speakers");
    bt.name = L"Headphones (Toyota Corolla)";
    check(sys::classify(bt) == Kind::Car, "a car is a car");

    sys::Output monitor;
    monitor.bus = L"INTELAUDIO";
    monitor.formFactor = 9;
    monitor.name = L"LD08 U2414H (Intel(R) Display Audio)";
    check(sys::classify(monitor) == Kind::Display, "a monitor's speakers are a TV or monitor");
}

}  // namespace

int main() {
    testRoundTrip();
    testDefaults();
    testKinds();
    std::printf("\n%s (%d failed)\n", failures ? "FAILED" : "all passed", failures);
    return failures ? 1 : 0;
}
