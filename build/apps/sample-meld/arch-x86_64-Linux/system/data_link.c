# 1 "/home/dcampbel/Research/blinkyBocksHardware/build/src-bobby/system/data_link.bb"
#include "../sim/block.h"
// data_link.c
//
// Implement Data Link Layer protocols

#ifndef _DATA_LINK_C_
#define _DATA_LINK_C_

#include "../hw-api/hwDataLink.h"
#include "../hw-api/hwSerial.h"
#include "data_link.h"
#include "led.h"


// global receive queue for packets
// move to network layer???
// ReceivePacketQueue globalRq;

//Port port[NUM_PORTS];

// used to allow message handler and callbacks access to state
// Chunk* thisChunk;

/////////////////// PROTECTED FUNCTIONS ///////////////////
// default message handler - does nothing
byte defaultMsgHandler(void)
{
    return 1;
}

// handler for reserved system mesages
//   using 0x0000 as message handler allows blocks
//   running different programs to communicate at the system level.
byte reservedSystemHandler(void)
{
    // bogus
    if( (this()->thisChunk)  == NULL) {
        return 0;
    }

    // find msg type and check
    switch(  (this()->thisChunk) ->data[0] )
    {
        case JUMP_BOOTLOADER: // CRC for this message: 0x2A
        {
	        int i = 1;

	        // check msg consistency
	        while( i < DATA_SIZE ) {
	            if(  (this()->thisChunk) ->data[i] != (JUMP_BOOTLOADER ^ i) ) {
	                // invalid bootloader packet, break and do nothing
	                break;
	            }
	            i++;
	        }
	        // consistent
    	    jumpToBootSection();
        }
        case NEIGHBOR_MSG:
            handleNeighborMessage();
            break;
        default:
            break;
    }

    return 0;
}

// removes the first packet from the send queue and
//    updates pointers
//    updates pointers
// inputs:  port
// output:  1-success, 0-failure
byte removeFromSq(PRef p, byte response)
{
    // empty or bad
    if( (p >= NUM_PORTS) || ( (this()->port) [p].sq.head == NULL) )
    {
        return 0;
    }

    BB_LOCK(SQ_LOCK)

    // get the first Chunk
     (this()->thisChunk)  =  (this()->port) [p].sq.head;

    // move the head of the queue
     (this()->port) [p].sq.head =  (this()->thisChunk) ->next;

    // tail was pointing to head, remove tail as well
    if(  (this()->port) [p].sq.head == NULL )
    {
         (this()->port) [p].sq.tail   = NULL;
         (this()->port) [p].sq.flags &= ~CHUNK_READY;
    }
    // otherwise, leave it to what it was pointing to

    BB_UNLOCK(SQ_LOCK)

    // remove thisChunk's references to queue
     (this()->thisChunk) ->next = NULL;

    if( (this()->thisChunk) ->callback != NULL)
    {
        // set response type for callback
        setChunkResponse( (this()->thisChunk) , response);

        // execute callback to clear memory and other user actions
        ( (this()->thisChunk) ->callback)();
    }

    // we assume that the callback has freed memory
     (this()->thisChunk)  = NULL;
    return 1;
}

// flush a send queue (used when retries fails)
void flushSendQueue(PRef p)
{
    if(p < NUM_PORTS)
    {
        // flush buffer (call all callbacks as if all messages failed)
        while( removeFromSq(p, MSG_RESP_NOREPLY) );
    }
}
///////////////// END PROTECTED FUNCTIONS /////////////////

