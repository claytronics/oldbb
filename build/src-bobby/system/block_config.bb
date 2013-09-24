// block_config.c
//
// Contains block configuration data

#ifndef _BLOCK_CONFIG_C_
#define _BLOCK_CONFIG_C_

#include "block_config.bbh"

threadextern blockConf EEMEM nv_conf;
threadextern blockConf conf;

// sets local copy of UID
void setUID(uint16_t newID)
{
    conf.UID = newID;
}

// sets local copy of UID and stores in EEPROM
void setAndStoreUID(uint16_t newID)
{
    conf.UID = newID;
    store(&nv_conf, &conf, sizeof(blockConf));
}

#endif
