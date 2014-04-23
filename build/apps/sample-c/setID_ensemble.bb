#include "block.bbh"

threadvar hasMoreThanTwoNeighbors;

byte sendNextID(PRef nextBlock, uint16_t nextID);
void setFirstBlockID(void);
void processIDChange(void);
void checkId(uint16_t cId);
void freeMyChunk(void);

void myMain(void)
{ 
  hasMoreThanTwoNeighbors = 0;
  byte neighborCount = 0;
  byte p; 
  
  setColor(WHITE);

  // Make sure that block has not more than 2 neighbors
  for (p = 0 ; p < NUM_PORTS ; p++) {
    if (!thisNeighborhood.n[p] == VACANT) neighborCount++;
    else continue; 
  }

  if (neighborCount > 2) {
      setColor(RED);
      hasMoreThanTwoNeighbors = 1;
  }
 
  // Wait for an ID message
  while(1);
}

void 
setFirstBlockID(void)
{
  // Set ID received from logger to first block
  uint16_t firstBlockID;
  firstBlockID = (uint16_t)(thisChunk->data[3]) << 8;
  firstBlockID |= thisChunk->data[4];

  // Avoid sending message back to sender
  PRef excluded = faceNum(thisChunk);

  // Store ID on EEPROM and checked that it has been set properly
  setAndStoreUID(firstBlockID);
  checkId(firstBlockID);
 
  // Send next ID to next block 
  PRef nextBlock;
  byte p;
  for (p = 0 ; p < NUM_PORTS ; p++) {
    if (p == excluded || thisNeighborhood.n[p] == VACANT) continue;
    else {
      nextBlock = p;
      break;
    }
  }
  sendNextID(nextBlock, ++firstBlockID);
}

// Set received ID to block and propagate setID message
void 
processIDChange(void) 
{
  // Make sure that block does not have more than two neighbors 
  if (!hasMoreThanTwoNeighbors) {
    // Set ID received from logger to first block
    uint16_t idToSet;
    idToSet = (uint16_t)(thisChunk->data[0]) << 8;
    idToSet |= thisChunk->data[1];
    
    // Avoid sending message back to sender
    PRef excluded = faceNum(thisChunk);
    
    // Store ID on EEPROM and checked that it has been set properly
    setAndStoreUID(idToSet);
    checkId(idToSet);
    
    // Send next ID to next block 
    PRef nextBlock;
    byte p;
    for (p = 0 ; p < NUM_PORTS ; p++) {
      if (p == excluded || thisNeighborhood.n[p] == VACANT) continue;
      else {
	nextBlock = p;
	break;
      }
    }
    sendNextID(nextBlock, ++idToSet);
  }
  else { // Do nothing, warn the user!
#ifdef LOG_DEBUG
    char s[15];
    snprintf(s, 15*sizeof(char), "err: 2+ neighbors!");
    printDebug(s);
#endif
  }
}

void checkId(uint16_t cId)
{
  if (getGUID() == cId) {
    setColor(GREEN); 
  }
  else {
    setColor(RED);
  }
#ifdef LOG_DEBUG
  char s[15];
  snprintf(s, 15*sizeof(char), "id: %u", getGUID());
  printDebug(s);
#endif 
}

byte sendNextID(PRef nextBlock, uint16_t nextID) 
{ 
  Chunk *c = getSystemTXChunk();  
  byte data[2];
  data[0] = (nextID >> 8) & 0x00FF;
  data[1] = (nextID & 0x00FF); 
    
  if (c == NULL) return 0;
  if (sendMessageToPort(c, nextBlock, data, 2, (MsgHandler)&processIDChange, (GenericHandler)&freeMyChunk) == 0) {
    freeChunk(c);
    return 0;
  }
  return 1;
}

void 
freeMyChunk(void)
{
  freeChunk(thisChunk);
}

void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
  registerHandler(EVENT_COMMAND_RECEIVED, (GenericHandler)&setFirstBlockID);
}
