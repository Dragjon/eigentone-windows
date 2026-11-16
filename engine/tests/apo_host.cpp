// Loads the plug-in the way Windows' audio engine does, outside it, and runs sound through it.
// A plug-in that crashes here would have crashed the PC's audio, so this runs before any install.
#include <windows.h>
#include <audioenginebaseapo.h>
#include <audiomediatype.h>
#include <mmreg.h>
#include <ks.h>
#include <ksmedia.h>

#include <cmath>
#include <cstdio>
#include <vector>

#include "eigentone/shared.h"

namespace {

int failures = 0;
void check(bool ok, const char* what, double value = -1) {
    if (value >= 0) std::printf("%s  %s  (%.4g)\n", ok ? "pass" : "FAIL", what, value);
    else std::printf("%s  %s\n", ok ? "pass" : "FAIL", what);
    if (!ok) ++failures;
}

/// A minimal stand-in for the audio engine's format object.
class Format final : public IAudioMediaType {
public:
    Format(UINT32 channels, float rate, bool isFloat) {
        f_.guidFormatType = isFloat ? KSDATAFORMAT_SUBTYPE_IEEE_FLOAT : KSDATAFORMAT_SUBTYPE_PCM;
        f_.dwSamplesPerFrame = channels;
        f_.dwBytesPerSampleContainer = isFloat ? 4 : 2;
        f_.dwValidBitsPerSample = isFloat ? 32 : 16;
        f_.fFramesPerSecond = rate;
    }
    STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override {
        if (riid == __uuidof(IUnknown) || riid == __uuidof(IAudioMediaType)) { *ppv = this; AddRef(); return S_OK; }
        *ppv = nullptr;
        return E_NOINTERFACE;
    }
    STDMETHODIMP_(ULONG) AddRef() override { return ++refs_; }
    STDMETHODIMP_(ULONG) Release() override { return --refs_; }  // lives on the stack
    STDMETHODIMP IsCompressedFormat(BOOL* c) override { *c = FALSE; return S_OK; }
    STDMETHODIMP IsEqual(IAudioMediaType*, DWORD*) override { return E_NOTIMPL; }
    STDMETHODIMP_(const WAVEFORMATEX*) GetAudioFormat() override { return nullptr; }
    STDMETHODIMP GetUncompressedAudioFormat(UNCOMPRESSEDAUDIOFORMAT* out) override { *out = f_; return S_OK; }
    ULONG refs() const { return refs_; }

private:
    UNCOMPRESSEDAUDIOFORMAT f_{};
    ULONG refs_ = 1;
};

}  // namespace

