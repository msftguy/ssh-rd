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
#include "mux_api.h"


// Based on iPhone_tunnel by novi (novi.mad@gmail.com ) http://novis.jimdo.com
// thanks
// http://i-funbox.com/blog/2008/09/itunesmobiledevicedll-changed-in-itunes-80/
// 2010-2012 msftguy

//
//#define TRUE 1
//#define FALSE 0
#define BUFFER_SIZE 256
#define MAX_SOCKETS 512

typedef struct {
	int from_handle;
	int to_handle;
} conn_struc ;

void* THREADPROCATTR wait_for_device(void*);
void wait_connections();
void notification(struct am_device_notification_callback_info*);
void*THREADPROCATTR conn_forwarding_thread(void* arg);

static int threadCount = 0;

static int  sock;

static muxconn_t muxConn = 0;
static am_device_t s_target_device = NULL;

int itmd_start_mux_tunnel(int localPort, int remotePort)
{
	struct sockaddr_in saddr;
	int ret = 0;
    
	memset(&saddr, 0, sizeof(saddr));
	saddr.sin_family = AF_INET;
	saddr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
	saddr.sin_port = htons(localPort);     
	sock = socket(AF_INET, SOCK_STREAM, 0);
    
	int temp = 1;
	if(setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (const char*)&temp, sizeof(temp))) {
		fprintf(stderr, "setsockopt() failed - ignorable");
	}
    
	ret = bind(sock, (struct sockaddr*)&saddr, sizeof(struct sockaddr));
    
	if ( ret == SOCKET_ERROR ) {
		fprintf(stderr, "bind error %i !", ret);
		return 1;
	}
    
	listen(sock, 0);
    
	int lpThreadId;
	pthread_t socket_thread;
	lpThreadId = pthread_create(&socket_thread, NULL, wait_for_device, (void*)(size_t)remotePort);
	pthread_detach(socket_thread);
    
	fprintf(stderr, "Waiting for new TCP connection on port %hu", localPort);
    
	fprintf(stderr, "Waiting for device...");
	fflush(stdout);
    
    return 0;
}

/****************************************************************************/

char* getConnectedDeviceName(struct am_device_notification_callback_info* info)
{
	static char deviceName[BUFFER_SIZE];
	*deviceName = '\0';
	CFStringRef devId = AMDeviceCopyDeviceIdentifier(info->dev);
	if (devId != nil) {
		CFStringGetCString(devId, deviceName, sizeof(deviceName), kCFStringEncodingASCII);
	}
	return deviceName;
}

void mux_notification_callback(struct am_device_notification_callback_info* info, void* context)
{
    PITMD_CONTEXT ctx = (PITMD_CONTEXT)context;
	switch (info->msg)
	{
        case ADNCI_MSG_CONNECTED:
        {
            int interfaceType = AMDeviceGetInterfaceType(info->dev);   
            int ignore = interfaceType != 1;
            fprintf(stderr, "Device connected: %s%s", getConnectedDeviceName(info), 
                ignore ? " - Ignoring (non-USB)" : "");
            if (!ignore) {
                s_target_device = info->dev;
            }
        }
            break;
        case ADNCI_MSG_DISCONNECTED:
            fprintf(stderr, "Device disconnected: %s", getConnectedDeviceName(info));
            if (info->dev == s_target_device) {
                fprintf(stderr, "Clearing saved mux connection");
                s_target_device = NULL;
                muxConn = 0;
            }
            break;
        default:
            break;
	}
}

