#ifndef __DEFS_H__
#define __DEFS_H__

#include <stdint.h>

// place all nicknames for standard variable types in this file

typedef uint8_t byte;
#define POINTER_SIZE sizeof(char*)

// These are standard definitions for the AVR compiler
//[signed | unsigned] char : 8 bits
//[signed | unsigned] short [int] : 16 bits
//[signed | unsigned] int: 16 bits
//[signed | unsigned] long [int] : 32 bits
//[signed | unsigned] long long [int] : 64 bits 

//---------NAMING CONVENTIONS---------------------
//
//	These are used for all globally accessible types, variables, and functions. 
//
//	type information ->	CapitalizedCapitalized
//		ex:	Color, Intensity, Timer
//
//	functions		-> uncapitalizedCapitalized
//		ex:	getChunk, push, getTime
//
//	global variables ->	uncapitalizedCapitalized
//		ex: thisChunk, thisTimeout, etc.
//
//	defines			-> ALLCAPS_UNDERSCORE_DELINEATION
//		ex:	NUM_PORTS, ACK
//
#endif
