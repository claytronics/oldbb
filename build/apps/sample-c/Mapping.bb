#include "handler.bbh"
#include "data_link.bbh"
#include "led.bbh"
#include "block.bbh"
#include "ensemble.bbh"
#include "clock.bbh"
#include "block_config.bbh"
#include "memory.bbh"

threadvar int x;
threadvar int y;
threadvar int z;
threadvar int distancet;
threadvar bool Dsend[6];
threadvar PRef except;

void
Diffusioncoordinate(PRef except, int x, int y, int z, int distancet)
{
	byte msg[17];
	msg[0] = DIFFUSE_COORDINATE;
	msg[1] = (byte) ((x >> 8) & OxFF);
	msg[2] = (byte) (x & 0xFF);
	msg[3] = (byte) ((y >> 8) & OxFF);
	msg[4] = (byte) (y & 0xFF);;
	msg[5] = (byte) ((z >> 8) & OxFF);
	msg[6] = (byte) (z & 0xFF);
	msg[7] = (byte) ((distancet >> 8) & OxFF);
	msg[8] = (byte) (distancet & 0xFF);

	Chunk* cChunk =getSystemTXChunk();
	
	for (int i = 0; i <NUM_PORTS; i++)
	{
		if(dsend[x] == 1)
		{
			if(sendMessageToPort(cChunk, i, msg, 9,DiffusionHandler, NULL) == 0)
			{
				freeChunk(cChunk);
			}
		}	    
	}	
}



void 
myMain(void)
{
	if (getGUID() == 1) //im master
	{
		x=0;
		y=0;
		z=0;
		distance=0;
		except = NULL;
		printf("id:%d  x=%d y=%d z=%d distance=%d\n",getGUID(),x,y,z,distance);
	
		//diffusion 1st time
			for (int j=0; j<6;j++)
			{
				if(thisNeighborhood.n[i] != VACANT)
	   			{
					Dsend[j] = 1;
				}
				else
				{
					Dsend[j] = 0;
				}
			}
	}
	Diffusioncoordinate(6, x, y, z, distancet+1);

	
while(1);

}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}