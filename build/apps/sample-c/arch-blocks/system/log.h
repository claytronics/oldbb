# 1 "/home/pthalamy/CMU/oldbb/build/src-bobby/system/log.bbh"
#ifndef __LOG_H__
#define __LOG_H__

#include <stdint.h>
#include "serial.h"

extern PRef toHost;

byte handleLogMessage(void);

byte printDebug(char*s);

byte blockingPrintDebug(char *s);

//byte printDebug(char *s, ...);

//byte blockingPrintDebug(char *s, ...);

void initLogDebug(void);

byte isHostPort(PRef p);

void processCmd(void);

#endif
