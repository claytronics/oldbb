#include <stdio.h>
#include "block.bbh"


void myMain(void)
{
  int numNeighbors;

      setColor(GREEN);

  while(1)
  {
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
