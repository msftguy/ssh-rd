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

typedef enum {
    EventDfuEnter = 0,
    EventDfuExit,
    EventRecoveryEnter = 2,
    EventRecoveryExit,
    EventRestoreEnter = 4,
    EventRestoreExit
}
event_type;

typedef void (*pfn_javaMobileDeviceCallbackProc_t)(void* ctx, int eventType, int productId, int productType);

MUX_API void itmd_restoreBundle(const char* bundlePath);

MUX_API void itmd_run(pfn_javaMobileDeviceCallbackProc_t callback, void* context);

MUX_API int itmd_start_mux_tunnel(int localPort, int remotePort);
