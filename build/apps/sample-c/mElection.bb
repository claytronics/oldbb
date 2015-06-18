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
threadvar PRef summon;
threadvar bool dsend[6];

threadvar Timeout answerCheck;

byte NbOfAnswer(){

    byte ret;

    ret = 0;

    for (int i = 0; i < 6; ++i)
    {
        if(dsend[i] == 1){
            ret++;
        }
    }

    return ret;

}

void checkAnswer(){

    if(NbOfAnswer() == 0){

        if(pmaster == 1 && getGUID() == bestId){

            printf("%d is ready !\n",getGUID());
            setColor(GREEN);

        }else{

            setColor(RED);

        }

    }

}

byte 
NAckHandler(void)
{


    if(thisChunk->data[0] == NACK_ID){

        int id;
        id = (int)(thisChunk->data[2]) & 0xFF;
        id |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;

        printf("%d received NACK from %d !\n",getGUID(),id);

        pmaster = 0;
        dsend[faceNum(thisChunk)] = 0;
        checkAnswer();

    }


}

byte 
AckHandler(void)
{


    if(thisChunk->data[0] == ACK_ID){

        int id;
        id = (int)(thisChunk->data[2]) & 0xFF;
        id |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;
        printf("%d received ACK from %d !\n",getGUID(),id);

        dsend[faceNum(thisChunk)] = 0;
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

void 
SendAck(PRef p, int id)
{

    byte msg[17];
    msg[0] = ACK_ID;

    msg[1] = (byte) ((id >> 8) & 0xFF);
    msg[2] = (byte) (id & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    if(sendMessageToPort(cChunk, p, msg, 3, AckHandler, NULL) == 0){

        freeChunk(cChunk);

    }

}

byte 
DiffusionHandler(void){

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

        if(id == bestId){

            SendAck(faceNum(thisChunk),getGUID());

        }

        if(id > bestId){

            SendNAck(faceNum(thisChunk),getGUID());

        }

    }

}

void 
DiffusionID(PRef except, int id)
{
    byte msg[17];
    msg[0] = DIFFUSE_ID;

    msg[1] = (byte) ((id >> 8) & 0xFF);
    msg[2] = (byte) (id & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    for (int x = 0; x < NUM_PORTS; ++x){

        if(dsend[x] == 1){

            if(sendMessageToPort(cChunk, x, msg, 3, DiffusionHandler, NULL) == 0){

                freeChunk(cChunk);

            }

        }


    }

}

void TAnswer(void)
{

    if(NbOfAnswer() != 0){

        DiffusionID(6, bestId);

        answerCheck.calltime = getTime() + 133 + getGUID();
        registerTimeout(&answerCheck);

        // printf("Timeut %d nb %d \n",getGUID(),NbOfAnswer);

    }


}

void myMain(void)
{

    delayMS(200);
    
    bestId = getGUID();

    pmaster = 1;

    for (int i = 0; i < 6; ++i) {

        if(thisNeighborhood.n[i] != VACANT){

            dsend[i] = 1;

        }else{

            dsend[i] = 0;

        }

        delayMS(getGUID());

    }

    printf("i got %d neighbors\n",NbOfAnswer());

    delayMS(400);

    answerCheck.callback = (GenericHandler)(&TAnswer);
    answerCheck.calltime = getTime() + 650 + getGUID();
    registerTimeout(&answerCheck);

    DiffusionID(6, bestId);

    while(1){}

}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}
