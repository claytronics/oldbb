#include "led.h"

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
	return currentColor;
}

void setColor(Color c)
{
	if(c < NUM_COLORS)
	{
		currentColor = c;

		setLED(Colors[c][0], Colors[c][1], Colors[c][2], currentIntensity);
	}
}

Color setNextColor()
{
	currentColor++;

	if(currentColor >= NUM_COLORS)
		currentColor = 0;

	setLED(Colors[currentColor][0], Colors[currentColor][1], Colors[currentColor][2], currentIntensity);

	return currentColor;
}

void setLED(uint8_t r, uint8_t g, uint8_t b, Intensity i)
{
	pthread_mutex_lock(&LEDmutex);

	currentLED.r = r;
	currentLED.g = g;
	currentLED.b = b;
	currentLED.i = i;

	pthread_mutex_unlock(&LEDmutex);
}

void setIntensity(Intensity i)
{
	currentIntensity = i;

	setLED(currentLED.r, currentLED.g, currentLED.b, i);
}

Intensity getIntensity()
{
	return currentIntensity;
}

void initLED()
{
	currentLED.r = 0;
	currentLED.g = 0;
	currentLED.b = 0;
	currentLED.i = 0;

	pthread_mutex_init(&LEDmutex, NULL);
}
