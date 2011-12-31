#pragma once

#ifdef MUX_BUILD

#ifdef WIN32
#define MUX_IMPORTED extern __declspec(dllimport)
#define MUX_API extern __declspec(dllexport)
#else 
#define MUX_IMPORTED extern
#define MUX_API __attribute__ ((visibility ("default")))
#endif

#else

#ifdef WIN32
#define MUX_API extern __declspec(dllimport)
#else 
#define MUX_API extern
#endif

#endif

MUX_API void itmd_restoreBundle(const char* bundlePath);