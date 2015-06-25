//Author : vincent.connat@gmail.com

/*

Descritpion : 

    - create a path to the master, is this case : the block number 1
    - maintaining an access to the master
    - optimize the path to the master

need to fix : 

    - need to fix after a deconnexion

*/

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

// Messages ID

#define DISTANCE_ID 2
#define ARE_YOU_CONNECTED_ID 3
#define I_AM_CONNECTED_ID 4
#define REACH_MASTER_ID 5
#define WHAT_IS_YOUR_DISTANCE_ID 6
#define MY_DISTANCE_IS_ID 7

//Timer for the routine

#define ROUTINE_CONNEXION_MS 50
#define ROUTINE_OPTIMIZATION_MS 1000
#define ROUTINE_DECONNEXION_MS 250

threadvar bool lock;
threadvar bool routine;
threadvar bool reachmaster;
threadvar int ownDistance;

// The path to the master
threadvar PRef toMaster;
threadvar PRef Connected[6];

//Routine timeout declaration

threadvar Timeout RoutineConnexionTime;
threadvar Timeout RoutineOptimizationTime;
threadvar Timeout RoutineDeconnexionTime;

void
freeMyChunk(){
//free the chunk

    freeChunk(thisChunk);

}

void
GetConnected(){
//put state of face in the Connected array

    for (int i = 0; i < NUM_PORTS; ++i)
    {

        if(thisNeighborhood.n[i] == VACANT)
        {

            Connected[i] = 0;

        }
        else
        {

            Connected[i] = 1;

        }

        delayMS(getGUID());
        // if big structure with big ID, use a modulo

    }

}

byte
SimpleHandler(void){
//very simple handler

    if(thisChunk == NULL){

        return 0;

    }

    switch(thisChunk->data[0])
    {

        case ARE_YOU_CONNECTED_ID:
        {

            //if i've received a are you connected and not appear to the network
            if(ownDistance == 0){

                SendSimpleMessage(I_AM_CONNECTED_ID, faceNum(thisChunk));

            }

        }
        break;

        case I_AM_CONNECTED_ID:
        {

            //if the new block answer, send him a distance and an "interface" to reach the master
            if(lock && ownDistance != 0 && faceNum(thisChunk) != toMaster){

                DiffusionDistance(ownDistance+1,toMaster);
                GetConnected();

            }

        }
        break;

        case REACH_MASTER_ID:
        {

            // It's just a exemple of how to use the toMaster variable

            if(getGUID() != 1)
            {

                SendSimpleMessage(REACH_MASTER_ID,toMaster);
                setColor(GREEN);

            }
            else
            {

                printf("The master received the message\n");

            }

        }
        break;

        case WHAT_IS_YOUR_DISTANCE_ID:
        {

            //the block can reach the master, he send his distance

            if(lock && ownDistance != 0 && faceNum(thisChunk) != toMaster){

                sendMyDistance(faceNum(thisChunk),ownDistance+1);

            }

        }
        break;

        case MY_DISTANCE_IS_ID:
        {

            int recvDistance;
            recvDistance = (int)(thisChunk->data[2]) & 0xFF;
            recvDistance |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;

            //if ownDistance is 0 it mean we are disconnected from toMaster or not currently connected to

            if(recvDistance < ownDistance || ownDistance == 0)
            {
                printf("I'm %d, my previous : %d, now %d !! Previous dis %d, now : %d\n",getGUID(),toMaster,faceNum(thisChunk),ownDistance,recvDistance-1);

                toMaster = faceNum(thisChunk);

                setColor(ORANGE);
                ownDistance = recvDistance;

            }

        }
        break;

        default:

        break;

    }

    freeChunk(thisChunk);
    return 1;

}

