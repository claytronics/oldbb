#include "handler.bbh"
#include "led.bbh"
#include "block.bbh"
#include "block_config.bbh"

void myMain(void)
{ 
  setColor(WHITE);
  

  while(1);
}

void
setAndCheckUID(void)
{
  // Set UID
  uint16_t idToSet;
  idToSet = (uint16_t)(thisChunk->data[3]) << 8;
  idToSet |= thisChunk->data[4];

#ifdef LOG_DEBUG
  char s[10];
  snprintf(s, 10*sizeof(char), "Uid: %hu", idToSet );
  printDebug(s);
#endif

  setAndStoreUID(idToSet);
  
  // Check that UID has been properly set
  if ( getGUID() == idToSet ) {
    setColor(GREEN); // success
  } else {
    setColor(RED); // failure
  }  

  // Check on host if log is activated
#ifdef LOG_DEBUG
  snprintf(s, 10*sizeof(char), "id: %hu", getGUID() );
  printDebug(s);
#endif
}

void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
  registerHandler(EVENT_COMMAND_RECEIVED, (GenericHandler)&setAndCheckUID);
}
