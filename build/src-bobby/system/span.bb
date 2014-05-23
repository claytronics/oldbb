#include "span.bbh"
#include "block.bbh"
////////////////////////////////////////////////////////////////
// private functions for spanning tree code, see below for public interface
////////////////////////////////////////////////////////////////

#define MaxSpanTreeId 16

// set by setSpanningTreeDebug
static int debugmode = 0;

// list of ptrs to spanning tree structures
static SpanningTree* trees[MaxSpanTreeId];

// number of already allocated spanning tree structures.
static int maxSpanId = 0;


//private functions
byte sendMySpChunk(byte port, byte *data, byte size, MsgHandler mh);
void screwyou(void);
void iamyourchild(void);
void spComplete( void);
void treeBroadcastMsg(void);
void treeBroadcastBackMsg(void);
void cstHelper(void);

//Timeout for the creation of the spanning tree
Timeout spCreationTimeout;

// this is sent by a potential parent trying to make me a child
// 0: id
// 1: value
void cstHelper(void)
{

  int potentialID = thisChunk->data[0];
  int potentialValue = charToGUID(&(thisChunk->data[1]));
	// if we have value that is less, send back a NACK (screwyou)
        // if we have value that is ==, send back a NACK (screwyou)
      if( (trees[potentialID]->value) <=  potentialValue )
      {
       byte data[2]; 
       data[0] = trees[potentialID]->spantreeid;
       data[1] = trees[potentialID]->value;
       sendMySpChunk(faceNum(thisChunk), data, 2, (MsgHandler)&screwyou); 
      }
       // otherwise recursively start process for all my other ports (meaning, send a cstHelper msg)
      else
      {
	trees[potentialID]->outstanding = 0;
	trees[potentialID]->value = potentialValue;
       trees[potentialID]->parent = faceNum(thisChunk);
	for( byte p = 0; p < NUM_PORTS;p++){
       if (thisNeighborhood.n[p] != VACANT && p != (trees[potentialID]->parent)) {	
       byte data[2];
       data[0] = potentialID;
       data[1] = potentialValue;
       sendMySpChunk(p, data, 2, (MsgHandler)&cstHelper); 
       trees[potentialID]->outstanding++;
       }
       }
       trees[potentialID]->state = WAITING;
       
	// when recusrive procedure is done, make sender my parent and send back an ACK (iamyourchild)
       
       byte data[2];
       data[0] = trees[potentialID]->spantreeid;
       data[1] = trees[potentialID]->value; 
       sendMySpChunk(faceNum(thisChunk), data, 2, (MsgHandler)&iamyourchild); 
       
       if( trees[potentialID]->outstanding == 0)
	{
	  setColor(PINK);
	  trees[potentialID]->state = DONE;
       byte data[2];
       data[0] = trees[potentialID]->spantreeid;
       data[1] = trees[potentialID]->value; 
     //  sendMySpChunk( trees[potentialID]->parent, data, 2, (MsgHandler)&spComplete);   
	}
  
      }     
  
}

void spComplete( void)
{
  int potentialID = thisChunk->data[0];
//  int potentialValue = thisChunk->data[1];
  trees[potentialID]->outstanding--;
  if( trees[potentialID]->outstanding == 0)
  {
  if(  trees[potentialID]->state == DONE )
  {
    byte data[2];
    data[0] = trees[potentialID]->spantreeid;
    data[1] = trees[potentialID]->value; 
    if( trees[potentialID]->parent != 255){
    sendMySpChunk( trees[potentialID]->parent, data, 2, (MsgHandler)&spComplete);   
    }
    else
    {
      return ;
    }
  }
  }
}

void screwyou(void)
{
  int potentialID = thisChunk->data[0];
 // int potentialValue = thisChunk->data[1];
  
	trees[potentialID]->outstanding--;
	if( trees[potentialID]->outstanding == 0)
	{
	  setColor(ORANGE);
	  trees[potentialID]->state = DONE;
	  trees[potentialID]->numchildren = trees[potentialID]->outstanding;
	}
	
}

