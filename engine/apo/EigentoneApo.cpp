// Eigentone for Windows: the audio plug-in.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#include "EigentoneApo.h"

#include <mmreg.h>
#include <ks.h>
#include <ksmedia.h>
#include <mmdeviceapi.h>
#include <propvarutil.h>
#include <sddl.h>

#include <cmath>
#include <cstring>
#include <cwchar>

namespace eigentone {

volatile LONG gServerLocks = 0;

namespace {

// {1da5d803-d492-4edd-8c23-e0c0ffee7f0e},4: the output's own GUID, the same one its registry key is named after.
constexpr PROPERTYKEY kEndpointGuid = {{0x1da5d803, 0xd492, 0x4edd, {0x8c, 0x23, 0xe0, 0xc0, 0xff, 0xee, 0x7f, 0x0e}}, 4};

constexpr DWORD kPollMs = 15;  // how often new settings are looked for: quick enough to feel instant

bool isFloat32(const UNCOMPRESSEDAUDIOFORMAT& f) {
    return f.guidFormatType == KSDATAFORMAT_SUBTYPE_IEEE_FLOAT && f.dwBytesPerSampleContainer == 4 &&
           f.dwValidBitsPerSample == 32;
}

}  // namespace

// ---------------------------------------------------------------------------------------------------------
// SharedLink

SharedLink::SharedLink() {
    PSECURITY_DESCRIPTOR sd = nullptr;
    if (!ConvertStringSecurityDescriptorToSecurityDescriptorW(kSectionSddl, SDDL_REVISION_1, &sd, nullptr)) return;
    SECURITY_ATTRIBUTES sa{sizeof(sa), sd, FALSE};
    // Tests load the plug-in in their own process and give it a private block, so they never read or
    // change the settings of the plug-in that's really playing. Inside audiodg this is never set.
    wchar_t name[128];
    const DWORD custom = GetEnvironmentVariableW(L"EIGENTONE_SECTION", name, 128);
    if (custom == 0 || custom >= 128) wcscpy_s(name, kSectionName);
    mapping_ = CreateFileMappingW(INVALID_HANDLE_VALUE, &sa, PAGE_READWRITE, 0, sizeof(SharedBlock), name);
    const bool existed = GetLastError() == ERROR_ALREADY_EXISTS;
    LocalFree(sd);
    if (!mapping_) return;
    block_ = static_cast<SharedBlock*>(MapViewOfFile(mapping_, FILE_MAP_ALL_ACCESS, 0, 0, sizeof(SharedBlock)));
    if (!block_) return;
    if (!existed) {
        block_->version = kVersion;
        writeParams(block_, dsp::neutralParams());
        MemoryBarrier();
        block_->magic = kMagic;  // last, so a reader never sees a half-made block
    }
}

SharedLink::~SharedLink() {
    if (block_) UnmapViewOfFile(block_);
    if (mapping_) CloseHandle(mapping_);
}

// ---------------------------------------------------------------------------------------------------------
// IUnknown

EigentoneApo::EigentoneApo(IUnknown* outer) : outer_(outer ? outer : &inner_) {
    inner_.self = this;
    InterlockedIncrement(&gServerLocks);
}

EigentoneApo::~EigentoneApo() {
    stopWorker();
    releaseSlot();
    InterlockedDecrement(&gServerLocks);
}

STDMETHODIMP EigentoneApo::Inner::QueryInterface(REFIID riid, void** ppv) {
    if (!ppv) return E_POINTER;
    if (riid == __uuidof(IUnknown)) {
        *ppv = static_cast<IUnknown*>(this);
    } else if (riid == __uuidof(IAudioProcessingObject)) {
        *ppv = static_cast<IAudioProcessingObject*>(self);
    } else if (riid == __uuidof(IAudioProcessingObjectRT)) {
        *ppv = static_cast<IAudioProcessingObjectRT*>(self);
    } else if (riid == __uuidof(IAudioProcessingObjectConfiguration)) {
        *ppv = static_cast<IAudioProcessingObjectConfiguration*>(self);
    } else if (riid == __uuidof(IAudioSystemEffects)) {
        *ppv = static_cast<IAudioSystemEffects*>(self);
    } else {
        *ppv = nullptr;
        return E_NOINTERFACE;
    }
    static_cast<IUnknown*>(*ppv)->AddRef();
    return S_OK;
}

STDMETHODIMP_(ULONG) EigentoneApo::Inner::AddRef() { return InterlockedIncrement(&self->refs_); }

STDMETHODIMP_(ULONG) EigentoneApo::Inner::Release() {
    const LONG left = InterlockedDecrement(&self->refs_);
    if (left == 0) delete self;
    return left;
}

// ---------------------------------------------------------------------------------------------------------
// IAudioProcessingObject

STDMETHODIMP EigentoneApo::Reset() {
    if (locked_) engine_.reset();
    return S_OK;
}

STDMETHODIMP EigentoneApo::GetLatency(HNSTIME* latency) {
    if (!latency) return E_POINTER;
    // In 100 ns units. The engine works in blocks, which delays the sound by a fixed few milliseconds.
    *latency = locked_ && sampleRate_ > 0
                   ? static_cast<HNSTIME>(static_cast<double>(engine_.latencyFrames()) * 1e7 / sampleRate_)
                   : 0;
    return S_OK;
}

STDMETHODIMP EigentoneApo::GetRegistrationProperties(APO_REG_PROPERTIES** props) {
    if (!props) return E_POINTER;
    auto* p = static_cast<APO_REG_PROPERTIES*>(CoTaskMemAlloc(sizeof(APO_REG_PROPERTIES)));
    if (!p) return E_OUTOFMEMORY;
    std::memset(p, 0, sizeof(*p));
    p->clsid = kApoClsid;
    p->Flags = APO_FLAG_DEFAULT;
    wcscpy_s(p->szFriendlyName, L"Eigentone");
    wcscpy_s(p->szCopyrightInfo, L"Copyright (C) 2026 Dragjon. GPL-3.0-or-later.");
    p->u32MajorVersion = 0;
    p->u32MinorVersion = 1;
    p->u32MinInputConnections = 1;
    p->u32MaxInputConnections = 1;
    p->u32MinOutputConnections = 1;
    p->u32MaxOutputConnections = 1;
    p->u32MaxInstances = 0xffffffff;
    p->u32NumAPOInterfaces = 1;
    p->iidAPOInterfaceList[0] = __uuidof(IAudioProcessingObject);
    *props = p;
    return S_OK;
}

STDMETHODIMP EigentoneApo::Initialize(UINT32 cbDataSize, BYTE* pbyData) {
    if (initialized_) return APOERR_ALREADY_INITIALIZED;
    // System effects are handed the output's properties, which say which output this is.
    if (pbyData && cbDataSize >= sizeof(APOInitSystemEffects)) {
        auto* init = reinterpret_cast<APOInitSystemEffects*>(pbyData);
        if (init->pAPOEndpointProperties) {
            PROPVARIANT v;
            PropVariantInit(&v);
            if (SUCCEEDED(init->pAPOEndpointProperties->GetValue(kEndpointGuid, &v)) && v.vt == VT_LPWSTR && v.pwszVal) {
                wcsncpy_s(endpoint_, v.pwszVal, _TRUNCATE);
            }
            PropVariantClear(&v);
        }
    }
    initialized_ = true;
    return S_OK;
}

HRESULT EigentoneApo::checkFormat(IAudioMediaType* opposite, IAudioMediaType* requested, IAudioMediaType** supported) {
    if (!requested || !supported) return E_POINTER;
    *supported = nullptr;
    UNCOMPRESSEDAUDIOFORMAT want{};
    if (FAILED(requested->GetUncompressedAudioFormat(&want))) return APOERR_FORMAT_NOT_SUPPORTED;
    if (opposite) {
        UNCOMPRESSEDAUDIOFORMAT other{};
        if (FAILED(opposite->GetUncompressedAudioFormat(&other))) return APOERR_FORMAT_NOT_SUPPORTED;
        const bool same = other.dwSamplesPerFrame == want.dwSamplesPerFrame &&
                          other.fFramesPerSecond == want.fFramesPerSecond && isFloat32(other) == isFloat32(want);
        if (!same) {
            // The input and output must match. Offer the other side's format if this plug-in can process it.
            if (!isFloat32(other)) return APOERR_FORMAT_NOT_SUPPORTED;
            opposite->AddRef();
            *supported = opposite;
            return S_FALSE;
        }
    }
    if (!isFloat32(want)) return APOERR_FORMAT_NOT_SUPPORTED;
    requested->AddRef();
    *supported = requested;
    return S_OK;
}

STDMETHODIMP EigentoneApo::IsInputFormatSupported(IAudioMediaType* opposite, IAudioMediaType* requested,
                                                  IAudioMediaType** supported) {
    return checkFormat(opposite, requested, supported);
}

STDMETHODIMP EigentoneApo::IsOutputFormatSupported(IAudioMediaType* opposite, IAudioMediaType* requested,
                                                   IAudioMediaType** supported) {
    return checkFormat(opposite, requested, supported);
}

STDMETHODIMP EigentoneApo::GetInputChannelCount(UINT32* count) {
    if (!count) return E_POINTER;
    if (!locked_) return APOERR_NOT_INITIALIZED;
    *count = channels_;
    return S_OK;
}

// ---------------------------------------------------------------------------------------------------------
// IAudioProcessingObjectConfiguration

STDMETHODIMP EigentoneApo::LockForProcess(UINT32 numInputs, APO_CONNECTION_DESCRIPTOR** inputs, UINT32 numOutputs,
                                          APO_CONNECTION_DESCRIPTOR** outputs) {
    if (!initialized_) return APOERR_NOT_INITIALIZED;
    if (locked_) return APOERR_APO_LOCKED;
    if (numInputs != 1 || numOutputs != 1 || !inputs || !outputs || !inputs[0] || !outputs[0]) {
        return APOERR_NUM_CONNECTIONS_INVALID;
    }
    UNCOMPRESSEDAUDIOFORMAT in{}, out{};
    if (!inputs[0]->pFormat || FAILED(inputs[0]->pFormat->GetUncompressedAudioFormat(&in))) return APOERR_INVALID_CONNECTION_FORMAT;
    if (!outputs[0]->pFormat || FAILED(outputs[0]->pFormat->GetUncompressedAudioFormat(&out))) return APOERR_INVALID_CONNECTION_FORMAT;
    if (!isFloat32(in) || !isFloat32(out) || in.dwSamplesPerFrame != out.dwSamplesPerFrame) {
        return APOERR_INVALID_CONNECTION_FORMAT;
    }
    channels_ = in.dwSamplesPerFrame;
    sampleRate_ = in.fFramesPerSecond;
    try {
        engine_.prepare(sampleRate_, channels_);
        silence_.assign(static_cast<size_t>(inputs[0]->u32MaxFrameCount) * channels_, 0.0f);
    } catch (...) {
        return E_OUTOFMEMORY;
    }
    seenSeq_ = -1;
    pollSettings();  // start with the current settings, not neutral ones
    claimSlot();
    locked_ = true;
    startWorker();
    return S_OK;
}

STDMETHODIMP EigentoneApo::UnlockForProcess() {
    if (!locked_) return APOERR_ALREADY_UNLOCKED;
    stopWorker();
    releaseSlot();
    locked_ = false;
    return S_OK;
}

// ---------------------------------------------------------------------------------------------------------
// The settings worker

void EigentoneApo::pollSettings() {
    const SharedBlock* block = link_.block();
    if (!block || block->magic != kMagic || block->seq == seenSeq_) return;
    dsp::Params params;
    const LONG64 seq = readParams(block, params);
    if (seq < 0) return;               // mid-write; the next poll gets it
    if (!engine_.update(params)) return;  // the audio thread hasn't caught up yet; the next poll retries
    seenSeq_ = seq;
    if (slot_) slot_->appliedSeq = seq;
}

DWORD WINAPI EigentoneApo::workerMain(void* raw) {
    auto* self = static_cast<EigentoneApo*>(raw);
    while (WaitForSingleObject(self->stop_, kPollMs) == WAIT_TIMEOUT) {
        try {
            self->pollSettings();
        } catch (...) {
            // Designing a filter can only fail by running out of memory. The sound carries on as it was.
        }
    }
    return 0;
}

void EigentoneApo::startWorker() {
    stop_ = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (!stop_) return;
    worker_ = CreateThread(nullptr, 0, workerMain, this, 0, nullptr);
    if (worker_) SetThreadPriority(worker_, THREAD_PRIORITY_BELOW_NORMAL);
}

void EigentoneApo::stopWorker() {
    if (worker_) {
        SetEvent(stop_);
        WaitForSingleObject(worker_, INFINITE);
        CloseHandle(worker_);
        worker_ = nullptr;
    }
    if (stop_) {
        CloseHandle(stop_);
        stop_ = nullptr;
    }
}

void EigentoneApo::claimSlot() {
    SharedBlock* block = link_.block();
    if (!block || slot_) return;
    const LONG me = static_cast<LONG>(GetCurrentProcessId());
    for (EndpointSlot& s : block->slots) {
        if (InterlockedCompareExchange(&s.owner, me, 0) == 0) {
            s.sampleRate = static_cast<uint32_t>(sampleRate_);
            s.channels = channels_;
            s.heartbeat = 0;
            s.frames = 0;
            s.latencyFrames = static_cast<uint32_t>(engine_.latencyFrames());
            s.appliedSeq = seenSeq_;
            wcsncpy_s(s.endpoint, endpoint_, _TRUNCATE);
            s.active = 1;
            slot_ = &s;
            return;
        }
    }
}

void EigentoneApo::releaseSlot() {
    if (!slot_) return;
    slot_->active = 0;
    slot_->endpoint[0] = 0;
    InterlockedExchange(&slot_->owner, 0);
    slot_ = nullptr;
}

// ---------------------------------------------------------------------------------------------------------
// IAudioProcessingObjectRT: runs on the audio thread. No allocation, no locks, no waiting.

STDMETHODIMP_(void) EigentoneApo::APOProcess(UINT32 numInputs, APO_CONNECTION_PROPERTY** inputs, UINT32 numOutputs,
                                             APO_CONNECTION_PROPERTY** outputs) {
    if (numInputs < 1 || numOutputs < 1) return;
    APO_CONNECTION_PROPERTY* in = inputs[0];
    APO_CONNECTION_PROPERTY* out = outputs[0];
    const UINT32 frames = in->u32ValidFrameCount;
    const UINT32 samples = frames * channels_;
    const auto* src = reinterpret_cast<const float*>(in->pBuffer);
    auto* dst = reinterpret_cast<float*>(out->pBuffer);

    out->u32ValidFrameCount = frames;
    if (samples > silence_.size()) {
        // More than the engine promised at lock time; pass it through rather than overrun anything.
        if (dst != src) std::memcpy(dst, src, samples * sizeof(float));
        out->u32BufferFlags = in->u32BufferFlags;
        return;
    }
    // A silent buffer's contents aren't promised to be zero, and it belongs to the stage before, so
    // silence comes from a buffer of our own. It still goes through the engine, so delayed sound and
    // reverb tails play out.
    float peakIn = 0;
    if (in->u32BufferFlags == BUFFER_SILENT) {
        src = silence_.data();
    } else {
        for (UINT32 i = 0; i < samples; ++i) peakIn = std::fmax(peakIn, std::fabs(src[i]));
    }
    engine_.process(src, dst, frames);
    float peakOut = 0;
    for (UINT32 i = 0; i < samples; ++i) peakOut = std::fmax(peakOut, std::fabs(dst[i]));
    // The engine keeps playing its delayed sound and reverb tail, so the output is never "silent" here.
    out->u32BufferFlags = BUFFER_VALID;

    if (slot_) {
        // Keep the newest sound for the spectrum: plain stores into a ring, then the position.
        LONG pos = slot_->scopePos;
        const UINT32 pair = channels_ >= 2 ? 1 : 0;
        for (UINT32 f = 0; f < frames; ++f, ++pos) {
            const float* frame = dst + static_cast<size_t>(f) * channels_;
            slot_->scope[static_cast<uint32_t>(pos) % kScopeFrames] = 0.5f * (frame[0] + frame[pair]);
        }
        MemoryBarrier();
        slot_->scopePos = pos;
        slot_->peakIn = peakIn;
        slot_->peakOut = peakOut;
        slot_->frames = slot_->frames + frames;
        slot_->heartbeat = slot_->heartbeat + 1;
    }
}

}  // namespace eigentone
