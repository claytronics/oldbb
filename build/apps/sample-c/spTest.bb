#include "block.bbh"
#include "block_config.bbh"
#include "memory.bbh"
#include "audio.bbh"
#include <stdlib.h>
#include "span.bbh"
#include "../sim/sim.h"

void handler(void);
void donefunc(SpanningTree* treee, SpanningTreeStatus status);



void 
myMain(void)
{
  volatile byte spFinished;
  SpanningTree* tree;
  int baseid;

  delayMS(1000);
  // setSpanningTreeDebug(1);
  blockprint(stderr, "init\n");
  baseid = initSpanningTrees(1);
  blockprint(stderr, "get\n");
  tree = getTree(baseid);
  spFinished = 0;
  blockprint(stderr, "create\n");
  createSpanningTree(tree, donefunc, 0);
  blockprint(stderr, "return\n");

  //byte data[1];  
  // data[0] = 2;
  
  while( spFinished != 1){ //wait for the tree to be created  and updated the tree every time
    char buffer[512];
    setColor(AQUA);
    blockprint(stderr, "%d: waiting: %s\n", blockTickRunning, tree2str(buffer, baseid));
    delayMS(100);
  }
  blockprint(stderr, "finished\n");  
  //treeBroadcast(tree,data, 1, handler );
  if ( treeBarrier(tree,1,5000) == 1 )
    {
      setColor(GREEN);
    }  
  else
    {
      setColor(INDIGO);
    }
  pauseForever();
  while(1);
}


void handler(void)
{
  setColor(BLUE);
}



void donefunc(SpanningTree* treee, SpanningTreeStatus status)
{ 
   
  blockprint(stderr, "DONEFUNC: %d %d\n", treee->spantreeid, status);

  if(status == COMPLETED)
  {
   if (isSpanningTreeRoot(treee) == 1)
  {
    setColor(YELLOW);
  }
  else
  {
    setColor(WHITE);
    if(treee->numchildren == 0)
    {
      setColor(PINK);
    }
  }
  }
  
  else
  { 
    setColor(RED);
  }

}


void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}

// Local Variables:
// mode: c
// tab-width: 8
// indent-tabs-mode: nil
// c-basic-offset: 2
// End:
