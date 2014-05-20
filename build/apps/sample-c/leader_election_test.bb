#include "handler.bbh"
#include "block.bbh"
#include "led.bbh"
#include "ensemble.bbh"
#include "clock.bbh"

#include "block_config.bbh"
#include "memory.bbh"
//#include "hw-api/hwMemory.h"

#ifdef LOG_DEBUG
#include "log.bbh"
#endif

void myMain(void)
{
	while (1) {
		if (isTimeLeader()) {
			setColor(RED);
		}
		delayMS(1);
	}
}
void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}
