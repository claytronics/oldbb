#include "../hw-api/hwDebug.h"
#include <avr/io.h>

/* DEBUGDEF */
/* #define DEBUG 5 */
#ifdef DEBUG

#if DEBUG == 5 // UP

#define DEBUGPORT 5
#define DEBUGUART USARTD1

#elif DEBUG == 1 // NORTH

#define DEBUGPORT 1
#define DEBUGUART USARTC1

#elif DEBUG == 4 //SOUTH

#define DEBUGPORT 4
#define DEBUGUART USARTE0

#elif DEBUG == 2 //EAST

#define DEBUGPORT 2
#define DEBUGUART USARTF0

#elif DEBUG == 3 //WEST

#define DEBUGPORT 3
#define DEBUGUART USARTC0

#elif DEBUG == 0 //DOWN

#define DEBUGPORT 0
#define DEBUGUART USARTD0

#endif
#ifndef DEBUGPORT
#error Invalid DEBUG option chosen - use a face enum.
#endif

#endif

FILE debug;

void initDebugUart(USART_t * uart)
{
	uart->CTRLA = 0;	// disable RX/TX interrupts
	uart->CTRLB = USART_RXEN_bm | USART_TXEN_bm;
	uart->CTRLC = USART_CHSIZE_8BIT_gc;
	uart->BAUDCTRLA = 16;  // 115200  103;	// 19200?!
	uart->BAUDCTRLB = 0;
}

int
debugPutChar(char c, FILE * fb)
{
#ifdef DEBUG
    while(!(DEBUGUART.STATUS & USART_DREIF_bm));
    DEBUGUART.DATA = c;
#endif
    return 0;
}

int
debugGetChar(FILE * fb)
{
#ifdef DEBUG
	//char c;
	// Wait for the receive buffer to be filled
    //loop_until_bit_is_set(UCSR0A, RXC0);
	if((DEBUGUART.STATUS & USART_RXCIF_bm))
	{
		// Read the receive buffer
		//c = uart->DATA;
	  return DEBUGUART.DATA;
	}
	else
	{
	    return -1;
	}
#else
	return -1;
#endif
}


void initHWDebug() 
{
#ifdef DEBUG
    initDebugUart(&DEBUGUART);
     
    fdev_setup_stream(&debug, debugPutChar, debugGetChar, _FDEV_SETUP_RW);

    stdout = &debug;
#endif
}



