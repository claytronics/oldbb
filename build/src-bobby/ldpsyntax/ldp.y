%{
/*
 * ldp.y
 *
 */

#include <stdio.h>
#include <stdarg.h>
#include "ldp.h"
     
extern FILE *yyin;
extern int linenum;
extern char* fname;
extern Table symbols;
 extern char* yytext;
 int error = 0;

void
yyerror (char* s)
{
  fprintf (stderr, "%s:%d:%s\n", fname, linenum, s);
  error = 1;
}

void
  myerror (char* s, ...)
{
  va_list ap;
  va_start(ap, s); 
  fprintf(stderr, "%s:%d:", fname, linenum);
  vfprintf (stderr, s, ap);
  error = 1;
 va_end(ap);
}

char*
itype2str(Itype type)
{
  switch (type)
  {
  case Const: return "Constant";
  case Function: return "Function";
  case Node: return "Node";
  case Field: return "Field";
  case Unknown: return "Unknown";
  }
 return "???";
}

void
checkID(Symbol s, Itype type)
{
  if (s->type == Unknown) {
    s->type = type;
    fprintf(stderr, "%d: Unknown identifier '%s', assuming it is a %s\n", linenum, s->text, itype2str(type));
    error = 1;
  }
  if (s->type != type) {
    fprintf(stderr, "%d: Expected a %s identifier, instead found the %s '%s'\n", linenum, itype2str(type), itype2str(s->type), s->text);
    error = 1;
  }
}

void
declareID(Symbol s, Itype type)
{
  //fprintf(stderr, "Declaring %s: %s\n", s->text, itype2str(type));
  if (s->type != Unknown) {
    myerror("%s '%s' redeclared\n", itype2str(type), s->text);
  }
  s->type = type;
}

%}

%error-verbose 
%expect 6

%token INTEGER 
%token FLOAT 
%token STRING 
%token INCLUDE 
%token TRUE 
%token FALSE 
%token FORALL 
%token WHERE 
%token SCALAR 
%token IDENT 
%token BINOP 
%token MINUS 
%token LPAR 
%token RPAR 
%token COMMA 
%token DOT 
%token SEMICOLON 
%token EQUAL
%token DO
%token CONST

%union {
  Symbol symbol;
};

%type <symbol> identifier


%%
program : stmt_list
 ;

stmt_list : stmt_list stmt
 | stmt
 ;

stmt : s_include SEMICOLON { }
| s_decl SEMICOLON { }
| s_forall SEMICOLON { }
| s_const SEMICOLON  { }
 ;

s_include : INCLUDE STRING
	{
	  myerror("Should not have gotten here: internal error");
        }
;

s_decl : SCALAR identifier 
	{
	  declareID($2, Field);
	}
 | SCALAR identifier EQUAL constant
	{
	  declareID($2, Field);
	}
 | SCALAR identifier EQUAL identifier
	{
	  declareID($2, Field);
	  checkID($4, Const);
	}
 | SCALAR identifier LPAR formals RPAR
	{
	  declareID($2, Function);
	}
 | SCALAR identifier LPAR RPAR
	{
	  declareID($2, Function);
	}
;

s_const : CONST identifier EQUAL constant
	{
	  declareID($2, Const);
	}
;

constant : INTEGER
 | FLOAT
 | STRING
 | TRUE
 | FALSE
 ;

s_forall : forall blocks WHERE predicate DO actions
	{
	  popWatermark(symbols);
	}
  ;

forall: FORALL
	{
	  pushWatermark(symbols);
	}
  ;

blocks : LPAR ident_list RPAR
  ;

ident_list : ident_list COMMA identifier
	{
	  declareID($3, Node);
	}
 | identifier
	{
	  declareID($1, Node);
	}
 ;

predicate : LPAR predicate RPAR
 | constant
 | predicate BINOP predicate
 | identifier DOT identifier
	{
	  checkID($1, Node);
	  checkID($3, Field);
	}
 | predicate MINUS predicate
 | MINUS predicate
 | fcall
 | identifier
	{    
	  Symbol s = $1;
	  checkID(s, Const);
	}
 ;

args : args COMMA predicate
 | predicate
 ;

formals : formals COMMA IDENT
 | IDENT
 ;


actions : actions COMMA action
 | action
 ;

action : identifier DOT identifier EQUAL predicate
	{
	  checkID($1, Node);
	  checkID($3, Field);
	}
 | fcall
 ;

fcall : identifier DOT identifier LPAR args RPAR 
	{
	  checkID($1, Node);
	  checkID($3, Function);
	}
 | identifier DOT identifier LPAR RPAR 
	{
	  checkID($1, Node);
	  checkID($3, Function);
	}
 ;

identifier : IDENT { $$ = insert(symbols, yytext);  }

%%


main( argc, argv )
int argc;
char **argv;
{
  initTable();
  linenum=1;
  ++argv, --argc;  /* skip over program name */
  if ( argc > 0 ) {
    fname = strdup(argv[0]);
    yyin = fopen( fname, "r" );
  } else {
    fname = "*stdin*";
    yyin = stdin;
  }
  
int   x = yyparse ();

       if (error) {
	 fprintf(stderr, "Your program had syntax errors\n");
       } else {
	 fprintf(stderr, "Your program passed the syntax check\n");
       }

       return x;
}
