#include <util/atomic.h>
#include <avr/io.h>
#include <avr/interrupt.h>
#include "../hw-api/hwTime.h"
#include "../system/hardwaretime.h"

#define PRECISE_RTC

uint16_t timeHi;			// semi-private data, do not modify outside of this file

// Time in ms
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

void initHWClock(void)
{
	OSC.CTRL |= _BV(OSC_RC32MEN_bp);			// turn on 32MHz internal RC oscillator
	while(!(OSC.STATUS & OSC_RC32MRDY_bm));  	// wait for it to be ready

	CCP=0xD8;							// allow modification of protected register
	CLK.CTRL = CLK_SCLKSEL_RC32M_gc;	// change from 2MHz to 32MHz
}

#ifndef PRECISE_RTC
void initHWRTC(void)
{
	RTC.CTRL = RTC_PRESCALER_DIV1_gc;
	CLK.RTCCTRL = CLK_RTCSRC_ULP_gc | CLK_RTCEN_bm; 

	RTC.INTCTRL = RTC_OVFINTLVL_HI_gc;
	// initalise the RTC as zero
	RTC.CNT = 0;
}
#else
void initHWRTC(void)
{
	//OSC_RC32KCAL = 80;
	OSC.CTRL |= OSC_RC32KEN_bm;
	do {/* Wait for the 32kHz oscillator to stabilize. */} while ( ( OSC.STATUS & OSC_RC32KRDY_bm ) == 0); 
	RTC.CTRL = RTC_PRESCALER_DIV1_gc;
	CLK.RTCCTRL = CLK_RTCSRC_RCOSC_gc | CLK_RTCEN_bm;
	//RTC.INTCTRL = RTC_OVFINTLVL_LO_gc;
	RTC.INTCTRL = RTC_OVFINTLVL_HI_gc;
	// initalise the RTC as zero
	RTC.CNT = 0;
}
#endif

/*
void initClock(void)
{
	OSC.CTRL |= _BV(OSC_RC32KEN_bp);			// turn on 32MHz internal RC oscillator
	while(!(OSC.STATUS & OSC_RC32KRDY_bm));  	// wait for it to be ready

	CCP=0xD8;							// allow modification of protected register
	CLK.CTRL = CLK_SCLKSEL_RC32K_gc;	// change from 2MHz to 32MHz
}


void initRTC(void)
{
	RTC.CTRL = RTC_PRESCALER_DIV1_gc;
	//CLK.RTCCTRL = CLK_RTCSRC_ULP_gc | CLK_RTCEN_bm; 
	CLK.RTCCTRL = CLK_RTCSRC_RCOSC_gc | CLK_RTCEN_bm;
	
	RTC.INTCTRL = RTC_OVFINTLVL_HI_gc;
}
*/

/* src: http://hardware-ntp.googlecode.com/svn-../branches/xmega-test/pcb-1/rtc.c */
/*
void initClock(void)
{
	OSC.CTRL |= OSC_RC32KEN_bm;			// turn on 32MHz internal RC oscillator
	while(!(OSC.STATUS & OSC_RC32KRDY_bm));  	// wait for it to be ready

	CCP=0xD8;							// allow modification of protected register
	CLK.CTRL = CLK_SCLKSEL_RC32K_gc;	// change from 2MHz to 32MHz
}


void initRTC(void)
{
	CLK.RTCCTRL = (CLK.RTCCTRL & ~(CLK_RTCSRC_gm)) | CLK_RTCSRC_RCOSC_gc;
	CLK.RTCCTRL |= CLK_RTCEN_bm;
	while (RTC.STATUS & RTC_SYNCBUSY_bm);
	// initalise the RTC as zero
	RTC.CNT = 0;
	while (RTC.STATUS & RTC_SYNCBUSY_bm);
	// set period to 1023, since internal 32kHz RC osc is divided down to 1024Hz
	RTC.PER = 1023;
	// start clock running, 1:1 prescaler
	RTC.CTRL = RTC_PRESCALER_DIV1_gc;
	
	// select low-level interrupt for overflows. It's only the 1Hz tick for the main loop, so low prio
	RTC.INTCTRL = (RTC.INTCTRL & ~(RTC_OVFINTLVL_gm)) | RTC_OVFINTLVL_LO_gc;
} */


void initHWTime()
{
	initHWClock();
	initHWRTC();
}

ISR(RTC_OVF_vect)
{
	timeHi++;
}
