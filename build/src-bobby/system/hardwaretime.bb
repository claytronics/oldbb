#include "hardwaretime.bbh"
#include "../hw-api/hwTime.h"
#include "assert.h"

threadvar Timeout* thisTimeout;		// global var referencing current timeout struct for callbacks
threadvar Timeout* timeoutList;	// semi-private data, do not modify outside of this file
threadvar Timer* timerList;		// semi-private data, do not modify outside of this file

#ifdef BBSIM
extern void yieldTil(Time x);
#endif
#include "../sim/sim.h"

void delayMS(int ms) 
{
  Time until = getTime() + ms;
	
  while(getTime() < until) {
#ifdef BBSIM
    yieldTil(until);
#endif
  }
}

Time getTime()
{
	return getHWTime();
}

void checkTimeout()
{
  if(timeoutList != NULL) {
    Time now = getTime();
		
    do {
      // check list, remove timer and call function
      if(now >= timeoutList->calltime) {
        // set reference variable, remove timeout from list
        thisTimeout = timeoutList;
        timeoutList = timeoutList->next;

        // if timeout was not pre-emptively disabled, disable it, execute callback();
        if(thisTimeout->state != INACTIVE) {
          // disable callback until reactivated/reinserted into list.
          thisTimeout->state = INACTIVE;
				   
          (thisTimeout->callback)();	
        }
      } else {
        // stop searching list
        break;
      }
			
    } while (timeoutList != NULL);
  }
}

threadvar int maxTimeouts = 1;

int 
registerTimeout(Timeout * t)
{
  maxTimeouts++;
  t->next = NULL;

  if(timeoutList == NULL) {
    timeoutList = t;
  } else {
    Timeout * prev = NULL;
    Timeout * cur;
		
    cur = timeoutList;
		
    int failsafe = 0;
    while((cur->calltime < t->calltime) && (cur->next != NULL)) {
      failsafe++;
      if (failsafe > maxTimeouts) {
        blockprint(stderr, "we must have creaed a cycle in timeout\n");
        int i;
        cur = timeoutList;
        for (i=0; i<maxTimeouts; i++) {
          blockprint(stderr, "time: %d, callback:%p, state=%d, arg=%d, ->%p\n", 
                     cur->calltime, cur->callback, cur->state, cur->arg, cur->next);
        }
        assert(failsafe < maxTimeouts);
      }
      prev = cur;
      cur = cur->next;
    }		
		
    if(cur->calltime >= t->calltime) {
      if(prev == NULL) {
        timeoutList = t;
      } else {
        prev->next = t;
      }
      t->next = cur;
    } else {
      cur->next = t;
      t->next = NULL;
    }
  }
	
  t->state = ACTIVE;
	
  // debugging code to check that we don't create a cicrcular list
  int failsafe = 0;

  Timeout* cur = timeoutList;
  while(cur->next != NULL) {
    failsafe++;
    if (failsafe > maxTimeouts) {
      blockprint(stderr, "we just created a cycle in timeout\n");
      int i;
      cur = timeoutList;
      for (i=0; i<maxTimeouts; i++) {
        blockprint(stderr, "time: %d, callback:%p, state=%d, arg=%d, ->%p\n", 
                   cur->calltime, cur->callback, cur->state, cur->arg, cur->next);
      }
      assert(failsafe < maxTimeouts);
    }
    cur = cur->next;
  }		

  return 1;	
}

int deregisterTimeout(Timeout * t)
{
	if(timeoutList == NULL)
	{		
		return 0;
	}
	else
	{
		Timeout * prev = NULL;
		Timeout * cur;

		cur = timeoutList;
		

		while((cur != NULL) && (t != cur)) //(cur->callback != t->callback) && (cur->calltime != t->calltime))
		{
			prev = cur;
			cur = cur->next;
		}

		
		if(cur == NULL)
		{
			return 0;
		}
		else
		{
			if(prev == NULL)
			{
				timeoutList = cur->next;
			}
			else
			{
				prev->next = cur->next;
			}
			
			t->state = INACTIVE;
			
			return 1;
		}
	}
}

int deregisterTimeoutByHandler(GenericHandler h)
{
	if(timeoutList == NULL)
	{		
		return 0;
	}
	else
	{
		Timeout * prev = NULL;
		Timeout * cur;

		cur = timeoutList;
		
		while((cur != NULL) && (cur->callback != h))
		{
			prev = cur;
			cur = cur->next;
		}
		
		if(cur == NULL)
		{
			return 0;
		}
		else
		{
			if(prev == NULL)
			{
				timeoutList = cur->next;
			}
			else
			{
				prev->next = cur->next;
			}
			
			cur->state = INACTIVE;
			
			return 1;
		}
	}

}

void checkTimer()
{
	Timer * tt = timerList;
	
	while(tt != NULL)
	{
		if(tt->state == ACTIVE)
		{
			if((tt->t).state == INACTIVE)
			{
				(tt->t).calltime = getTime() + tt->period;
				registerTimeout(&(tt->t));
				
			}
		}
	
		tt = tt->next;
	}
}

int registerTimer(Timer * tt)
{
  tt->next = NULL;

	if(timerList == NULL)
	{
		timerList = tt;
		
	}
	else
	{
		Timer * cur = timerList;
		
		
		while(cur->next != NULL)
		{
		  assert(tt != cur);
			cur = cur->next;
		}
		assert(tt != cur);
		
		cur->next = tt;
	}
	
	tt->state = ACTIVE;	
	
	return 1;
}

int deregisterTimer(Timer * tt)
{
	if(timerList == NULL)
	{
		return 0;
	}
	else
	{
		Timer * prev = NULL;
		Timer * cur = timerList;
	
		while(cur != NULL && cur != tt)
		{
			prev = cur;
			cur = cur->next;
		}
		
		if(cur == NULL)
		{
			return 0;
		}
		else
		{
			if(prev == NULL)
			{
				timerList = cur->next;
			}
			else
			{
				prev->next = cur->next;
			}
			cur->next = NULL;
			cur->state = INACTIVE;
			
			return 1;
		}
	}
}

// attempts to deregister the timer and its timeout from both queues.
// returns the sum of the component deregistrations.
int clearTimer(Timer * tt)
{

  int ret = deregisterTimer(tt);


  if(tt != NULL)
    {
      ret += deregisterTimeout(&(tt->t));
    }

  return ret;
}

void initTime()
{
	timeoutList = NULL;
	thisTimeout = NULL;
	timerList = NULL;
	
	initHWTime();
}	


// Local Variables:
// mode: c
// tab-width: 8
// indent-tabs-mode: nil
// c-basic-offset: 2
// End:
