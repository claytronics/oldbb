/** @file menu.c
 *  
 *  The menu has options for changing the display mode, toggling the shiny
 *  material, recording an animation, and exiting.
 *  
 *  @author Bobby Prochnow (rprochno)
 *  @bugs No known bugs.
 */

#include <GL/gl.h>
#include <GL/glu.h>
#include <GL/glut.h>

#include <stdlib.h>

/* - Menu Library - */
#include "menu.h"

/* - Rendering Library - */
#include "render.h"

/* - Menu Definitions - */ 
typedef enum {M_EXIT, M_SIZE} menu_entry;
char *menu[] = {"Exit"};

/* - Display Submenu Definition - */
typedef enum {D_VERTEX=0, D_WIREFRAME, D_SOLID, D_SIZE} display_entry;
char *display_menu[] = {"Vertices", "Wireframe", "Solid"};
 
/** @brief Display submenu callback function. 
 *   
 *  @param value The value of the menu entry selected. 
 *  @return Void. 
 */ 
void display_menu_func(int value) 
{
    switch (value) 
    {
    case D_VERTEX:
	    set_display_mode(GL_POINT);
	    break;
    case D_WIREFRAME:
	    set_display_mode(GL_LINE);
	    break;
    case D_SOLID:
	    set_display_mode(GL_FILL);
	    break;
    default:
	    break;
    }
}

/** @brief Main menu callback function. 
 *   
 *  @param value The value of the menu entry selected. 
 *  @return Void. 
 */   
void menu_func(int value) 
{
    switch (value) 
    {
    case M_EXIT:
       	exit(0);
       	break;
    default:
	    break;
    }
}

/** @brief Initializes the menu.
 *  
 *  @return Void.
 */
void menu_init () 
{
    /* create display menu */ 
    int g_displayMenuID = glutCreateMenu(display_menu_func);
     
    display_entry d;
    for (d = 0; d < D_SIZE; d++) 
        glutAddMenuEntry(display_menu[d], d);
         
    /* allow the user to quit&change options 
     * using the right mouse button menu  
     */ 
    int g_iMenuId = glutCreateMenu(menu_func);
    glutSetMenu(g_iMenuId);
     
    glutAddSubMenu("Display", g_displayMenuID);
     
    menu_entry m;
    for (m = 0; m < M_SIZE; m++) 
        glutAddMenuEntry(menu[m], m);
     
    //glutAttachMenu(GLUT_RIGHT_BUTTON);
}
