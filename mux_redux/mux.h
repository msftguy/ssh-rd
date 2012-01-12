#pragma once

#if WIN32 
#include <winsock2.h>
#include <stdio.h>
#else 
#include <dlfcn.h>
#include <unistd.h>
#include <signal.h>
#include <pthread.h>
#include <netinet/ip.h>
#include <mach/error.h>
#endif 


///////////////////////// WIN32 //////////////////////////////
#if WIN32 
typedef void* pthread_t;
typedef void* pthread_attr_t;

#define THREADPROCATTR WINAPI

typedef void *(THREADPROCATTR* thread_proc_t)(void *);

__inline int pthread_create(pthread_t * __restrict pHandle,
                          const pthread_attr_t * __restrict attr,
                          thread_proc_t threadStart,
                          void* arg) 
{
	DWORD tid;
	*pHandle = CreateThread(NULL, 0, (LPTHREAD_START_ROUTINE)threadStart, arg, CREATE_SUSPENDED, &tid);
	return tid;
}

#define pthread_detach ResumeThread

#define ERR_SUCCESS 0

typedef size_t socklen_t;

#else ////////////////////////// OS X ///////////////////

#define _cdecl

#define THREADPROCATTR 

#define Sleep(ms) usleep(ms*1000)

#define SOCKET_ERROR -1

#define closesocket close

#endif // WIN32

void mux_notification_callback(struct am_device_notification_callback_info *, void* ctx);
