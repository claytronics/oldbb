
#include "handler.bbh"
#include "block.bbh"
#include "led.bbh"
#include "ensemble.bbh"
#include "clock.bbh"

#include "block_config.bbh"
#include "memory.bbh"

#ifdef LOG_DEBUG
#include "log.bbh"
#endif

#define DIFFUSE_COORDINATE 3
#define END_MAP 4

threadvar bool dsend[6];

threadvar int16_t px;
threadvar int16_t py;
threadvar int16_t pz;
threadvar int distancet;
threadvar PRef nodetomaster;
threadvar int cpp;
threadvar int vlock;


//handler for all type of message
byte
CoordinateHandler(void)
{

//	printf("lock value:%d for id:%d\n",vlock,getGUID());

	if(thisChunk == NULL)
	{
		return 0;
	}

	//if the type of message if DIFFUSE_COORDINATE
	if(thisChunk->data[0] == DIFFUSE_COORDINATE)
	{
		if (vlock == 0) //check if the block is already mapped
		{
			printf("%d is already in map\n",getGUID());
			EndMap(faceNum(thisChunk));
		}
		else
		{
			vlock = 0; //to say the block is already in the map
		
			//	printf("valeur lock=%d\n",vlock);
			
			px = (int16_t)(thisChunk->data[2]) & 0xFF;
			px |= ((int16_t)(thisChunk->data[1]) << 8) & 0xFF00;

			py = (int16_t)(thisChunk->data[4]) & 0xFF;
			py |= ((int16_t)(thisChunk->data[3]) << 8) & 0xFF00;

			pz = (int16_t)(thisChunk->data[6]) & 0xFF;
			pz |= ((int16_t)(thisChunk->data[5]) << 8) & 0xFF00;

			distancet = (int)(thisChunk->data[8]) & 0xFF;
			distancet |= ((int)(thisChunk->data[7]) << 8) & 0xFF00;
			
			//	printf("lock value=%d\n",vlock);

			//	printf("id:%d  x=%d y=%d z=%d distancetomaster=%d\n",getGUID(),px,py,pz,distancet);
		
			// diffusion 

			nodetomaster = faceNum(thisChunk);
			printf("id=%d nodemaster=%d\n",getGUID(),nodetomaster);

			DiffusionCoordinate(nodetomaster,px,py,pz,distancet);
			
			if(cpp == 0) // if no neighboor
			{
				EndMap(nodetomaster);
			}
		
		}
	}

	//if type of message is END_MAP
	if(thisChunk->data[0] == END_MAP)
	{
		printf("block id:%d received endmap from %d\ and my except is %dn",getGUID(),faceNum(thisChunk),nodetomaster);
		cpp--;
		printf("id: %d wait answer : %d\n",getGUID(),cpp);

		if (cpp == 0 && getGUID() != 1) //if we are not the master
		{
			delayMS(1);
			EndMap(nodetomaster);
			setColor(GREEN);
		}

		if (cpp == 0 && getGUID()==1)//if we are the master
		{
			setColor(RED);
		}

		//debug
		if (cpp == 2)
		{
			setColor(ORANGE);
		}
		if (cpp == 1)
		{
			setColor(BLUE);
		}
		if (cpp == 3)
		{
			setColor(WHITE);
		}
	}

	return 1;
}


//diffuse my coordinate and my distance to all neighboor except my parent

