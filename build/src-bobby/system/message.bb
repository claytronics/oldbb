#include "message.bbh"
#include "led.bbh"



byte sendMessageToUid(Chunk* c, Uid dest, byte * msg, byte length, MsgHandler mh, GenericHandler cb)
{
    byte i;

    for(i = 0; i < NUM_PORTS; ++i)
    {
        if(thisNeighborhood.n[i] == dest)
        {
            if(setupChunk(c,i, msg, length, mh, cb) == 0)
            {
                return 0;
            }
            queueChunk(c);
            return 1;
        }
    }

    return 0;
}

byte sendMessageToPort(Chunk* c, PRef dest, byte * msg, byte length, MsgHandler mh, GenericHandler cb)
{
    // NOTE: Can no longer support BROADCAST since requires 6 memory chunks passed in
    if(dest == BROADCAST)
    {
        return 0;
    }
    else
    {
        if(dest < NUM_PORTS)
        {
            if(setupChunk(c,dest, msg, length, mh, cb) == 0)
            {
                return 0;
            }
            queueChunk(c);
            return 1;
        }
    }
    
    return 0;
}


// ----------- SEND SYSTEM MESSAGE to PORT
//
// Probably shouldn't be used by a user, but needed by various sytem routines.
byte sendSystemMessage(PRef dest, byte * msg, byte length, MsgHandler mh, GenericHandler cb)
{
    Chunk* c;

    if(dest == BROADCAST)
    {
        byte i;
        
        for(i = 0; i < NUM_PORTS; ++i)
        {
            // set it to appropriate chunk
            c = getSystemTXChunk();
            
            // in use - can't send
            if( c == NULL ) 
            {
                continue;
            }
            
            if(setupChunk(c,i, msg, length, mh, cb) == 0)
            {
                freeChunk(c);
                continue;
            }
            queueChunk(c);
        }    
        
        return i;
    }
    else
    {
        if(dest < NUM_PORTS)
        {
            // set it to appropriate chunk
            c = getSystemTXChunk();
            
            // in use - can't send
            if( c == NULL ) 
            {
                return 0;
            }
            
            if(setupChunk(c,dest, msg, length, mh, cb) == 0)
            {
                freeChunk(c);
                return 0;
            }
            queueChunk(c);
            return 1;
        }
    }
    
    return 0;
}


void initSystemMessage()
{
  // this isn't specified but parts of the code expect the second part of this message to be a uid
  //char buf[2] = {MSG_BLOCK_STARTUP, PRESENT};
  //TODO: not enough to represent block id

}
