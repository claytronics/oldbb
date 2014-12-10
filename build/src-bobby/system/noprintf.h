#ifndef _NO_PRINTF_H_
#define _NO_PRINTF_H_

# ifndef BBSIM
   void xprintf(void*, ...);
#  define fprintf xprintf
#  define printf(fmt, ...) xprintf((void*)0, fmt, __VA_ARGS__)     
# endif


#endif
