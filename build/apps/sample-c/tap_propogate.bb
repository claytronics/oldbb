#include "block.bbh"

threaddef #define MYCHUNKS 12

threadextern Chunk* thisChunk;
threadvar Chunk myChunks[MYCHUNKS];

threadvar int tapDetected;


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

byte freeUserChunk(void)
{
	if(thisChunk == NULL) { 
        return 0;
	}

    thisChunk->status = CHUNK_FREE;
    return 1;
}

byte changeColorHandler(void)
{
	if(thisChunk == NULL) { 
        return 0;
	}
	
	byte x;
	byte msg[17];
	
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
			if( sendMessageToPort(cChunk, x, msg, 1, changeColorHandler, freeUserChunk) == 0 ) {
				cChunk->status = CHUNK_FREE;
			}
		}
	}
				
	setColor(next);
	return 1;
}

void orientationHandler(void)
{
    AccelData acc = getAccelData();

	if(acc.status & 0x20) {
		tapDetected = 1;
	}
}


void myMain(void)
{
	int x;
	byte msg[17];

	Color color = WHITE;

	setColor(color);
	for(x=0; x<MYCHUNKS; x++) {
		myChunks[x].status = CHUNK_FREE;
	}	
	
	while(1)
	{
        // was a tap
		if( tapDetected ) {
			color = setNextColor();
			tapDetected = 0;

			msg[0] = color;
				
			// broadcast
			for( x=0; x<NUM_PORTS; x++ ) {
				Chunk* cChunk = getFree();
					
				if(cChunk != NULL) {
					if( sendMessageToPort(cChunk, x, msg, 1, changeColorHandler, freeUserChunk) == 0 ) {
				        cChunk->status = CHUNK_FREE;
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

