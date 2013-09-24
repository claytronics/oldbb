#include "block.bbh"
#include "../hw-block/ext_memory.h"
#include <avr/io.h>

threadvar Timer tt;

void fade(void)
{
	static int i = 255;
	static int countup;
	
	if(i <= 255)
	{
		setIntensity(i);
	}
	
	if(countup)
	{
		i+= 5;
	}
	else
	{
		i-= 5;
	}
	
	if(i <= 0)
	{
		i = 0;

		countup = 1;
	}
	
	if(i > 255)
	{
		i = 255;
		countup = 0;
	}
}

#define BLINKYDELAY 50

void checkLEDs(void)
{
	setIntensity(255);
	
	
// try just one LED
	PORTC.DIRCLR = PIN1_bm | PIN5_bm;
	PORTD.DIRCLR = PIN0_bm;
	
	setColor(RED);
	delayMS(BLINKYDELAY);
	setColor(GREEN);
	delayMS(BLINKYDELAY);
	setColor(BLUE);
	delayMS(BLINKYDELAY);

// now try just the other one
	PORTC.DIRCLR = PIN0_bm | PIN4_bm;
	PORTC.DIRCLR = PIN1_bm;
	
	PORTC.DIRSET = PIN1_bm | PIN5_bm;
	PORTD.DIRSET = PIN0_bm;
		
	setColor(RED);
	delayMS(BLINKYDELAY);
	setColor(GREEN);
	delayMS(BLINKYDELAY);
	setColor(BLUE);
	delayMS(BLINKYDELAY);
	
// now both
	PORTC.DIRSET = PIN0_bm | PIN4_bm;
	PORTC.DIRSET = PIN0_bm;

	setColor(RED);
	delayMS(BLINKYDELAY);
	setColor(GREEN);
	delayMS(BLINKYDELAY);
	setColor(BLUE);
	delayMS(BLINKYDELAY);

}


void tap(void)
{
	static Time lastTap;
	
	if(lastTap == 0)
	{
		lastTap = 1;
		

	}
	else if(getTime() - lastTap > 500)
	{
		if(lastTap == 1)
		{
			checkLEDs();
		}

		lastTap = getTime();

		setNextColor();
	}
}

void checkEXTMEM(void)
{
	char data[4];

	initExtMem();

	data[0] = 0x37;
	data[1] = 0x22;

	writeExtMem(SRAM, 0, data, 1);
	writeExtMem(FLASH,0, data+1, 1);

	data[0] = 0x00;
	data[1] = 0x00;

	readExtMem(SRAM, 0, data, 1);
	readExtMem(FLASH, 0, data+1, 1);

	if(data[0] != 0x37 || data[1] != 0x22)
	{
		setColor(RED);
		tt.period = 2;
	}
	else
	{
		setColor(PINK);
	}

}

void myMain(void)
{
	static Time i;


	if(i == 0)
	{
		//delayMS(BLINKYDELAY);
	
		checkEXTMEM();

		//delayMS(BLINKYDELAY);

		// check audio
		//chirp(40000,1);
	
		// now check accelerometer
		registerHandler(EVENT_ACCEL_TAP, (GenericHandler)&tap);
		//tt.t.callback = (GenericHandler)&fade;
		//tt.period = 50;

		//registerTimer(&tt);
		
		i+= 1000;
	}

	// now check communications on all ports via neighbor count
	if(i < getTime())
	{
		//int numNeighbors = getNeighborCount();
		i += 1000;
		
		// an end
	    if( thisNeighborhood.n[DOWN] != VACANT ) { 
		    setColor(RED);
		}
		else if( thisNeighborhood.n[NORTH] != VACANT  ) { 
		    setColor(ORANGE);
		}
		else if( thisNeighborhood.n[EAST] != VACANT  ) { 
		    setColor(YELLOW);
		}
		else if( thisNeighborhood.n[WEST] != VACANT ) { 
		    setColor(GREEN);
		}
		else if( thisNeighborhood.n[SOUTH] != VACANT  ) { 
		    setColor(BLUE);
		}
		else if( thisNeighborhood.n[UP] != VACANT ) { 
		    setColor(PURPLE);
		}
		else {
			setColor(WHITE);
		}
		
	}
}

void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);


}