void iamyourchild(void)
{
  int potentialID = thisChunk->data[0];
 // int potentialValue = thisChunk->data[1];
  
	trees[potentialID]->outstanding--;
	trees[potentialID]->numchildren++;
	trees[potentialID]->children[faceNum(thisChunk)]=1;
	
	if( trees[potentialID]->outstanding == 0)
	{
	  setColor(ORANGE);
	  trees[potentialID]->state = DONE;
	  trees[potentialID]->numchildren = trees[potentialID]->outstanding;
	}
	
}

byte sendMySpChunk(byte port, byte *data, byte size, MsgHandler mh) 
{ 
  Chunk *c=getSystemTXChunk();
 
  if (sendMessageToPort(c, port, data, size, mh, NULL) == 0) {
    freeChunk(c);
    return 0;
  }
  return 1;
}

void spCreation(void)
{
  int id = spCreationTimeout.arg;
 
  if(trees[id]->state != DONE )
  {
   trees[id]->mydonefunc(id,TIMEDOUT);
  }
  else
  {
   trees[id]->mydonefunc(id,COMPLETED);
  }
  
  if (isSpanningTreeRoot(trees[id]) == 1)
  {
    setColor(YELLOW);
  }
  else
  {
    setColor(RED);
  }
  
}


void barrierMsg(void){
  int  id = thisChunk->data[0]; 
 treeBarrier(trees[id],id,200); 
}



void treeBroadcastMsg(void){
  int  spID = thisChunk->data[0];
  int  size = thisChunk->data[1];
  byte buf[size+2];
  buf[0] = spID;
  buf[1] = size;
  buf[2] = thisChunk->data[2];
  memcpy(buf+2, thisChunk->data+2, size*sizeof(byte));
  trees[spID]->outstanding = 0;
    for( byte p = 0; p < NUM_PORTS;p++){
       if (trees[spID]->children[p] == 1) {	
      sendMySpChunk(p, buf, size, (MsgHandler)&treeBroadcastMsg); 
      trees[spID]->outstanding++;
       }
       }
    if( trees[spID]->outstanding == 0 ){
	trees[spID]->state = DONE;
	byte data[1];
	data[0] = spID;
	sendMySpChunk(trees[spID]->parent, data, 1, (MsgHandler)&treeBroadcastBackMsg); 
       }  
}

void treeBroadcastBackMsg(void){
  int  spID = thisChunk->data[0];
  trees[spID]->outstanding --;
    if( trees[spID]->outstanding == 0 ){
      if(!isSpanningTreeRoot){
      byte data[1];
      data[0] = spID;
      sendMySpChunk(trees[spID]->parent, data, 1, (MsgHandler)&treeBroadcastBackMsg); 
      }	
      trees[spID]->state = DONE;
    }
}

////////////////////////////////////////////////////////////////
// public interface to spanning tree code
////////////////////////////////////////////////////////////////

// for debugging to have some determinism
// 0 -> no debugging, random ids
// 1 -> above + colors for states
// 2 -> above + send log msgs back to host
void setSpanningTreeDebug(int val)
{
}

// allocate a set of num spanning trees.  If num is 0, use system default.
// returns the base of num spanning tree to be used ids.
int initSpanningTrees(int num)
{
 
  if((maxSpanId + num) < MaxSpanTreeId)
  {
    for(int i = 0; i<num ;i++)
    {
      trees[maxSpanId] = allocateSpanningTree(0);
    }
    return (maxSpanId-num);
  }
  else
  {
      return -1; //the wanted allocation number exceed the maximun
  }
    

}

// get a new spanning tree structure for tree with id, id.
// if id == 0 -> get me next legal spanning tree
SpanningTree* allocateSpanningTree(int id)
{
  if( !id ){
    if( maxSpanId != 17 ){
  SpanningTree* ret = (SpanningTree*)malloc(sizeof(SpanningTree));
    ret->spantreeid = maxSpanId;		/* the id of this spanning tree */
    ret->state= WAITING;			/* state i am in forming the spanning tree */
    return ret;
    }
    else
    {
      return NULL;
    }
  }
  else
  {
    if( trees[id] == NULL)
    {
    SpanningTree* ret = (SpanningTree*)malloc(sizeof(SpanningTree));
    ret->spantreeid = id;		
    ret->state= WAITING;
    return ret;
    }
    else
    {
      return NULL;
    }
  }
}

