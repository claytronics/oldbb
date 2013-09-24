#ifndef __HW_MEMORY_C__
#define __HW_MEMORY_C__

#include "../system/defs.h"
#include "../sim/block.h"
#include "../hw-api/hwSerial.h"

//  Store a data structure in EEPROM. 
//    arguments are in the form:
//    EEPROM_DEST, SRAM_SOURCE, SIZEOF(SRAM_SOURCE)
void store(void * dest, void * src, int len){

}

//  Load a data structure from EEPROM. 
//    arguments are in the form:
//    SRAM_DEST, EEPROM_SOURCE, SIZEOF(EEPROM_SOURCE)
void restore(void * dest, void * src, int len){

}

//"Reads" GUID from memory
//ensures each block has guid even if not previously saved
uint16_t getGUID(){
  return this()->id;
}

#endif
