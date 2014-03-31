#include "circ_buffer.h"

void push(byte data, CircBuf * b)
{ 
  b->buf[b->end++] = data;

  if(b->end == CIRC_BUF_LEN)
  {
    b->end = 0;
  }

  if(b->end == b->start)
  {
    b->start++;
	
	if(b->start == CIRC_BUF_LEN)
	{
	  b->start = 0;
	}
  }
}

int pop(CircBuf * b)
{
  uint8_t data;

  if( isEmpty(b) )
  {
	return -1;
  }

  data = b->buf[b->start++];
  
  if(b->start == CIRC_BUF_LEN)
  {
    b->start = 0;
  }
  
  return data;
}

byte isEmpty(CircBuf * b)
{
  if(b->start == b->end)
  {
    return 1;
  }
  return 0;
}