// start a spanning tree with id#, id, where I am the root.  Must be starte by only one node.
// if timeout == 0, never time out
void startTreeByParent(SpanningTree* tree, int id, SpanningTreeHandler donefunc, int timeout)
{
 //  niy("startTreeByParent");
}

// start a spanning tree with a random root, more than one node can initiate this.  
// if timeout == 0, never time out
void createSpanningTree(SpanningTree* tree, SpanningTreeHandler donefunc, int timeout)
{
	delayMS(500);
	byte id = tree->spantreeid;
	trees[id] = tree;
	// set the state to STARTED
	trees[id]->state = STARTED;
	//done function for the spanning tree
	trees[id]->mydonefunc = donefunc;
	//initialize parent
	trees[id]->parent = 255;
	// pick a tree->value = rand()<<8|myid (unless debug mode, then it is just id)
	trees[id]->value = rand()<<8|getGUID();
	// set tree->outstanding = counter to number of neighbors.
	// send to all neighbors: tree->id, tree->value, cstHelper
	trees[id]->outstanding = 0;
	 byte data[2];
       data[0] = id;
       GUIDIntoChar(trees[id]->value, &(data[1]));
       // tree->state = WAITING;
	for( byte p = 0; p < NUM_PORTS;p++){
       if (thisNeighborhood.n[p] != VACANT) {	
       trees[id]->outstanding++;
       sendMySpChunk(p, data, 2, (MsgHandler)&cstHelper); 
       }
       }
      
	// if timeout > 0, set a timeout
	if(timeout > 0)
	{
      spCreationTimeout.callback = (GenericHandler)(&spCreation);
      spCreationTimeout.arg = trees[id]->spantreeid;
      spCreationTimeout.calltime = getTime() + timeout;
      registerTimeout(&spCreationTimeout); 
	}	
	// all done
}


// send msg in data to everyone in the spanning tree, call handler when everyone has gotten the msg
void treeBroadcast(SpanningTree* tree, byte* data, byte size, MsgHandler handler)
{
  tree->outstanding = 0;
  
  byte buf[size+2];
  buf[0] = tree->spantreeid;
  buf[1] = size;
  memcpy(buf+2, data, size*sizeof(byte));
  
  if( isSpanningTreeRoot(tree)) 
  {
    for( byte p = 0; p < NUM_PORTS;p++){
       if (tree->children[p] == 1) {
      sendMySpChunk(p, buf, size+2, (MsgHandler)&treeBroadcastMsg); 
       tree->outstanding++;
       }
       }
      while( tree->outstanding != 0)
      {
      }
      handler();
  }
  
}


// wait til everyone gets to a barrier.  I.e., every node in spanning
// tree calls this function with the same id.  Will not return until
// done or timeout secs have elapsed.  If timeout is 0, never timeout.
// return 1 if timedout, 0 if ok.
int treeBarrier(SpanningTree* tree, int id, int timeout)
{
 /* barrierTimeout.callback = (GenericHandler)(&checkBarrier);
  barrierTimeout.arg = id;
  barrierTimeout.calltime = getTime() + timeout;
  registerTimeout(&barrierTimeout); 
  byte buf[0];
  buf[0] = id;
  if( trees[id]->outstanding == 0 ){
    deregisterTimeout(&barrierTimeout);
  if( isSpanningTreeRoot(tree))
  {
    return 0;
  }
  else{
    sendMySpChunk(trees[id]->parent, buf, size, (MsgHandler)&barrierMsg); 
    return 0;
  }
  }
  else
  {
   
  }*/
}


// find out if I am root
int isSpanningTreeRoot(SpanningTree* tree)
{
  int id= tree->spantreeid;
  if(trees[id]->parent == 255)
  {
    return 1;
  }
  else
  {
    return 0;
  }
}

