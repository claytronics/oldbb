/** @file render.c
 *
 *  A library for initializing and rendering an OpenGL scene.
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
#include <string.h>

/* - Render Library - */
#include "render.h"

/* - Threading Library - */
#include <pthread.h>
#include <errno.h>

#include "block.h"
#include "variable_queue.h"

/* - Lighting Properties - */
GLfloat light_ambient[]  =   {0.2f, 0.2f, 0.2f, 1.0f};
GLfloat light_diffuse[]  =   {0.5f, 0.5f, 0.5f, 0.5f};
GLfloat light_pos[]      =   {2.0f, 2.0f, 0.0f, 0.0f};
GLfloat light_specular[] =   {0.5f, 0.5f, 0.5f, 0.5f};
 
/* - Material Properties - */
GLfloat mat_shininess[1]  =   {0.0f};
GLfloat mat_specular[4]   =   {0.0f, 0.0f, 0.0f, 0.0f};

GLUquadricObj *quad;

#define BLOCK_SIZE      1.0f

#define PRONG_WIDTH		.2f
#define PRONG_SLICES 	4
#define PRONG_STACKS 	4
#define PRONG_LOOPS  	4
#define PRONG_HEIGHT    .15f
#define PRONG_OFFSET    .25f

/** @brief Initializes lighting for our scene.
 *  
 *  Creates one light and enables lighting for the OpenGL scene.
 *  
 *  @return Void.
 */
void light_init ()
{
    /* initialize GL_LIGHT0 light source */
    glLightfv(GL_LIGHT0, GL_AMBIENT,  light_ambient);
    glLightfv(GL_LIGHT0, GL_DIFFUSE,  light_diffuse);
    glLightfv(GL_LIGHT0, GL_POSITION, light_pos);
    glLightfv(GL_LIGHT0, GL_SPECULAR, light_specular);
        
    glEnable(GL_LIGHTING);
    glEnable(GL_LIGHT0);    
}

/** @brief Initializes the material for the model in our scene.
 *
 *  Gives the model a slightly shiny material, and allows for
 *  the models color to reflect properly.
 *
 *  @return Void.
 */
void material_init ()
{
    glMaterialfv(GL_FRONT, GL_SPECULAR,  mat_specular);
    glMaterialfv(GL_FRONT, GL_SHININESS, mat_shininess);

    glColorMaterial(GL_FRONT, GL_AMBIENT_AND_DIFFUSE);
    glEnable(GL_COLOR_MATERIAL);

    glShadeModel(GL_SMOOTH);

	quad = gluNewQuadric();
	gluQuadricNormals(quad, GLU_SMOOTH);
}

/** @brief Initializes the perspective for our scene.
 *  
 *  Establishes the far and near clipping planes for the perspective,
 *  enables back-face culling, and enables depth buffering.
 *  
 *  @return Void.
 */
void perspective_init (int window_width, int window_height)
{
    glMatrixMode(GL_PROJECTION);
    
    glLoadIdentity();
    glViewport(0, 0, window_width, window_height);
    gluPerspective(FOV, ((float)window_width / (float)window_height),
		   Z_NEAR, Z_FAR);
    
    /* ready to draw models */ 
    glMatrixMode(GL_MODELVIEW);

    /* back-face culling */ 
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    
    /* enable depth buffering */ 
    glDepthFunc(GL_LEQUAL);
    glEnable(GL_DEPTH_TEST);
}

/** @brief Initializes the display lists for our blinky blocks environment.
 *  
 *  Initializes block information for the rendering library.
 * 
 *  @param blocks The array of blocks.
 *  @param block_count The number of blocks in the array.
 *  @return Void.
 */
void world_init ()
{
    /* enable normalization for normal vectors */
    glEnable(GL_NORMALIZE);
    glEnable(GL_RESCALE_NORMAL);
}

/** @brief Set model display mode.
 *
 *  @param The display mode to use.
 *  @return Void.
 */
void set_display_mode (GLenum disp_mode)
{
    glPolygonMode(GL_FRONT_AND_BACK, disp_mode);
}

