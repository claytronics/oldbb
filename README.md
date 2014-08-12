oldbb - modifiedVM
===================
The *modifiedVM* branch of oldbb is a fork from the master branch in which the original Meld virtual machine has been revised to allow the execution of _Linear Meld_ onto the Blinky Blocks.  

## What changes have been made?

The major difference with the original *oldbb* is in the *build/src-bobby/meldinterp-runtime/* directory, which contains the VM's source files:
- LMParser source files have been added, this program is used to parse a Linear Meld byte code file compiled by the [cl-meld](https://github.com/flavioc/cl-meld/tree/dev) compiler, extract relevant information from it, and create another byte code file suited for our VM.
- The virtual machine's source files have been deeply altered using [Flavioc's MeldVM](https://github.com/flavioc/cl-meld/tree/dev) as a template. 
- A script has been added to the *build/bin/* directory, **compile-meld.sh**, which takes a *.m* Meld byte code file as input, and automates its parsing and the virtual machine's compilation.

## What are all these files in meldinterp-runtime for?
Not all the files in *meldinterp-runtime* have been altered, most of the modifications have taken place in *core.h, core.c, meldvm.bb*, the VM core files. Here is a brief overview of the content of the *meldinterp-runtime* folder:
- **LMParser.h/c:** Parser program's source files.
- **Makefile:** To compile LMParser and send it to the *bin/* directory only, not for the VM.
- **api.h:** Essentially contains macros to access Meld types' value within the VM.
- **extern_functions.bb/bbh:** External functions and their definition.
- **list_runtime and set_runtime:** Set and list data structures used to store aggregates.
- **model.bbh:** Meld types and struct definitions for queues.
- **melvm.bb:** The virtual machine's main file, which runs the program's main loop, handles messages and actions.
- **core.h:** All global variable and macro declarations, as well as instructions and their size are here.
- **core.c:** Where serious stuff happens! Manages queues and the database, and executes byte code.

## 
