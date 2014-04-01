# 1 "/home/pthalamy/CMU/oldbb/build/src-bobby/system/microphone.bbh"
#ifndef __MICROPHONE_BBH__
#define __MICROPHONE_BBH__

#include "defs.h"

 typedef int16_t MicData;

MicData getMicData(void);
//void    updateMic(void);	// updates microphone data
//int     newMicData(void);   // returns the microphone data

void initHWMic(void);

#endif
