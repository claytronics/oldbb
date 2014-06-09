#include "block.bbh"
#include "block_config.bbh"
#include "memory.bbh"
#include "audio.bbh"
#include <stdlib.h>
#include "span.bbh"
void launchTimeout(void);
void handler(void);
void donefunc(SpanningTree* tree, SpanningTreeStatus status);

byte spFinished;

Timeout tout;
          
SpanningTree* tree;


void myMain(void)
{
  initSpanningTrees(1);
  tree = allocateSpanningTree(1);
  
 /*
  get(tree,id); qui retourne un *tree et égalise tree avec trees[id]
  get(*SpanningTree tree,byte id)
  {
    tree = trees[id];
    return tree;
  }
  */
  spFinished = 0;
  createSpanningTree(tree, donefunc, 2000);
  
  byte data[1];  
  data[0] = 2;
  get(tree,1);
  start :
  while( spFinished != 1){
    setColor(AQUA);
    get(tree,1); 
    
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

void launchTimeout(void)
{

}


void handler(void)
{
  setColor(BLUE);
}



void donefunc(SpanningTree* tree, SpanningTreeStatus status)
{ 
  if( spFinished != 1 ){   
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
   spFinished = 1;
    }
}


void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}