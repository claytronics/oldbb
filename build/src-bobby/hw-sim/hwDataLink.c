#ifndef _HW_DATALINK_C_
#define _HW_DATALINK_C_

#include "../hw-api/hwDataLink.h"
#include "../system/ensemble.h"
#include "../sim/world.h"
#include "../system/led.bbh"

#ifdef CLOCK_SYNC
#include  "../system/clock.bbh"
#endif

// is a HW only function
void processBuffer(PRef p)
{
    return;
}

// emulates the HW equivalent of out-of-retries (neighbor gone)
void outOfRetries(PRef p)
{
    // flush send queue
    flushSendQueue(p);

    // ensemble level updates - remove neighbor, restart neighbor scanning
    //     These don't belong in data-link in my opinion
    //restartScan(p);
}

// emulate send, ack, nack
void sendOnSerial(PRef p)
{
    Block* b = this();

    // chunk for receiver
    Chunk* recd;

    // get next chunk to send
    Chunk* send = b->port[p].sq.head;

    if( send == NULL ) {
        return;
    }

    // find port to be received in
    byte destPort = p;
  
    switch(destPort)
    {
        case North:
            destPort = South;
            break;
        case South:
            destPort = North;
            break;
        case East:
            destPort = West;
            break;
        case West:
            destPort = East;
            break;
        case Top:
            destPort = Down;
            break;
        case Down:
            destPort = Top;
            break;
        default:
	    // this is very bad - I would like to just drop it silently
            printf("did not rewrite direction\n");

            // pretend that it just couldn't get a response?
            outOfRetries(p);
            return;
    }

    // invalid port
    if(destPort < NUM_PORTS)
    {
        Block* dest = seeIfNeighborAt(this(), p);

        // neighbor doesn't exist - won't receive it
        if(!dest) {
            outOfRetries(p);
            return;
        }

        // I'm not sure what this means, but I assume it should be handled like above
        if( !dest->blockReady || dest->destroyed) {
            outOfRetries(p);
            return;
        }

        // emulate a receive
        recd = malloc(sizeof(*send));

        if(recd == NULL) {
            printf("out of memory!\n");
            outOfRetries(p);
            return;
        }
#ifdef CLOCK_SYNC
		// insert receive time
		if (isAClockSyncMessage(send))
		{
			insertSendTime(send);
		}
#endif
        memcpy(recd, send, sizeof(*send));
        recd->status = CHUNK_USED | CHUNK_FILLED | destPort;
        recd->next = NULL;
#ifdef CLOCK_SYNC
		if (isAClockSyncMessage(recd))
		{
			// insert receive time
			insertReceiveTime(recd);
		}
#endif
        // add to receive queue
        BB_LOCK(&(dest->neighborMutex));

        // add to queue - queue empty
        if( dest->globalRq.head == NULL ) 
        {
            dest->globalRq.head = recd;
        }
        // queue has stuff
        else 
        {
            dest->globalRq.tail->next = recd;
        }
        // add to tail and update flags
        dest->globalRq.tail  = recd;
        dest->globalRq.flags = PACKET_READY;

        BB_UNLOCK(&(dest->neighborMutex));

        // emulate an ACK
        removeFromSq(p, MSG_RESP_ACK);
        return;
    }
    else
    {
        // can't be sent
        outOfRetries(p);
        return;
    }
}

// gets data from the global receive queue
Chunk* nextPacket(void)
{
    Block* b = this();

    BB_LOCK(&(b->neighborMutex));

    Chunk* c = b->globalRq.head;
    
    // no packets
    if( c == NULL )
    {
      b->globalRq.flags &= ~PACKET_READY;
      b->globalRq.tail = NULL;
      
      BB_UNLOCK(&(b->neighborMutex));

      return NULL;
    }
    
    BB_UNLOCK(&(b->neighborMutex));

    // this is annoying but has to be done as to ensure using system chunks
    Chunk* recd = getSystemRXChunk();

    // can't process yet - delay
    if( recd == NULL ) {
        return NULL;
    }

    BB_LOCK(&(b->neighborMutex));

    // update pointers
    b->globalRq.head = c->next;

    if( b->globalRq.head == NULL )
    {
        b->globalRq.flags &= ~PACKET_READY;
        b->globalRq.tail = NULL;
    }
    
    // isolate and return
    c->next = NULL;
    BB_UNLOCK(&(b->neighborMutex));

    // copy malloc'ed chunk into a static system chunk
    memcpy(recd, c, sizeof(*c));
    
    // free the malloc'ed copy (to avoid memory leaks)
    free(c);
    return recd;
}

// just init the mutexes
void initHWDataLink()
{
    pthread_mutex_init(&(this()->neighborMutex), NULL);
    pthread_mutex_init(&(this()->sendQueueMutex), NULL);
}

#endif