void
DiffusionCoordinate(PRef except, int16_t xx, int16_t yy, int16_t zz, int16_t dd)
{

	byte msg[17];
	msg[0] = DIFFUSE_COORDINATE;

	int16_t bx = xx;
	int16_t by = yy;
	int16_t bz = zz;

	int16_t bd = dd+1;

	Chunk* cChunk = getSystemTXChunk();
	for (PRef k = 0; k < NUM_PORTS; k++)
	{

		if(thisNeighborhood.n[k] != VACANT && k != except)		//test if you have a block on this interface and he is not my parent
		{
			dsend[k] = 1;
			cpp ++;	
		}

	}

	for (int i = 0; i <NUM_PORTS; i++)
	{
		if(dsend[i] == 1) //if thats ok for the previous test 

		{

			printf("message from %d to interface %d my master is %d\n",getGUID(),i,nodetomaster);
			
		//change the coordinate for all differente interface
			if (i==0)
			{
				bz = zz -1;
		//		printf("down oldz=%d newz=%d\n",zz,bz);
			}
			if (i==1)
			{
				by = yy+1;
		//	 	printf("north 1 oldy=%d newy=%d\n",yy,by);
			}
			if (i==2)
			{
				bx = xx +1;
		//	 	printf("east 2 oldx=%d newx=%d\n",xx,bx);
			}
			if (i==3)
			{
				bx = xx -1;
		//		printf("west oldx=%d newx=%d\n",xx,bx);
			}
			if (i==5)
			{
				bz = zz +1  ;
		//		printf("up  oldz=%d newz=%d\n",zz,bz);
			}
			if (i==4)
			{
				by = yy -1;
		//		printf("south oldy=%d newy=%d\n",yy,by);  
			}
		

		msg[1] = (byte) ((bx >> 8) & 0xFF);
		msg[2] = (byte) (bx & 0xFF);
		
		msg[3] = (byte) ((by >> 8) & 0xFF);
		msg[4] = (byte) (by & 0xFF);
		
		msg[5] = (byte) ((bz >> 8) & 0xFF);
		msg[6] = (byte) (bz & 0xFF);

		msg[7] = (byte) ((bd >> 8) & 0xFF);
		msg[8] = (byte) (bd & 0xFF);
	
	//	printf("x=%d y=%d z=%d distancetomaster=%d message from id:%d transmitted to face %d\n",bx,by,bz,bd,getGUID(),i);
		
		if (i != nodetomaster)
		{
		if(sendMessageToPort(cChunk, i, msg, 9, CoordinateHandler, NULL) == 0)
		{
			freeChunk(cChunk);
		}
		
		bx = xx;
		by = yy;
		bz = zz;
	//	printf("reinitialisation test x=%d y=%d z=%d\n",bx,by,bz);
	    	
		}
		}
	}
	// printf("id: %d wait answer : %d\n",getGUID(),cpp);	
	
	//debug
	if (cpp == 2)
	{
		setColor(ORANGE);
	}
	if (cpp == 1)
	{
		setColor(BLUE);
	}
	if (cpp == 0)
	{
	 	setColor(ORANGE); //he have finish with the map
	}	
	if (cpp == 3)
	{
		setColor(WHITE);
	}
}

void
EndMap(int desti)  //send the last message of this protocol
{

	setColor(PURPLE);

	byte msg[17];
	msg[0] = END_MAP;
	
	Chunk* cChunk = getSystemTXChunk();

	if(sendMessageToPort(cChunk, desti, msg, 1, CoordinateHandler, NULL) == 0)
		{
			freeChunk(cChunk);
		}


	printf("end map to %d from %d\n",desti,getGUID());

}


void 
myMain(void)
{

	delayMS(400);
	//test if i'm the master (I suppose the 1 is the master)
	if (getGUID() == 1) 
	{
		px=0;
		py=0;
		pz=0;
		distancet=0;
		vlock = 0;

//		printf("id:%d  x=%d y=%d z=%d distancetomaster=%d\n",getGUID(),px,py,pz,distancet);

		delayMS(400);
	
		//1st time diffusion

		DiffusionCoordinate(6, px, py, pz, distancet);

	}
	// I'm not the master
	else 
	{
//		printf("test lock %d\n",vlock);
		vlock = 1;
		printf("validation not master for id:%d lock=%d\n",getGUID(),vlock);
	}
	


	while(1);

}

void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}