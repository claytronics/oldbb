#include <stdio.h>
#include "block.bbh"

#define BLINKLENGTH 100
threadvar Timeout tBlink;
threadvar byte readyToUpdate;

// resume previous user-defined color here.
void colorRestore()
{
  if(thisTimeout->arg < NUM_COLORS)
    {
      setColor(thisTimeout->arg);
    }

  readyToUpdate++;
}

void neighborChangeDetect()
{
  static Neighborhood oldNeighborHood;
  byte i, lost, new;

  // scan to determine what has changed in the neighborhood
  for(i = 0, lost = 0, new = 0; i < NUM_PORTS; ++i)
    {
      if(oldNeighborHood.n[i] != thisNeighborhood.n[i])
	{
	  // is it vacant now but didn't used to be?
	  if(thisNeighborhood.n[i] == VACANT)
	    {
	      lost++;
	    }
	  // new neighbor added
	  else
	    {
	      new++;
	    }
	}
    }

  // did anything change?  if so, blink to alert user
  if(new || lost)
    {
      // are we currently already blinking a neighbor change?
      if(tBlink.state == ACTIVE)
	// if so, then deregister timer so we can reset timing.
	{
	
	  deregisterTimeout(&tBlink);
	}
      else
	// otherwise, save the last user set color!
	{
	  tBlink.arg = getColor();
	}

      if(new >= lost)
	// green for happy, gaining neighbors
	{
	  setColor(GREEN);
	}
      else
	// red for sad, losing blocks
	{
	  setColor(RED);
	}

      tBlink.calltime = getTime() + BLINKLENGTH;

      registerTimeout(&tBlink);
    }

  // save current neighborhood state for next time
  oldNeighborHood = thisNeighborhood; 
}

void myMain(void)
{
  int numNeighbors;

  setColor(WHITE);

  while(1)
  {
    if(readyToUpdate)
      {
	numNeighbors = getNeighborCount();

	if(numNeighbors)
	  {
	    setColor(getNeighborCount() - 1);
	  }
	else
	  {
	    setColor(WHITE);
	  }

	readyToUpdate--;
      }

    delayMS(50);
  }
}

int tapCount=0;
void blocktap(void){
	tapCount++;
	if(tapCount==6){
		jumpToBootSection();
	}
}


void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
	registerHandler(EVENT_NEIGHBOR_CHANGE, (GenericHandler)&neighborChangeDetect);

	// configure a timeout to blink state changes when needed
	tBlink.arg = NUM_COLORS;
	tBlink.callback = (GenericHandler)&colorRestore;
}
