oldbb - modifiedVM - 08/12/14
====================================
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

## How do I CURRENTLY compile a Meld program and send it to the blocks?
1. Follow the instructions in *build/README* to compile all the tools you will need.
2. Go to *build/apps/sample-meld/*, where you can find some *.meld* files, which are **suited for the old VM**, and the *LM-programs/* folder, where the current version's Meld programs are.
3. Go to the *LM-programs/* folder.  
4. `export BB=SIM` if making for the sim **or** `export BB=block` for the blocks.
5. Run ". ./initbb.sh" and then run `compile-meld.sh [meld_program_name_without_extension]` to compile the program.
6. `../arch-$ARCH/blinkyblocks [OPTIONS]` to run the simulator.  
   **or**  
   `reprogrammer -p /dev/[PORT] -f ../arch-blocks/ends.hex` to reprogram the blocks.  
   (cf. *build/README*)  

## What is the format of a Meld byte code file and how does it get executed?
A Meld program goes through several steps before it actually gets executed. The first one is the compilation of the *.meld* program in a *.m* byte code file by the [cl-meld](https://github.com/flavioc/cl-meld/tree/dev) compiler.  
Below is a draft diagram of the format of the output file: 
![](http://i58.tinypic.com/68udj9.jpg)

Then, the *.m* file is passed to LMParser, which will extract the useful information form it and reformat it as a new file, with a *.bb* extension, and the following format:
![](http://i62.tinypic.com/2ntfbxl.jpg)

Next, this file is copied to *build/apps/sample-meld/arch-$ARCH/meldinterp-runtime/*, and finally, the VM is recompiled with the *.bb* file.

## What is the easiest way to implement new instructions?

Here is what I did for the instructions already implemented:  
1. Checkout the `dev` branch of [meld](https://github.com/flavioc/meld) if you don't have it yet.  
2. Open the following files: `vm/instr.cpp vm/instr.hpp exec.cpp`  
3. In `vm/instr.hpp`, you should be able to get a good view of the format of the instructions, by looking at the size of the instruction, and its fields.  
4. Then, you can also use `vm/instr.cpp` to get an even clearer idea of what its format is by looking at how its printing is performed.  
5. Once you have understood its format, browse to `vm/exec.cpp` to see how it is executed.  
6. Finally, implement its execution to the VM by adding a case statement to the loop's switch in `core.c` and a call to its execution function.