/** @brief Adds onscreen text directions for using the simulator
 * 
 *  Writes several lines of bitmap characters that describe how to
 *  move around and add and delete blocks.
 *
 * @return Void.
 */
void show_directions ()
{
	int num_lines = 5;
	
	char lines[num_lines][65];
	int i, j;

	// Text to display
	strcpy(lines[0], "Use left and middle mouse buttons to rotate scene.             ");
	strcpy(lines[1], "Use Ctrl with left and middle mouse buttons to translate scene.");
	strcpy(lines[2], "Use the right mouse button on a block to add a neighbor block. ");
	strcpy(lines[3], "Use Ctrl and the right mouse button to delete a block.      ");
	strcpy(lines[4], "Press 'D' to hide or display these directions.                 ");


	// This bit of code changes the color for the text
	glPushMatrix();
	glMatrixMode(GL_MODELVIEW);
	glLoadIdentity();
	glColor3f(0.6f, 0.0f, 0.8f);

	// Draw 5 lines of bitmap char text
	for (i = 0; i < num_lines; i++)
	{
	    glRasterPos2f(-7, -2 - i );
	    for (j = 0; j < 65; j++)
	    {
 	        glutBitmapCharacter(GLUT_BITMAP_HELVETICA_18, lines[i][j]);
	    }
	}
	
	// Change color back to whatever it was before
	glColor3f(1.0, 1.0, 1.0);
	glPopMatrix();
}

/** @brief Redraws the current block world.
 *  
 *  @return Void.
 */
void redraw_world ()
{
	Block *block;
	
	glInitNames();
	glPushName(-1);
	Q_FOREACH(block, getBlockList(), blockLink)
	{
	    draw_block(block);
	}
	Q_ENDFOREACH(getBlockList());

	show_directions();
}


/** @brief Draws a prong on top of a block.
 *
 *  @param x_off The x offset of the prong.
 *  @param z_off The z offset of the prong.
 *  @return Void.
 */
void draw_block_top(GLfloat x_off, GLfloat z_off)
{
	glPushMatrix();
	glTranslatef(x_off, (BLOCK_SIZE/2) + PRONG_HEIGHT, z_off);

	glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
	glRotatef(45.0f, 0.0f, 0.0f, 1.0f);
	gluQuadricOrientation(quad, GLU_OUTSIDE);
	gluCylinder(quad, PRONG_WIDTH, PRONG_WIDTH, 0.15f, PRONG_SLICES, PRONG_STACKS);
	gluQuadricOrientation(quad, GLU_INSIDE);
	gluDisk(quad, 0, PRONG_WIDTH, PRONG_SLICES, PRONG_LOOPS);
	glPopMatrix();
}

/** @brief Draws the body of a block
 *  
 *  @param name The identifier for this block.
 *  @return Void.
 */
