#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "parser.h"

byte readType (FILE *pFile);
byte readTypeID (FILE *pFile, byte typeArray[]);
void skipNodeReferences (FILE *pFile);

int 
main (int argc, char* argv[])
{
  printf ("MELD BYTE CODE PARSER\n"
	  "---------------------\n");

  /* Verify arguments */
  if (argc != 2) {
    perror ("Invalid number of arguments\n"
	    "Usage: ./parser <path-to-.m-file>\n");
    exit(1);
  }
  
  char *inNameBuf = argv[1];

  /* Get name for output */
  char outNameBuf[strlen (inNameBuf) + 3];
  int i;
  int j;
  for (i = 0;  i < (strlen (inNameBuf) - 1); ++i) {
    outNameBuf[i] = inNameBuf[i];
  }
  outNameBuf[i++] = 'b';
  outNameBuf[i++] = 'b';
  outNameBuf[i] = '\0';

  /* Open input file */
  FILE *pMeldProg;
  pMeldProg = fopen (inNameBuf, "r");

  /* Open output file */
  FILE *pBBFile;
  pBBFile = fopen (outNameBuf, "w");

  /* Start parsing */
  if (pMeldProg == NULL) perror ("Error opening file");
  else
    {
      /* Read magic and check that program is Meld file*/
      uint32_t magic1;
      fread (&magic1, 4, 1, pMeldProg);
      uint32_t magic2;
      fread (&magic2, 4, 1, pMeldProg);	  

      printf ("magics: %#x %#x\n", magic1, magic2);

      if (magic1 != MAGIC1 || magic2 != MAGIC2) {
	perror ("Not a Meld byte code file!");
	exit (1);
      }
      else printf ("magics OK\n");

      /* Check file version -- has to be 11 for now */
      uint32_t majorVersion;
      fread (&majorVersion, 4, 1, pMeldProg);
      uint32_t minorVersion;
      fread (&minorVersion, 4, 1, pMeldProg);

      if ( (!VERSION_AT_LEAST(0, 11)) || VERSION_AT_LEAST(0, 12) ) {
	perror ("Unsupported byte code version");
      }
      else printf ("Version OK\n");

      /* Read number of predicates */
      byte numPredicates;
      fread (&numPredicates, 1, 1, pMeldProg);
      printf ("Number of predicates: %d\n", numPredicates);

      /* Read number of nodes -- USELESS BB */
      uint32_t numNodes;
      fread (&numNodes, 4, 1, pMeldProg);
      printf ("Number of nodes: %d\n", numNodes);

      /* Read number of types */
      byte numTypes;
      fread (&numTypes, 1, 1, pMeldProg);
      printf ("Number of types: %d\n", numTypes);

      /* Read types */
      byte types[numTypes];
      printf ("Types: ");
      for (i = 0; i < numTypes; ++i) types[i] = readType (pMeldProg);
      printf ("\n");

      /* Read number of imported predicates */
      uint32_t numImportedPreds;
      fread (&numImportedPreds, 4, 1, pMeldProg);
      printf ("Number of imported predicates: %d\n", numImportedPreds);

      /* Read imported predicates */
      /* Ignore for now as there should be none */

      /* Read number of exported predicates */
      uint32_t numExportedPreds;
      fread (&numExportedPreds, 4, 1, pMeldProg);
      printf ("Number of exported predicates: %d\n", numExportedPreds);

      /* Read exported predicates */
      /* Ignore for now as there should be none */
	  
      /* Read number of args needed by program */
      byte numProgArgs;
      fread (&numProgArgs, 1, 1, pMeldProg);
      printf ("Number of program arguments: %d\n", numProgArgs);

      /* Read rule info */
      uint32_t numRules;
      fread (&numRules, 4, 1, pMeldProg);
      printf ("Number of rules: %i\n", numRules);

      for (i = 0; i < numRules; ++i) {
	printf ("  Rule %lu: ", i);
	    
	/* Read rule length */
	uint32_t ruleLength;
	fread (&ruleLength, 4, 1, pMeldProg);
	    
	/* Read rule string */
	char rule_str[ruleLength + 1];
	fread (&rule_str, 1, ruleLength, pMeldProg);
	rule_str[ruleLength] = '\0';

	printf ("%s\n", rule_str);
	/* TODO: store somewhere? */
      }
      printf ("\n");

      /* Read string constants */
      uint32_t numStrings;
      fread (&numStrings, 4, 1, pMeldProg);
      printf ("Number of string constants: %d\n", numStrings);

      for (i = 0; i < numStrings; ++i) {
	printf ("  String %d: ", i);

	uint32_t stringLength;
	fread (&stringLength, 4, 1, pMeldProg);
				
	char constStr[stringLength + 1];
	fread (&constStr, 1, stringLength, pMeldProg);
	constStr[stringLength] = '\0';

	printf ("%s\n", constStr);
	/* TODO: store somewhere? */
      }

      /* Read constants */
      uint32_t numConstants;
      fread (&numConstants, 4, 1, pMeldProg);
      printf ("Number of constants: %d\n", numConstants);
 
      /* Read type */
      byte constTypes[numConstants];

      for (i = 0; i < numConstants; ++i) {
	constTypes[i] = readTypeID(pMeldProg, types);
      }

      /* Read and store code */
      uint32_t constCodeSize;
      fread (&constCodeSize, 4, 1, pMeldProg);
      printf ("Constant code size: %d\n", constCodeSize);

      byte constCode[constCodeSize];
      fread (&constCode, 1, constCodeSize, pMeldProg);
      printf("code: %x\n", constCode[0]);
      skipNodeReferences (pMeldProg);

      /* Read function code */
      uint32_t numFunctions;
      fread (&numFunctions, 4, 1, pMeldProg);
      printf ("Number of functions: %d\n", numFunctions);

      for (i = 0; i < numFunctions; ++i) {
	uint32_t functionSize;
	fread (&functionSize, 4, 1, pMeldProg);

	byte functionCode[functionSize];
	fread (&functionSize, 1, functionSize, pMeldProg);
	/* TODO: Store somewhere? */
	skipNodeReferences (pMeldProg);
      }

      /* Read external functions definitions */
      uint32_t numExternalFunctions;
      fread (&numExternalFunctions, 4, 1, pMeldProg);
      printf ("Number of external functions: %d\n", numExternalFunctions);
      
      for (i = 0; i < numExternalFunctions; ++i) {
	printf ("  Extern %d:\n", i);
	
	uint32_t externID;
	fread (&externID, 4, 1, pMeldProg);
	printf ("    ID: %d\n", externID);

	char externName[256];
	fread (&externName, 1, sizeof(externName), pMeldProg);
	printf ("    Name: %s\n", externName);
	
	char skipFilename[1024];
	fread (&skipFilename, 1, sizeof(skipFilename), pMeldProg);
	printf ("    Filename: %s\n", skipFilename);

	uint64_t skipPtr;
	fread (&skipPtr, sizeof(uint64_t), 1, pMeldProg);
	
	uint32_t numFuncArgs;
	fread (&numFuncArgs, 4, 1, pMeldProg);
	printf ("    Number of args: %d\n", numFuncArgs);
	
	if (numFuncArgs) {
	  printf ("    Types: ");
	  byte funcArgTypes[numFuncArgs];
	  
	  for (j = 0; j < numFuncArgs; j++) {
	    funcArgTypes[j] = readTypeID (pMeldProg, types);
	  }
	  /* TODO: store somewhere? */
	} /* else TODO: store somewhere? */
      }

      /* Read predicate information */
      size_t totalArguments = 0; /* Number of predicate args in program */
      
      /* Initialize global containers */
      Predicate predicates[numPredicates];
      Rule rules[numRules];
      byte allArguments[256];

      printf ("\n PREDICATE DESCRIPTORS \n");

      for (i = 0; i < numPredicates; ++i) { 
	printf("  Predicate %lu:\n", i);
	
	/* Read code size */
	uint32_t codeSize;
	fread (&codeSize, 4, 1, pMeldProg);
	printf("    code size: %lu\n", codeSize);
	predicates[i].codeSize = codeSize;

	/* Read predicate properties */
	byte prop;
	fread (&prop, 1, 1, pMeldProg);

	printf ("    Properties: ");
	
	/* Format it to old property byte format */
	byte oldProp = 0x0;

	if (prop & PRED_AGG) {	/* AGG? */
	  printf ("AGG ");
	  oldProp |= 0x01;
	}
	if (prop & PRED_LINEAR) {
	  printf ("LINEAR ");
	  oldProp |= 0x04;
	}
	/* TODO: Demistify this */
	/* else { */
	/*   printf ("PERSISTENT "); */
	/*   oldProp |= 0x02; */
	/* } */
	if (prop & PRED_ROUTE) {
	  printf ("ROUTE ");
	  oldProp |= 0x20;
	}
	if (prop & PRED_REVERSE_ROUTE) {
	  printf ("REVERSE-ROUTE ");
	  oldProp |= 0x20;
	}
	if (prop & PRED_ACTION) {
	  printf ("ACTION ");
	  /* Not specified in old VM */
	}
	if (prop & PRED_REUSED) {
	  printf ("REUSED ");
	  /* Not specified in old VM */
	}
	if (prop & PRED_CYCLE) {
	  printf ("CYCLE ");
	  /* Not specified in old VM */
	}
	printf ("\n");
	predicates[i].properties = oldProp;
	
	/* Aggregate information if any */
	byte agg;
	fread (&agg, 1, 1, pMeldProg);
	predicates[i].agg = agg;

	if (prop & PRED_AGG) {
	  printf ("    Aggregate: \n");

	  byte aggField = agg & 0xf; 
	  printf ("     field: %d\n", aggField);

	  byte type = ((0xf0 & agg) >> 4);
	  printf ("     type: ");
	  switch(type) {
	  case AGG_FIRST: printf ("first\n"); break;
	  case AGG_MAX_INT: printf ("max_int\n"); break;
	  case AGG_MIN_INT: printf ("min_int\n"); break;
	  case AGG_SUM_INT: printf ("sum_int\n"); break;
	  case AGG_MAX_FLOAT: printf ("max_float\n"); break;
	  case AGG_MIN_FLOAT: printf ("min_float\n"); break;
	  case AGG_SUM_FLOAT: printf ("sum_float\n"); break;
	  case AGG_SUM_LIST_FLOAT: printf ("sum_list_float\n"); break;
	  }
	}

	/* Stratification level */
	byte stratLevel;
	fread (&stratLevel, 1, 1, pMeldProg);
	predicates[i].level = stratLevel;
	printf ("    Stratification level: %d\n", stratLevel);

	/* Number of fields */
	byte numFields;
	fread (&numFields, 1, 1, pMeldProg);
	predicates[i].nFields = numFields;
	printf ("    Number of fields: %d\n", numFields);

	/* Argument types */
	printf ("    Field types: ");
	predicates[i].argOffset = totalArguments;
	
	int k;
	for (k = 0; k < numFields; ++k) {
	  allArguments[totalArguments++] = readTypeID (pMeldProg, types);
	}
	printf ("\n");

	/* Predicate name */
	/* USE PC */
	predicates[i].pName = malloc (PRED_NAME_SIZE_MAX + 1);
	fread (predicates[i].pName, 1, PRED_NAME_SIZE_MAX, pMeldProg);
  
	printf ("    Name: ");
	char *sc = predicates[i].pName;
	for (j = 0; (j < PRED_NAME_SIZE_MAX) || (*sc == '\0'); ++j) {
	  printf("%c", *sc);
	  ++sc;
	}
	printf ("\n");

	/* Aggregate info */
	char bufVec[PRED_AGG_INFO_MAX];
	fread (&bufVec, 1, PRED_AGG_INFO_MAX, pMeldProg);
	char *buf = bufVec;
	
	printf ("    Aggregate info (if any): ");
	if (prop & PRED_AGG) {
	  if (buf[0] == PRED_AGG_LOCAL) {
	    buf++;
	    printf ("local_agg\n");
	  } else if (buf[0] == PRED_AGG_REMOTE) {
	    buf++;
	    printf ("neighborhood_arg\n");
	  } else if(buf[0] == PRED_AGG_REMOTE_AND_SELF) {
	    buf++;
	    printf ("neighborhood_and_self_agg\n");
	  } else if(buf[0] == PRED_AGG_IMMEDIATE) {
	    buf++;
	    printf ("immediate_agg\n");
	  } else if(buf[0] & PRED_AGG_UNSAFE) {
	    buf++;
	    printf ("unsafe_agg\n");
	  }
	  else printf ("unknown\n");
	} else printf ("  none\n");
      
	totalArguments += numFields;
	printf ("\n");
    }
  
      /* Read global priority info */
      printf ("\nPRIORITY INFO\n");

      byte globalInfo;
      fread (&globalInfo, 1, 1, pMeldProg);
      
      switch (globalInfo) {
      case 0x01: perror ("Priority by predicate - not supported anymore.\n"); break;
      case 0x02: {
	printf ("Normal priority\n");
	byte type = 0x0;
	byte ascDesc;

	fread (&type, 1, 1, pMeldProg);
	printf ("Type: field float\n");

	fread (&ascDesc, 1, 1, pMeldProg);
	if (ascDesc & 0x01) printf ("Order: asc\n");
	else printf ("Order: desc\n");

	double initialPriorityValue;
	fread (&initialPriorityValue, sizeof(double), 1, pMeldProg);
	printf ("Initial priority value: %f\n", initialPriorityValue);
      }	break;
      case 0x03: perror ("File wrongly appears as data file\n"); break;
      }

      /* Read predicate bytecode */
      printf ("\nEXTRACTING PREDICATE BYTECODE...\n");
      for (i = 0; i < numPredicates; ++i) {
	uint32_t bytecodeSize = predicates[i].codeSize;
	predicates[i].pBytecode = malloc (bytecodeSize);

	fread (predicates[i].pBytecode, 1, bytecodeSize, pMeldProg);
	
	skipNodeReferences (pMeldProg);
      }

      /* Read rule bytecode */
      printf ("\nEXTRACTING RULE BYTECODE\n");

      uint32_t numRulesCode;
      fread (&numRulesCode, 4, 1, pMeldProg);
      printf ("Number of rule codes: %d\n", numRulesCode);

      for (i = 0; i < numRulesCode; ++i) {
	uint32_t ruleCodeSize;
	fread (&ruleCodeSize, 4, 1, pMeldProg);
	rules[i].codeSize = ruleCodeSize;

	rules[i].pBytecode = malloc (ruleCodeSize);
	fread (rules[i].pBytecode, 1, ruleCodeSize, pMeldProg);

	skipNodeReferences(pMeldProg);

	byte persistence = 0x0;
	fread (&persistence, 1, 1, pMeldProg);
	/* TODO: Need to store? */
	
	uint32_t numInclPreds;
	fread (&numInclPreds, 4, 1, pMeldProg);

	for (j = 0; j < numInclPreds; ++j) {
	  byte predID;
	  fread (&predID, 1, 1, pMeldProg);
	  
	  /* TODO: Need to do something? */
	}
      }

      /* Print byte code header to output file */
      printf ("\nPRINTING BYTE CODE HEADER...\n");
      
      fprintf (pBBFile, "const unsigned char meld_prog[] = {");

      /* Print number of predicates */
      fprintf (pBBFile, "\n/* NUMBER OF PREDICATES */\n");
      fprintf (pBBFile, "%#x, ", numPredicates);
      
      /* Print number of rules */
      fprintf (pBBFile, "\n/* NUMBER OF RULES */\n");
      fprintf (pBBFile, "%#x, ", numRules);

      /* Calculate and print offset to predicate descriptor for every predicate */
      size_t descriptorStart = 2 + numPredicates * sizeof(byte);
      size_t currentOffset = descriptorStart;
      fprintf (pBBFile, "\n/* OFFSET TO PREDICATE DESCRIPTORS */");
      
      for (i = 0; i < numPredicates; ++i) {
      fprintf (pBBFile, "\n");

	/* Print offset */
	fprintf (pBBFile, "%#x, ", currentOffset);

	// Increment current offset
	currentOffset += PREDICATE_DESCRIPTOR_SIZE + 
	  predicates[i].nFields;
      }

      /* Calculate byte code offsets */
      
      /* Start with offset to each predicate's bytecode */
      size_t bcOffset = currentOffset + numRules * sizeof(unsigned short);
  
      printf ("Predicate byte code offsets: ");
      for (i = 0; i < numPredicates; ++i) {
	printf ("%d ,", bcOffset);
	predicates[i].bytecodeOffset = bcOffset;
	bcOffset += predicates[i].codeSize;
      }
      printf ("\n");

      /* Then set rule offsets */
      printf ("Rule byte code offsets: ");
      for (i = 0; i < numRules; ++i) {
	printf ("%d ,", bcOffset);
	rules[i].bytecodeOffset = bcOffset;
	bcOffset += rules[i].codeSize;
      }

      fprintf (pBBFile, "\n/* PREDICATE DESCRIPTORS */");
      /* Print predicate descriptors */
      for (i = 0; i < numPredicates; ++i) {
	fprintf (pBBFile, "\n");
	
	/* Force printing 2 bytes of the offset */
	fprintf (pBBFile, "%#x, ", predicates[i].bytecodeOffset & 0x00ff );
	fprintf (pBBFile, "%#x, ", (predicates[i].bytecodeOffset & 0xff00) >> 8);

	/* Type properties */
	fprintf (pBBFile, "%#x, ", predicates[i].properties);

	/* Aggregate type */
	fprintf (pBBFile, "%#x, ", predicates[i].agg);

	/* Stratification rount */
	fprintf (pBBFile, "%#x, ", predicates[i].level);

	/* Number of arguments */
	fprintf (pBBFile, "%#x, ", predicates[i].nFields);

	/* Number of deltas -- Force to 0*/
	fprintf (pBBFile, "%#x, ", 0);

	/* Print argument descriptor */
	for (j = 0; j < predicates[i].nFields; ++j)
	  fprintf (pBBFile, "%#x, ", allArguments[predicates[i].argOffset + j]);
      }

      /* Print rule offsets */
      fprintf (pBBFile, "\n/* OFFSETS TO RULE BYTECODE */");
      for (i = 0; i < numRules; ++i) {
	fprintf (pBBFile, "\n");
	/* Force printing 2 bytes of the offset */
	  fprintf (pBBFile, "%#x, ", rules[i].bytecodeOffset & 0x00ff );
	  fprintf (pBBFile, "%#x, ", (rules[i].bytecodeOffset & 0xff00) >> 8);
      }

      /* Print predicate bytecode */
      printf ("\nPRINTING PREDICATE BYTE CODE...\n");
      fprintf (pBBFile, "\n/* PREDICATE BYTECODE */");
      for (i = 0; i < numPredicates; ++i) {
	fprintf (pBBFile, "\n/* Predicate %d: */", i);
        byte *pc =  predicates[i].pBytecode;
	for (j = 0; j < predicates[i].codeSize; j++) {
	  fprintf (pBBFile, "%#x, ", *pc);
	  ++pc;
	}
      }

      /* Print rule bytecode */
      printf ("\nPRINTING RULE BYTE CODE...\n");
      fprintf (pBBFile, "\n/* RULE BYTECODE */");
      for (i = 0; i < numRules; ++i) {
	fprintf (pBBFile, "\n/* Rule %d: */", i);
	byte *pc =  rules[i].pBytecode;
	for (j = 0; j < rules[i].codeSize; j++) {
	  fprintf (pBBFile, "%#x, ", *pc);
	  ++pc;
	}
      }

      // Close byte code array
      fprintf (pBBFile, "};\n");

      /* Print predicate name array */
      printf ("\nPRINTING PREDICATE NAMES LIST...\n");

      fprintf (pBBFile, "\nchar *tuple_names[] = {");
      for (i = 0; i < numPredicates; ++i) {
	char *sc = predicates[i].pName;
	fprintf (pBBFile, "\"");
	for (j = 0; (j < PRED_NAME_SIZE_MAX) && (*sc != 0x0); ++j) {
	  fprintf (pBBFile, "%c", *sc);
	  ++sc;
	}
	fprintf (pBBFile, "\"");
	fprintf (pBBFile, ", ");
      }
      fprintf (pBBFile, "};\n\n");

      /* Print remaining elements */
      printf ("\nPRINTING EXTERNAL FUNCTIONS\n");

      fprintf (pBBFile, "#include \"extern_functions.bbh\"\n");
      fprintf (pBBFile, "Register (*extern_functs[])() = {};\n");
      fprintf (pBBFile, "\nint extern_functs_args[] = {};\n");

      fclose (pMeldProg);
      fclose (pBBFile);

      printf ("\n\n-------- DONE --------\n\n");
    }
  return 0;
}

