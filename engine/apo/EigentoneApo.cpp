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

float dbToGain(float db) {
    if (!(db > -96.0f)) return 0.0f;    // also catches NaN
    if (db > 24.0f) db = 24.0f;         // a corrupted setting must never be able to blast the output
    return std::pow(10.0f, db / 20.0f);
}

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
    mapping_ = CreateFileMappingW(INVALID_HANDLE_VALUE, &sa, PAGE_READWRITE, 0, sizeof(SharedBlock), kSectionName);
    const bool existed = GetLastError() == ERROR_ALREADY_EXISTS;
    LocalFree(sd);
    if (!mapping_) return;
    block_ = static_cast<SharedBlock*>(MapViewOfFile(mapping_, FILE_MAP_ALL_ACCESS, 0, 0, sizeof(SharedBlock)));
    if (!block_) return;
    if (!existed) {
        block_->version = kVersion;
        block_->enabled = 1;
        block_->gainDb = 0.0f;
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

STDMETHODIMP EigentoneApo::Reset() { return S_OK; }

STDMETHODIMP EigentoneApo::GetLatency(HNSTIME* latency) {
    if (!latency) return E_POINTER;
    *latency = 0;
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
    gain_ = 1.0f;
    claimSlot();
    locked_ = true;
    return S_OK;
}

STDMETHODIMP EigentoneApo::UnlockForProcess() {
    if (!locked_) return APOERR_ALREADY_UNLOCKED;
    releaseSlot();
    locked_ = false;
    return S_OK;
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
    out->u32BufferFlags = in->u32BufferFlags;
    if (in->u32BufferFlags == BUFFER_SILENT) {
        if (dst != src) std::memset(dst, 0, samples * sizeof(float));
        if (slot_) { slot_->peakIn = 0; slot_->peakOut = 0; slot_->heartbeat = slot_->heartbeat + 1; }
        return;
    }

    float target = 1.0f;
    const SharedBlock* block = link_.block();
    if (block && block->magic == kMagic && block->enabled) target = dbToGain(block->gainDb);

    // Ease from the last level to the new one across this buffer, so a change never clicks.
    const float start = gain_;
    const float step = frames ? (target - start) / static_cast<float>(frames) : 0.0f;
    float peakIn = 0, peakOut = 0;
    float g = start;
    for (UINT32 f = 0; f < frames; ++f) {
        g += step;
        for (UINT32 c = 0; c < channels_; ++c) {
            const UINT32 i = f * channels_ + c;
            const float x = src[i];
            const float y = x * g;
            dst[i] = y;
            peakIn = std::fmax(peakIn, std::fabs(x));
            peakOut = std::fmax(peakOut, std::fabs(y));
        }
    }
    gain_ = target;

    if (slot_) {
        slot_->peakIn = peakIn;
        slot_->peakOut = peakOut;
        slot_->frames = slot_->frames + frames;
        slot_->heartbeat = slot_->heartbeat + 1;
    }
}

}  // namespace eigentone
