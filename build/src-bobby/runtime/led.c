# 1 "/seth/claycvs/svn/blinkyblocks/newcode/src/runtime/led.bb"
#include "led.h"

// Color 	currentColor;				// current color from enum.
												// NOT guaranteed to be the actual color if set_led was directly called.
// Intensity currentIntensity;			// current intensity.
												// NOT guaranteed to be the actual intensity if set_led was directly called.
// LED 		currentLED;					// current physical R, G, B, I values.

// pthread_mutex_t 	LEDmutex;			// mutex to protect led access.
												// as the simulator will need to access the LED concurrent to the block thread.


// stolen from: http://www.free-webmaster-tools.com/colorpicker.htm
//enum {RED, ORANGE, YELLOW, GREEN, BLUE, INDIGO, VIOLET, WHITE, AQUA, FUCHSIA, LAWNGREEN, LIGHTPINK, LIGHTSLATEGRAY, SADDLEBROWN, GOLD, NUM_COLORS};
const uint8_t Colors[NUM_COLORS][3] = {
{0xFF, 0x00, 0x00}, // red
{0xE1, 0x2D, 0x00}, // orange
{0xFF, 0xEB, 0x00}, // yellow
{0x00, 0xFF, 0x00}, // green
{0x00, 0xFF, 0xFF}, // aqua
{0x00, 0x00, 0xFF}, // blue
{0xFF, 0xFF, 0xFF}, // white
{0xFF, 0x00, 0xFF}, // purple
{0xFF, 0x00, 0x2D}, // pink
//{0x7C, 0xFC, 0x00}, // lawngrean
//{0xFF, 0xB6, 0xC1}, // lightpink
//{0x77, 0x88, 0x99}, // light slate gray
//{0x8B, 0x45, 0x13}, // saddlebrown
//{0xFF, 0xD7, 0x00}, // gold
//{0x4B, 0x00, 0xB0}, // indigo
//{0xEE, 0x82, 0xEE}, // violet
};

Color getColor()
{
	return  (this()->currentColor) ;
}

void setColor(Color c)
{
	if(c < NUM_COLORS)
	{
		 (this()->currentColor)  = c;

		setLED(Colors[c][0], Colors[c][1], Colors[c][2],  (this()->currentIntensity) );
	}
}

Color setNextColor()
{
	 (this()->currentColor) ++;

	if( (this()->currentColor)  >= NUM_COLORS)
		 (this()->currentColor)  = 0;

	setLED(Colors[ (this()->currentColor) ][0], Colors[ (this()->currentColor) ][1], Colors[ (this()->currentColor) ][2],  (this()->currentIntensity) );

	return  (this()->currentColor) ;
}

void setLED(uint8_t r, uint8_t g, uint8_t b, Intensity i)
{
	pthread_mutex_lock(& (this()->LEDmutex) );

	 (this()->currentLED) .r = r;
	 (this()->currentLED) .g = g;
	 (this()->currentLED) .b = b;
	 (this()->currentLED) .i = i;

	pthread_mutex_unlock(& (this()->LEDmutex) );
}

void setIntensity(Intensity i)
{
	 (this()->currentIntensity)  = i;

	setLED( (this()->currentLED) .r,  (this()->currentLED) .g,  (this()->currentLED) .b, i);
}

Intensity getIntensity()
{
	return  (this()->currentIntensity) ;
}

void initLED()
{
	 (this()->currentLED) .r = 0;
	 (this()->currentLED) .g = 0;
	 (this()->currentLED) .b = 0;
	 (this()->currentLED) .i = 0;

	pthread_mutex_init(& (this()->LEDmutex) , NULL);
}
