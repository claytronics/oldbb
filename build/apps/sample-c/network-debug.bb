#include "block.h"
#include "memory.h"

#include "handler.h"
#include "time.h"
#include "led.h"
#include "hw-api/hwMemory.h"


#define MYCHUNKS 12
extern Chunk* thisChunk;
Chunk myChunks[MYCHUNKS];

// find a useable chunk
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

// simple message handler
byte colorHandler(void)
{
    if(thisChunk == NULL) {
        return 0;
    }

    Color c = thisChunk->data[0];
    setColor(c);

    return 1;
}


// main program
void myMain(void)
{
    static Time i = 1000;
    static int c = 0;
    int x;

    setColor(WHITE);

    // init messages
    for(x=0; x<MYCHUNKS; x++) {
        myChunks[x].status = CHUNK_FREE;
    }

    while(1)
    {
        // broadcast color messages at 1 Hz
        if(i < getTime()) 
        {
            i += 1000;
            char msg[17];

            msg[0] = (c++) % 3;

            for(x=0; x<NUM_PORTS; x++) {
                Chunk* cChunk = getFree();

                if(cChunk != NULL) {
                    if( sendMessageToPort(cChunk, x, msg, 1, colorHandler, NULL) == 0 ) {
                        freeChunk(cChunk);
                    }
                }
            }
        }
    }
}

// register handlers
void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
