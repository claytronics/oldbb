#include "block.bbh"
#include "block_config.bbh"
#include "memory.bbh"
#include "audio.bbh"
#include <stdlib.h>
#include "span.bbh"
#include "coordinate.bbh"

void launchTimeout(void);
void handler(void);
Timeout tout;

void myMain(void)
{
  initCoordination(2,handler);
  tout.callback = (GenericHandler)(&launchTimeout);
  tout.calltime = getTime() + 2000;
  registerTimeout(&tout);
  while(1);
}

void launchTimeout(void)
{
  if( getGUID() == 7)
  {
    setColor(PINK);
    byte p;
    for( p = 0; p < NUM_PORTS ; p++)
    {
    if ( thisNeighborhood.n[p] == VACANT)
    {
      if(checkVirtualNeighbor(p) == 1) 
      {
	byte buf[1];
	buf[0] = 2;
	sendDataToVirtualNeighbor( p ,buf,1);
      }
    }
    }
  }
}


void handler(void)
{
  byte buf[1];
  memcpy(buf, thisChunk->data + 5, 1*sizeof(byte)); 
  if( buf[0] == 2)
  {
    setColor(BLUE);
  }
  else
  {
    setColor(BROWN);
  }
}

void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}