byte
readType (FILE *pFile)
{
  byte fieldType;
  fread (&fieldType, 1, 1, pFile);

  switch (fieldType) {
  case FIELD_BOOL:    printf ("BOOL ");   perror ("BOOL not supported\n"); exit(1);
  case FIELD_INT:     printf ("INT ");    return 0x0;
  case FIELD_FLOAT:   printf ("FLOAT ");  return 0x1;
  case FIELD_NODE:    printf ("NODE ");   return 0x2;
  case FIELD_STRING:  printf ("STRING "); return 0x9;
  case FIELD_LIST:
    {
      byte listType;
      fread (&listType, 1, 1, pFile);
      
      switch (listType) {
      case FIELD_INT:
	printf ("INTLIST ");
	return 0x3;
      case FIELD_FLOAT:
	printf ("FLOATLIST ");
	return 0x4;
      case FIELD_NODE:
	printf ("NODELIST ");
	return 0x5;
      default:
	perror ("UNKNOWN LIST type!");
	exit (1);
      }
    }	
  case FIELD_STRUCT:    
    perror ("STRUCT type not supported yet!");
    exit (1);
  default:	
    perror ("UNKNOWN type!");
    exit (1);
  }
  return 0xff;
}

byte
readTypeID (FILE *pFile, byte typeArray[])
{
  byte pos;
  fread (&pos, 1, 1, pFile);
  return typeArray[pos];
}

void
skipNodeReferences (FILE *pFile)
{
  uint32_t sizeNodes;
  fread (&sizeNodes, 1, sizeof(uint32_t), pFile);
  fseek (pFile, sizeNodes * sizeof(uint32_t), SEEK_CUR);
}
