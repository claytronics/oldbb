# 1 "/home/dcampbel/Research/blinkyBocksHardware/build/src-bobby/system/block_config.bb"
#include "../sim/block.h"
// block_config.c
//
// Contains block configuration data

#ifndef _BLOCK_CONFIG_C_
#define _BLOCK_CONFIG_C_

#include "block_config.h"

//blockConf EEMEM nv_conf;
//blockConf conf;

// sets local copy of UID
void setUID(uint16_t newID)
{
     (this()->conf) .UID = newID;
}

// sets local copy of UID and stores in EEPROM
void setAndStoreUID(uint16_t newID)
{
     (this()->conf) .UID = newID;
    store(& (this()->nv_conf) , & (this()->conf) , sizeof(blockConf));
}

#endif
