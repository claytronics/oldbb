#include <stdio.h>
#include "../system/handler.h"
#include "../hw-api/hwMemory.h"

// HARDWARE INCLUDE
#ifndef HOST
#include "eeprom_driver.h"
#endif

// Stores a data structure in EEPROM.  Writes it in pages to take a reasonable length of time.
//
//  nv_addr points to the desired EEMEM structure address (if you declared it using 'type EEMEM mydata', then '(char *)&mydata'
//  data points to the desired SRAM structure address
//  len    is sizeof(type)
void store(void * nv_addr, void * data, int len)
{
    uint8_t partial_offset, partial_length;

    while(len != 0)
    {
        // grab the page offset address from the current
        partial_offset = (uint16_t)nv_addr & (EEPROM_PAGESIZE - 1);
    
        partial_length = EEPROM_PAGESIZE - partial_offset;
        
        if(partial_length > len)
        {
            partial_length = len;
        }

        EEPROM_LoadPartialPage(data, partial_offset, partial_length);
        EEPROM_AtomicWritePage((uint16_t)nv_addr / (EEPROM_PAGESIZE));

        len -= partial_length;
        nv_addr += partial_length;
        data += partial_length;
    }
    
}

// this could be done in pages but reads are pretty quick so it shouldn't matter
void restore(void * vaddr, void * vnv_addr, int len)
{    
	byte *addr=(byte *)vaddr;
	byte *nv_addr=(byte *) vnv_addr;
    while(len != 0)
    {
        *addr = EEPROM_ReadByte(((uint16_t)(nv_addr) / (EEPROM_PAGESIZE)), (uint16_t)(nv_addr) & (EEPROM_PAGESIZE-1));
        nv_addr++;
        addr++;
        len--;
    }
}

#define ID_PAGE_ADDR 0x00
#define ID_BYTE_ADDR 0x00


/* TODO: This is WRONG. It should be Uid, but the build system is being stupid and now is not a good time to spend forever trying to fix it. */
//reads GUID from eeprom
uint16_t getGUID(){
  //return 255;
	//TODO: read from eeprom
  return (((uint16_t)EEPROM_ReadByte(ID_PAGE_ADDR, ID_BYTE_ADDR))<<8) |
	  ((uint16_t)EEPROM_ReadByte(ID_PAGE_ADDR, ID_BYTE_ADDR+1));
}
