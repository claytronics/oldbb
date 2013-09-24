#ifndef __EXT_MEMORY_H__
#define __EXT_MEMORY_H__

#include <stdint.h>
#include <stdio.h>

#define SRAM 	1
#define FLASH 	0

#define SRAM_READ	0x03
#define SRAM_WRITE	0x02
#define SRAM_RDSR	0x05
#define SRAM_WRSR	0x01

#define FLASH_BF1WR	0x84
#define FLASH_BF1RD	0xD4

int readExtMem(int which, int start, char * buf, int length);
int writeExtMem(int which, int start, char * buf, int length);

void initFlash(void);
void initSRAM(void);
void initExtMem(void);

#endif
