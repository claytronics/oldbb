#include "block.bbh"
#include "block_config.bbh"
#include "memory.bbh"
#include "audio.bbh"
#include <stdlib.h>
#include "span.bbh"

void handler(void);
void donefunc(SpanningTree* tree, SpanningTreeStatus status);

byte spFinished;

          
SpanningTree* tree;


void myMain(void)
{
 // setSpanningTreeDebug(1);
  initSpanningTrees(1);
  tree = allocateSpanningTree(1);
  spFinished = 0;
  createSpanningTree(tree, donefunc, 0);
  
  //byte data[1];  
 // data[0] = 2;
  

  while( spFinished != 1){ //wait for the tree to be created  and updated the tree every time
    setColor(AQUA);
  }
  
  //treeBroadcast(tree,data, 1, handler );
  if ( treeBarrier(tree,1,5000) == 1 )
  {
    setColor(GREEN);
  }  
  else
  {
    setColor(INDIGO);
  }
  while(1);
}


void handler(void)
{
  setColor(BLUE);
}



void donefunc(SpanningTree* tree, SpanningTreeStatus status)
{ 
   

  if(status == COMPLETED)
  {
   if (isSpanningTreeRoot(tree) == 1)
  {
    setColor(YELLOW);
  }
  else
  {
    setColor(WHITE);
    if(tree->numchildren == 0)
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