void
sendMyDistance(PRef p, int sendDistance){
// Send ownDistance to p

    byte msg[17];
    msg[0] = MY_DISTANCE_IS_ID;

    msg[1] = (byte) ((sendDistance >> 8) & 0xFF);
    msg[2] = (byte) (sendDistance & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    if(sendMessageToPort(cChunk, p, msg, 4, SimpleHandler, (GenericHandler)&freeMyChunk) == 0)
    {

        freeChunk(cChunk);

    }

}

void
SendSimpleMessage(int msg_id, PRef p){
// Just a simple sender of messages

    byte msg[17];
    msg[0] = msg_id;

    Chunk* cChunk = getSystemTXChunk();

    if(sendMessageToPort(cChunk, p, msg, 1, SimpleHandler, (GenericHandler)&freeMyChunk) == 0)
    {

        freeChunk(cChunk);

    }

}

void
RoutineConnexion(void){
//Send a message when see a connexion in empty face via VACANT


    if(getGUID() > 36 && !reachmaster)
    {

        //This is just to see the path to the master

        SendSimpleMessage(REACH_MASTER_ID, toMaster);
        setColor(BLUE);
        reachmaster = 1;

    }

    for (int i = 0; i < NUM_PORTS; ++i)
    {

        if(Connected[i] == 0)
        {

            if(thisNeighborhood.n[i] != VACANT)
            {
                //if a neighbor is now not vacant, ask for answer
                SendSimpleMessage(ARE_YOU_CONNECTED_ID, i);

            }

        }

    }

    RoutineConnexionTime.calltime = getTime() + ROUTINE_CONNEXION_MS + getGUID();
    registerTimeout(&RoutineConnexionTime);

}

void
RoutineOptimization(void){

//Routine executed every ROUTINE_OPTIMIZATION_MS

    if(getGUID() != 1)
    {

        GetConnected();

        //Ask every blocks connected for their owndistance

        for (int i = 0; i < NUM_PORTS; ++i)
        {

            if(Connected[i] == 1)
            {

                SendSimpleMessage(WHAT_IS_YOUR_DISTANCE_ID, i);

            }

        }

        RoutineOptimizationTime.calltime = getTime() + ROUTINE_OPTIMIZATION_MS + getGUID();
        registerTimeout(&RoutineOptimizationTime);

    }

}

void
RoutineDeconnexion(void){

 //Made the inverse of the connexion routine, and force an otpimization to find a new path to the master

    if(getGUID() != 1)
    {

        if(thisNeighborhood.n[toMaster] == VACANT){

            ownDistance = 0;
            lock = 0;

            // We can force the Routine Optimization for a better quality of service
            // RoutineOptimization();

        }

    }

    RoutineDeconnexionTime.calltime = getTime() + ROUTINE_DECONNEXION_MS + getGUID();
    registerTimeout(&RoutineDeconnexionTime);

}

byte DiffusionDistanceHandler(){

    if(thisChunk == NULL){
        return 0;
    }

    if(thisChunk->data[0] == DISTANCE_ID && !lock)
    {

        lock = 1;
        setColor(YELLOW);

        int recvDistance;
        recvDistance = (int)(thisChunk->data[2]) & 0xFF;
        recvDistance |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;

        ownDistance = recvDistance;
        //toMaster is our "path to the master"
        toMaster = faceNum(thisChunk);

        DiffusionDistance(ownDistance+1,toMaster);

        printf("%d distance is %d\n",getGUID(),ownDistance);

        //Load the Connected array
        GetConnected();

        //Start timeout for every routine

        RoutineConnexionTime.callback = (GenericHandler)(&RoutineConnexion);
        //doubling the timing to be sure the neighbors are well initialized
        RoutineConnexionTime.calltime = getTime() + ROUTINE_CONNEXION_MS*2 + getGUID();
        registerTimeout(&RoutineConnexionTime);

        RoutineOptimizationTime.callback = (GenericHandler)(&RoutineOptimization);
        RoutineOptimizationTime.calltime = getTime() + ROUTINE_OPTIMIZATION_MS*2 + getGUID();
        registerTimeout(&RoutineOptimizationTime);

        RoutineDeconnexionTime.callback = (GenericHandler)(&RoutineDeconnexion);
        RoutineDeconnexionTime.calltime = getTime() + ROUTINE_DECONNEXION_MS * 5 + getGUID();
        registerTimeout(&RoutineDeconnexionTime);
        
    }

    return 1;
    freeChunk(thisChunk);

}

void
DiffusionDistance(int sendDistance, PRef except){

    // Send distance to every neighbors except, the "except"

    byte msg[17];
    msg[0] = DISTANCE_ID;

    msg[1] = (byte) ((sendDistance >> 8) & 0xFF);
    msg[2] = (byte) (sendDistance & 0xFF);

    Chunk* cChunk = getSystemTXChunk();

    for (int x = 0; x < NUM_PORTS; ++x)
    {

        if(x != except)
        {

            if(sendMessageToPort(cChunk, x, msg, 4, DiffusionDistanceHandler, NULL) == 0)
            {

                freeChunk(cChunk);

            }

            delayMS(getGUID());

        }

    }

}

void
myMain(void)
{

    delayMS(2000);


    //The master is the block number 1
    if(getGUID() == 1)
    {

        setColor(BLUE);
        ownDistance = 0;
        DiffusionDistance(ownDistance+1,6);

    }

}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}