void* THREADPROCATTR wait_for_device(void* arg)
{
	int iphonePort = (int)(intptr_t)arg;
	int ret;
	int handle = -1;
	restore_dev_t restore_dev;
    
	while (1) {
		int new_sock;
		if (s_target_device == NULL) {
			sleep(1);
			continue;
		}
        
		{
            struct sockaddr_in sockAddrin;
            socklen_t len = sizeof(sockAddrin);
            new_sock = accept(sock, (struct sockaddr*) &sockAddrin , &len);
        }
		
		if (new_sock == -1) {
			fprintf(stderr, "accept() error");
			continue;
		}
		
		fprintf(stderr, "Info: New connection...");
		
		if (muxConn == 0)
		{
            ret = AMDeviceConnect(s_target_device);
			fprintf(stderr, "AMDeviceConnect() = 0x%x", ret);
            
            if (ret == ERR_SUCCESS) {
				muxConn = AMDeviceGetConnectionID(s_target_device);
			} else if (ret == -402653144) { // means recovery mode .. I think
				muxconn_t mux_tmp = AMDeviceGetConnectionID(s_target_device);
               fprintf(stderr, "muxConnTmp = %X", mux_tmp);
                muxConn = mux_tmp;
                restore_dev = AMRestoreModeDeviceCreate(0, mux_tmp, 0);
                fprintf(stderr, "restore_dev = %p", restore_dev);
                if (restore_dev != NULL) {
                    AMRestoreModeDeviceReboot(restore_dev);
                    sleep(5);
                } 
			} else if (ret == -402653083) { // after we call 'reboot', api host is down
                muxconn_t mux_tmp = AMDeviceGetConnectionID(s_target_device);
                fprintf(stderr, "muxConnTmp = %X", mux_tmp);
                muxConn = mux_tmp;
            } else {
				fprintf(stderr, "AMDeviceConnect = %i", ret);
				goto error_connect;
			}
		}                               
		fprintf(stderr, "Device connected");
        
		ret = USBMuxConnectByPort(muxConn, htons(iphonePort), &handle);
		if (ret != ERR_SUCCESS) {
			fprintf(stderr, "USBMuxConnectByPort = %x, handle=%x", ret, handle);
			goto error_service;
		}
        
		fprintf(stderr, "USBMuxConnectByPort OK");
		
		conn_struc* connection1;
		conn_struc* connection2;
		
		connection1 = (conn_struc*)malloc(sizeof(conn_struc));
		if (!connection1) {
			fprintf(stderr, "Malloc failed!");
			continue;
		}
		connection2 = (conn_struc*)malloc(sizeof(conn_struc));    
		if (!connection2) {
			fprintf(stderr, "Malloc failed!");
			continue;
		}
		
		connection1->from_handle = new_sock;
		connection1->to_handle = handle;
		connection2->from_handle = handle;
		connection2->to_handle = new_sock;
		
		fprintf(stderr, "sock handle newsock:%d iphone:%d", new_sock, handle);
        
		int lpThreadId;
		int lpThreadId2;
		pthread_t thread1;
		pthread_t thread2;
		
		lpThreadId = pthread_create(&thread1, NULL, conn_forwarding_thread, (void*)connection1);
		lpThreadId2 = pthread_create(&thread2, NULL, conn_forwarding_thread, (void*)connection2);
		
		pthread_detach(thread2);
		pthread_detach(thread1);
        
		Sleep(100);
		
		continue;
        
    error_connect:
		fprintf(stderr, "Error: Device Connect");
		AMDeviceDisconnect(s_target_device);
		sleep(1);
		
		continue;
		
    error_service:
		fprintf(stderr, "Error: Device Service");
		AMDeviceDisconnect(s_target_device);
		sleep(1);
		continue;
		
	}
	return NULL;
}


/****************************************************************************/

void* THREADPROCATTR conn_forwarding_thread(void* arg)
{
	conn_struc* con = (conn_struc*)arg;
	uint8_t buffer[BUFFER_SIZE];
	int bytes_recv, bytes_send;
	
	threadCount++;
	fprintf(stderr, "threadcount=%d",threadCount);
	
	while (1) {
		bytes_recv = recv(con->from_handle, (char*)buffer, BUFFER_SIZE, 0);
		
		bytes_send = send(con->to_handle, (char*)buffer, bytes_recv, 0);
		
		if (bytes_recv == 0 || bytes_recv == SOCKET_ERROR || bytes_send == 0 || bytes_send == SOCKET_ERROR) {
			threadCount--;
			fprintf(stderr, "threadcount=%d\n", threadCount);
			
			close(con->from_handle);
			close(con->to_handle);
            
			free(con);
			
			break;
		}
	}
	return nil;
}



