#include "handler.bbh"
#include "led.bbh"
#include "block.bbh"
#include "block_config.bbh"

void myMain(void)
{ 
  uint16_t id = 256*ARG_ID; 
  
  setColor(WHITE);
  
  setAndStoreUID(id);
  
  if (getGUID() == ARG_ID)
  {
    setColor(GREEN);
  }
  else
  {
    setColor(RED);
  }
  
  /*char s[150];
  snprintf(s, 150*sizeof(char), "");
  s[149] = '\0';*/
  
  while(1)
  {
/*#ifdef LOG_DEBUG
    printDebug(s);
    delayMS(1000);
#endif*/
  }
}

void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}
