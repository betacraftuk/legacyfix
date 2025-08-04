cmake_minimum_required( VERSION 3.15 )

project( LegacyFixNative VERSION 1.0 )

set( SOURCES
    src/native/legacyfixnative.c
    src/native/legacyfixnative.h
    src/native/rawinputpatch.c
)

add_library( LegacyFixNative SHARED ${SOURCES} )

install( TARGETS LegacyFixNative DESTINATION build/libs )