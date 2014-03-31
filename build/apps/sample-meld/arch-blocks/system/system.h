# 1 "/home/anaz/blinkyblocks/build/src-bobby/system/system.bbh"
#ifndef __SYSTEM_H__
#define __SYSTEM_H__

#include <stdint.h>

#include "block.h"
#include "handler.h"

#ifdef DEBUG
#include "debug.h"
#endif

extern void userRegistration(void);

int blockProgram(void);

#endif
