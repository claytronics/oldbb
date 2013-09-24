#include <avr/io.h>
#include <avr/interrupt.h>
#include "../system/defs.h"
#include "../system/block.h"
#include <avr/wdt.h>
#include "../hw-api/hwBlockTick.h"

extern int blockTickRunning;

void scaryBlockTickHack(void)
{
	TCF0.CTRLA = TC_CLKSEL_DIV1_gc;
	
	TCF0.PER = 16000;  // 2000hz blocktick
	TCF0.INTCTRLA = TC_OVFINTLVL_MED_gc;
}


void initBlockTick()
{

	blockTickRunning=0;
     
	scaryBlockTickHack();

	/* Enable interrupt levels. */
	PMIC.CTRL |= PMIC_HILVLEN_bm | PMIC_MEDLVLEN_bm | PMIC_LOLVLEN_bm;
	sei();
	
}

// part of scary blocktick hack
// called every 38400 khz or so
ISR(TCF0_OVF_vect)
{
	if (!blockTickRunning)
	  {
	    wdt_reset();
	    blockTick();
	  }
}
