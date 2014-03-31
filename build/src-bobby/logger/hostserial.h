// hostserial.h - provide serial comms for host

#ifndef _HOSTSERIAL_H_
#define _HOSTSERIAL_H_

#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <termios.h>
#include <pthread.h>
#include "circ_buffer.h"
#include <stdio.h>
#include <stdlib.h>

// special serial handle
typedef void *serialHandle;

// serial control
int startupSerial(const char *port, char debug, int baud);
void shutdownSerial(void);

// serial processing
void process(void);

// serial sends
int sendMessage(byte* data, byte size);

#endif

