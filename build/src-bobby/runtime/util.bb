#include "util.h"

#include <time.h>
#include <unistd.h>

int16_t max (int16_t a, int16_t b)
{
	return (a > b)?a:b;
}

void delay (int16_t millis)
{
	usleep(millis * 1000);
}

int16_t getTime ()
{
	return clock();
}