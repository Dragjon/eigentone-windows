// Eigentone for Windows: outputs, hooks, registration and Windows Audio. Shared by the setup tool and
// the service.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#pragma once

#include <windows.h>

#include <optional>
#include <string>
#include <vector>

namespace eigentone::sys {

// ---------------------------------------------------------------------------------------------------------
// Outputs

/// What an output is, for its tab's name and drawing and for what the app suggests. The Windows
/// counterparts of the Android app's Phone, Headphones, Earbuds, Speaker and Car.
enum class Kind { BuiltIn, Speakers, Headphones, Earbuds, Display, Car };

const wchar_t* kindName(Kind kind);

/// One physical connector behind an output, as the driver describes it.
struct Jack {
    int connection = 0;  // EPcxConnectionType: 1 = 3.5 mm, 2 = 6.3 mm, 5 = optical, 7 = HDMI ...
    int location = 0;    // EPcxGenLocation: 0 = on the main box, 1 = internal, 2 = separate, 3 = other
    int port = 0;        // EPxcPortConnection: 0 = a jack, 1 = built in, 2 = built-in jack, 3 = a separate box
    bool connected = false;
};

struct Output {
    std::wstring id;        // Windows' full ID, {0.0.0.00000000}.{guid}
    std::wstring guid;      // lower case; also the output's registry key name
    std::wstring name;      // "Speaker/HP (Realtek(R) Audio)"
    std::wstring device;    // the output's own part of the name: "Speaker/HP"
    std::wstring adapter;   // the hardware: "Realtek(R) Audio"
    std::wstring bus;       // how it's connected: HDAUDIO, USB, BTHENUM, BTHHFENUM, ...
    int formFactor = 0;     // EndpointFormFactor: 1 = speakers, 3 = headphones, 5 = headset, 9 = display ...
    DWORD state = 0;
    bool isDefault = false;
    bool hooked = false;
    std::vector<Jack> jacks;
    Kind kind = Kind::Speakers;
};

/// Works out what an output is from what Windows says about it. Pure, so it can be tested on its own.
Kind classify(const Output& output);

/// Active and unplugged render outputs, with what they are.
std::vector<Output> listOutputs();

std::wstring guidOf(const std::wstring& id);

// ---------------------------------------------------------------------------------------------------------
// Hooks: the plug-in placed first in an output's endpoint effects, with the original saved

bool isHooked(const std::wstring& endpoint);
int hook(const std::wstring& endpoint);
int unhook(const std::wstring& endpoint);
std::vector<std::wstring> hookedOutputs();

// ---------------------------------------------------------------------------------------------------------
// Registration and Windows Audio

std::wstring installDir();
int registerApo(const std::wstring& dll);
void unregisterApo();
/// Stops Windows Audio, and first any running services that need it, which startAudio starts again.
bool stopAudio();
bool startAudio();

/// Eigentone's own background service: set to start with Windows, restarted if it ever stops unexpectedly.
int installEigentoneService(const std::wstring& exe);
void stopEigentoneService();
void removeEigentoneService();

// ---------------------------------------------------------------------------------------------------------
// Small things

int fail(const wchar_t* what, LONG code = 0);
std::wstring lower(std::wstring s);
bool isAdmin();
bool enablePrivilege(const wchar_t* name);

}  // namespace eigentone::sys
