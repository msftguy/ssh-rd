#ifndef _ITMD
#define _ITMD
#include "mux_api.h"

#define ADNCI_MSG_CONNECTED     1
#define ADNCI_MSG_DISCONNECTED  2
#define ADNCI_MSG_UNKNOWN       3

typedef void* restore_dev_t;
typedef void* AMRecoveryModeDevice;
typedef void* afc_conn_t;
typedef void* am_device_t;
typedef int muxconn_t;

struct am_device_notification_callback_info
{
        am_device_t dev;	/* 0    device */
        unsigned int msg;   /* 4    one of ADNCI_MSG_* */
};

#ifdef __cplusplus
extern "C"  {
#endif

#ifdef WIN32	
    
#undef kCFAllocatorDefault

MUX_IMPORTED CFAllocatorRef kCFAllocatorDefault;

#undef kCFTypeDictionaryKeyCallBacks
#undef kCFTypeDictionaryValueCallBacks

MUX_IMPORTED const CFDictionaryKeyCallBacks kCFTypeDictionaryKeyCallBacks;

MUX_IMPORTED const CFDictionaryValueCallBacks kCFTypeDictionaryValueCallBacks;

#undef kCFBooleanTrue
#undef kCFBooleanFalse

MUX_IMPORTED const CFBooleanRef kCFBooleanTrue;
MUX_IMPORTED const CFBooleanRef kCFBooleanFalse;

EXTERN_API_C(void)CFRunLoopRun();

#endif // WIN32	

typedef void (*am_device_notification_callback_t)(struct am_device_notification_callback_info *, void* ctx);

typedef void (*am_restore_device_notification_callback)(AMRecoveryModeDevice device, void* ctx);

typedef void* am_device_callbacks_t;
        
MUX_IMPORTED int AMDeviceNotificationSubscribe(am_device_notification_callback_t notificationCallback, int , int, void* ctx, am_device_callbacks_t *callbacks);
MUX_IMPORTED int AMDeviceConnect(am_device_t am_device);
MUX_IMPORTED int AMDeviceIsPaired(am_device_t am_device);
MUX_IMPORTED int AMDeviceValidatePairing(am_device_t am_device);
MUX_IMPORTED int AMDeviceStartSession(am_device_t am_device);
MUX_IMPORTED int AMDeviceStartService(am_device_t am_device, CFStringRef service_name, int *handle, unsigned int *unknown );
MUX_IMPORTED int AFCConnectionOpen(int handle, unsigned int io_timeout, afc_conn_t* afc_connection);
MUX_IMPORTED int AMDeviceDisconnect(am_device_t am_device);
MUX_IMPORTED int AMDeviceStopSession(am_device_t am_device);

MUX_IMPORTED int AMRestoreRegisterForDeviceNotifications(
    am_restore_device_notification_callback dfu_connect_callback,
    am_restore_device_notification_callback recovery_connect_callback,
    am_restore_device_notification_callback dfu_disconnect_callback,
    am_restore_device_notification_callback recovery_disconnect_callback,
    unsigned int unknown0,
    void *ctx);

MUX_IMPORTED int AMDFUModeDeviceGetProductID(AMRecoveryModeDevice device);
MUX_IMPORTED int AMDFUModeDeviceGetProductType(AMRecoveryModeDevice device);

MUX_IMPORTED int AMRecoveryModeDeviceGetProductID(AMRecoveryModeDevice device);
MUX_IMPORTED int AMRecoveryModeDeviceGetProductType(AMRecoveryModeDevice device);
    
MUX_IMPORTED int AMRecoveryModeDeviceReboot(AMRecoveryModeDevice device);
MUX_IMPORTED int AMRecoveryModeDeviceSetAutoBoot(AMRecoveryModeDevice device, bool autoboot);

MUX_IMPORTED CFStringRef AMDeviceCopyDeviceIdentifier(am_device_t device);

MUX_IMPORTED int AMDeviceGetInterfaceType(am_device_t device);
MUX_IMPORTED muxconn_t AMDeviceGetConnectionID(am_device_t device);
MUX_IMPORTED int AMRestoreModeDeviceGetDeviceID(restore_dev_t restore_device);
MUX_IMPORTED int AMRestoreModeDeviceReboot(restore_dev_t restore_device);
MUX_IMPORTED int USBMuxConnectByPort(muxconn_t muxConn, short netPort, int* sockHandle);

MUX_IMPORTED restore_dev_t AMRestoreModeDeviceCreate(int arg1_is_0, int connId, int arg3_is_0);
typedef void (*PFN_RESTORE_PROGRESS_CALLBACK)(AMRecoveryModeDevice device, int operationId, int progress, void* callbackCtx);
MUX_IMPORTED int AMRestorePerformDFURestore(AMRecoveryModeDevice device, CFDictionaryRef restoreOptions, PFN_RESTORE_PROGRESS_CALLBACK progressCallback, void* callbackCtx);

MUX_IMPORTED int AMRestorePerformRecoveryModeRestore(AMRecoveryModeDevice device, CFDictionaryRef restoreOptions, PFN_RESTORE_PROGRESS_CALLBACK progressCallback, void* callbackCtx);

MUX_IMPORTED int AMRestoreEnableFileLogging(const char* logPath);

#ifdef __cplusplus
}
#endif

// Probably should separate the following into own header..

typedef struct {
    CFDictionaryRef restoreOptions;
    int dfuAttempts;
    int recoveryAttempts;
    pfn_javaMobileDeviceCallbackProc_t javaCallback;
    void* javaContext;
} ITMD_CONTEXT, *PITMD_CONTEXT;

#ifdef __cplusplus 
extern "C" {
#endif
void invokeCallback(PITMD_CONTEXT c, event_type eventType, AMRecoveryModeDevice device);
#ifdef __cplusplus     
}
#endif

#endif //_ITMD