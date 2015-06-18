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

#define DIFFUSE_ID  2
#define ACK_ID  3
#define NACK_ID  4
#define VALIDATION_ID  5

threadvar int bestId;
threadvar bool pmaster;
threadvar bool lockD;
threadvar PRef summon;
threadvar bool dsend[6];

threadvar Timeout answerCheck;
threadvar Timeout Validation;

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

void loaddsend(PRef except){

    for (int i = 0; i < NUM_PORTS; ++i)
    {

        if(except != i){

            if(thisNeighborhood.n[i] != VACANT){

                dsend[i] = 1;
                delayMS(getGUID());
                // if big structure with big ID, use a modulo

            }

        }
    }

}
byte ValidationHandler(){

    if(thisChunk == NULL){
        freeChunk(thisChunk);
        return 0;
    }

    if(thisChunk->data[0] == VALIDATION_ID){

        if(NbOfAnswer() == 0){

            //For sure i'm not a potential master, need to diffuse

            int id;
            id = (int)(thisChunk->data[2]) & 0xFF;
            id |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;

            if(id <= bestId){

                printf("%d received a validation message\n",getGUID());

                //ACK for dsend or timeout + callback on SendValidation

            }

            setColor(YELLOW);

        }

        if(NbOfAnswer() != 0 && pmaster ==1){

            //Need create a timeout callback
            //I can be a potential master

        }

    }

    return 1;
}

void SendValidationMessage(PRef except, int id){

    byte msg[17];
    msg[0] = VALIDATION_ID;

    msg[1] = (byte) ((id >> 8) & 0xFF);
    msg[2] = (byte) (id & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    //Need anoter var for the real answear, not just ACK

    for (int p = 0; p < NUM_PORTS; ++p)
    {

        if(dsend[p] == 1 && p != except){

            if(sendMessageToPort(cChunk, p, msg, 3, ValidationHandler, NULL) == 0){

                freeChunk(cChunk);

            }

        }

    }

}

void SendValidation(PRef except, int id){

    printf("%d is a potential master\n",id);
    setColor(GREEN);

    loaddsend(6);

    SendValidationMessage(6, id);

    printf("Answer : %d\n",NbOfAnswer());

}

void checkAnswer(){

    if(NbOfAnswer() == 0){

        if(pmaster == 1 && getGUID() == bestId){

            if(!lockD){

                SendValidation(6,bestId);
                lockD = 1;

            }

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

        if(id <= bestId){

            bestId = id;
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

        answerCheck.calltime = getTime() + 50 + getGUID();
        registerTimeout(&answerCheck);

    }


}

void myMain(void)
{

    delayMS(200);
    
    bestId = getGUID();
    lockD = 0;
    pmaster = 1;

    loaddsend(6);

    delayMS(450);

    answerCheck.callback = (GenericHandler)(&TAnswer);
    answerCheck.calltime = getTime() + 800 + getGUID();
    registerTimeout(&answerCheck);

    DiffusionID(6, bestId);

    while(1){}

}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}
