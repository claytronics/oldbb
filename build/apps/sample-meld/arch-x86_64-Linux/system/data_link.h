# 1 "/home/dcampbel/Research/blinkyBocksHardware/build/src-bobby/system/data_link.bbh"
#include "../sim/block.h"
// data_link.h
//
// Define all Data Link Layer protocols

#ifndef _DATA_LINK_H_
#define _DATA_LINK_H_

#include "defs.h"
#include "string.h"
#include "serial.h"
#include "memory.h"
#include "queues.h"
#include "handler.h"
#include "ensemble.h"
#include "message.h"
#include "boot.h"
#include "led.h"

#define NUM_RETRIES 4
#define DEFAULT_TIMEOUT 50

#define RES_SYS_HANDLER 0x0000
#define JUMP_BOOTLOADER	0xff
#define NEIGHBOR_MSG	0x01

//Chunk * thisChunk;

// correctly formats/prepares all components of the chunk for sending
// inputs:  c - chunk to format
//          port - port to send on (0-NUM_PORTS only)
//          msg - data associated with the message
//          length - length of the data portion only
//          mh - message handler for this message
//          cb - callback on send failure
// outputs: 1 - chunk correctly formatted
//          0 - inputs contained error, format failed
//byte setupChunk(Chunk*, PRef, char*, byte, MsgHandler);
byte setupChunk(Chunk*, PRef, byte *, byte, MsgHandler, GenericHandler);

// queues the chunk for sending
// no guarantee chunk will be received - best-effort only
// inputs:  c - correctly formatted chunk
// outputs: 1 - chunk successfully queued
//          0 - error, chunk was not queued
byte queueChunk(Chunk*);

// call active message handler and send ack
// returns: 1 - message processed
//          0 - no new messages
byte handleOneMessage(void);

// initialization
void initDataLink(void);

#endif
