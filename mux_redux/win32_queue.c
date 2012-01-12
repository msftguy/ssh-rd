#include <CoreFoundation.h>
#include <stdio.h>
#include <stdlib.h>
#include "mux_api.h"
#include "itmd.h"
#include "mux.h"
#include "win32_queue.h"

static HANDLE s_dispatchThread =  NULL;

int win32_dispatch_thread_init()
{
	if (!DuplicateHandle(GetCurrentProcess(), GetCurrentThread(), GetCurrentProcess(), &s_dispatchThread, 0/*access*/, TRUE, DUPLICATE_SAME_ACCESS)) {
		fprintf(stderr, "DuplicateHandle(GetCurrentThread()) failed!"); fflush(stderr);
		return -1;
	}
	return 0;
}

void win32_dispatch_thread_run()
{
	for (;;) {
		DWORD dw = SleepEx(5*1000, TRUE/*Alertable*/);
	}
}

typedef struct {
    pfn_javaMobileDeviceCallbackProc_t javaCallback;
    void* javaContext;
	int eventType;
	int productId;
	int productType;
} APC_CONTEXT, *PAPC_CONTEXT;

void _stdcall javaApcCallbackWrapper(ULONG_PTR ctx) 
{
	PAPC_CONTEXT c = (PAPC_CONTEXT)ctx;
	c->javaCallback(c->javaContext, c->eventType, c->productId, c->productType);
	free(c);
}

int win32_run_on_thread(PITMD_CONTEXT ctx, int eventType, int productId, int productType)
{
	PAPC_CONTEXT c = (PAPC_CONTEXT)malloc(sizeof(APC_CONTEXT));
	DWORD dw;
	c->javaCallback = ctx->javaCallback;
	c->javaContext = ctx->javaContext;
	c->eventType = eventType;
	c->productId = productId;
	c->productType = productType;
	dw = QueueUserAPC(javaApcCallbackWrapper, s_dispatchThread, (ULONG_PTR)c);
	if (dw == 0) {
		fprintf(stderr, "QueueUserAPC() failed, err=0x%x", GetLastError()); fflush(stderr);
	}
	return dw == 0 ? -1 : 0;
}