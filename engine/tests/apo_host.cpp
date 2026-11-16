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
void check(bool ok, const char* what) {
    std::printf("%s  %s\n", ok ? "pass" : "FAIL", what);
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

    APO_CONNECTION_PROPERTY inP{reinterpret_cast<UINT_PTR>(in.data()), frames, BUFFER_VALID, APO_CONNECTION_PROPERTY_SIGNATURE};
    APO_CONNECTION_PROPERTY outP{reinterpret_cast<UINT_PTR>(out.data()), 0, BUFFER_INVALID, APO_CONNECTION_PROPERTY_SIGNATURE};
    APO_CONNECTION_PROPERTY* inPs[] = {&inP};
    APO_CONNECTION_PROPERTY* outPs[] = {&outP};
    double maxDiff = 0;
    for (int b = 0; b < 10; ++b) {
        for (UINT32 i = 0; i < frames; ++i) {
            const float s = static_cast<float>(std::sin(2 * 3.14159265358979 * 1000 * (b * frames + i) / 48000.0));
            in[i * 2] = s;
            in[i * 2 + 1] = -s;
        }
        rt->APOProcess(1, inPs, 1, outPs);
        for (UINT32 i = 0; i < frames * ch; ++i) maxDiff = std::fmax(maxDiff, std::fabs(out[i] - in[i]));
    }
    // Without the shared block (an ordinary user can't create it), the plug-in passes sound through at 0 dB.
    check(outP.u32ValidFrameCount == frames && outP.u32BufferFlags == BUFFER_VALID, "marks the output valid");
    check(maxDiff < 1e-6, "passes sound through unchanged at 0 dB");

    inP.u32BufferFlags = BUFFER_SILENT;
    out.assign(out.size(), 1.0f);
    rt->APOProcess(1, inPs, 1, outPs);
    check(outP.u32BufferFlags == BUFFER_SILENT && out[0] == 0.0f, "silence stays silent");

    // In place, as some chains run it.
    inP.u32BufferFlags = BUFFER_VALID;
    APO_CONNECTION_PROPERTY sameP{reinterpret_cast<UINT_PTR>(in.data()), 0, BUFFER_INVALID, APO_CONNECTION_PROPERTY_SIGNATURE};
    APO_CONNECTION_PROPERTY* samePs[] = {&sameP};
    const float before = in[10];
    rt->APOProcess(1, inPs, 1, samePs);
    check(in[10] == before, "works in place");

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
