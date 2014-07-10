#include <stdio.h>
#include <stdlib.h>

#include "dummyCore.h"
#include "samples/noNeighborBlue.bb"

int
main (int argc, char* argv[]) 
{
  printf ("MELD BYTE CODE HEADER CHECKER\n"
	  "-----------------------------\n");
  
  printf ("Number of predicates: %d\n", NUM_TYPES);
  printf ("Number of rules: %d\n", NUM_RULES);

  printf ("\nOffsets to predicate descriptors: \n");
  int i;
  int j;
  for (i = 0; i < NUM_TYPES; ++i) {
    printf ("  Predicate %d: %hu\tvalue: %#x\n"
	    , i, TYPE_OFFSET(i), *TYPE_DESCRIPTOR(i));
  }

  printf ("\nPredicate descriptors: \n");
  for (i = 0; i < NUM_TYPES; ++i) {
    printf ("\n  Predicate %d: ", i);
    /* Print name -- WITH ARTIFICIAL FIRST NODE ARG*/
    printf ("%s(NODE", tuple_names[i]);
    for (j = 0; j < TYPE_NOARGS(i); ++j) {
      printf (", ");
      switch (*TYPE_ARG_DESC(i, j)) {
      case FIELD_INT: printf ("INT"); break;
      case FIELD_FLOAT: printf ("FLOAT"); break;
      case FIELD_ADDR: printf ("NODE"); break;
      case FIELD_STRING: printf ("STRING"); break;
      case FIELD_LIST_INT: printf ("LIST_INT"); break;
      case FIELD_LIST_FLOAT:printf ("LIST_FLOAT"); break;
      case FIELD_LIST_ADDR:printf ("LIST_NODE"); break;
      case FIELD_SET_INT:printf ("SET_INT"); break;
      case FIELD_SET_FLOAT:printf ("SET_FLOAT"); break;
      case FIELD_TYPE: printf ("TYPE?!"); break;
      default:
	perror ("UNKNOWN TYPE!\n"); exit (1);
      }
    }
    printf (")\n");

    /* Print properties */
    printf ("    Properties: ");
    if (TYPE_IS_AGG(i)) printf ("AGGREGATE ");
    if (TYPE_IS_PERSISTENT(i)) printf ("PERSISTENT ");
    if (TYPE_IS_LINEAR(i)) printf ("LINEAR ");
    if (TYPE_IS_DELETE(i)) printf ("DELETE ");
    if (TYPE_IS_SCHEDULE(i)) printf ("SCHEDULE ");
    if (TYPE_IS_ROUTING(i)) printf ("ROUTE ");
    if (TYPE_IS_PROVED(i)) printf ("PROVED ");
    printf("\n");

    /* Print aggregate type, if any */
    printf ("    Aggregate: ");
    if (!TYPE_AGGREGATE(i)) printf ("Not aggregate\n");
    else {
      printf ("Type: ");
      switch (AGG_AGG(TYPE_AGGREGATE(i))) {
      case AGG_FIRST: printf ("FIRST"); break;
      case AGG_MAX_INT: printf ("MAX_INT"); break;
      case AGG_MIN_INT: printf ("MIN_INT"); break;
      case AGG_MAX_FLOAT: printf ("MAX_FLOAT"); break;
      case AGG_MIN_FLOAT: printf ("MIN_FLOAT"); break;
      case AGG_SUM_FLOAT:printf ("SUM_FLOAT);"); break;
      case AGG_SET_UNION_INT:printf ("SET_UNION_INT"); break;
      case AGG_SET_UNION_FLOAT:printf ("SET_UNION_FLOAT"); break;
      case AGG_SUM_LIST_INT:printf ("SUM_LIST_INT"); break;
      case AGG_SUM_LIST_FLOAT:printf ("SUM_LIST_FLOAT"); break;
      default:
	perror ("UNKNOWN TYPE!\n"); exit (1);
      }
      
      printf ("\tField: %d\n", AGG_FIELD(TYPE_AGGREGATE(i)));
    }

    printf ("    Stratification round: %d\n", TYPE_STRATIFICATION_ROUND(i));
    printf ("    Number of arguments: %d\n", TYPE_NOARGS(i));
    printf ("    Number of deltas: %d\n", TYPE_NODELTAS(i));

    /* Print offset to byte code */
    printf ("    Offset: %d\tvalue: %#x\n", TYPE_START_CHECK(i), *TYPE_START(i));
  }

  printf ("\nOffsets to rule byte code: \n");
  for (i = 0; i < NUM_RULES; ++i) {
    printf ("  Rule %d: %hu\tvalue: %#x\n" 
	    , i, RULE_START_CHECK(i), *RULE_START(i));
  }  

  printf ("\n------ PRINTING COMPLETE ------\n\n");
  exit (0);
}
