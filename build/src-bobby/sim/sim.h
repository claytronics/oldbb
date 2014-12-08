#ifndef _SIM_H_
# define _SIM_H_


# ifdef BBSIM

#  include "block.h"

   extern int simDebugLevel;
   extern void (*onBlockTick)(void);
   Block* port2block(Block* b, PRef p);
   Uid port2id(Block* b, PRef p);

#  define DEBUGPRINT(level, ...) debugprint(level, stderr, __VA_ARGS__)
#  define IFSIMDEBUG(level) if (level <= simDebugLevel)

#  include <stdarg.h>
   void err(char *prompt, ...);
   void blockprint(FILE* f, char* fmt, ...);
   void debugprint(int level, FILE* f, char* fmt, ...);
   void pauseForever(void);
# else

#  define DEBUGPRINT(level, ...) 
#  define IFSIMDEBUG(level) if (0)
#  define blockprint(f, fmt, ...)

# endif

#endif
