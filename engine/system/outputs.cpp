// Eigentone for Windows: listing outputs and working out what each one is.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#include "eigentone/system.h"

#include <mmdeviceapi.h>
#include <devicetopology.h>
#include <mmreg.h>
#include <ks.h>
#include <ksmedia.h>
#include <functiondiscoverykeys_devpkey.h>

#include <cwctype>
#include <regex>

namespace eigentone::sys {

namespace {

// {1da5d803-d492-4edd-8c23-e0c0ffee7f0e},0: speakers, headphones, headset, display ...
constexpr PROPERTYKEY kFormFactor = {{0x1da5d803, 0xd492, 0x4edd, {0x8c, 0x23, 0xe0, 0xc0, 0xff, 0xee, 0x7f, 0x0e}}, 0};

enum FormFactor { kSpeakersForm = 1, kLineLevel = 2, kHeadphonesForm = 3, kHeadsetForm = 5, kSpdif = 8, kDisplay = 9 };
enum PortConnection { kJackPort = 0, kIntegratedDevice = 1, kIntegratedJack = 2 };
enum ConnectionType { kMiniJack = 1, kQuarterInch = 2 };
enum GenLocation { kInternal = 1 };

// The Android app's word lists (OutputKind.guess), so a device is called the same thing on both.
const std::wregex kCarWords(
    LR"(\b(car|carkit|car kit|auto|android auto|carplay|sync|uconnect|mbux|idrive|mylink|bmw|audi|toyota|honda|ford|vw|volkswagen|kia|hyundai|mazda|nissan|tesla|subaru|lexus|mercedes|peugeot|renault|skoda|volvo|seat|opel|chevrolet|jeep|porsche)\b)",
    std::regex::icase);
const std::wregex kSpeakerWords(
    LR"((speaker|soundbar|sound bar|soundlink|soundcore motion|partybox|\bflip\b|\bcharge\b|\bboom\b|megaboom|wonderboom|xtreme|\bpulse\b|\becho\b|\bnest\b|sonos|homepod|stanmore|emberton|acton|marshall woburn))",
    std::regex::icase);
const std::wregex kEarbudWords(
    LR"((buds|pods|earbud|\biem\b|\btws\b|\bwf-|nothing ear|jabra elite|soundcore liberty|momentum true|\bfreebuds|\bear \())",
    std::regex::icase);

std::wstring readString(IPropertyStore* store, const PROPERTYKEY& key) {
    PROPVARIANT v;
    PropVariantInit(&v);
    std::wstring out;
    if (SUCCEEDED(store->GetValue(key, &v)) && v.vt == VT_LPWSTR && v.pwszVal) out = v.pwszVal;
    PropVariantClear(&v);
    return out;
}

int readUint(IPropertyStore* store, const PROPERTYKEY& key) {
    PROPVARIANT v;
    PropVariantInit(&v);
    int out = 0;
    if (SUCCEEDED(store->GetValue(key, &v)) && v.vt == VT_UI4) out = static_cast<int>(v.ulVal);
    PropVariantClear(&v);
    return out;
}

/// Follows the output to the hardware it's connected to: which bus that hardware sits on, and the jacks
/// its driver describes.
void readTopology(IMMDevice* device, Output& out) {
    IDeviceTopology* topology = nullptr;
    if (FAILED(device->Activate(__uuidof(IDeviceTopology), CLSCTX_INPROC_SERVER, nullptr, reinterpret_cast<void**>(&topology)))) return;
    IConnector* connector = nullptr;
    if (SUCCEEDED(topology->GetConnector(0, &connector))) {
        // The hardware's ID reads like {2}.\\?\hdaudio#func_01&ven_10ec..., which names its bus.
        LPWSTR id = nullptr;
        if (SUCCEEDED(connector->GetDeviceIdConnectedTo(&id)) && id) {
            std::wstring s(id);
            const auto start = s.find(L"\\\\?\\");
            if (start != std::wstring::npos) {
                const auto from = start + 4;
                const auto end = s.find(L'#', from);
                std::wstring bus = s.substr(from, end == std::wstring::npos ? std::wstring::npos : end - from);
                for (auto& c : bus) c = static_cast<wchar_t>(std::towupper(c));
                out.bus = bus;
            }
            CoTaskMemFree(id);
        }
        IConnector* other = nullptr;
        if (SUCCEEDED(connector->GetConnectedTo(&other))) {
            IPart* part = nullptr;
            if (SUCCEEDED(other->QueryInterface(IID_PPV_ARGS(&part)))) {
                IKsJackDescription* jacks = nullptr;
                if (SUCCEEDED(part->Activate(CLSCTX_INPROC_SERVER, __uuidof(IKsJackDescription), reinterpret_cast<void**>(&jacks)))) {
                    UINT count = 0;
                    jacks->GetJackCount(&count);
                    for (UINT i = 0; i < count; ++i) {
                        KSJACK_DESCRIPTION d{};
                        if (FAILED(jacks->GetJackDescription(i, &d))) continue;
                        out.jacks.push_back({static_cast<int>(d.ConnectionType), static_cast<int>(d.GenLocation),
                                             static_cast<int>(d.PortConnection), d.IsConnected != FALSE});
                    }
                    jacks->Release();
                }
                part->Release();
            }
            other->Release();
        }
        connector->Release();
    }
    topology->Release();
}

bool isBluetooth(const std::wstring& bus) { return bus.rfind(L"BTH", 0) == 0; }

}  // namespace

const wchar_t* kindName(Kind kind) {
    switch (kind) {
        case Kind::BuiltIn: return L"Built-in speakers";
        case Kind::Speakers: return L"Speakers";
        case Kind::Headphones: return L"Headphones";
        case Kind::Earbuds: return L"Earbuds";
        case Kind::Display: return L"TV or monitor";
        case Kind::Car: return L"Car";
    }
    return L"Speakers";
}

Kind classify(const Output& o) {
    const std::wstring& name = o.name;
    if (o.formFactor == kDisplay) return Kind::Display;

    // A headphone jack with something plugged in. Laptops often have one output for both the speakers
    // and the jack, which stays the same output when headphones go in; only the jack says so.
    for (const Jack& j : o.jacks) {
        const bool headphoneJack = (j.port == kJackPort || j.port == kIntegratedJack) &&
                                   (j.connection == kMiniJack || j.connection == kQuarterInch);
        if (headphoneJack && j.connected && o.formFactor != kLineLevel && o.formFactor != kSpdif) {
            // A desktop's green line-out also is a 3.5 mm jack; it's speakers unless Windows says otherwise.
            const bool combined = o.jacks.size() > 1;
            if (combined || o.formFactor == kHeadphonesForm || o.formFactor == kHeadsetForm) {
                return std::regex_search(name, kEarbudWords) ? Kind::Earbuds : Kind::Headphones;
            }
        }
    }

    if (isBluetooth(o.bus)) {
        if (std::regex_search(name, kCarWords)) return Kind::Car;
        if (std::regex_search(name, kSpeakerWords)) return Kind::Speakers;
        if (std::regex_search(name, kEarbudWords)) return Kind::Earbuds;
        return o.formFactor == kSpeakersForm ? Kind::Speakers : Kind::Headphones;
    }
    if (o.formFactor == kHeadphonesForm || o.formFactor == kHeadsetForm) {
        return std::regex_search(name, kEarbudWords) ? Kind::Earbuds : Kind::Headphones;
    }
    if (std::regex_search(name, kSpeakerWords) && o.bus != L"HDAUDIO" && o.bus != L"INTELAUDIO") return Kind::Speakers;

    // Built in: the speakers are part of the machine, by the driver's own description.
    bool builtIn = false;
    for (const Jack& j : o.jacks) builtIn |= j.port == kIntegratedDevice || j.location == kInternal;
    if (builtIn && o.formFactor == kSpeakersForm) return Kind::BuiltIn;
    return Kind::Speakers;
}

std::wstring guidOf(const std::wstring& id) {
    const auto dot = id.rfind(L'.');
    return lower(dot == std::wstring::npos ? id : id.substr(dot + 1));
}

std::vector<Output> listOutputs() {
    std::vector<Output> out;
    IMMDeviceEnumerator* en = nullptr;
    if (FAILED(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_ALL, IID_PPV_ARGS(&en)))) return out;
    std::wstring defaultGuid;
    IMMDevice* def = nullptr;
    if (SUCCEEDED(en->GetDefaultAudioEndpoint(eRender, eConsole, &def))) {
        LPWSTR id = nullptr;
        if (SUCCEEDED(def->GetId(&id))) {
            defaultGuid = guidOf(id);
            CoTaskMemFree(id);
        }
        def->Release();
    }
    IMMDeviceCollection* all = nullptr;
    if (SUCCEEDED(en->EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE | DEVICE_STATE_UNPLUGGED, &all))) {
        UINT n = 0;
        all->GetCount(&n);
        for (UINT i = 0; i < n; ++i) {
            IMMDevice* d = nullptr;
            if (FAILED(all->Item(i, &d))) continue;
            Output o;
            LPWSTR id = nullptr;
            if (SUCCEEDED(d->GetId(&id))) {
                o.id = id;
                o.guid = guidOf(id);
                CoTaskMemFree(id);
            }
            d->GetState(&o.state);
            IPropertyStore* ps = nullptr;
            if (SUCCEEDED(d->OpenPropertyStore(STGM_READ, &ps))) {
                o.name = readString(ps, PKEY_Device_FriendlyName);
                o.device = readString(ps, PKEY_Device_DeviceDesc);
                o.adapter = readString(ps, PKEY_DeviceInterface_FriendlyName);
                o.formFactor = readUint(ps, kFormFactor);
                ps->Release();
            }
            if (o.state == DEVICE_STATE_ACTIVE) readTopology(d, o);
            o.isDefault = o.guid == defaultGuid;
            o.hooked = isHooked(o.guid);
            o.kind = classify(o);
            out.push_back(std::move(o));
            d->Release();
        }
        all->Release();
    }
    en->Release();
    return out;
}

}  // namespace eigentone::sys
