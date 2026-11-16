// Eigentone for Windows: the plug-in DLL's COM entry points.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
//
// Registration is done by eigentone-setup.exe rather than DllRegisterServer, so that installing,
// hooking into outputs and putting everything back on uninstall live in one place.
#include <new>

#include "EigentoneApo.h"

using namespace eigentone;

namespace {

class Factory final : public IClassFactory {
public:
    STDMETHODIMP QueryInterface(REFIID riid, void** ppv) override {
        if (!ppv) return E_POINTER;
        if (riid == __uuidof(IUnknown) || riid == __uuidof(IClassFactory)) {
            *ppv = static_cast<IClassFactory*>(this);
            AddRef();
            return S_OK;
        }
        *ppv = nullptr;
        return E_NOINTERFACE;
    }
    // The factory is a static object that lives as long as the DLL.
    STDMETHODIMP_(ULONG) AddRef() override { return 2; }
    STDMETHODIMP_(ULONG) Release() override { return 1; }

    STDMETHODIMP CreateInstance(IUnknown* outer, REFIID riid, void** ppv) override {
        if (!ppv) return E_POINTER;
        *ppv = nullptr;
        // An aggregating owner must ask for the inner IUnknown, per COM's rules.
        if (outer && riid != __uuidof(IUnknown)) return CLASS_E_NOAGGREGATION;
        auto* apo = new (std::nothrow) EigentoneApo(outer);
        if (!apo) return E_OUTOFMEMORY;
        IUnknown* inner = apo->inner();
        const HRESULT hr = inner->QueryInterface(riid, ppv);
        inner->Release();  // drop the reference the constructor started with
        return hr;
    }

    STDMETHODIMP LockServer(BOOL lock) override {
        if (lock) InterlockedIncrement(&gServerLocks); else InterlockedDecrement(&gServerLocks);
        return S_OK;
    }
};

Factory gFactory;

}  // namespace

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) DisableThreadLibraryCalls(instance);
    return TRUE;
}

STDAPI DllGetClassObject(REFCLSID clsid, REFIID riid, void** ppv) {
    if (!ppv) return E_POINTER;
    *ppv = nullptr;
    if (clsid != kApoClsid) return CLASS_E_CLASSNOTAVAILABLE;
    return gFactory.QueryInterface(riid, ppv);
}

STDAPI DllCanUnloadNow() { return gServerLocks == 0 ? S_OK : S_FALSE; }
