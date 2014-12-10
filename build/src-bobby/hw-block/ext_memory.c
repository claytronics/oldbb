#include "ext_memory.h"
#include <avr/io.h>
#include "../system/noprintf.h"

uint8_t spiTxRx(uint8_t input)
{
	SPIE.DATA = input;
	
	while(!(SPIE.STATUS & SPI_IF_bm));
	
	return SPIE.DATA;
	
}

void setSS(int which, int level)
{
	if(which == SRAM)
	{
		if(level)
		{
			PORTF.OUTSET = PIN4_bm;
		}
		else
		{
			PORTF.OUTCLR = PIN4_bm;	
		}
	}
	else
	{
		if(level)
		{
			PORTE.OUTSET = PIN4_bm;
		}
		else
		{
			PORTE.OUTCLR = PIN4_bm;	
		}	
	
	}
}

int readExtMem(int which, int start, char * buf, int length)
{
	if(which == SRAM)
	{
		int i;
		
		setSS(SRAM, 0);
		
		spiTxRx(SRAM_READ);
		spiTxRx((start >> 8) & 0x00FF);
		spiTxRx(start & 0x00FF);
		
		for(i = 0; i < length; ++i)
		{
			buf[i] = spiTxRx(0);
		}
		
		setSS(SRAM, 1);	
		
		return 1;
	}
	else
	{
		int i;
		
		setSS(FLASH, 0);
		
		spiTxRx(FLASH_BF1RD);
		spiTxRx(0x00);
		spiTxRx((start & 0x0300) >> 8);
		spiTxRx(start & 0x00FF);
		spiTxRx(0x00);
		
		for(i = 0; i < length; ++i)
		{
			buf[i] = spiTxRx(0);
		}
		
		setSS(FLASH, 1);	
		
		return 1;
	}
}

int writeExtMem(int which, int start, char * buf, int length)
{
	if(which == SRAM)
	{
		int i;
		
		setSS(SRAM, 0);
		
		spiTxRx(SRAM_WRITE);
		spiTxRx((start >> 8) & 0x00FF);
		spiTxRx(start & 0x00FF);
		
		for(i = 0; i < length; ++i)
		{
			spiTxRx(buf[i]);
		}
		
		setSS(SRAM, 1);	
		
		return 1;
	}
	else
	{
		int i;
		
		setSS(FLASH, 0);
		
		spiTxRx(FLASH_BF1WR);
		spiTxRx(0x00);
		spiTxRx((start & 0x0300) >> 8);
		spiTxRx(start & 0x00FF);
		
		for(i = 0; i < length; ++i)
		{
			spiTxRx(buf[i]);
		}
		
		setSS(FLASH, 1);	
		
		return 1;
	}


}



void initFlash()
{

}

void initSRAM()
{
		setSS(SRAM, 0);
		
		spiTxRx(SRAM_WRSR);
		spiTxRx(0x41);	// set mode to sequential, disable HOLD feature
		
		setSS(SRAM, 1);	
	
	{
		uint8_t val;
		setSS(SRAM, 0);
		
		spiTxRx(SRAM_RDSR);
		val = spiTxRx(0x0);	// set mode to sequential, disable HOLD feature
		
		setSS(SRAM, 1);			
		
		printf("SR is: %x\r\n",val);
	}
}

void initExtMem()
{
	PORTE.OUTSET = PIN4_bm;
	PORTE.DIRSET = PIN4_bm;

	// set !SS1 lhigh
	PORTF.OUTSET = PIN4_bm;
	PORTF.DIRSET = PIN4_bm;


	PORTE.DIRSET = PIN5_bm | PIN7_bm;	// !ss/mosi/sck as output
	PORTE.DIRCLR = PIN6_bm;				// miso as input

	SPIE.CTRL = SPI_ENABLE_bm | SPI_MASTER_bm;	//mode 0, clock/4

	initFlash();
	initSRAM();

}
