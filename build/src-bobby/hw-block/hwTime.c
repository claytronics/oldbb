#include <util/atomic.h>
#include <avr/io.h>
#include <avr/interrupt.h>
#include "../hw-api/hwTime.h"
#include "../system/hardwaretime.h"

uint16_t timeHi;			// semi-private data, do not modify outside of this file

Time getHWTime()
{
	Time tmp;
	// prevents ISRs from corrupting multibyte write
	ATOMIC_BLOCK(ATOMIC_FORCEON)
    {
		tmp = (((Time)timeHi) << 16) | RTC.CNT;
	}
	
	return tmp;
}

void initClock(void)
{
	OSC.CTRL |= _BV(OSC_RC32MEN_bp);			// turn on 32MHz internal RC oscillator
	while(!(OSC.STATUS & OSC_RC32MRDY_bm));  	// wait for it to be ready

	CCP=0xD8;							// allow modification of protected register
	CLK.CTRL = CLK_SCLKSEL_RC32M_gc;	// change from 2MHz to 32MHz
}

void initRTC(void)
{
	RTC.CTRL = RTC_PRESCALER_DIV1_gc;
	CLK.RTCCTRL = CLK_RTCSRC_ULP_gc | CLK_RTCEN_bm; 

	RTC.INTCTRL = RTC_OVFINTLVL_HI_gc;
}

void initHWTime()
{
	initClock();
	initRTC();
}

ISR(RTC_OVF_vect)
{
	timeHi++;
}
