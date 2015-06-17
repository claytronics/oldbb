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

threadvar int bestId;
threadvar byte pmaster = 0;
threadvar byte nbOfAnswer = 0;
threadvar Timeout Answer;

#define DIFFUSION_ID 2

#define MYCHUNKS 12
Chunk myChunks[MYCHUNKS];

byte DiffusionHandler(void)
{
    if(thisChunk == NULL) {
        return 0;
    }

    byte id_handler = thisChunk->data[0];

    switch(id_handler){

        case DIFFUSION_ID:{

            byte id = thisChunk->data[1];

            if(id < bestId){

                pmaster++;
                bestId = id;

            }

        }break;

        default:

        break;

    }

    // setColor(BLUE);

    return 1;
}

void DiffusionID(PRef except, int id){

    byte msg[17];
    msg[0] = DIFFUSION_ID;
    msg[1] = id;

    for (int x = 0; x < NUM_PORTS; ++x)
    {

        if(thisNeighborhood.n[x] != VACANT && thisNeighborhood.n[x] != except){

            Chunk* cChunk = getSystemTXChunk();

            if(sendMessageToPort(cChunk, x, msg, 2, DiffusionHandler, NULL) == 0){

                freeChunk(cChunk);

            }

            nbOfAnswer++;

        }
    }
}

void test(void){

        setColor(BLUE);

}

void myMain(void)
{

    delayMS(200);

    for(byte x=0; x < MYCHUNKS; x++) {
        myChunks[x].status = CHUNK_FREE;
    }

    bool lockm = 0;
    bestId = getGUID();

    //0 : BAS

    while(1){

        if(lockm == 0){
            
            Answer.callback = (GenericHandler)(&test);
            Answer.calltime = getTime() + 3000; 
            registerTimeout(&Answer);

            DiffusionID(NULL,bestId);
            lockm = 1;

        }

    }

}
void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}
