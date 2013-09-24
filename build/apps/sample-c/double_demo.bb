#include "block.bbh"
#include "memory.bbh"

#include "handler.bbh"
#include "time.bbh"
#include "led.bbh"
#include "hw-api/hwMemory.h"


#define DEBOUNCE_COUNT 30
#define MYCHUNKS 12
extern Chunk* thisChunk;
Chunk myChunks[MYCHUNKS];

int tapDetected;
int flipped;

Chunk* getFree(void)
{
    Chunk* c;
	int i;
	for(i=0; i<MYCHUNKS; i++) {
		c = &(myChunks[i]);
	
		if( !chunkInUse(c) ) {
		    return c;
		}	
	}

	return NULL;
}

byte changeColorHandler(void)
{
	if(thisChunk == NULL) { 
        return 0;
	}
	
	byte x;
	char msg[17];
	
	Color curr = getColor();
	Color next = thisChunk->data[0];
	byte  from = faceNum(thisChunk);
	
	// check for difference
	if( curr == next ) {
	    return 0;
	}
	
	msg[0] = next;
	
	// set and send!
	for( x=0; x<NUM_PORTS; x++ ) {
		if(from == x) {
		    continue;
		}
		
		Chunk* cChunk = getFree();
					
		if(cChunk != NULL) {
			if( sendMessageToPort(cChunk, x, msg, 1, changeColorHandler, NULL) == 0 ) {
				freeChunk(cChunk);
			}
		}
	}
				
	setColor(next);
	return 1;
}

void orientationHandler(void)
{
    static Time lastTap;
	AccelData acc = getAccelData();

	if(acc.status & 0x20) {
		if(getTime() - lastTap > 500)
		{
			lastTap = getTime();
			tapDetected = 1;
		}
	}
	
    if(acc.z > 15) {
    	flipped = 0;
	}
    else if(acc.z < -15) {
    	flipped = 1;
	}
}

void myMain(void)
{
	int x;
	char msg[17];

	Color color = WHITE;

	setColor(color);
	for(x=0; x<MYCHUNKS; x++) {
		myChunks[x].status = CHUNK_FREE;
	}	
	
	while(1)
	{
	    // ends
		if( flipped )
	    {
			int numNeighbors = getNeighborCount();
		
			// an end
		    if( numNeighbors == 1 ) { 
			    setColor(RED);
			}
			// regular block
			else {
				setColor(BLUE);
			}
		}
		// tap demo
		else
		{
			// was a tap
			if (tapDetected) {
				color = setNextColor();
				tapDetected = 0;

				msg[0] = color;
				
				// broadcast
				for( x=0; x<NUM_PORTS; x++ ) {
					Chunk* cChunk = getFree();
					
					if(cChunk != NULL) {
						if( sendMessageToPort(cChunk, x, msg, 1, changeColorHandler, NULL) == 0 ) {
							freeChunk(cChunk);
						}
					}
				}
			}
		}
	}
}

void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
	registerHandler(EVENT_ACCEL_CHANGE, (GenericHandler)&orientationHandler);
}
