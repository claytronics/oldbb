#define DEFINE_GLOBALS

#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

#include "block.h"
#include "block_dispatch.h"
#include "viewer.h"
#include "sim.h"
#include "config.h"
#include "variable_queue.h"

//SCGextern void vm_init(void);

char* progname;			/* name of this program */
char* configname = 0;		/* name of config file */
int numberOfRobots = 0;		/* number of blinkblocks in system */
bool debug = false;             /* debug-mode */

int lastBlockId = 0;

void __myassert(char* file, int line, char* exp)
{
    fprintf(stderr, "%s:%d: ASSERTION FAILED: %s\n", file, line, exp);
    *(int *)0 = 1;
    exit(-1);
}

void err(char* prompt, ...)
{
   va_list ap;
   va_start(ap,prompt);

   fprintf(stderr, "%s: Error: ", progname);
   vfprintf(stderr, prompt, ap);
   fprintf(stderr, "\n");
   exit(-1);
}

void help(void)
{
  fprintf(stderr, "%s: simulate blocks\n", progname);
  fprintf(stderr, "\t-c <name>:\tfile with initial configuration\n");
  fprintf(stderr, "\t-r generates a random block configuration\n");
  fprintf(stderr, "\t-d debug statements enabled\n");
  fprintf(stderr, "\t-n disables graphics\n");

  exit(0);
}

pthread_mutex_t vminitmutex;
pthread_mutex_t printmutex;
pthread_mutex_t debugmutex;
pthread_mutex_t sendmutex;
pthread_mutex_t checkmutex;
pthread_mutex_t destroymutex;
pthread_cond_t destroycond = PTHREAD_COND_INITIALIZER;

void blockprint(FILE* f, char* fmt, ...)
{
  va_list ap;
  char buffer[128];

  va_start(ap,fmt);
  pthread_mutex_lock(&printmutex);
//  debuginfo(this(), buffer);
  fprintf(f, "%s:(%s) ", nodeIDasString(this()->id, 0), buffer);
  vfprintf(f, fmt, ap);
  fflush(f);
  pthread_mutex_unlock(&printmutex);
  va_end(ap);
}

int main(int argc, char** argv)
{
   // save argv, argc for initialization of GLUT
   char** orig_argv = argv;
   int    orig_argc = argc;
   bool   configured = false;
   bool   graphics = true;

   // create blocklist and initialize mutex
   initBlockList();

   --argc;
   progname = *argv++;
   while (argc > 0 && (argv[0][0] == '-')) {
      switch (argv[0][1]) {
      case 'c':
    	  if (configured)
    		  help();

    	  readConfig(argv[1]);
    	  argc--;  argv++;
    	  configured = true;
    	  break;
      case 'd':
    	  debug = true;
          break;
      case 'n':
   	  graphics = false;
    	  break;
      case 'r':
    	  if (configured)
    		  help();
    	  randomConfig(0);
    	  configured = true;
    	  break;
      case 'R':
    	  if (configured)
    		  help();
    	  argc--;  argv++;
	  int num = atoi(argv[0]);
    	  randomConfig(num);
    	  configured = true;
    	  break;
      default:
    	  help();
      }
      argc--; argv++;
   }

   if (!configured)
	   help();

   if (debug) fprintf(stdout, "initial configuration\n");

   // vm initialization
   //SCG   vm_init();

   // start threads for each block
   pthread_mutex_init(&printmutex, NULL);
   pthread_mutex_init(&vminitmutex, NULL);
   pthread_mutex_init(&debugmutex, NULL);
   pthread_mutex_init(&sendmutex, NULL);
   pthread_mutex_init(&checkmutex, NULL);
   pthread_mutex_init(&destroymutex, NULL);
   pthread_cond_init(&destroycond, NULL);

   Block *block;

   Q_FOREACH(block, getBlockList(), blockLink)
     {
       startBlock(block);
     }
   Q_ENDFOREACH(getBlockList());

   // initialize viewer
   viewer_init(orig_argc, orig_argv);

   // GL loop indefinitely
   event_loop();

   /*
   // join the threads
   void* tstat;
   for (i=0; i<lastBlock; i++) {
     Block* b = blocks[i];
     int status = pthread_join(b->threadID, &tstat);
     if (status) err("Error in join for block %d\n", b->id);
     pthread_mutex_lock(&printmutex);
     if (debug) fprintf(stderr, "%d exited with %p\n", b->id, tstat);
     pthread_mutex_unlock(&printmutex);
   }
   pthread_attr_destroy(&attr);

   // print message stats
   if (debug)
   {
     for (i=0; i<lastBlock; i++) {
       Block* b = blocks[i];
       fprintf(stderr, "%d:", b->id);
       byte j;
       for (j=0; j<MSG_ILLEGAL; j++) {
	 fprintf(stderr, "\t%d", b->mstats[(int)j]);
       }
       fprintf(stderr, "\n");
     }
   }

   */
   // we are all done
   pthread_exit(0);
   return 0;
}

void pauseForever(void)
{
    // pause without killing processor
    pthread_mutex_lock(&destroymutex);
    pthread_cond_wait(&destroycond, &destroymutex); /* Wait for something that never happens */
}

void tellNeighborsDestroyed(Block *b)
{
    int i;
    for(i = 0; i < NUM_PORTS; ++i)
    {
	// see if we have a neighbor at port i
	Block* d = seeIfNeighborAt(b, i);
	if (d != 0) {
	    // if so, then eliminate it from that block
	    int j;
	    for (j=0; j<NUM_PORTS; j++) {
		if (d->thisNeighborhood.n[j] == b->id) {
		    d->thisNeighborhood.n[j] = 0;
		    fprintf(stderr, "%u no longer has %u on port %d\n", d->id, b->id, j);
		}
	    }
	}
    }
}

// will return 0 if never called before, other return 1
int 
alreadyExecuted(int enter)
{
  static int count = 0;
  int r;

  if (enter == 0) {
    pthread_mutex_lock(&vminitmutex);
    r = count++;
    if (r == 0) return 0;
    // we aren't first AND vm_init has run
    pthread_mutex_unlock(&vminitmutex);
    return r;
  } else {
    // we ran vm_init
    pthread_mutex_unlock(&vminitmutex);
    return 0;
  }
}

// for now ignore x and just yield this thread
void yieldTil(Time x)
{
  sched_yield();
}
