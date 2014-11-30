#include "block.bbh"
#include "block_config.bbh"
#include "memory.bbh"
#include "audio.bbh"
#include <stdlib.h>
#include "../sim/sim.h"
#include "myassert.h"
#include "span.bbh"
////////////////////////////////////////////////////////////////
// test program
////////////////////////////////////////////////////////////////

void handler(void);

void donefunc(SpanningTree* spt, SpanTreeState status)
{ 
  blockprint(stderr, "DONEFUNC: %d %d\n", spt->spantreeid, status);
}

threadvar uint16_t rootId;
threadvar byte gotmsg = 0;

void
setRootColor(byte* msg)
{
  setColor(AQUA);
  rootId = charToGUID(msg);  
  blockprint(stderr, "Setting root's id to %d\n", rootId);
  gotmsg = 1;
}

void
setChildColor(byte* msg)
{
  setColor(PURPLE);
}

void showStatus(void);

void
stablize(void)
{
  byte lastCount = 0;
  int counter = 0;
  while (counter++ < 1) {
    if (lastCount != getNeighborCount()) {
      lastCount = getNeighborCount();
      counter = 0;
    }
    delayMS(100);
  }
}

void 
myMain(void)
{
  SpanningTree* tree;
  int baseid;

  stablize();

  IFSIMDEBUG(1) {
    onBlockTick = showStatus;
  }
  // setSpanningTreeDebug(1);
  blockprint(stderr, "init\n");
  baseid = initSpanningTrees(1);
  tree = getTree(baseid);
  createSpanningTree(tree, donefunc, 0, 0, 0);
  blockprint(stderr, "return\n");
  setColor(AQUA);
  
  blockprint(stderr, "finished\n");  
  if ( treeBarrier(tree,5000) == 1 )
    {
      setColor(GREEN);
    }  
  else
    {
      setColor(RED);
    }
  treeBarrier(tree, 0);
  setColor(YELLOW);
  treeBarrier(tree, 0);
  setColor(GREEN);

  delayMS(1000);

  byte data[2];
  int myid = getGUID();
  GUIDIntoChar(myid, data);
  if (isSpanningTreeRoot(tree)) {
    blockprint(stderr, "Root is %d\n", myid);
    treeBroadcast(tree, data, 2, setRootColor);
    setColor(AQUA);
    rootId = myid;
    gotmsg = 1;
  }
  while (gotmsg == 0) delayMS(1000);
  blockprint(stderr, "The root's id is: %d\n", rootId);
  delayMS(1000);
  if ((myid == 5)&&(!isSpanningTreeRoot(tree))) {
    treeBroadcast(tree, data, 2, setChildColor);
  } else if ((myid == 4)&&(rootId == 5)) {
    treeBroadcast(tree, data, 2, setChildColor);
  }
  
  // get count of nodes
  if (isSpanningTreeRoot(tree)) {
    int count = treeCount(tree, 0);
    blockprint(stderr, "Number of nodes in tree = %d\n", count);
  }

  pauseForever();
  while(1);
}


void handler(void)
{
  setColor(BLUE);
}

void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}

threadvar int stcounter = 0;

void showTree(SpanningTree* st);

void
showStatus(void)
{
  char buffer[512];
  if (stcounter++ > 1000) {
    stcounter = 0;
    int i;
    for (i=0; i<MAX_SPANTREE_ID; i++) {
      if (trees[i] != 0) {
        DEBUGPRINT(0, "btstat: %s\n", tree2str(buffer, i));
        showTree(trees[i]);
      }
    }
  }
}

extern void showTreeNodes(Block* block, int id, char* bp, int depth);


void
showTree(SpanningTree* st)
{
  if (st->kind != Root) return;
  char buffer[2048];
  showTreeNodes(this(), st->spantreeid, buffer, 0);
  blockprint(stderr, "Current Tree:\n%s\n", buffer);
}

#include "../../tree.h"

// Local Variables:
// mode: c
// tab-width: 8
// indent-tabs-mode: nil
// c-basic-offset: 2
// End:
