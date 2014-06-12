/** @file viewer.c
 *  
 *  The entry point for the project.  This file contains the window
 *  initialization and mouse input handling.
 *  
 *  @author Bobby Prochnow (rprochno)
 *  @bugs No known bugs.
 */

/* - OpenGL Libraries - */
#ifdef __APPLE__
#include <OpenGL/gl.h>
#include <OpenGL/glu.h>
#include <GLUT/glut.h>
#else
#include <GL/gl.h>
#include <GL/glu.h>
#include <GL/glut.h>
#endif

/* - Standard Libraries - */
#include <math.h>
#include <stdlib.h>
#include <unistd.h>

/* - Rendering Library - */
#include "render.h"

/* - Menu Library - */
#include "menu.h"

/* - Click Processing Library - */
#include "click.h"

/* - Block Libraries -*/
#include "block.h"

#define ACCEL_LOCK_KEY 'z'
#define VIEW_RESET_KEY 'x'

/* - Mouse State Variables - */
int g_vMousePos[2]       = {0, 0};
int g_iLeftMouseButton   = 0;    /* 1 if pressed, 0 if not */
int g_iMiddleMouseButton = 0;
int g_iRightMouseButton  = 0;

/* - Program Control States - */
typedef enum { ROTATE, TRANSLATE } CONTROLSTATE;
CONTROLSTATE g_ControlState = ROTATE; // initial state

/* - Accelerometer Variable (?) 
  This variable doesn't seem to actually do anything. 
  It toggles when you press the z key, but does nothing
  else in this routine. */
int accelActive = 0;                  // initial state

/* - Current Transformation State - */
float g_vLandRotate[3]    = {0.0, 0.0, 0.0};
float g_vLandTranslate[3] = {0.0, 0.0, -10.0};

/* - Function Prototypes - */
void display ();
void do_idle ();
void window_init ();
void callback_init ();
void mouse_drag ();
void mouse_idle ();
void mouse_button ();
void process_tap(int x, int y);
void add_block (int screenX, int screenY);
void remove_block (int screenX, int screenY);

/** @brief Model display function.
 *  
 *  @return Void.
 */
void display()
{
    clear_screen();
    set_transform(g_vLandRotate, g_vLandTranslate);
    redraw_world();
    glutSwapBuffers();    
}

/** @brief Idle callback function.
 *  
 *  Posts for the scene to redisplay.
 *  
 *  @return Void.
 */
void do_idle()
{
    glutPostRedisplay();
}

/** @brief Mouse drag callback function.
 *  
 *  Converts mouse drags into information about 
 *  rotation/translation/scaling.
 *  
 *  @param x The x position of the mouse.
 *  @param y The y position of the mouse.
 *  @return Void.
 */
void mouse_drag(int x, int y)
{
    int vMouseDelta[2] = {x-g_vMousePos[0], y-g_vMousePos[1]};

    switch (g_ControlState)
    {
	case ROTATE:
	    if (g_iLeftMouseButton)
	    {
	    	g_vLandRotate[0] += vMouseDelta[1];
	    	g_vLandRotate[1] += vMouseDelta[0];
	    }
	    if (g_iMiddleMouseButton)
	    {
	    	g_vLandRotate[2] += vMouseDelta[1];
	    }
	    break;
	case TRANSLATE:
	    if (g_iLeftMouseButton)
	    {
	    	g_vLandTranslate[0] += vMouseDelta[0]*0.01;
	    	g_vLandTranslate[1] -= vMouseDelta[1]*0.01;

	    }
	    if (g_iMiddleMouseButton)
	    {
	    	g_vLandTranslate[2] += vMouseDelta[1]*0.01;
	    }
	    break;
	default:
		break;
    }

    g_vMousePos[0] = x;
    g_vMousePos[1] = y;
}


/** @brief Mouse idle callback function.
 *  
 *  Updates the mouse position.
 *  
 *  @param x The x position of the mouse.
 *  @param y The y position of the mouse.
 *  @return Void.
 */
