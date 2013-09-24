# 1 "/home/dcampbel/Research/blinkyBocksHardware/build/src-bobby/system/block_config.bbh"
// block_config.h
//
// Contains block configuration data

#ifndef _BLOCK_CONFIG_H_
#define _BLOCK_CONFIG_H_

#include "memory.h"

// sets local copy of UID
void setUID(uint16_t);

// sets local copy of UID and stores in EEPROM
void setAndStoreUID(uint16_t);


#endif
