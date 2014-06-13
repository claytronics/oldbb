#include "defs.hpp"
#include "parser.hpp"

#include <iostream>
#include <fstream>

using namespace std;

static char *program = NULL;
static char *progname = NULL;

static void
help(void)
{
cerr << "convertBytecode: Convert new MeldVM bytecode file to allow its interpretation by the old MeldVM" << endl;
cerr << "\t-f <name>.m\tmeld bytecode file" << endl;
cerr << "\t-h \t\tshow this screen" << endl;
  
exit(EXIT_SUCCESS);
}

static void
read_arguments(int argc, char **argv)
{	
progname = *argv++;
--argc;

while (argc > 0 && (argv[0][0] == '-')) {
switch(argv[0][1]) {
 case 'f': {
if (program != NULL || argc < 2)
  help();

program = argv[1];

argc--;
argv++;
}
break;
 case 'h':
help();
break;
 default:
help();
}

/* advance */
argc--; argv++;
}
}

int
main(int argc, char **argv)
{
read_arguments(argc, argv);

if(program == NULL) {
  cerr << "Error: please provide a bytecode file to convert" << endl;
return EXIT_FAILURE;
}
     
print_program(program);
   
return EXIT_SUCCESS;
}
