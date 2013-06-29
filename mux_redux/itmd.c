#ifdef __APPLE__
#include <CoreFoundation/CoreFoundation.h>
#else
#include <CoreFoundation.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include "mux_api.h"
#include "itmd.h"
#include "mux.h"
#ifdef WIN32
#include "win32_queue.h"
#endif

static PITMD_CONTEXT s_restoreContext = NULL;
static AMRecoveryModeDevice s_device = NULL;

CFDictionaryRef RestoreOptionsDictWithBundlePath(const char* bundlePath)
{
    typedef const void* pvoid;
    int int0 = 0;
    const CFStringRef keys[] = {
        CFSTR("AutoBootDelay"),											CFSTR("BootImageType"),		CFSTR("CreateFilesystemPartitions"), 
        CFSTR("DFUFileType"),		CFSTR("FlashNOR"),		CFSTR("KernelCacheType"),
        CFSTR("NORImageType"),		CFSTR("RestoreBootArgs"),							CFSTR("RestoreBundlePath"),
        CFSTR("SystemImageType"),		CFSTR("UpdateBaseband"),
        
    };
    const pvoid values[] = {
        CFNumberCreate(kCFAllocatorDefault,	kCFNumberIntType, &int0),	CFSTR("UserOrInternal"),	kCFBooleanTrue,
        CFSTR("RELEASE"),			kCFBooleanTrue,			CFSTR("Release"),
        CFSTR("production"),		CFSTR("rd=md0 nand-enable-reformat=1 -progress"),	CFStringCreateWithCString(kCFAllocatorDefault, bundlePath, kCFStringEncodingASCII),
        CFSTR("User"),				kCFBooleanTrue
    };
    
    CFDictionaryRef dict = CFDictionaryCreate (
                                               kCFAllocatorDefault,
                                               (const void**)keys,
                                               (const void**)values,
                                               sizeof(keys)/sizeof(void*),
                                               &kCFTypeDictionaryKeyCallBacks,
                                               &kCFTypeDictionaryValueCallBacks);
    return dict;
}

void restoreProgressCallback(AMRecoveryModeDevice device, int operationId, int progress, void* callbackCtx)
{
    fprintf(stderr, "RestoreProgress: dev=%p, op=%u progress=%u ctx=%p\n", device, operationId, progress, callbackCtx);
    fflush(stderr);
}

void invokeCallback(PITMD_CONTEXT c, event_type eventType, AMRecoveryModeDevice device) 
{
    int productId = 0, productType = 0;
	if (c->javaCallback == NULL)
        return;
	switch (eventType) {
        case EventDfuEnter:
        case EventDfuExit:
            productId = AMDFUModeDeviceGetProductID(device);
            productType = AMDFUModeDeviceGetProductType(device);
            break;
        case EventRecoveryEnter:
        case EventRecoveryExit:
            productId = AMRecoveryModeDeviceGetProductID(device);
            productType = AMRecoveryModeDeviceGetProductType(device);
            break;
        case EventRestoreEnter: // Could have used restore mode-specific APIs, but they return strings anyway..
        case EventRestoreExit:
        default:
            productId = 0;
            productType = 0;
            break;
    }
#ifdef WIN32
	win32_run_on_thread(c, eventType, productId, productType);
#else
	c->javaCallback(c->javaContext, eventType, productId, productType);
#endif
}

void dfuConnect(AMRecoveryModeDevice device, void* ctx)
{
    PITMD_CONTEXT c = (PITMD_CONTEXT)ctx;
    if (s_device == NULL) {
        s_device = device;   
    }
    invokeCallback(c, EventDfuEnter, device);
    if (c->restoreOptions && c->dfuAttempts > 0) {
        --c->dfuAttempts;
        AMRestorePerformDFURestore(device, c->restoreOptions, restoreProgressCallback, ctx);
    }
}

void recoveryConnect(AMRecoveryModeDevice device, void* ctx)
{
    PITMD_CONTEXT c = (PITMD_CONTEXT)ctx;
    invokeCallback(c, EventRecoveryEnter, device);
    if (c->restoreOptions && c->recoveryAttempts > 0) {
        --c->recoveryAttempts;
        AMRestorePerformRecoveryModeRestore(device, c->restoreOptions, restoreProgressCallback, ctx);
    }
}

void dfuDisconnect(AMRecoveryModeDevice device, void* ctx)
{
    PITMD_CONTEXT c = (PITMD_CONTEXT)ctx;
    if (s_device != NULL /*&& s_device == device*/) {
        s_device = NULL;
    }
    invokeCallback(c, EventDfuExit, device);
}

void recoveryDisconnect(AMRecoveryModeDevice device, void* ctx)
{
    PITMD_CONTEXT c = (PITMD_CONTEXT)ctx;
    invokeCallback(c, EventRecoveryExit, device);    
}


MUX_API void itmd_restoreBundle(const char* bundlePath)
{
#ifdef WIN32
    AMRestoreEnableFileLogging("c:\\temp\\md.log"); ///FIXME: hardcoded path
#else
    AMRestoreEnableFileLogging("/tmp/md.log");
#endif
    s_restoreContext->dfuAttempts = 5;
    s_restoreContext->recoveryAttempts = 1;
    s_restoreContext->restoreOptions = RestoreOptionsDictWithBundlePath(bundlePath);
    if (s_device != NULL) {
        --s_restoreContext->dfuAttempts;
        AMRestorePerformDFURestore(s_device, s_restoreContext->restoreOptions, restoreProgressCallback, s_restoreContext);
    }
}

MUX_API void itmd_run(pfn_javaMobileDeviceCallbackProc_t callback, void* context)
{
	void* unkOut = NULL;
#ifdef WIN32
	win32_dispatch_thread_init(); // otherwise we lose the first event if the device is already connected
#endif

    s_restoreContext = (PITMD_CONTEXT) malloc(sizeof(ITMD_CONTEXT));
    s_restoreContext->restoreOptions = NULL;
    s_restoreContext->javaCallback = callback;
    s_restoreContext->javaContext = context;
    AMRestoreRegisterForDeviceNotifications(dfuConnect, recoveryConnect, dfuDisconnect, recoveryDisconnect, 0, s_restoreContext);
    AMDeviceNotificationSubscribe(mux_notification_callback, 0, 0, s_restoreContext, &unkOut);

#ifdef WIN32
	win32_dispatch_thread_run();
#else
	CFRunLoopRun();
#endif
}