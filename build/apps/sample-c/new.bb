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

threadvar volatile uint16_t nodecount = 0;

void
setCount(byte* msg)
{
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  nodecount = *((uint16_t*)msg);
  blockprint(stderr, "nodecount = %d\n", nodecount);
}

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

#ifdef BBSIM
  IFSIMDEBUG(1) {
    onBlockTick = showStatus;
  }
#endif

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
      setColor(YELLOW);
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


  treeBarrier(tree, 0);
  setColor(RED);
  delayMS(1000);


  treeBarrier(tree, 0);
  setColor(GREEN);
  delayMS(1000);
 
  /*---------------------------------------------------
  // get count of nodes
  if (isSpanningTreeRoot(tree)) {
  	setColor(YELLOW);
	  delayMS(2000);

    int count = treeCount(tree, 0);
    nodecount = count;
    treeBroadcast(tree, &nodecount, 2, setCount);
    blockprint(stderr, "Number of nodes in tree = %d\n", count);

  	setColor(GREEN);
  }
  treeBarrier(tree, 0);
  setColor(GREEN);
  delayMS(1000);
//---------------------------------------------*/

  // get count of nodes
  if (isSpanningTreeRoot(tree)) {
    int count = treeCount(tree, 0);

    blockprint(stderr,"-------------------------\n");
    setColor(RED);
    delayMS(1000);
    setColor(YELLOW);
    delayMS(1000);
    setColor(GREEN);

    nodecount = count;
    treeBroadcast(tree, (byte*)&nodecount, 2, setCount);
    blockprint(stderr, "Number of nodes in tree = %d\n", count);
  }


  while (nodecount == 0) delayMS(10);

  setColor(GREEN);
  while (1) {
    setColor(YELLOW);
    delayMS(1000);
    setColor(GREEN);
  }
  while (nodecount == 0) delayMS(10);

  setColor(BLUE);
  while (1) {
    setColor(YELLOW);
    delayMS(1000);
    setColor(BLUE);
  }
#ifdef BBSIM
  pauseForever();
  while(1);
#endif
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
#ifdef BBSIM
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
#endif
}

#ifdef BBSIM
extern void showTreeNodes(Block* block, int id, char* bp, int depth);
#endif

void
showTree(SpanningTree* st)
{
#ifdef BBSIM
  if (st->kind != Root) return;
  char buffer[2048];
  showTreeNodes(this(), st->spantreeid, buffer, 0);
  blockprint(stderr, "Current Tree:\n%s\n", buffer);
#endif
}

#ifdef BBSIM
# include "../../tree.h"
#endif

// Local Variables:
// mode: c
// tab-width: 8
// indent-tabs-mode: nil
// c-basic-offset: 2
// End:
