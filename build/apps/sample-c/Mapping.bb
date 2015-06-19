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

threadvar int px;
threadvar int py;
threadvar int pz;
threadvar int distancet;

byte
CoordinateHandler(void)
{
	if(thisChunk == NULL)
	{
		return 0;
	}

	if(thisChunk->data[0] == DIFFUSE_COORDINATE)
	{

		px = (int)(thisChunk->data[2]) & 0xFF;
		px |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;

		py = (int)(thisChunk->data[4]) & 0xFF;
		py |= ((int)(thisChunk->data[3]) << 8) & 0xFF00;

		pz = (int)(thisChunk->data[6]) & 0xFF;
		pz |= ((int)(thisChunk->data[5]) << 8) & 0xFF00;

		distancet = (int)(thisChunk->data[8]) & 0xFF;
		distancet |= ((int)(thisChunk->data[7]) << 8) & 0xFF00;

		printf("id:%d  x=%d y=%d z=%d distance=%d\n",getGUID(),px,py,pz,distancet);

	}

	return 1;
}


void
DiffusionCoordinate(PRef except, int xx, int yy, int zz, int dd)
{
	byte msg[17];
	msg[0] = DIFFUSE_COORDINATE;

	dd++;

	Chunk* cChunk = getSystemTXChunk();
	
	for (int i = 0; i <NUM_PORTS; i++)
	{

		int bx = xx;
		int by = yy;
		int bz = zz;

		if (i==0)
		{
			bz = zz-1;
			 printf("interface 0 oldz=%d newz=%d\n",zz,bz);
		}
		else if (i==1)
		{
			by = yy+1;
			 printf("interface 1 oldy=%d newy=%d\n",yy,by);
		}
		else if (i==2)
		{
			 bx++;
			 printf("interface 2 oldx=%d newx=%d\n",xx,bx);
		}
		else if (i==3)
		{
			bx = xx -1;
			printf("interface 3 oldx=%d newx=%d\n",xx,bx);
		}
		else if (i==4)
		{
			by = yy -1;
			printf("interface 4 oldy=%d newy=%d\n",yy,by);
		}
		else if (i==5)
		{
			bz = zz+1;
			printf("interface 5 oldz=%d newz=%d\n",zz,bz);
		}

		msg[1] = (byte) ((bx >> 8) & 0xFF);
		msg[2] = (byte) (bx & 0xFF);
		
		msg[3] = (byte) ((by >> 8) & 0xFF);
		msg[4] = (byte) (by & 0xFF);
		
		msg[5] = (byte) ((bz >> 8) & 0xFF);
		msg[6] = (byte) (bz & 0xFF);

		msg[7] = (byte) ((dd >> 8) & 0xFF);
		msg[8] = (byte) (dd & 0xFF);
		printf("x=%d y=%d z=%d message send to %d\n",bx,by,bz,i);

		if(sendMessageToPort(cChunk, i, msg, 9, CoordinateHandler, NULL) == 0)
		{
			freeChunk(cChunk);
		}
		printf("x=%d y=%d z=%d message was send to %d\n",bx,by,bz,i);	    
		bx = xx;
		by = yy;
		bz = zz;
		printf("test reinitialization x=%d y=%d z=%d\n",bx,by,bz);
		
	
	}	
}


void 
myMain(void)
{

	delayMS(200);

	if (getGUID() == 1) //im master
	{

		px=0;
		py=0;
		pz=0;
		distancet=0;

		printf("id:%d  x=%d y=%d z=%d distance=%d\n",getGUID(),px,py,pz,distancet);

		delayMS(400);
	
		//1st time diffusion

		DiffusionCoordinate(6, px, py, pz, distancet);

	}


	while(1);

}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}