//////////////////// PUBLIC FUNCTIONS /////////////////////
// correctly formats/prepares all components of the chunk for sending
// inputs:  c - chunk to format
//          port - port to send on (0-NUM_PORTS only)
//          msg - data associated with the message
//          length - length of the data portion only
//          mh - handler for this message
//          cb - callback on send failure
// outputs: 1 - chunk correctly formatted
//          0 - inputs contained error, format failed
byte setupChunk(Chunk* c, PRef p, byte * msg, byte length, MsgHandler mh, GenericHandler cb)
{
    // invalid Chunk, invalid port, message too long, or no message
    if( (c == NULL) || (p >= NUM_PORTS) || (length > DATA_SIZE) || (msg == NULL) )
    {
        return 0;
    }

    // set the flags
    c->status = CHUNK_USED | CHUNK_FILLED | MSG_RESP_SENDING |  (this()->port) [p].pnum;

    // clear out next pointer
    c->next = NULL;

    // set message handler
    *((MsgHandler*)(c->handler)) = mh;

    // setup callback
    c->callback = cb;

    // copy message
    memcpy(c->data, msg, length);

    // 'zero' out extra bytes (use ff's)
    memset((c->data)+length, 0xFF, DATA_SIZE-length);

    return 1;
}

// queues the chunk for sending
// no guarantee chunk will be received - best-effort only
// inputs:  c - correctly formatted chunk
// outputs: 1 - chunk successfully queued
//          0 - error, chunk was not queued
byte queueChunk(Chunk* c)
{
    // null Chunk
    if(c == NULL)
    {
        return 0;
    }

    byte p = faceNum(c);

    if(p < NUM_PORTS)
    {
#ifdef DEBUG
#if DEBUG == 5 // UP

#define DEBUGPORT 5
#define DEBUGUART USARTD1

#elif DEBUG == 1 // NORTH

#define DEBUGPORT 1
#define DEBUGUART USARTC1

#elif DEBUG == 4 //SOUTH

#define DEBUGPORT 4
#define DEBUGUART USARTE0

#elif DEBUG == 2 //EAST

#define DEBUGPORT 2
#define DEBUGUART USARTF0

#elif DEBUG == 3 //WEST

#define DEBUGPORT 3
#define DEBUGUART USARTC0

#elif DEBUG == 0 //DOWN

#define DEBUGPORT 0
#define DEBUGUART USARTD0

#endif
#ifndef DEBUGPORT
#error Invalid DEBUG option chosen - use a face enum.
#endif

        if(p == DEBUGPORT)
        {
            freeChunk(c);
            return 0;
        }
#endif

        BB_LOCK(SQ_LOCK)

        // add to queue - queue empty
        if(  (this()->port) [p].sq.head == NULL )
        {
             (this()->port) [p].sq.head = c;
        }
        // queue has stuff
        else
        {
             (this()->port) [p].sq.tail->next = c;
        }
        // add to tail and update flags
         (this()->port) [p].sq.tail   = c;
         (this()->port) [p].sq.flags |= CHUNK_READY;

        BB_UNLOCK(SQ_LOCK)

        return 1;
    }
    else
    {
        freeChunk(c);
        return 0;
    }
}

// call active message handler and send ack
// returns: 1 - message processed
//          0 - no new messages
byte handleOneMessage()
{
    // set the global chunk that needs to be processed by the handler
     (this()->thisChunk)  = nextPacket();

    // no packet, no action
    if(  (this()->thisChunk)  == NULL )
    {
        return 0;
    }

    // call handler
    if( *((MsgHandler*) (this()->thisChunk) ->handler) == RES_SYS_HANDLER )
    {
	    reservedSystemHandler();
    }
    else
    {
	    (*((MsgHandler*)( (this()->thisChunk) ->handler)))();
    }

    // set flag to send ACK
     (this()->port) [faceNum( (this()->thisChunk) )].sq.flags |= setAck( (this()->thisChunk) );

    // free the rx chunk
    freeChunk( (this()->thisChunk) );

    // reset the global chunk since memory is no longer valid
     (this()->thisChunk)  = NULL;

    return 1;
}

// initialization
void initDataLink()
{
    initHWDataLink();

     (this()->globalRq) .head  = NULL;
     (this()->globalRq) .tail  = NULL;
     (this()->globalRq) .flags = 0;
}
////////////////// END PUBLIC FUNCTIONS ///////////////////

#endif
