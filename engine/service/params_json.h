// Eigentone for Windows: the sound engine's settings as JSON, the way the app sends them.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#pragma once

#include <nlohmann/json.hpp>

#include "eigentone/dsp/params.h"

namespace eigentone::service {

/// Reads settings from the app. Anything missing stays neutral, lists longer than the engine takes are
/// cut short, and numbers out of range are clamped by the engine itself. Throws nlohmann::json::exception
/// on the wrong types.
dsp::Params paramsFromJson(const nlohmann::json& j);

nlohmann::json paramsToJson(const dsp::Params& p);

}  // namespace eigentone::service
