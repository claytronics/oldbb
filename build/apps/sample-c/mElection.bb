#include "handler.bbh"
#include "block.bbh"
#include "led.bbh"
#include "ensemble.bbh"
#include "clock.bbh"

#include "block_config.bbh"
#include "memory.bbh"

#ifdef LOG_DEBUG
#include "log.bbh"
#endif

int bestId;

#define MYCHUNKS 12
Chunk myChunks[MYCHUNKS];

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

byte colorHandler(void)
{
    if(thisChunk == NULL) {
        return 0;
    }

    setColor(YELLOW);
    printf("%d : %d\n",bestId,this()->id);

    return 1;
}

void myMain(void)
{
    // setColor(RED);

    for(byte x=0; x < MYCHUNKS; x++) {
        myChunks[x].status = CHUNK_FREE;
    }

    bestId = this()->id;

    printf("BestID : %d\n",bestId);

    int lock = 0;
    char msg[17];

    //0 : BAS

    while(1){
        
        Chunk* cChunk = getFree();
        msg[0] = 42;
        sendMessageToPort(cChunk, 4, msg, 1, colorHandler, NULL);

    }

}
void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}
