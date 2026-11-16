# The sound engine on its own, free of Windows audio types, so it can be tested anywhere.
add_library(eigentone-dsp STATIC dsp/src/fft.cpp dsp/src/design.cpp dsp/src/engine.cpp)
target_include_directories(eigentone-dsp PUBLIC dsp/include)

# Outputs, hooks, registration and Windows Audio, shared by the setup tool and the service.
add_library(eigentone-system STATIC system/system.cpp system/outputs.cpp)
target_include_directories(eigentone-system PUBLIC include)
target_link_libraries(eigentone-system PUBLIC eigentone-dsp ole32 advapi32 shell32)

# The audio plug-in, loaded by Windows' audio engine for each hooked output.
add_library(EigentoneApo SHARED
    apo/EigentoneApo.cpp
    apo/dllmain.cpp
    apo/EigentoneApo.def
)
target_include_directories(EigentoneApo PRIVATE include)
target_link_libraries(EigentoneApo PRIVATE eigentone-dsp ole32 advapi32 propsys)

# Installs the plug-in, hooks it into outputs and restores them.
add_executable(eigentone-setup setup/main.cpp)
target_include_directories(eigentone-setup PRIVATE include)
target_link_libraries(eigentone-setup PRIVATE eigentone-system)
# Keep the tool next to the plug-in, which it copies from there.
set_target_properties(EigentoneApo eigentone-setup PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
    RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}/bin
    RUNTIME_OUTPUT_DIRECTORY_DEBUG ${CMAKE_BINARY_DIR}/bin
)

# Runs the plug-in outside Windows' audio engine, before it is ever installed.
add_executable(apo-host tests/apo_host.cpp)
target_include_directories(apo-host PRIVATE include)
target_link_libraries(apo-host PRIVATE eigentone-dsp ole32)
set_target_properties(apo-host PROPERTIES RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}/bin RUNTIME_OUTPUT_DIRECTORY_DEBUG ${CMAKE_BINARY_DIR}/bin)
add_test(NAME apo-host COMMAND apo-host $<TARGET_FILE:EigentoneApo>)

add_executable(dsp-tests tests/dsp_tests.cpp)
target_link_libraries(dsp-tests PRIVATE eigentone-dsp)
set_target_properties(dsp-tests PROPERTIES RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}/bin RUNTIME_OUTPUT_DIRECTORY_DEBUG ${CMAKE_BINARY_DIR}/bin)
add_test(NAME dsp-tests COMMAND dsp-tests)

# JSON for talking to the app and keeping settings. MIT licence.
include(FetchContent)
FetchContent_Declare(json URL https://github.com/nlohmann/json/releases/download/v3.11.3/json.tar.xz
    URL_HASH SHA256=d6c65aca6b1ed68e7a182f4757257b107ae403032760ed6ef121c9d55e81757d)
FetchContent_MakeAvailable(json)

# The background service.
add_library(eigentone-service-core STATIC service/params_json.cpp)
target_link_libraries(eigentone-service-core PUBLIC eigentone-dsp nlohmann_json::nlohmann_json)
add_executable(eigentone-service service/main.cpp)
target_link_libraries(eigentone-service PRIVATE eigentone-service-core eigentone-system)
set_target_properties(eigentone-service PROPERTIES RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}/bin RUNTIME_OUTPUT_DIRECTORY_DEBUG ${CMAKE_BINARY_DIR}/bin)

# The agent, which the app runs in the user's session to see which apps are playing.
add_executable(eigentone-agent agent/main.cpp)
target_link_libraries(eigentone-agent PRIVATE nlohmann_json::nlohmann_json ole32 version)
set_target_properties(eigentone-agent PROPERTIES RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}/bin RUNTIME_OUTPUT_DIRECTORY_DEBUG ${CMAKE_BINARY_DIR}/bin)

add_executable(service-tests tests/service_tests.cpp)
target_link_libraries(service-tests PRIVATE eigentone-service-core eigentone-system)
set_target_properties(service-tests PROPERTIES RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}/bin RUNTIME_OUTPUT_DIRECTORY_DEBUG ${CMAKE_BINARY_DIR}/bin)
add_test(NAME service-tests COMMAND service-tests)
