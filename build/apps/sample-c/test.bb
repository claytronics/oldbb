#include "handler.bbh"
#include "led.bbh"
//#include "hw-api/hwMemory.h"

void myMain(void)
{
	setColor(3);

	while(1);
}

void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
