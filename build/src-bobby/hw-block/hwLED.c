#include <avr/io.h>

#include "../system/defs.h"
#include "../system/led.h"

#include "../hw-api/hwLED.h"

extern byte currentRGB[3];

void setHWLED(byte r, byte g, byte b, Intensity i)
{
	uint16_t rr, gg, bb;
	
	//r = 255 - r;
	//g = 255 - g;
	//b = 255 - b;

	currentRGB[0] = r;
	currentRGB[1] = g;
	currentRGB[2] = b;

	rr = (uint16_t)i*(uint16_t)r;
	gg = (uint16_t)i*(uint16_t)g;
	bb = (uint16_t)i*(uint16_t)b;
	
	rr = 0xFFFF - rr;
	gg = 0xFFFF - gg;
	bb = 0xFFFF - bb;
	
	TCC0.CCABUF = rr;
	TCC0.CCBBUF = rr;

	TCD0.CCABUF = gg;
	TCD0.CCBBUF = bb;
	
	TCC1.CCABUF = gg;
	TCC1.CCBBUF = bb;
}


void initHWLED()
{
	// Initialize the RED
	TCC0.CTRLA |= TC_CLKSEL_DIV4_gc;	// RED clock source
	TCC0.CTRLB = TC0_CCAEN_bm | TC0_CCBEN_bm | TC_WGMODE_SS_gc;	// enable REDs, set WGM to SS PWM
	TCC0.PERBUF = 0xFFFF;		// set period to 8-bit for 8-bit resolution
	TCC0.CCABUF = 0;		// set to off by default
	TCC0.CCBBUF = 0;		// set to off by default
	PORTC.DIRSET = PIN0_bm | PIN1_bm;  // set REDs to OUTPUT

	TCC1.CTRLA = TC_CLKSEL_DIV4_gc;	// BLUE clock source
	TCC1.CTRLB = TC1_CCAEN_bm | TC1_CCBEN_bm | TC_WGMODE_SS_gc;	// enable BLUEs, set WGM to SS PWM
	TCC1.PERBUF = 0xFFFF;		// set period to 8-bit for 8-bit resolution
	TCC1.CCABUF = 0;		// set to off by default
	TCC1.CCBBUF = 0;		// set to off by default
	PORTC.DIRSET = PIN4_bm | PIN5_bm;  // set BLUEs to OUTPUT

	TCD0.CTRLA = TC_CLKSEL_DIV4_gc;	// GREEN clock source
	TCD0.CTRLB = TC0_CCAEN_bm | TC0_CCBEN_bm | TC_WGMODE_SS_gc;	// enable REDs, set WGM to SS PWM
	TCD0.PERBUF = 0xFFFF;		// set period to 8-bit for 8-bit resolution
	TCD0.CCABUF = 0;		// set to off by default
	TCD0.CCBBUF = 0;		// set to off by default
	PORTD.DIRSET = PIN0_bm | PIN1_bm;  // set GREENs to OUTPUT	
}
