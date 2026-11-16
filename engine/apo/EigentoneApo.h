// Eigentone for Windows: the audio plug-in (an APO) that Windows' audio engine loads for an output.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#pragma once

#include <windows.h>
#include <audioenginebaseapo.h>
#include <audiomediatype.h>

#include "eigentone/shared.h"

namespace eigentone {

/// Opens the shared settings block, creating it if this is the first plug-in to start.
class SharedLink {
public:
    SharedLink();
    ~SharedLink();
    SharedLink(const SharedLink&) = delete;
    SharedLink& operator=(const SharedLink&) = delete;

    SharedBlock* block() const { return block_; }

private:
    HANDLE mapping_ = nullptr;
    SharedBlock* block_ = nullptr;
};

/**
 * Windows hands this object each buffer of mixed sound on its way to the output. It must answer quickly and
 * never block, so all settings arrive through SharedLink as plain numbers.
 *
 * The object can be aggregated: the audio engine may wrap it in an outer object, so IUnknown on the public
 * interfaces goes to the outer one and Inner holds the object's own reference count.
 */
class EigentoneApo final : public IAudioProcessingObject,
                           public IAudioProcessingObjectRT,
                           public IAudioProcessingObjectConfiguration,
                           public IAudioSystemEffects {
public:
    explicit EigentoneApo(IUnknown* outer);

    IUnknown* inner() { return &inner_; }

    // IUnknown, passed to the outer object
    STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override { return outer_->QueryInterface(riid, ppv); }
    STDMETHODIMP_(ULONG) AddRef() override { return outer_->AddRef(); }
    STDMETHODIMP_(ULONG) Release() override { return outer_->Release(); }

    // IAudioProcessingObject
    STDMETHODIMP Reset() override;
    STDMETHODIMP GetLatency(HNSTIME* latency) override;
    STDMETHODIMP GetRegistrationProperties(APO_REG_PROPERTIES** props) override;
    STDMETHODIMP Initialize(UINT32 cbDataSize, BYTE* pbyData) override;
    STDMETHODIMP IsInputFormatSupported(IAudioMediaType* opposite, IAudioMediaType* requested,
                                        IAudioMediaType** supported) override;
    STDMETHODIMP IsOutputFormatSupported(IAudioMediaType* opposite, IAudioMediaType* requested,
                                         IAudioMediaType** supported) override;
    STDMETHODIMP GetInputChannelCount(UINT32* count) override;

    // IAudioProcessingObjectRT
    STDMETHODIMP_(void) APOProcess(UINT32 numInputs, APO_CONNECTION_PROPERTY** inputs, UINT32 numOutputs,
                                   APO_CONNECTION_PROPERTY** outputs) override;
    STDMETHODIMP_(UINT32) CalcInputFrames(UINT32 outputFrames) override { return outputFrames; }
    STDMETHODIMP_(UINT32) CalcOutputFrames(UINT32 inputFrames) override { return inputFrames; }

    // IAudioProcessingObjectConfiguration
    STDMETHODIMP LockForProcess(UINT32 numInputs, APO_CONNECTION_DESCRIPTOR** inputs, UINT32 numOutputs,
                                APO_CONNECTION_DESCRIPTOR** outputs) override;
    STDMETHODIMP UnlockForProcess() override;

private:
    struct Inner final : IUnknown {
        EigentoneApo* self;
        STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override;
        STDMETHODIMP_(ULONG) AddRef() override;
        STDMETHODIMP_(ULONG) Release() override;
    };

    ~EigentoneApo();
    HRESULT checkFormat(IAudioMediaType* opposite, IAudioMediaType* requested, IAudioMediaType** supported);
    void claimSlot();
    void releaseSlot();

    Inner inner_;
    IUnknown* outer_;
    volatile LONG refs_ = 1;

    bool initialized_ = false;
    bool locked_ = false;
    UINT32 channels_ = 0;
    float sampleRate_ = 0;
    wchar_t endpoint_[64] = {};

    SharedLink link_;
    EndpointSlot* slot_ = nullptr;
    float gain_ = 1.0f;  // the level actually applied, eased toward the requested one
};

/// Counts live objects and server locks, for DllCanUnloadNow.
extern volatile LONG gServerLocks;

}  // namespace eigentone
