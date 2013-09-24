#ifndef __SOFT_UART2_H__
#define __SOFT_UART2_H__
// UART.H

#include <stdio.h>
#include <avr/io.h>
#include <avr/interrupt.h>
#include <util/delay.h>
#include "../system/circ_buffer.h"

//#define BAUD_RATE		 19200.0
#define BAUD_RATE	 	 38400	// bps

#define NUM_SOFTWARE_UARTS	2

enum{SU_RX_IDLE, SU_RX_BYTE, SU_RX_STOP};

typedef struct _software_uart
{
	volatile char				internal_rx_buffer;
	volatile uint16_t			internal_tx_buffer;
	
	uint8_t rx_mask;
	uint8_t rx_state;
	uint8_t sample_time;
	
	CircBuf *		tx;		// where to pop tx bytes from
	CircBuf *		rx;		// where to push rx bytes into
	
	//char				user_tx_buffer;
	
} soft_uart_t;

extern soft_uart_t soft_uart[NUM_SOFTWARE_UARTS];

void timer_set( uint32_t bps, TC0_t * tx_timer, TC1_t * rx_timer);

// pass a whole bunch of stuff to configure a software uart. 
void configure_soft_uart(soft_uart_t * uart, CircBuf * tx, CircBuf * rx);
void init_soft_uart(void);

int su_getchar(soft_uart_t *);			// non-blocking, returns -1 if no char
void su_putchar( char, soft_uart_t *);	// blocking

int kbhit( soft_uart_t *);				// returns 0 if no data in buffer, 1 otherwise

#endif
