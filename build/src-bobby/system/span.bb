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

// to know if the all get to a barrier or nothing
static int allHaveBarrier = 0;

//private functions
byte sendMySpChunk(byte port, byte *data, byte size, MsgHandler mh);
void screwyou(void);
void iamyourchild(void);
void spComplete( void);
void treeBroadcastMsg(void);
void treeBroadcastBackMsg(void);
void cstHelper(void);
byte countChildren(byte id);
void sendToLeaf(void);
void allHaveBarrierMsg(void);

//Timeout for the creation of the spanning tree
Timeout barrierTimeout;

// this is sent by a potential parent trying to make me a child
// 0: id
// 1: value
void cstHelper(void)
{

  byte potentialID = thisChunk->data[0];
  uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
	// if we have value that is less, send back a NACK (screwyou)
        // if we have value that is ==, send back a NACK (screwyou)
      if( trees[potentialID]->value >=  potentialValue )
      {
       byte data[3]; 
       data[0] = trees[potentialID]->spantreeid;
       GUIDIntoChar(trees[potentialID]->value, &(data[1]));
       sendMySpChunk(faceNum(thisChunk), data, 3, (MsgHandler)&screwyou); 
      }
       // otherwise recursively start process for all my other ports (meaning, send a cstHelper msg)
       // when recusrive procedure is done, make sender my parent and send back an ACK (iamyourchild)
      else
      {
       trees[potentialID]->outstanding = 0;
       trees[potentialID]->value = potentialValue;
       trees[potentialID]->parent = faceNum(thisChunk);
       trees[potentialID]->state = HAVEPARENT;
        for( int i = 0; i<NUM_PORTS; i++)
	{
	  trees[potentialID]->children[i] = 0;
	}
       trees[potentialID]->numchildren = countChildren(potentialID);
       trees[potentialID]->state = WAITING;
       byte data[3];
       data[0] = potentialID;
       GUIDIntoChar(trees[potentialID]->value, &(data[1]));
       
	for( byte p = 0; p < NUM_PORTS;p++){
	  if (thisNeighborhood.n[p] != VACANT && p != (trees[potentialID]->parent)) {	
	  trees[potentialID]->outstanding++;
	  sendMySpChunk(p, data,3, (MsgHandler)&cstHelper); 
	  }
	}
       byte buf[3];
       buf[0] = trees[potentialID]->spantreeid;
       GUIDIntoChar(trees[potentialID]->value, &(buf[1]));
       sendMySpChunk((trees[potentialID]->parent), buf, 3, (MsgHandler)&iamyourchild); 
       
       //if has no children send a back message with the value to the root
        if( trees[potentialID]->numchildren == 0)
	{
       byte data[3];
       data[0] = trees[potentialID]->spantreeid;
       GUIDIntoChar(trees[potentialID]->value, &(data[1]));
       if( trees[potentialID]->parent != 255 ){
       sendMySpChunk( trees[potentialID]->parent, data, 3, (MsgHandler)&spComplete);   
       }
	}
      }
      
}
//message back to the root to complete the tree 
void spComplete( void)
{
  byte potentialID = thisChunk->data[0];
  uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
  if( trees[potentialID]->value == potentialValue ){ // if the value potentialValue is different from the tree value just ignore
  if( trees[potentialID]->outstanding != 0){
	trees[potentialID]->outstanding--;
	}
//when received all the messages from the children execute donefunc and send spComplete to parent
  if(trees[potentialID]->outstanding == 0){
  if(  trees[potentialID]->state == DONE )
  {
    deregisterTimeout(&trees[potentialID]->spantimeout);
    byte data[3];
    data[0] = trees[potentialID]->spantreeid;
    GUIDIntoChar(trees[potentialID]->value, &(data[1]));
    if( (trees[potentialID]->parent) != 255){
    sendMySpChunk( trees[potentialID]->parent, data, 3, (MsgHandler)&spComplete);   
    trees[potentialID]->status = COMPLETED;
    trees[potentialID]->mydonefunc(trees[potentialID],COMPLETED);
    }
    else
    {
      //if the root receive the spComplete send message to the leaves to tell them to execute their done function
      trees[potentialID]->status = COMPLETED;
      for(byte i; i<NUM_PORTS; i++){
	if(trees[potentialID]->children[i] == 1){
      sendMySpChunk(i , data, 3, (MsgHandler)&sendToLeaf);   
	}
      }
      trees[potentialID]->mydonefunc(trees[potentialID],COMPLETED);
    }
  }
  }
  }
  
}