void draw_block_body(GLuint name)
{
        // top face
	glLoadName(ENCODE_NAME(name, Top));
        glBegin(GL_QUADS);
	glNormal3f( 0.0, 1.0, 0.0 );
        glVertex3f( BLOCK_SIZE/2, BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2, BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2, BLOCK_SIZE/2, BLOCK_SIZE/2);
        glVertex3f( BLOCK_SIZE/2, BLOCK_SIZE/2, BLOCK_SIZE/2);
        glEnd();

        // bottom face
	glLoadName(ENCODE_NAME(name, Down));
        glBegin(GL_QUADS);
	glNormal3f( 0.0,-1.0, 0.0 );
        glVertex3f( BLOCK_SIZE/2,-BLOCK_SIZE/2, BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2,-BLOCK_SIZE/2, BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2,-BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glVertex3f( BLOCK_SIZE/2,-BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glEnd();

        // front face                                         
	glLoadName(ENCODE_NAME(name, North));
        glBegin(GL_QUADS);
	glNormal3f( 0.0, 0.0, 1.0 );
        glVertex3f( BLOCK_SIZE/2, BLOCK_SIZE/2, BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2, BLOCK_SIZE/2, BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2,-BLOCK_SIZE/2, BLOCK_SIZE/2);
        glVertex3f( BLOCK_SIZE/2,-BLOCK_SIZE/2, BLOCK_SIZE/2);
        glEnd();

        // back face                                          
	glLoadName(ENCODE_NAME(name, South));
        glBegin(GL_QUADS);
	glNormal3f( 0.0, 0.0,-1.0 );
        glVertex3f( BLOCK_SIZE/2,-BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2,-BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2, BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glVertex3f( BLOCK_SIZE/2, BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glEnd();

        // right face                                         
	glLoadName(ENCODE_NAME(name, West));
        glBegin(GL_QUADS);
	glNormal3f( 1.0, 0.0, 0.0 );
        glVertex3f( BLOCK_SIZE/2, BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glVertex3f( BLOCK_SIZE/2, BLOCK_SIZE/2, BLOCK_SIZE/2);
        glVertex3f( BLOCK_SIZE/2,-BLOCK_SIZE/2, BLOCK_SIZE/2);
        glVertex3f( BLOCK_SIZE/2,-BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glEnd();

        // left face                                          
	glLoadName(ENCODE_NAME(name, East));
        glBegin(GL_QUADS);
	glNormal3f(-1.0, 0.0, 0.0 );
        glVertex3f(-BLOCK_SIZE/2, BLOCK_SIZE/2, BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2, BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2,-BLOCK_SIZE/2,-BLOCK_SIZE/2);
        glVertex3f(-BLOCK_SIZE/2,-BLOCK_SIZE/2, BLOCK_SIZE/2);
        glEnd();
}
/** @brief Draws the model of the block
 *
 *  @param name The identifier for this block.
 *  @return Void.
 */
void draw_block_model(GLuint name)
{
	draw_block_body(name);
	
    // draw the top
	glLoadName(ENCODE_NAME(name, Top));
	draw_block_top(PRONG_OFFSET, PRONG_OFFSET);
	draw_block_top(-PRONG_OFFSET, PRONG_OFFSET);
	draw_block_top(PRONG_OFFSET, -PRONG_OFFSET);
	draw_block_top(-PRONG_OFFSET, -PRONG_OFFSET);
}

/** @brief Draws a block in position with the correct color.
 *
 *  @param phys_block Block to draw.
 *  @return Void.
 */
void draw_block (Block *block)
{
/*
	Color r, g, b;
	Intensity i;

	getPhysicalLED(block, &r, &g, &b, &i);

	glPushMatrix();

	glTranslatef(block->x, block->y, block->z);

	glColor4f((float)r / 255.0,
		  (float)g / 255.0,
		  (float)b / 255.0,
		  (float)i / 255.0);
*/



	glPushMatrix();
	glTranslatef(block->x, block->y, block->z);


	glColor4f((float)block->simLEDr /255.0,
		  (float)block->simLEDg /255.0,
		  (float)block->simLEDb /255.0,
		  (float)block->simLEDi /255.0);

	draw_block_model(block->id);

	glPopMatrix();
}

/** @brief Clears the screen.
 *  
 *  Clears the color and depth buffers.
 *  
 *  @return Void.
 */
void clear_screen ()
{
    /* black background */
    glClearColor(0.0, 0.0, 0.0, 0.0);

    /* show triangles at all depths */
    glClearDepth(CLEAR_DEPTH);

    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
}

/** @brief Sets the model transformation state.
 *  
 *  Sets the scale for any following rendering.
 *  
 *  @param vLandRotate The rotation matrix {x, y, z}.
 *  @param vLandTranslate The translation matrix {x, y, z}.
 *  @return Void.
 */
void set_transform (float vLandRotate[], float vLandTranslate[])
{
    /* ignore the previous matrix transformations */
    glLoadIdentity();

    glTranslatef(vLandTranslate[0], vLandTranslate[1], vLandTranslate[2]);

    glRotatef(vLandRotate[0], 1.0, 0.0, 0.0);
    glRotatef(vLandRotate[1], 0.0, 1.0, 0.0);
    glRotatef(vLandRotate[2], 0.0, 0.0, 1.0);
}

