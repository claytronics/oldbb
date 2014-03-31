#include "soft_uart2.h"

#include "../system/handler.h"

soft_uart_t soft_uart[NUM_SOFTWARE_UARTS];

/*
void timer_isr(soft_uart_t * uart)
{
	char			start_bit, flag_in;
	
//	uart->timer1->PER = 93;

// Transmitter Section
	if ( uart->flag_tx_ready )
		{
		if ( --(uart->timer_tx_ctr) <= 0 )
			{
			//mask = (uart->internal_tx_buffer) & 0x01;
			//(uart->internal_tx_buffer) >>= 1;
			if ( ((uart->internal_tx_buffer) & 0x0001) == 0x01)
				{
				SET_TX_PIN_HIGH(uart);
				}
			else
				{
				SET_TX_PIN_LOW(uart);
				}
				
			uart->internal_tx_buffer >>= 1;
			uart->timer_tx_ctr = 3;
			if ( --(uart->bits_left_in_tx) <= 0 )
				{
					uart->flag_tx_ready = FALSE;
					SET_TX_PIN_HIGH(uart);	
					TriggerHandler(SYSTEM_SOFT_UART_UPDATE);		
				}
			}
		}
// Receiver Section
	if ( uart->flag_rx_off==FALSE )
		{
		if ( uart->flag_rx_waiting_for_stop_bit )
			{
			if ( --(uart->timer_rx_ctr) <=0 )
				{
				uart->flag_rx_waiting_for_stop_bit = FALSE;
				uart->flag_rx_ready = FALSE;
				uart->internal_rx_buffer &= 0xFF;
				if ( uart->internal_rx_buffer!=0xC2 )
					{
					(uart->inbuf)[uart->qin] = uart->internal_rx_buffer;
					TriggerHandler(SYSTEM_SOFT_UART_UPDATE);
					if ( ++(uart->qin)>=SOFT_IN_BUF_SIZE )
						{
						uart->qin = 0;
						}
					}
				}
			}
		else		// rx_test_busy
			{
			if ( uart->flag_rx_ready==FALSE )
				{
					start_bit = GET_RX_PIN_STATUS(uart);
// Test for Start Bit
				if ( start_bit==0 )
					{
					uart->flag_rx_ready = TRUE;
					uart->internal_rx_buffer = 0;
					uart->timer_rx_ctr = 4;
					uart->bits_left_in_rx = uart->rx_num_of_bits;
					uart->rx_mask = 1;
					}
				}
			else	// rx_busy
				{
				if ( --(uart->timer_rx_ctr)<=0 )
					{				// rcv
					uart->timer_rx_ctr = 3;
					flag_in = GET_RX_PIN_STATUS(uart);
					if ( flag_in )
						{
						(uart->internal_rx_buffer) |= uart->rx_mask;
						}
					uart->rx_mask <<= 1;
					if ( --(uart->bits_left_in_rx)<=0 )
						{
						uart->flag_rx_waiting_for_stop_bit = TRUE;
						}
					}
				}
			}
		}
	}
*/

void timer_set( uint32_t bps, TC0_t * tx_timer, TC1_t * rx_timer)
{
	uint16_t per;
	
	per = ((uint32_t)F_CPU / (bps));

	tx_timer->INTCTRLA = TC_OVFINTLVL_HI_gc;
	tx_timer->PER = per;
	tx_timer->CTRLA = TC_CLKSEL_DIV1_gc;
	
//	printf("TCE0 set to %x %x %x\r\n",tx_timer->INTCTRLA, tx_timer->PER, tx_timer->CTRLA);
	
	per = ((uint32_t)F_CPU / (bps*3));	
	//per = 31; // bps*9
	
	rx_timer->INTCTRLA = TC_OVFINTLVL_HI_gc;
	rx_timer->PER = per;
	rx_timer->CTRLA = TC_CLKSEL_DIV1_gc;	
}

void configure_soft_uart(soft_uart_t * uart, CircBuf * ctx, CircBuf * crx)
{
	uart->rx = crx;
	uart->tx = ctx;
	uart->internal_rx_buffer =  0;
	uart->internal_tx_buffer  = 0;
}

void init_soft_uart()
{
	PORTD.OUTSET = PIN5_bm | PIN6_bm; // set tx output high (idle)
	PORTD.DIRSET = PIN5_bm | PIN6_bm; // tx pins as output
	PORTD.DIRCLR = PIN4_bm | PIN7_bm; // rx pins as input
	
	PORTD.PIN4CTRL |= PORT_OPC_PULLUP_gc;	// set pullups on rx lines to prevent floating noise
	PORTD.PIN7CTRL |= PORT_OPC_PULLUP_gc;	

	soft_uart[1].rx_state = SU_RX_IDLE;

	timer_set( BAUD_RATE, &TCE0, &TCD1);	
}