//send message to the leaves to launch their mydonefunc
void sendToLeaf(void)
{
  byte potentialID = thisChunk->data[0];
  uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
  byte data[3];
  data[0] = trees[potentialID]->spantreeid;
  GUIDIntoChar(trees[potentialID]->value, &(data[1]));
    
  if(trees[potentialID]->value == potentialValue && trees[potentialID]->numchildren == 0){
    deregisterTimeout(&trees[potentialID]->spantimeout);
    trees[potentialID]->status = COMPLETED;
    trees[potentialID]->mydonefunc(trees[potentialID],COMPLETED);
    return ;
  } 
  for(byte i; i<NUM_PORTS; i++){
	if(trees[potentialID]->children[i] == 1){
	sendMySpChunk(i , data, 3, (MsgHandler)&sendToLeaf);   
	}
      }
  
  
}

void screwyou(void)
{
  
	byte potentialID = thisChunk->data[0];
	uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
	if( potentialValue == trees[potentialID]->value ){
	if( trees[potentialID]->outstanding != 0){
	trees[potentialID]->outstanding--;
	}
	trees[potentialID]->children[faceNum(thisChunk)] = 0;
	trees[potentialID]->numchildren = countChildren(potentialID);
	if( trees[potentialID]->outstanding == 0)
	{
	  trees[potentialID]->state = DONE;
	  trees[potentialID]->outstanding = trees[potentialID]->numchildren;
	   }
	}
	
}


void iamyourchild(void)
{
	byte potentialID = thisChunk->data[0];
	uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
	if( potentialValue == trees[potentialID]->value ){
	if( trees[potentialID]->outstanding != 0){
	trees[potentialID]->outstanding--;
	}
	trees[potentialID]->children[faceNum(thisChunk)] = 1;
	trees[potentialID]->numchildren = countChildren(potentialID);
	
	if( trees[potentialID]->outstanding == 0)
	{
	  trees[potentialID]->state = DONE;
	  trees[potentialID]->outstanding = trees[potentialID]->numchildren;
	}
	}
}

