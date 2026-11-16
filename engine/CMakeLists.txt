# The sound engine on its own, free of Windows audio types, so it can be tested anywhere.
add_library(eigentone-dsp STATIC dsp/src/fft.cpp dsp/src/design.cpp dsp/src/engine.cpp)
target_include_directories(eigentone-dsp PUBLIC dsp/include)

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
target_link_libraries(eigentone-setup PRIVATE eigentone-dsp ole32 advapi32 shell32)
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
