#ifndef __AUDIO_H__
#define __AUDIO_H__

#include <stdint.h>

#define SAMPLES 64

void chirp(unsigned int, unsigned int);
void setDac(unsigned int, unsigned int);

void initAudio(void);

#endif
