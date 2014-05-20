#ifndef _HW_DATALINK_C_
#define _HW_DATALINK_C_

#include "../hw-api/hwDataLink.h"
#include "../hw-api/hwSerial.h"
#include "../system/ensemble.h"
#include "../system/serial.h"

#ifdef CLOCK_SYNC
#include  "../system/clock.bbh"
#endif

#ifdef TESTING
#define FILENUM 5
#endif

#define CRC_POLY        0xA6

// store if received an escape char
byte   wasEscapeStore[NUM_PORTS];

extern ReceivePacketQueue globalRq;
extern Port port[NUM_PORTS];

// calculates part of crc
byte crcCalc(byte currCrc, byte newVal)
{
    byte crc = currCrc;
	int i;
	
	crc ^= newVal;
    for (i=0; i<8; i++)
	{
        if (0x80 & crc) {
            crc = (crc << 1) ^ CRC_POLY;
        }
		else {
            crc = crc << 1;
        }
    }
	return crc;
} 

// handles the ack byte and updates the packet queue
//    (parity, timer, CTS)
// inputs:  port received on
// returns: nothing
void ackHandler(PRef p)
{
    byte currParity = sendParity(port[p].sq);
    
    // check for double acks (shouldn't ever happen)
    if( !(gotOddAck(port[p].rq) ^ gotEvenAck(port[p].rq)) ) 
    {
        clearReceivedAcks( port[p].rq );
        return;
    }
    byte recdParity = gotOddAck(port[p].rq);
    clearReceivedAcks( port[p].rq );
    
    // parity matches
    if(currParity == recdParity)
    {
        // remove the Chunk, call callback with successfull response
        removeFromSq(p, MSG_RESP_ACK);
        
        // reset the flags
        port[p].sq.retry    = NUM_RETRIES;
        flipParity(port[p].sq);
        port[p].sq.flags   |= CLEAR_TO_SEND;
        
        // reset the timer
        deregisterTimeout(&(port[p].sq.qtout.tout));
    }
    // else, parity error - don't do anything
}

// adds a Chunk to the global receive queue
void addToGlobalRq(Chunk* c)
{
    // nothing to add
    if( c == NULL ) 
    {
        return;
    }
        
    // isolate Chunk
    c->next = NULL;
    
    // update pointers
    if( globalRq.head == NULL ) 
    {
        globalRq.head = c;
    }
    else 
    {
        globalRq.tail->next = c;
    }
    
    // add/flag it
    globalRq.tail  = c;
    globalRq.flags = PACKET_READY;
}

// check to see if character is special
byte isSpecial(byte val)
{
    if( ((val & ACK_MASK) == ACK   ) || ((val & ACK_MASK) == NACK  ) ||
        ((val & FD_MASK)  == FD    ) || (val              == ESCAPE )   )
    {
        return 1;
    }
        
    return 0;
}

// makes the Chunk into the correct send string
void sendToBuffer(PRef p, Chunk* c, byte parity)
{
    byte checksum = 0;
    byte val;
    byte i;
    byte delim;
    
    if(c == NULL)
    {
        return;
    }
	
    // send FD
    delim = (FD | parity);

    // send messageHandler
    for(i=0; i<POINTER_SIZE; i++)
    {
        val = c->handler[i];
        checksum = crcCalc(checksum, val);
        
        // escape it
        if( isSpecial(val) ) 
        {
            push(ESCAPE, &(port[p].tx));
            push(val^ESCAPE_CHAR, &(port[p].tx));
        }
        // send regular
        else 
        {
            push(val, &(port[p].tx));
        }
    }
    
    // send data
    for(i=0; i<DATA_SIZE; i++)
    {
        val = c->data[i];
        checksum = crcCalc(checksum, val);
        
        // escape it
        if( isSpecial(val) ) 
        {
            push(ESCAPE, &(port[p].tx));

            push(val^ESCAPE_CHAR, &(port[p].tx));
        }
        // send regular
        else 
        {
            push(val, &(port[p].tx));
        }
    }

    // send checksum
	// escape it
    if( isSpecial(val) ) 
    {
        push(ESCAPE, &(port[p].tx));
        push(checksum^ESCAPE_CHAR, &(port[p].tx));
    }
    // send regular
    else 
    {
        push(checksum, &(port[p].tx));
    }
    
    // start the interrupt by sending a byte (FD)
    pPutChar(delim, port[p].pnum);
}


