#ifndef _SIM_H_
#define _SIM_H_

#ifdef BBSIM

# include <stdarg.h>
  void err(char *prompt, ...);
  void blockprint(FILE* f, char* fmt, ...);
  void pauseForever(void);
#endif

#endif