int su_getchar(soft_uart_t * uart)
{
	char		ch;

	if(isEmpty((uart->rx)))
	{
		return -1;
	}
	else
	{	
		ch = pop((uart->rx));
		return( ch );
	}
}

void set_tx_char(int ch, soft_uart_t * uart)
{
	if(ch != -1)
	{
		uart->internal_tx_buffer = (ch << 1) | 0x200;
	}
}

void su_putchar( char ch, soft_uart_t * uart)
{	
	while ( uart->internal_tx_buffer != 0 );
	
	set_tx_char(ch, uart);
//	uart->user_tx_buffer = ch;

// invoke_UART_transmit
//	uart->timer_tx_ctr = 3;
//	uart->bits_left_in_tx = uart->tx_num_of_bits;

	// insert start/stop bits around character

}

int kbhit( soft_uart_t * uart )
{
	return( !isEmpty(uart->rx));
}

// rx ISR
ISR(TCD1_OVF_vect)
{
	if(!soft_uart[0].sample_time)
	{
		if(soft_uart[0].rx_state == SU_RX_IDLE)
		{
			if(!(PORTD.IN & PIN4_bm))
			{
				soft_uart[0].rx_state = SU_RX_BYTE;
				soft_uart[0].rx_mask = 0x01;
				soft_uart[0].sample_time = 3;	// begin sampling every third time
			}
		}
		else if (soft_uart[0].rx_state == SU_RX_BYTE)
		{
			if(PORTD.IN & PIN4_bm)
			{
				soft_uart[0].internal_rx_buffer |= soft_uart[0].rx_mask;
			}
			
			soft_uart[0].rx_mask <<= 1;

			if(!soft_uart[0].rx_mask)
			{
				soft_uart[0].rx_state = SU_RX_STOP;
			}
			
			soft_uart[0].sample_time = 2;	
		}
		else if (soft_uart[0].rx_state == SU_RX_STOP)
		{
			if(PORTD.IN & PIN4_bm)
			{
				push(soft_uart[0].internal_rx_buffer, soft_uart[0].rx);
				soft_uart[0].internal_rx_buffer = 0;
			}
			
			soft_uart[0].rx_state = SU_RX_IDLE;		
		}
	}
	else
	{
		soft_uart[0].sample_time--;
	}

	if(!soft_uart[1].sample_time)
	{
		if(soft_uart[1].rx_state == SU_RX_IDLE)
		{
			if(!(PORTD.IN & PIN7_bm))
			{
				soft_uart[1].rx_state = SU_RX_BYTE;
				soft_uart[1].rx_mask = 0x01;
				soft_uart[1].sample_time = 3;	// begin sampling every third time
			}
		}
		else if (soft_uart[1].rx_state == SU_RX_BYTE)
		{
			if(PORTD.IN & PIN7_bm)
			{
				soft_uart[1].internal_rx_buffer |= soft_uart[1].rx_mask;
			}
			
			soft_uart[1].rx_mask <<= 1;

			if(!soft_uart[1].rx_mask)
			{
				soft_uart[1].rx_state = SU_RX_STOP;
			}
			
			soft_uart[1].sample_time = 2;	
		}
		else if (soft_uart[1].rx_state == SU_RX_STOP)
		{
			if(PORTD.IN & PIN7_bm)
			{
				push(soft_uart[1].internal_rx_buffer, soft_uart[1].rx);
				soft_uart[1].internal_rx_buffer = 0;
			}
			
			soft_uart[1].rx_state = SU_RX_IDLE;		
		}
	}
	else
	{
		soft_uart[1].sample_time--;
	}	
}
/*
// tx ISR
ISR(TCE0_OVF_vect)
{
	if(soft_uart[0].internal_tx_buffer & 0x0001)
	{
		PORTD.OUTSET = PIN5_bm;
		
		soft_uart[0].internal_tx_buffer >>= 1;		
	}
	else
	{
		if(!(soft_uart[0].internal_tx_buffer))
		{
			set_tx_char(pop(soft_uart[0].tx), &(soft_uart[0]));
			if(!(soft_uart[0].internal_tx_buffer))
			{
				PORTD.OUTSET = PIN5_bm;
			}
		}	
		else
		{
			PORTD.OUTCLR = PIN5_bm;
			soft_uart[0].internal_tx_buffer >>= 1;
		}
	}

	if(soft_uart[1].internal_tx_buffer & 0x0001)
	{
		PORTD.OUTSET = PIN6_bm;
		soft_uart[1].internal_tx_buffer >>= 1;
	}
	else
	{
		if(!(soft_uart[1].internal_tx_buffer))
		{
			set_tx_char(pop(soft_uart[1].tx), &(soft_uart[1]));
			if(!(soft_uart[1].internal_tx_buffer))
			{
				PORTD.OUTSET = PIN6_bm;
			}
		}	
		else
		{
			PORTD.OUTCLR = PIN6_bm;
			soft_uart[1].internal_tx_buffer >>= 1;
		}
	}
	


}
*/