// pulls bytes from the buffer and puts them into Chunks
// also handles ACKs appropriately
void processBuffer(PRef p)
{
    byte   currByte;
    Chunk* currChunk = NULL;
    byte   wasEscape = wasEscapeStore[p];


    // nothing to process
    if( isEmpty(&(port[p].rx)) )
    {
        return;
    }

    // continue filling unfinished Chunk
    if( (port[p].rq.curr != NULL) && chunkFilling(port[p].rq.curr) ) 
    {
        currChunk = port[p].rq.curr;
    }
    else
    {
        port[p].rq.index    = 0;
        port[p].rq.checksum = 0;
        wasEscape           = 0;
    }
                
    while( !isEmpty(&(port[p].rx)) )
    {
        currByte = (byte)pop(&(port[p].rx));
        
        // is ACK
        if( (currByte & ACK_MASK) == ACK )
        {
            // set ack parity
            port[p].rq.flags |= (1 << (currByte & 0x01));
        
            ackHandler(p);
            continue;
        }
        
        /// TODO: HANDLE NACKS
        /*if( (currByte & ACK_MASK) == NACK ){
	        //set ack parity
	        //port[p].rq.flags |= (1<<(currByte & 0x01));
	        
	        nackHandler(p);
	        continue;
	        }*/
        
        // is FRAME_DELIMETER
        if( (currByte & FD_MASK) == FD )
        {
            // determine parity
            byte parity = setParityFromByte(currByte);
        
            // restart the fill
            port[p].rq.index    = 0;
            port[p].rq.checksum = 0;
            wasEscape           = 0;
                
            // unfinished Chunk
            if(port[p].rq.curr != NULL)
            {
                currChunk = port[p].rq.curr;
            }
            // need new Chunk
            else
            {
                currChunk = getSystemRXChunk();
#ifdef CLOCK_SYNC
				/*if (isAClockSyncMessage(currChunk))
				{		
					// insert receive time
					//insertReceiveTime(currChunk);
				}*/
#endif
                // out of memory, can't fill
                if( currChunk == NULL )
                {
                    // discard bytes, hopefully will get resent
                    continue;
                }
                
                // add to the queue
                port[p].rq.curr = currChunk;
            }
            
            // reset the status of the Chunk, just in case
            currChunk->status = CHUNK_USED | CHUNK_FILLING | parity | port[p].pnum;
            
            continue;
        }
        
        // no use processing if can't put anywhere
        if(currChunk == NULL)
        {
            continue;
        }
        
        // is an escape char        
        if( currByte == ESCAPE )
        {
            wasEscape = 1;   
            continue;
        }

        // is a regular character
        // was escaped
        if( wasEscape )
        {
            currByte ^= ESCAPE_CHAR;
            wasEscape = 0;
        }

        // is transmitted checksum
        if( port[p].rq.index >= (DATA_SIZE + POINTER_SIZE) )
        {
            // checksum matches!
            if( currByte == port[p].rq.checksum )
            {
                currChunk->status &= ~CHUNK_FILLING;
                port[p].rq.flags  |=  CHUNK_READY;
				
                // check for parity error
                byte parity = chunkParity(currChunk);
                byte last   = parityLastChunk(port[p].rq);
	      
                // not a duplicate packet		
                if(parity != last) {
                    // update neighborhood (fix for race condition)
                    //updateNeighbor(p, PRESENT);
#ifdef CLOCK_SYNC
					if (isAClockSyncMessage(currChunk) == 1)
					{		
						// insert receive time
						insertReceiveTime(currChunk);
					}
#endif
                    // add to global receive queue
                    addToGlobalRq(currChunk);
					
                    // flip the parity
                    flipParityLast(port[p].rq);
                    
                    //port[faceNum(currChunk)].sq.flags |= setAck(currChunk);
                }
                // free the chunk
                else 
                {
                    freeChunk(currChunk);
                }
              
                // remove from the port rq
                port[p].rq.curr = NULL;
            }
            
            // reset the data
            port[p].rq.index    = 0;
            port[p].rq.checksum = 0;
            wasEscape           = 0;
            currChunk           = NULL;
	    
            /// TODO: send NACK
            continue;
        }
		
        // message handler
        if( port[p].rq.index < POINTER_SIZE )
        {
            currChunk->handler[port[p].rq.index] = currByte;
        }
        // regular byte
        else
        {
            currChunk->data[port[p].rq.index-POINTER_SIZE] = currByte;
        }

        port[p].rq.checksum = crcCalc(port[p].rq.checksum, currByte);
        port[p].rq.index++;
    }
    
    // store wasEscape
    wasEscapeStore[p] = wasEscape;
}

