
#include "block.bbh"

void myMain(void)
{
  int input;
  static Time i = 1000;
  
  setColor(ORANGE);
  
  while(1)
    {
      input = debugGetChar(&debug);
      if(input != -1)
	{
	  switch(input)
	    {
	    case 'R':
	      printf("Rebooting into bootloader!\r\n");
	      jumpToBootSection();
	      break;
	    default:
	      printf("Unknown input %x\r\n",input);
	    }
	}

      if(i < getTime()) 
	{
	  i += 1000;
	  setNextColor();
	  printf("Time is %ld\r\n",getTime());
	  	  
	}
    }	
}

void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