byte countChildren(byte id)
{
  byte count = 0;
  for( int i = 0; i<NUM_PORTS; i++)
  {
    if(trees[id]->children[i] == 1)
    {
      count++;
    }
  }	
  return count;
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

//timeout for the creation of the spanning tree
void spCreation(void)
{
  byte id = trees[id]->spantimeout.arg;
  trees[id]->status = TIMEDOUT;
  trees[id]->mydonefunc(trees[id],TIMEDOUT);
}

//message send to parent if only all the children get into a barrier 
void barrierMsg(void){
  byte  spID = thisChunk->data[0]; 
  trees[spID]->outstanding--; 
  if( trees[spID]->outstanding == 0)
  {
    trees[spID]->status == COMPLETED;
  }
}

//timeout for checking if all the blocks get into a barrier
void checkBarrier(void)
{
  byte id = barrierTimeout.arg;
  trees[id]->status = TIMEDOUT;
}

//message sent from the root to the leaves to say that all the blocks get into a barrier 
void allHaveBarrierMsg(void)
{
  byte  spID = thisChunk->data[0]; 
  trees[spID]->status == COMPLETED;
}

//handler for sending the data to all the tree
void treeBroadcastMsg(void){
  byte  spID = thisChunk->data[0];
  byte  size = thisChunk->data[1];
  byte buf[size + 2];
  buf[0] = spID;
  buf[1] = size;
  memcpy(buf+2, thisChunk->data+2, size*sizeof(byte));//the data will start from buf[2] if users want to use it
    for( byte p = 0; p < NUM_PORTS;p++){ //send data to all the children
       if (trees[spID]->children[p] == 1) {	
      sendMySpChunk(p, buf, size + 2, (MsgHandler)&treeBroadcastMsg); 
       }
       }
   if( trees[spID]->numchildren == 0 ){
	byte data[1];
	data[0] = spID;
	sendMySpChunk(trees[spID]->parent, data, 1, (MsgHandler)&treeBroadcastBackMsg); 
	trees[spID]->broadcasthandler();
       } 
}

//back message handler, when the block receive all the message from its children execute the broadcasthandler
void treeBroadcastBackMsg(void){
  byte  spID = thisChunk->data[0];
  trees[spID]->outstanding --;
    if( trees[spID]->outstanding == 0 ){
      if(isSpanningTreeRoot(trees[spID]) != 1){
	byte data[1];
	data[0] = spID;
	sendMySpChunk(trees[spID]->parent, data, 1, (MsgHandler)&treeBroadcastBackMsg); 
	trees[spID]->broadcasthandler();
	}
      else
	{
	  trees[spID]->broadcasthandler();
	}
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
    maxSpanId ++;
    
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
	delayMS(200);
	setColor(WHITE);
	byte id = tree->spantreeid;
	trees[id] = tree;
	// set the state to STARTED
	trees[id]->state = STARTED;
	trees[id]->status = WAIT;
	//done function for the spanning tree
	trees[id]->mydonefunc = donefunc;
	//initialize parent, every blocks thinks they are root at the beginning 
	trees[id]->parent = 255;
	//initialize children number
	trees[id]->numchildren = 0;
	for( int i = 0; i<NUM_PORTS; i++)
	{
	  trees[id]->children[i] = 0;
	}
	
	// pick a tree->value = rand()<<8|myid (unless debug mode, then it is just id)
	trees[id]->value = rand()<<8|getGUID();
	  
	// set tree->outstanding = counter to number of neighbors.
	// send to all neighbors: tree->id, tree->value, cstHelper
	trees[id]->outstanding = 0;
	byte data[3];
	data[0] = id;
	GUIDIntoChar(trees[id]->value, &(data[1]));
	
	// if timeout > 0, set a timeout
	if(timeout > 0)
	{
	  trees[id]->spantimeout.callback = (GenericHandler)(&spCreation);
	  trees[id]->spantimeout.arg = id;
	  trees[id]->spantimeout.calltime = getTime() + timeout;
	  registerTimeout(&trees[id]->spantimeout); 
	}
	
        tree->state = WAITING;
	//send message to add children
	for( byte p = 0; p < NUM_PORTS;p++){
	  if (thisNeighborhood.n[p] != VACANT) {	
	  trees[id]->outstanding++;
	  sendMySpChunk(p, data, 3, (MsgHandler)&cstHelper); 
	  }
	}
      
	// all done
}


// send msg in data to everyone in the spanning tree, call handler when everyone has gotten the msg
void treeBroadcast(SpanningTree* tree, byte* data, byte size, MsgHandler handler)
{  
  delayMS(500); //wait for the creation of the spanning in case it is not finished
  byte id = tree->spantreeid;      
  trees[id]->broadcasthandler = handler;
  trees[id]->outstanding = trees[id]->numchildren;
     
  //the root will send the data its children and it will be propagate until the leaves
 if( isSpanningTreeRoot(trees[id]) == 1) 
  {
    byte buf[size + 2];
  buf[0] = id;
  buf[1] = size;
  memcpy(buf+2, data, size*sizeof(byte));
 for( byte p = 0; p < NUM_PORTS;p++){
   if (trees[id]->children[p] == 1) {
   sendMySpChunk(p, buf, size + 2 , (MsgHandler)&treeBroadcastMsg); 
   }
   }
  }
}


// wait til everyone gets to a barrier.  I.e., every node in spanning
// tree calls this function with the same id.  Will not return until
// done or timeout secs have elapsed.  If timeout is 0, never timeout.
// return 1 if timedout, 0 if ok.
int treeBarrier(SpanningTree* tree, byte id, int timeout)
{
 delayMS(500);
  byte spID = tree->spantreeid;
  trees[spID]->status = WAIT;
  trees[spID]->outstanding = trees[spID]->numchildren;  
  
  if( timeout > 0){                                  //timeout for creating the barrier
  barrierTimeout.callback = (GenericHandler)(&checkBarrier);
  barrierTimeout.arg = spID;
  barrierTimeout.calltime = getTime() + timeout;
  registerTimeout(&barrierTimeout); 
  }
  byte buf[1];
  buf[0] = spID;
  
  if( trees[spID]->numchildren == 0)
   {
     trees[spID]->status = COMPLETED;
   }
  
  while( trees[spID]->status == WAIT ){ setColor(RED); }   //while the blocks doesn't receive barrier message from all the children do nothing
  if( trees[spID]->status == COMPLETED) //if receive all the message from the children send to parent and deregister because not TIMEDOUT
   {
     trees[spID]->status = WAIT;
     if (isSpanningTreeRoot(trees[spID]) == 1){//if root received all messages from its children, it will send messages to all the tree to tell that all the block get to a barrier
        if(timeout > 0){
      deregisterTimeout(&barrierTimeout); 
      }
      for(byte p = 0; p < NUM_PORTS ; p++){
	if (trees[spID]->children[p] == 1){
      sendMySpChunk(p, buf, 1, (MsgHandler)&allHaveBarrierMsg);   //this message will change the status of the tree into completed
      }}
      return 1;
      }
      else{
      byte buf[1];
      buf[0] = spID;
      sendMySpChunk(trees[spID]->parent, buf, 1, (MsgHandler)&barrierMsg); // send message to parent to make their status COMPLETED
      }
   }
   
  while (  trees[spID]->status == WAIT ){ setColor(BROWN);} //wait for the message from the root which is telling that all the block get into a barrier
  if( trees[spID]->status == COMPLETED )
  {
     if(timeout > 0){
      deregisterTimeout(&barrierTimeout); 
      }
      for(byte p = 0; p < NUM_PORTS ; p++){
	if (trees[spID]->children[p] == 1){
      sendMySpChunk(p, buf, 1, (MsgHandler)&allHaveBarrierMsg);   // send messages to all the tree to tell that all the block get to a barrier
      }}
    return 1;
  }
  if( trees[spID]->status == TIMEDOUT)
  {
    return 0;
  }
  
}


// find out if I am root
byte isSpanningTreeRoot(SpanningTree* tree)
{
  byte id = tree->spantreeid;
  if(trees[id]->parent == 255)
  {
    return 1;
  }
  else
  {
    return 0;
  }
}

