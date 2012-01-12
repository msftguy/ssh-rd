#pragma  once
#ifdef WIN32

int win32_dispatch_thread_init();

void win32_dispatch_thread_run();

int win32_run_on_thread(PITMD_CONTEXT ctx, int eventType, int productId, int productType);

#endif //WIN32