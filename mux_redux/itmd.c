#ifdef __APPLE__
#include <CoreFoundation/CoreFoundation.h>
#else
#include <CoreFoundation.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include "mux_api.h"
#include "itmd.h"

typedef struct {
    CFDictionaryRef restoreOptions;
} RESTORE_CONTEXT, *PRESTORE_CONTEXT;

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

void dfuConnect(AMRecoveryModeDevice device, void* ctx)
{
    PRESTORE_CONTEXT c = (PRESTORE_CONTEXT)ctx;
    if (c->restoreOptions) {
        AMRestorePerformDFURestore(device, c->restoreOptions, restoreProgressCallback, ctx);
    }
}

void recoveryConnect(AMRecoveryModeDevice device, void* ctx)
{
    PRESTORE_CONTEXT c = (PRESTORE_CONTEXT)ctx;
    if (c->restoreOptions) {
        AMRestorePerformRecoveryModeRestore(device, c->restoreOptions, restoreProgressCallback, ctx);
    }
}

void dfuDisconnect(AMRecoveryModeDevice device, void* ctx)
{
    
}

void recoveryDisconnect(AMRecoveryModeDevice device, void* ctx)
{
    
}


MUX_API void itmd_restoreBundle(const char* bundlePath)
{
    PRESTORE_CONTEXT restoreContext = (PRESTORE_CONTEXT) malloc(sizeof(RESTORE_CONTEXT));
#ifdef WIN32
    AMRestoreEnableFileLogging("c:\\temp\\md.log");
#else
    AMRestoreEnableFileLogging("/tmp/md.log");
#endif
    restoreContext->restoreOptions = RestoreOptionsDictWithBundlePath(bundlePath);
    AMRestoreRegisterForDeviceNotifications(dfuConnect, recoveryConnect, dfuDisconnect, recoveryDisconnect, 0, restoreContext);
#ifndef WIN32
    CFRunLoopRun();
#endif
}
