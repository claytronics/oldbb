#include <avr/io.h>
#include <avr/interrupt.h>
#include "../system/circ_buffer.h"
#include "../hw-api/hwSerial.h"
#include "../system/serial.h"

//#include "soft_uart2.h"

#define HARDWARE_UART 0
#define SOFTWARE_UART 1

typedef union
{
	USART_t* huart;
	//soft_uart_t* suart;
} Uart;

typedef struct _uart
{
	Uart uart;
	byte ptype;
} HWPort;

HWPort hwPort[NUM_PORTS];
extern Port port[NUM_PORTS];

void initHWUart(USART_t * uart);


void u_putchar(char c, USART_t * uart)
{
	while(!(uart->STATUS & USART_DREIF_bm));
    //loop_until_bit_is_set(UCSR0A, UDRE0);
	// Load buffer with your character
    uart->DATA = c;
}

int	u_getchar(USART_t * uart)
{
	//char c;
	// Wait for the receive buffer to be filled
    //loop_until_bit_is_set(UCSR0A, RXC0);
	if((uart->STATUS & USART_RXCIF_bm))
	{
		// Read the receive buffer
		//c = uart->DATA;
		return uart->DATA;
	}
	else
	{
		return -1;
	}
}

void pPutChar(char c, PRef p)
{
	if(p >= 0 && p < NUM_PORTS)
	{
		if(hwPort[p].ptype == HARDWARE_UART)
		{
			u_putchar(c, hwPort[p].uart.huart);
		}
		else
		{
			//su_putchar(c, hwPort[p].uart.suart);
		}
	}
}

int pGetChar(PRef p)
{
	if(p >= 0 && p < NUM_PORTS)
	{
		if(hwPort[p].ptype == HARDWARE_UART)
		{
			return u_getchar(hwPort[p].uart.huart);
		}
		else
		{
			//return su_getchar(hwPort[p].uart.suart);
		}
	}

	return -1;
}

void initializeHWPort(HWPort *p, USART_t *huart)
{
	if(huart != NULL)
	{
		(p->uart).huart = huart;
		p->ptype = HARDWARE_UART;

		initHWUart(huart);
	}
}

void initHWPorts()
{
	// male headers
	PORTD.DIRSET = PIN7_bm;
	initializeHWPort(&(hwPort[UP]), &USARTD1);

	// near the double TVS diodes
	PORTC.DIRSET = PIN7_bm;
	initializeHWPort(&(hwPort[NORTH]), &USARTC1);
	
	// closest to audio amp
	PORTE.DIRSET = PIN3_bm;
	initializeHWPort(&(hwPort[SOUTH]), &USARTE0);
	
	// closest to LEDs
	PORTF.DIRSET = PIN3_bm;	
	initializeHWPort(&(hwPort[EAST]), &USARTF0);
	
	// closest to analog input dividers
	PORTC.DIRSET = PIN3_bm;	
	initializeHWPort(&(hwPort[WEST]), &USARTC0);
	
	PORTD.DIRSET = PIN3_bm;	
	initializeHWPort(&(hwPort[DOWN]), &USARTD0);
}

void initHWUart(USART_t * huart)
{
	huart->CTRLB = USART_RXEN_bm | USART_TXEN_bm;		// turn on RX/TX, somewhat helpful!
	huart->CTRLC = USART_CHSIZE_8BIT_gc;				// use 8-bit data

	huart->CTRLA = USART_RXCINTLVL_HI_gc | USART_TXCINTLVL_HI_gc;	// enable RX/TX interrupt support
	huart->BAUDCTRLA = 51;  // 38400 		// 57600   			//103;	//19200 	16; // 115200
	huart->BAUDCTRLB = 0;				//0xB4;  // 57600, set to 0 for others
}


ISR(USARTD1_RXC_vect)
{
	uint8_t c;
	
	c = USARTD1.DATA;
	//printf("R:%x",c);	
	push(c, &(port[UP].rx));
}

ISR(USARTD1_TXC_vect)
{
	int16_t c;
	
	c  = pop(&(port[UP].tx));
	
	if(c != -1)
	{
		USARTD1.DATA = c;
	}
	else
	{
		// should flag that we're waiting for an ACK now
	}
}

ISR(USARTC1_RXC_vect)
{
	uint8_t c;
	
	c = USARTC1.DATA;
	//printf("R:%x",c);	
	push(c, &(port[NORTH].rx));
}
ISR(USARTC1_TXC_vect)
{
	int16_t c;
	
	c  = pop(&(port[NORTH].tx));
	
	if(c != -1)
	{
		USARTC1.DATA = c;
	}
}

ISR(USARTF0_RXC_vect)
{
	uint8_t c;
	
	c = USARTF0.DATA;
	//printf("R:%x",c);	
	push(c, &(port[EAST].rx));
}
ISR(USARTF0_TXC_vect)
{
	int16_t c;
	
	c  = pop(&(port[EAST].tx));
	
	if(c != -1)
	{
		USARTF0.DATA = c;
	}
}

ISR(USARTE0_RXC_vect)
{
	uint8_t c;
	
	c = USARTE0.DATA;
	//printf("R:%x",c);	
	push(c, &(port[SOUTH].rx));
}
ISR(USARTE0_TXC_vect)
{
	int16_t c;
	
	c  = pop(&(port[SOUTH].tx));
	
	if(c != -1)
	{
		USARTE0.DATA = c;
	}
}

ISR(USARTC0_RXC_vect)
{
	uint8_t c;
	
	c = USARTC0.DATA;
	//printf("R:%x",c);	
	push(c, &(port[WEST].rx));
}
ISR(USARTC0_TXC_vect)
{
	int16_t c;
	
	c  = pop(&(port[WEST].tx));
	
	if(c != -1)
	{
		USARTC0.DATA = c;
	}
}

ISR(USARTD0_RXC_vect)
{
	uint8_t c;
	
	c = USARTD0.DATA;
	//printf("R:%x",c);
	push(c, &(port[DOWN].rx));
}
ISR(USARTD0_TXC_vect)
{
	int16_t c;
	
	c  = pop(&(port[DOWN].tx));
	
	if(c != -1)
	{
		USARTD0.DATA = c;
	}
}

