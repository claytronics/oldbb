%option   8bit  bison-bridge
%option   warn 
%option   yylineno
%option   outfile="scanner.c" header-file="scanner.h"
%x incl
%x inclname
%x incldone

/* scanner for a toy Pascal-like language */
     
%{
#include <stdio.h>
#include "ldp.h"
#include "ldp.tab.h"
   int linenum;
   char* fname;

// for include files
#define MAX_INCLUDE_DEPTH 10
YY_BUFFER_STATE include_stack[MAX_INCLUDE_DEPTH];
int include_stack_ptr = 0;
   struct {  char* fname;  int linenum; } fileinfo[MAX_INCLUDE_DEPTH];

%}
     
DIGIT    [0-9]
ID       [a-zA-Z_][_A-Za-z0-9]*
     
%%
     
{DIGIT}+    return INTEGER;
-{DIGIT}+    return INTEGER;
{DIGIT}+"."{DIGIT}*    return FLOAT;
-{DIGIT}+"."{DIGIT}*    return FLOAT;
\"([^\"\\]|\\.)*\"	return STRING;

const	return CONST;
do	return DO;
include             BEGIN(incl);
true	return TRUE;
false	return FALSE;
forall	return FORALL;
where		return WHERE;
scalar	return SCALAR;
{ID}   return IDENT;
"+"	return BINOP;
"-"	return MINUS;
"*"	return BINOP;
"/"	return BINOP;
">"	return BINOP;
"%"	return BINOP;
"<"	return BINOP;
"=="	return BINOP;
"<="	return BINOP;
">="	return BINOP;
"!="	return BINOP;
"="	return EQUAL;
"("	return LPAR;
")"	return RPAR;
","	return COMMA;
"&"	return BINOP;
"."	return DOT;
";"   	return SEMICOLON;
     
#.*\n     linenum++; /* eat up one-line comments */
     
[ \t\r]+          /* eat up whitespace */

\n	linenum++;
     
<incl>[ \t]*\"      BEGIN(inclname); /* eat the whitespace before an include name */
<inclname>[^\"]+  { /* got the include file name */
  
		     if ( include_stack_ptr >= MAX_INCLUDE_DEPTH ) {
		       myerror("Includes nested too deeply when including '%s'\n", yytext);
		     }

		     include_stack[include_stack_ptr] =  YY_CURRENT_BUFFER;
		     fileinfo[include_stack_ptr].linenum = linenum;
		     fileinfo[include_stack_ptr].fname = fname;
		     include_stack_ptr++;

		     yyin = fopen( yytext, "r" );
		     if ( ! yyin )
		       myerror("Cannot open include file '%s'\n", yytext);

		     fname = strdup(yytext);
		     linenum = 1;

		     yy_switch_to_buffer(yy_create_buffer( yyin, YY_BUF_SIZE ));

		     BEGIN(INITIAL);  
	           }

<<EOF>> {
          if ( --include_stack_ptr < 0 ) {
            yyterminate();
	  } else {
            yy_delete_buffer( YY_CURRENT_BUFFER );
            yy_switch_to_buffer(include_stack[include_stack_ptr] );
	    fname = fileinfo[include_stack_ptr].fname;
	    linenum = fileinfo[include_stack_ptr].linenum;
	  }
	  BEGIN(incldone);
        }

<incldone>\"[ \t]*;  BEGIN(INITIAL);

.           printf( "Unrecognized character: [%s]\n", yytext );
     
%%
