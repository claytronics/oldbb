#include "bbassert.bbh"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
//#include <libgen.h> // basename

#include "led.bbh"
#include "hardwaretime.bbh"

#include "../hw-api/hwMemory.h" // getGUID

static char* _basename(char *file);

void _bbassert (char *file, int ln, char* exp) {
  char s[100];
  byte i = 0;
  
  snprintf(s, 100*sizeof(char), "Assert failed %s (%s:%d)",exp,_basename(file),ln);
  s[99] = '\0';

  while(1) {
    setColor(RED);
    delayMS(100);
    setColor(BLUE);
    delayMS(100);

    if (i == 0) { // every 3 seconds (15 * 200 ms)
#ifdef BBSIM
      fprintf(stderr, "%u: %s\n",getGUID(),s);
#endif

#ifdef LOB_DEBUG
      // report assert using the log system
      printDebug(s);
#endif
    }
    i = (i+1) % 15;
  }
}

static char* _basename(char *file) {
  uint16_t len = strlen(file);
  uint16_t i = len-1;
  char *p = file+i;
  
  while (p != file) {
    if (*p == '/') {
      p++;
      break;
    }
    p--;
  }

  return p;
}