void mouse_idle(int x, int y)
{
    g_vMousePos[0] = x;
    g_vMousePos[1] = y;
}

/** @brief Mouse click callback function.
 *  
 *  Check the mouse button states:
 *     Crtl key -> Zooming
 *     otherwise -> Rotating
 *  
 *  @param button The button being interacted with.
 *  @param state The state of the button.
 *  @param x The x position of the mouse.
 *  @param y The y position of the mouse.
 *  @return Void.
 */
void mouse_button(int button, int state, int x, int y)
{
    switch (button)
    {
	case GLUT_LEFT_BUTTON:
	    g_iLeftMouseButton = (state==GLUT_DOWN);
	    break;
	case GLUT_MIDDLE_BUTTON:
	    g_iMiddleMouseButton = (state==GLUT_DOWN);
	    break;
	case GLUT_RIGHT_BUTTON:
	    g_iRightMouseButton = (state==GLUT_DOWN);
	    break;
    }

    switch(glutGetModifiers())
    {
	case GLUT_ACTIVE_CTRL:
	    g_ControlState = TRANSLATE;
	    break;
	default:
		g_ControlState = ROTATE;
		break;
    }

    if (g_iLeftMouseButton)
        process_tap(x, y);
    else if ((g_iRightMouseButton) && g_ControlState == ROTATE)
	add_block(x, y);
    else if ((g_iRightMouseButton) && g_ControlState == TRANSLATE)
	remove_block(x, y);

    g_vMousePos[0] = x;
    g_vMousePos[1] = y;
}

/** @brief Processes a keyboard press.
 *
 *  @param key The key press.
 *  @param x The x coordinate of the mouse.
 *  @param y The y coordinate of the mouse.
 *  @return Void.
 */
void key_button (unsigned char key, int x, int y)
{
	switch(key)
	{
	case ACCEL_LOCK_KEY:
		accelActive = !accelActive;
		break;
	case VIEW_RESET_KEY:
		g_vLandRotate[0]    = 0.0;
		g_vLandRotate[1]    = 0.0;
		g_vLandRotate[2]    = 0.0;
		g_vLandTranslate[0] = 0.0;
		g_vLandTranslate[1] = 0.0;
		g_vLandTranslate[2] = -10.0;
		break;
	default:
		break;
	}
}

/** @brief Processes a tap event.
 *
 *  @param x The screen x coordinate.
 *  @param y The screen y coordinate.
 *  @return Void.
 */
void process_tap(int x, int y)
{
	Block *block;

	block = click(x,y, NULL);
	if (block == NULL)
		return;

	pthread_mutex_lock(&block->tapMutex);
	fprintf(stderr, "%u tap", block->id);
	block->tapBuffer++;
	pthread_mutex_unlock(&block->tapMutex);
	fprintf(stderr, "ped\n");
}

/** @brief Adds a block on the face that the user clicked.
 *
 *  @param screenX The screen x coordinate.
 *  @param screenY The screen y coordinate.
 *  @return Void.
 */
void add_block (int screenX, int screenY)
{
	Block *block, *newBlock;
	int face;
	int x, y, z;

	face = -1;
	block = click(screenX, screenY, &face);
	if (block == NULL || face == -1)
		return;

	fprintf(stderr, "add block @ %s face %d\r\n", nodeIDasString(block->id, 0), face);

	x = 0;
	y = 0;
	z = 0;
	getPointInDirection(block->x, block->y, block->z,
						face, &x, &y, &z);

	// already a block on this face - this theoretically shouldn't
	// happen, but GL picking is kind of (actually, very) picky
//	if (block->neighbors[face] != NULL)
//		return;

	newBlock = createBlock(x, y, z);
	fprintf(stderr, "block created");
	if (newBlock == NULL)
	{
		fprintf(stderr, "Could not add new block - out of memory\r\n");
		return;
	}

	// run block thread
	startBlock(newBlock);

	fprintf(stderr, "started block @ %s face %d\r\n", nodeIDasString(block->id, 0), face);
}

