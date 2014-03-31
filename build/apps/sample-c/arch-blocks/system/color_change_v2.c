# 1 "arch-blocks/system/color_change_v2.bb"
#include "handler.h"
#include "block.h"
#include "led.h"
#include "ensemble.h"
#include "clock.h"

#include "block_config.h"
#include "memory.h"
//#include "hw-api/hwMemory.h"

#ifdef LOG_DEBUG
#include "log.h"
#endif

#define PERIOD (3000)

// enum{RED, ORANGE, YELLOW, GREEN, AQUA, BLUE, WHITE, PURPLE, PINK, NUM_COLORS};

Color nextColor() {
	Color c = 0;
	c = (getClock()/PERIOD)%NUM_COLORS;
	if ((c == AQUA) || (c == WHITE))
	{
		c = ORANGE;
	}
	return c;
}

void myMain(void)
{
	Time changeT = PERIOD;

	while(getNeighborCount() == 0)
	{
		setColor(WHITE);
	}

	while (!isSynchronized())
	{
		setColor(AQUA);
		delayMS(6);
	}

	setColor(nextColor());
	changeT = (getClock()/PERIOD)*PERIOD + PERIOD;

	while (1) {
		while(getClock() < changeT)
		{
			delayMS(1);
		}
		/*#ifdef LOG_DEBUG
			char s[150];
			snprintf(s, 150*sizeof(char), "color change: t: %lu, c: %lu", getTime(), getClock());
			s[149] = '\0';
			printDebug(s);
		#endif*/
		//printf("clock %u\n", getClock());
		if (isTimeLeader()) {
			setColor(RED);
		} else {
			setColor(nextColor());
		}
		delayMS(1);
		changeT = (getClock()/PERIOD)*PERIOD + PERIOD;
		//changeT += PERIOD;
	}
	//printf("Color Change Clock %u\n", getClock());

	while(1);
}
void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
