# 1 "/home/pthalamy/CMU/oldbb/build/src-bobby/system/clock.bbh"
#ifndef __CLOCK_H__
#define __CLOCK_H__

#include <stdint.h>
#include "serial.h"
#include "hardwaretime.h"

void initClock(void);

Time getClock(void);

byte handleClockSyncMessage(void);

byte handleNeighborChange(PRef p);

byte isAClockSyncMessage(Chunk *c);

void insertReceiveTime(Chunk *c);

void insertSendTime(Chunk *c);

byte synchronizeNeighbor(PRef p);

byte synchronizeNeighbors(void);

byte isTimeLeader(void);

byte isSynchronized(void);

void printSlope(void);

//DEBUG
byte isElecting(void);

#endif
