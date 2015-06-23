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

#define DISTANCE_ID 2
#define ARE_YOU_CONNECTED_ID 3
#define I_AM_CONNECTED_ID 4
#define REACH_MASTER_ID 5
#define WHAT_IS_YOUR_DISTANCE_ID 6
#define MY_DISTANCE_IS_ID 7

#define ROUTINE_CONNEXION_MS 250
#define ROUTINE_OPTIMIZATION_MS 2000

threadvar bool lock;
threadvar bool routine;
threadvar bool reachmaster;
threadvar int ownDistance;

threadvar PRef toMaster;
threadvar PRef Connected[6];

threadvar Timeout RoutineConnexionTime;
threadvar Timeout RoutineOptimizationTime;
threadvar Timeout RoutineDeconnexionTime;

void GetConnected(){

    for (int i = 0; i < NUM_PORTS; ++i)
    {

        if(thisNeighborhood.n[i] == VACANT){

            Connected[i] = 0;

        }else{

            Connected[i] = 1;

        }

        delayMS(getGUID());
        // if big structure with big ID, use a modulo

    }

}

byte SimpleHandler(void){

    if(thisChunk == NULL){

        return 0;

    }

    switch(thisChunk->data[0]){

        case ARE_YOU_CONNECTED_ID:{

            if(ownDistance == 0){

                SendSimpleMessage(I_AM_CONNECTED_ID,faceNum(thisChunk));

            }

        }break;

        case I_AM_CONNECTED_ID:{

            DiffusionDistance(ownDistance+1,toMaster);
            GetConnected();

        }break;

        case REACH_MASTER_ID:{

            if(getGUID() != 1){

                SendSimpleMessage(REACH_MASTER_ID,toMaster);
                setColor(GREEN);

            }else{

                printf("I've received the message\n");

            }

        }break;

        case WHAT_IS_YOUR_DISTANCE_ID:{

            // printf("Hey buddy, what's your ? Your friend : %d \n",getGUID());
            sendMyDistance(faceNum(thisChunk),ownDistance+1);

        }break;

        case MY_DISTANCE_IS_ID:{

            printf("Recv otpi\n");

            int recvDistance;
            recvDistance = (int)(thisChunk->data[2]) & 0xFF;
            recvDistance |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;

            if(recvDistance < ownDistance){

                printf("I'm %d, my previous : %d, now %d !! Previous d %d, now : %d\n",getGUID(),toMaster,faceNum(thisChunk),ownDistance,recvDistance-1);

                toMaster = faceNum(thisChunk);
                ownDistance = recvDistance-1;

            }

        }break;

        default:

        break;

    }

    freeChunk(thisChunk);
    return 1;

}

void sendMyDistance(PRef p, int sendDistance){

    byte msg[17];
    msg[0] = MY_DISTANCE_IS_ID;

    msg[1] = (byte) ((sendDistance >> 8) & 0xFF);
    msg[2] = (byte) (sendDistance & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    if(sendMessageToPort(cChunk, p, msg, 4, SimpleHandler, NULL) == 0){

        freeChunk(cChunk);

    }

}

void SendSimpleMessage(int MSG_ID, PRef p){

    byte msg[17];
    msg[0] = MSG_ID;

    Chunk* cChunk = getSystemTXChunk();

    if(sendMessageToPort(cChunk, p, msg, 1, SimpleHandler, NULL) == 0){

        freeChunk(cChunk);

    }

}

void RoutineConnexion(void){

    // if(routine == 0){

    //     setColor(ORANGE);
    //     routine = 1;

    // }else{

    //     setColor(YELLOW);
    //     routine = 0;

    // }

    if(getGUID() >= 36 && !reachmaster){

        SendSimpleMessage(REACH_MASTER_ID, toMaster);
        setColor(BLUE);
        reachmaster = 1;

    }

    for (int i = 0; i < NUM_PORTS; ++i)
    {

        if(Connected[i] == 0){

            if(thisNeighborhood.n[i] != VACANT){

                SendSimpleMessage(ARE_YOU_CONNECTED_ID, i);

            }

        }

    }

    RoutineConnexionTime.calltime = getTime() + ROUTINE_CONNEXION_MS + getGUID();
    registerTimeout(&RoutineConnexionTime);

}

void RoutineOptimization(void){

    if(getGUID() != 1){

        GetConnected();

        for (int i = 0; i < NUM_PORTS; ++i){

            if(Connected[i] == 1){

                SendSimpleMessage(WHAT_IS_YOUR_DISTANCE_ID, i);

            }

        }

        RoutineOptimizationTime.calltime = getTime() + ROUTINE_OPTIMIZATION_MS + getGUID();
        registerTimeout(&RoutineOptimizationTime);

    }

}

byte DiffusionDistanceHandler(){

    if(thisChunk == NULL){
        return 0;
    }

    if(thisChunk->data[0] == DISTANCE_ID && !lock){

        lock = 1;
        setColor(YELLOW);

        int recvDistance;
        recvDistance = (int)(thisChunk->data[2]) & 0xFF;
        recvDistance |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;

        ownDistance = recvDistance;
        toMaster = faceNum(thisChunk);

        DiffusionDistance(ownDistance+1,toMaster);

        GetConnected();

        RoutineConnexionTime.callback = (GenericHandler)(&RoutineConnexion);
        RoutineConnexionTime.calltime = getTime() + ROUTINE_CONNEXION_MS*2 + getGUID();
        registerTimeout(&RoutineConnexionTime);


        RoutineOptimizationTime.callback = (GenericHandler)(&RoutineOptimization);
        RoutineOptimizationTime.calltime = getTime() + ROUTINE_OPTIMIZATION_MS*2 + getGUID();
        registerTimeout(&RoutineOptimizationTime);




    }

    return 1;
    freeChunk(thisChunk);

}

void DiffusionDistance(int sendDistance, PRef except){

    byte msg[17];
    msg[0] = DISTANCE_ID;

    msg[1] = (byte) ((sendDistance >> 8) & 0xFF);
    msg[2] = (byte) (sendDistance & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    for (int x = 0; x < NUM_PORTS; ++x){

        if(x != except){

            if(sendMessageToPort(cChunk, x, msg, 4, DiffusionDistanceHandler, NULL) == 0){

                freeChunk(cChunk);

            }

            delayMS(getGUID());

        }

    }

}

void myMain(void)
{

    delayMS(2000);

    if(getGUID() == 1){

        setColor(BLUE);
        ownDistance = 0;
        DiffusionDistance(ownDistance+1,6);

    }

}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}
