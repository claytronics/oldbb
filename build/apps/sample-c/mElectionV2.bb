//Author : vincent.connat@gmail.com

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

#define DIFFUSION_ID    2;

threadvar int bestId;
threadvar byte pmaster;
threadvar byte answer;
threadvar byte nei;

void CheckAnswer(){

    if(nei == 0){

        nei = getNeighborCount();

    }

    if(nei == answer){

        printf("%d is ok with %d neighbors.\n",getGUID(),nei);

    }else{

        // printf("%d is not ok with %d neighbors.\n",getGUID(),nei);

    }

}

byte DiffusionIDHandler(){

    if(thisChunk->data[0]){

        bestId = getGUID();

        int id;
        id = (int)(thisChunk->data[2]) & 0xFF;
        id |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;

        if(id <= bestId){

            pmaster++;

        }else{

        }

        answer++;
        CheckAnswer();

    }


}

void DiffusionID(int id){

    byte msg[17];
    msg[0] = DIFFUSION_ID;

    msg[1] = (byte) ((id >> 8) & 0xFF);
    msg[2] = (byte) (id & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    for (int x = 0; x < NUM_PORTS; ++x){

        if(sendMessageToPort(cChunk, x, msg, 4, DiffusionIDHandler, NULL) == 0){

            freeChunk(cChunk);

        }

    }

}

void myMain(void)
{

    DiffusionID(getGUID());

    while(1){}

}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}