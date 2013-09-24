# 1 "arch-blocks/system/neighbor_count.bb"
#include <stdio.h>
#include "block.h"


void myMain(void)
{
  int numNeighbors;

  setColor(WHITE);

  while(1)
  {
    numNeighbors = getNeighborCount();

    if( numNeighbors == 1 ) {
      setColor(RED);
    }
    else if( numNeighbors == 2 ) {
      setColor(ORANGE);
    }
    else if( numNeighbors == 3 ) {
      setColor(YELLOW);
    }
    else if( numNeighbors == 4 ) {
      setColor(GREEN);
    }
    else if( numNeighbors == 5 ) {
      setColor(BLUE);
    }
    else if( numNeighbors == 6 ) {
      setColor(PURPLE);
    }
    else {
      setColor(WHITE);
    }
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
	//registerHandler(EVENT_ACCEL_TAP, (GenericHandler)&blocktap);
}
