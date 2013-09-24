# 1 "/home/dcampbel/Research/blinkyBocksHardware/build/src-bobby/system/circ_buffer.bbh"
#include "../sim/block.h"
#ifndef __CIRC_BUFFER_H__
#define __CIRC_BUFFER_H__

#include "defs.h"

// #define CIRC_BUF_LEN	30

// typedef struct _circ_buf_t { byte buf[CIRC_BUF_LEN]; byte start; byte end; } CircBuf;

void push(byte, CircBuf *);
int pop(CircBuf *);
byte isEmpty(CircBuf *);

#endif