// gets data from the global receive queue
Chunk* nextPacket(void)
{
    Chunk* c = NULL;

    // no packets
    if( globalRq.head == NULL )
    {
        globalRq.tail = NULL;
        globalRq.flags &= ~PACKET_READY;
        return NULL;
    }

    // update pointers
    c = globalRq.head;
    
    globalRq.head = c->next;
    if( globalRq.head == NULL )
    {
        globalRq.flags &= ~PACKET_READY;
        globalRq.tail = NULL;
    }
    
    // isolate and return
    c->next = NULL;
    return c;
}

// try sending stuff if available and ready
void sendOnSerial(PRef p)
{
    // send acks
    if( shouldSendOddAck(port[p].sq) )
    {
        if( isEmpty(&(port[p].tx)) ) {
            pPutChar(ACK | ODD, port[p].pnum);
        }
        else {
            push(ACK | ODD, &(port[p].tx));
        }
    }
    if( shouldSendEvenAck(port[p].sq) )
    {
        if( isEmpty(&(port[p].tx)) ) {
            pPutChar(ACK | EVEN, port[p].pnum);
        }
        else {
            push(ACK | EVEN, &(port[p].tx));
        }
    }
    clearSendAcks(port[p].sq);
    
    // send Chunk?
    if( chunkReady(port[p].sq) )
    {
        if( clearToSend(port[p].sq) )
        {
            // out of retries
            if( (port[p].sq.retry <= 0) || (port[p].sq.retry > NUM_RETRIES))
            {
                // reset the timer
                deregisterTimeout(&(port[p].sq.qtout.tout));

                // flush the queue
                flushSendQueue(p);
                //removeFromSq(p, MSG_RESP_NOREPLY);
                        
                // reset the flags
                port[p].sq.retry    = NUM_RETRIES;
                port[p].sq.flags   |= CLEAR_TO_SEND;
                flipParity(port[p].sq);

                // ensemble level updates - remove neighbor, restart neighbor scanning
                //     These don't belong in data-link in my opinion
                //restartScan(p);

                return;
            }
            port[p].sq.retry--;

#ifdef CLOCK_SYNC
			if (isAClockSyncMessage(port[p].sq.head) == 1)
			{		
				// insert send time
				insertSendTime(port[p].sq.head);
			}
#endif
            sendToBuffer(p, port[p].sq.head, sendParity(port[p].sq) );
            port[p].sq.flags &= ~CLEAR_TO_SEND;
            
            // start the timeout
            port[p].sq.qtout.tout.calltime = getTime() + DEFAULT_TIMEOUT;
            registerTimeout(&(port[p].sq.qtout.tout));
        }
    }
}

// initialize
void initHWDataLink(){
    // nothing extra to init
}

#endif