void showstatus(void)
{
    Block *block;
    int i;

    Q_FOREACH(block, getBlockList(), blockLink)
    {
	fprintf(stderr, "block:%u has: (", block->id);
	int count;
	for(count = 0, i = 0; i < NUM_PORTS; ++i)
	    if(block->thisNeighborhood.n[i] != 0) fprintf(stderr, "%d ", i);
	fprintf(stderr, "\n");
    }
    Q_ENDFOREACH(getBlockList());
}


/** @brief Removes a block on the face that the user clicked.
 *
 *  @param screenX The screen x coordinate.
 *  @param screenY The screen y coordinate.
 *  @return Void.
 */
void remove_block (int screenX, int screenY)
{
	Block *block;
	int face;

	fprintf(stderr, "\n------------ removing\n");
	showstatus();

	face = -1;
	block = click(screenX, screenY, &face);
	if (block == NULL)
		return;

	fprintf(stderr, "remove block @ %s face %d\n", nodeIDasString(block->id, 0), face);
	destroyBlock(block);

	showstatus();
}


/** @brief Calculates accelerometer force.
 *
 *  @param vMouseDelta The mouse movement delta.
 *  @return Void.
 */
void process_accel (float vMouseDelta[])
{
	float vAccelForce[3] = {0, 0, 0};

    switch (g_ControlState)
    {
	case ROTATE:
/*	    if (g_iLeftMouseButton)
	    {
	    	g_vLandRotate[0] += vMouseDelta[1];
	    	g_vLandRotate[1] += vMouseDelta[0];
	    }
	    if (g_iMiddleMouseButton)
	    {
	    	g_vLandRotate[2] += vMouseDelta[1];
	    }
*/	    break;
	case TRANSLATE:
	    if (g_iLeftMouseButton)
	    {
	    	vAccelForce[0] += vMouseDelta[0]*0.01;
	    	vAccelForce[1] -= vMouseDelta[1]*0.01;
	    }
	    if (g_iMiddleMouseButton)
	    {
	    	vAccelForce[2] += vMouseDelta[1]*0.01;
	    }
//	    rotate(vAccelForce, g_vLandRotate);
	    break;
	default:
		break;
    }
}

/** @brief Code entry point.
 * 
 *  Accepts input and performs operations according to the provided 
 *  specification.
 *  
 *  @param argc Number of arguments.
 *  @param argv Array of arguments.
 *  @return Exit code.
 */
int viewer_init (int argc, char ** argv)
{
    glutInit(&argc,argv);

    /* user interface initialization */
    window_init();
    menu_init();
    callback_init();

    /* rendering initialization */
    perspective_init(WINDOW_WIDTH, WINDOW_HEIGHT);
    material_init();
    light_init();
    
    clear_screen();
    
    world_init();

    return 0;
}

/** @brief Event loop for the window.
 *
 *  @return Void.
 */
void event_loop()
{
	glutMainLoop();
}

/** @brief Initializes the window.
 *  
 *  @return Void.
 */
void window_init ()
{
    /* RGB Double buffer with depth buffer */
    glutInitDisplayMode(GLUT_DEPTH | GLUT_RGBA | GLUT_DOUBLE);  
    
    /* Create Window of size 640 x 480 */
    glutInitWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    glutInitWindowPosition(0, 0);
    glutCreateWindow("Blinky Blocks Simulator");
}

/** @brief Initializes the callback functions.
 *  
 *  Installs the mouse, display, and idle callback functions.
 *  
 *  @return Void.
 */
void callback_init ()
{
    /* tells glut to use a particular display function to redraw */
    glutDisplayFunc(display);

    /* replace with any animate code */
    glutIdleFunc(do_idle);
  
    /* callback for mouse drags */
    glutMotionFunc(mouse_drag);
  
    /* callback for idle mouse movement */
    glutPassiveMotionFunc(mouse_idle);
  
    /* callback for mouse button changes */
    glutMouseFunc(mouse_button);

    /* callback for accelerometer locking and other hotkeys */
    glutKeyboardFunc(key_button);
}