int wmain(int argc, wchar_t** argv) {
    const wchar_t* path = argc > 1 ? argv[1] : L"EigentoneApo.dll";
    HMODULE dll = LoadLibraryW(path);
    check(dll != nullptr, "the DLL loads");
    if (!dll) return 1;

    using GetClassObject = HRESULT(STDAPICALLTYPE*)(REFCLSID, REFIID, void**);
    auto getClass = reinterpret_cast<GetClassObject>(GetProcAddress(dll, "DllGetClassObject"));
    check(getClass != nullptr, "it exports DllGetClassObject");
    IClassFactory* factory = nullptr;
    check(SUCCEEDED(getClass(eigentone::kApoClsid, IID_PPV_ARGS(&factory))), "it hands out a factory for its CLSID");
    void* none = nullptr;
    check(getClass(GUID{}, __uuidof(IClassFactory), &none) == CLASS_E_CLASSNOTAVAILABLE, "it refuses other CLSIDs");

    // Created plainly and aggregated, as the audio engine may do either.
    IUnknown* outerless = nullptr;
    check(SUCCEEDED(factory->CreateInstance(nullptr, IID_PPV_ARGS(&outerless))), "it creates an instance");
    void* bad = nullptr;
    check(factory->CreateInstance(outerless, __uuidof(IAudioProcessingObject), &bad) == CLASS_E_NOAGGREGATION,
          "aggregation must ask for IUnknown");

    IAudioProcessingObject* apo = nullptr;
    IAudioProcessingObjectRT* rt = nullptr;
    IAudioProcessingObjectConfiguration* cfg = nullptr;
    IAudioSystemEffects* fx = nullptr;
    check(SUCCEEDED(outerless->QueryInterface(IID_PPV_ARGS(&apo))), "IAudioProcessingObject");
    check(SUCCEEDED(outerless->QueryInterface(IID_PPV_ARGS(&rt))), "IAudioProcessingObjectRT");
    check(SUCCEEDED(outerless->QueryInterface(IID_PPV_ARGS(&cfg))), "IAudioProcessingObjectConfiguration");
    check(SUCCEEDED(outerless->QueryInterface(IID_PPV_ARGS(&fx))), "IAudioSystemEffects");

    APO_REG_PROPERTIES* props = nullptr;
    check(SUCCEEDED(apo->GetRegistrationProperties(&props)) && props->clsid == eigentone::kApoClsid,
          "registration properties name its CLSID");
    CoTaskMemFree(props);

    check(SUCCEEDED(apo->Initialize(0, nullptr)), "Initialize with no data");
    check(apo->Initialize(0, nullptr) == APOERR_ALREADY_INITIALIZED, "a second Initialize is refused");

    Format f48(2, 48000, true), pcm(2, 48000, false), f44(2, 44100, true);
    IAudioMediaType* got = nullptr;
    check(apo->IsInputFormatSupported(nullptr, &f48, &got) == S_OK && got == &f48, "accepts 32-bit float");
    if (got) got->Release();
    check(apo->IsInputFormatSupported(nullptr, &pcm, &got) == APOERR_FORMAT_NOT_SUPPORTED, "refuses 16-bit PCM");
    check(apo->IsOutputFormatSupported(&f48, &f44, &got) == S_FALSE && got == &f48, "suggests the matching rate");
    if (got) got->Release();

    // Process a 1 kHz tone at full scale, ten buffers of 10 ms.
    const UINT32 frames = 480, ch = 2;
    std::vector<float> in(frames * ch), out(frames * ch);
    APO_CONNECTION_DESCRIPTOR inD{APO_CONNECTION_BUFFER_TYPE_EXTERNAL, reinterpret_cast<UINT_PTR>(in.data()), frames, &f48, APO_CONNECTION_DESCRIPTOR_SIGNATURE};
    APO_CONNECTION_DESCRIPTOR outD{APO_CONNECTION_BUFFER_TYPE_EXTERNAL, reinterpret_cast<UINT_PTR>(out.data()), frames, &f48, APO_CONNECTION_DESCRIPTOR_SIGNATURE};
    APO_CONNECTION_DESCRIPTOR* ins[] = {&inD};
    APO_CONNECTION_DESCRIPTOR* outs[] = {&outD};
    check(SUCCEEDED(cfg->LockForProcess(1, ins, 1, outs)), "locks for 48 kHz stereo float");
    UINT32 count = 0;
    check(SUCCEEDED(apo->GetInputChannelCount(&count)) && count == 2, "reports 2 channels");

    HNSTIME latency = 0;
    const bool gotLatency = SUCCEEDED(apo->GetLatency(&latency));
    check(gotLatency && latency > 0 && latency < 100000, "reports its delay, under 10 ms", latency / 10000.0);
    const size_t delay = static_cast<size_t>(std::llround(latency * 48000.0 / 1e7));

    // Ten buffers of a 1 kHz tone, separate buffers and then in place. Without the shared block (an ordinary
    // user can't create it), the engine runs neutral, so the output is the input, delayed.
    APO_CONNECTION_PROPERTY inP{reinterpret_cast<UINT_PTR>(in.data()), frames, BUFFER_VALID, APO_CONNECTION_PROPERTY_SIGNATURE};
    APO_CONNECTION_PROPERTY outP{reinterpret_cast<UINT_PTR>(out.data()), 0, BUFFER_INVALID, APO_CONNECTION_PROPERTY_SIGNATURE};
    APO_CONNECTION_PROPERTY sameP{reinterpret_cast<UINT_PTR>(in.data()), 0, BUFFER_INVALID, APO_CONNECTION_PROPERTY_SIGNATURE};
    APO_CONNECTION_PROPERTY* inPs[] = {&inP};
    APO_CONNECTION_PROPERTY* outPs[] = {&outP};
    APO_CONNECTION_PROPERTY* samePs[] = {&sameP};
    auto tone = [](size_t n) { return static_cast<float>(std::sin(2 * 3.14159265358979 * 1000 * static_cast<double>(n) / 48000.0)); };
    double maxDiff = 0;
    bool valid = true;
    for (int b = 0; b < 20; ++b) {
        for (UINT32 i = 0; i < frames; ++i) {
            in[i * 2] = tone(b * frames + i);
            in[i * 2 + 1] = -tone(b * frames + i);
        }
        const bool inPlace = b >= 10;
        rt->APOProcess(1, inPs, 1, inPlace ? samePs : outPs);
        const std::vector<float>& result = inPlace ? in : out;
        valid &= (inPlace ? sameP : outP).u32ValidFrameCount == frames && (inPlace ? sameP : outP).u32BufferFlags == BUFFER_VALID;
        for (UINT32 i = 0; i < frames; ++i) {
            const size_t n = b * frames + i;
            const float want = n >= delay ? tone(n - delay) : 0.0f;
            maxDiff = std::fmax(maxDiff, std::fabs(result[i * 2] - want));
            maxDiff = std::fmax(maxDiff, std::fabs(result[i * 2 + 1] + want));
        }
    }
    check(valid, "marks the output valid");
    check(maxDiff < 1e-5, "passes sound through unchanged, only delayed, in separate buffers and in place", maxDiff);

    // A silent input plays out what's still inside, then silence. Its buffer isn't touched.
    inP.u32BufferFlags = BUFFER_SILENT;
    std::fill(in.begin(), in.end(), 0.5f);  // a "silent" buffer's contents aren't promised to be zero
    double tail = 0;
    for (int b = 0; b < 3; ++b) {
        rt->APOProcess(1, inPs, 1, outPs);
        if (b == 2) for (float v : out) tail = std::fmax(tail, std::fabs(v));
    }
    check(tail == 0.0, "silence comes out silent once the delay has played out");
    check(in[0] == 0.5f, "a silent input's buffer is left alone");

    check(SUCCEEDED(cfg->UnlockForProcess()), "unlocks");
    check(cfg->UnlockForProcess() == APOERR_ALREADY_UNLOCKED, "a second unlock is refused");

    apo->Release();
    rt->Release();
    cfg->Release();
    fx->Release();
    check(outerless->Release() == 0, "releases to zero");
    using CanUnload = HRESULT(STDAPICALLTYPE*)();
    auto canUnload = reinterpret_cast<CanUnload>(GetProcAddress(dll, "DllCanUnloadNow"));
    check(canUnload && canUnload() == S_OK, "the DLL can unload once everything is released");
    check(f48.refs() == 1, "format objects are not leaked");

    std::printf("\n%s (%d failed)\n", failures ? "FAILED" : "all passed", failures);
    return failures ? 1 : 0;
}
