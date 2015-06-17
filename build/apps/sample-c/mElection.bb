//Author : Vincent

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

#define DIFFUSE_ID  2
#define ACK_ID  3
#define NACK_ID  4

threadvar int bestId;
threadvar bool pmaster;
threadvar byte NbOfAnswer = 0;
threadvar PRef summon;
threadvar bool dsend[6];

Timeout answerCheck;

void checkAnswer(){

    if(NbOfAnswer == 0){

        if(pmaster == 1){

            printf("%d is ready !\n",getGUID());
            setColor(GREEN);

        }else{

            setColor(RED);

        }

    }

}

byte NAckHandler(void){


    if(thisChunk->data[0] == NACK_ID){

        int id;
        id = (int)(thisChunk->data[2]) & 0xFF;
        id |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;

        printf("%d received NACK from %d !\n",getGUID(),id);

        pmaster = 0;
        NbOfAnswer--;
        dsend[faceNum(thisChunk)] = 1;
        checkAnswer();

    }


}

byte AckHandler(void){


    if(thisChunk->data[0] == ACK_ID){

        int id;
        id = (int)(thisChunk->data[2]) & 0xFF;
        id |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;
        printf("%d received ACK from %d !\n",getGUID(),id);

        NbOfAnswer--;
        dsend[faceNum(thisChunk)] = 1;
        checkAnswer();

    }


}

void SendNAck(PRef p, int id){

    byte msg[17];
    msg[0] = NACK_ID;

    msg[1] = (byte) ((id >> 8) & 0xFF);
    msg[2] = (byte) (id & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    if(sendMessageToPort(cChunk, p, msg, 3, NAckHandler, NULL) == 0){

        freeChunk(cChunk);

    }

}

void SendAck(PRef p, int id){

    byte msg[17];
    msg[0] = ACK_ID;

    msg[1] = (byte) ((id >> 8) & 0xFF);
    msg[2] = (byte) (id & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    if(sendMessageToPort(cChunk, p, msg, 3, AckHandler, NULL) == 0){

        freeChunk(cChunk);

    }

}

byte DiffusionHandler(void){

    if(thisChunk == NULL){return 0;}

    if(thisChunk->data[0] == DIFFUSE_ID){

        int id;
        id = (int)(thisChunk->data[2]) & 0xFF;
        id |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;

        if(id < bestId){

            bestId = id;
            summon = faceNum(thisChunk);

            SendAck(summon,getGUID());

        }

        if(id > bestId){

            SendNAck(faceNum(thisChunk),getGUID());

        }

    }

}

void DiffusionID(PRef except, int id){

    byte msg[17];
    msg[0] = DIFFUSE_ID;

    msg[1] = (byte) ((id >> 8) & 0xFF);
    msg[2] = (byte) (id & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    for (int x = 0; x < NUM_PORTS; ++x){

        if(dsend[x] == 0){

            if(sendMessageToPort(cChunk, x, msg, 3, DiffusionHandler, NULL) == 0){

                freeChunk(cChunk);

            }

        }


    }

}

void TAnswer(void){

    if(NbOfAnswer != 0){
        DiffusionID(NULL,bestId);
    }

    printf("Timeout %d\n",getGUID());

    answerCheck.calltime = getTime() + 300;
    registerTimeout(&answerCheck);

}

void myMain(void)
{

    delayMS(100);
    
    bestId = getGUID();
    NbOfAnswer = getNeighborCount();

    printf("%d got %d neighbor\n",getGUID(),NbOfAnswer);

    pmaster = 1;

    for (int i = 0; i < 6; ++i){dsend[i] = 0;}

    delayMS(200);

    answerCheck.callback = (GenericHandler)(&TAnswer);
    answerCheck.calltime = getTime() + 300;

    registerTimeout(&answerCheck);

    DiffusionID(NULL,bestId);

    while(1){}

}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}