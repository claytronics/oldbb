# 1 "/home/anaz/Desktop/oldbb-ssh/build/src-bobby/system/led.bbh"
#ifndef __LED_H__
#define __LED_H__

#include "defs.h"

 typedef uint8_t Color;				// a specific color from the ENUM below
 typedef uint8_t Intensity;			// intensity is 0-255.  255 = 100% on, 0 = completely off

 #define INTENSITY_MAX	255
 #define INTENSITY_MIN	0

enum{RED, ORANGE, YELLOW, GREEN, AQUA, BLUE, WHITE, PURPLE, PINK, NUM_COLORS};		// more colors go here - keep in ROYGBIV if possible

Color getColor(void);				// returns the current color state variable.
									// this is NOT guaranteed to be the actual color if your code calls set_led().
									// call set_color(get_color()) to ensure that the displayed color is the current color.
Color getNextColor(void);			// returns the next color (according to get_color()) in the ENUM, looping if necessary.


void setColor(Color);				// sets the cube color to any color from the ENUM, up to NUM_COLORS.  Setting it at or beyond NUM_COLORS has no effect.

Color setNextColor(void);			// cycles to the next color (according to get_color()) in the ENUM, looping if necessary.  Returns the new color.

void setIntensity(Intensity);		// sets intensity from MAX brightness (255) to MIN brightness (0).  Updates immediately.
Intensity getIntensity(void);		// returns the current intensity

void setLED(byte, byte, byte, Intensity);	// low-level routine to set arbitrary color mixes.  Calling this makes the meaning of get_color() inaccurate.

void initLED(void);				// sets up PWM registers for LEDs

#endif
