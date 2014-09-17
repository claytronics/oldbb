TODO - as of August 2014
====

## Instructions not implemented yet:

Almost all base instructions have been implemented already, however, all have not been tested because they have never appeared in a Meld byte code file for Blinky Blocks so far. Therefore, be careful when executing programs containing instructions marked as "Not tested".  
Besides, most of the instructions to be implemented are related to **lists** and **Meld data structures**.   
Here is the complete list, instructions already implemented are in **bold**:

  - **RETURN_INSTR         =  0x00,**
  - **NEXT_INSTR =  0x01,**
  - **PERS_ITER_INSTR      =  0x02,**
  - TESTNIL_INSTR =  0x03,
  - OPERS_ITER_INSTR     =  0x04,
  - **LINEAR_ITER_INSTR    =  0x05,**
  - RLINEAR_ITER_INSTR   =  0x06,
  - **NOT_INSTR =  0x07,**
  - **SEND_INSTR =  0x08,**
  - FLOAT_INSTR          =  0x09,
  - SELECT_INSTR         =  0x0A,
  - RETURN_SELECT_INSTR  =  0x0B,
  - OLINEAR_ITER_INSTR   =  0x0C,
  - DELETE_INSTR         =  0x0D,
  - RESET_LINEAR_INSTR   =  0x0E,
  - END_LINEAR_INSTR     =  0x0F,
  - **RULE_INSTR           =  0x10,**
  - **RULE_DONE_INSTR      =  0x11,**
  - ORLINEAR_ITER_INSTR  =  0x12,
  - NEW_NODE_INSTR       =  0x13,
  - NEW_AXIOMS_INSTR     =  0x14,
  - **SEND_DELAY_INSTR     =  0x15,**
  - PUSH_INSTR           =  0x16,
  - POP_INSTR            =  0x17,
  - PUSH_REGS_INSTR      =  0x18,
  - POP_REGS_INSTR       =  0x19,
  - CALLF_INSTR          =  0x1A,
  - CALLE_INSTR          =  0x1B,
  - SET_PRIORITY_INSTR   =  0x1C,
  - MAKE_STRUCTR_INSTR   =  0x1D,
  - **MVINTFIELD_INSTR     =  0x1E,**
  - **MVINTREG_INSTR       =  0x1F,**
  - CALL_INSTR        =  0x20,
  - **MVFIELDFIELD_INSTR   =  0x21,**
  - **MVFIELDREG_INSTR     =  0x22,**
  - **MVPTRREG_INSTR       =  0x23,**
  - MVNILREG_INSTR       =  0x24,
  - MVFIELDFIELDR_INSTR  =  0x25,
  - **MVREGFIELD_INSTR     =  0x26,**
  - MVREGFIELDR_INSTR    =  0x27,
  - **MVHOSTFIELD_INSTR    =  0x28,**
  - MVREGCONST_INSTR     =  0x29,
  - MVCONSTFIELD_INSTR   =  0x2A,
  - MVCONSTFIELDR_INSTR  =  0x2B,
  - **MVADDRFIELD_INSTR    =  0x2C,**
  - **MVFLOATFIELD_INSTR   =  0x2D,**
  - **MVFLOATREG_INSTR     =  0x2E,**
  - MVINTCONST_INSTR     =  0x2F,
  - SET_PRIORITYH_INSTR  =  0x30,
  - MVWORLDFIELD_INSTR   =  0x31,
  - MVSTACKPCOUNTER_INSTR=  0x32,
  - MVPCOUNTERSTACK_INSTR=  0x33,
  - MVSTACKREG_INSTR     =  0x34,
  - MVREGSTACK_INSTR     =  0x35,
  - **MVADDRREG_INSTR      =  0x36,**
  - **MVHOSTREG_INSTR      =  0x37,**
  - **ADDRNOTEQUAL_INSTR   =  0x38,**
  - **ADDREQUAL_INSTR      =  0x39,**
  - **INTMINUS_INSTR       =  0x3A,**
  - **INTEQUAL_INSTR       =  0x3B,**
  - **INTNOTEQUAL_INSTR    =  0x3C,**
  - **INTPLUS_INSTR        =  0x3D,**
  - **INTLESSER_INSTR      =  0x3E,**
  - **INTGREATEREQUAL_INSTR=  0x3F,**
  - **ALLOC_INSTR        =  0x40,**
  - **BOOLOR_INSTR         =  0x41,**
  - **INTLESSEREQUAL_INSTR =  0x42,**
  - **INTGREATER_INSTR     =  0x43,**
  - **INTMUL_INSTR         =  0x44,**
  - **INTDIV_INSTR         =  0x45,**
  - **FLOATPLUS_INSTR      =  0x46,**
  - **FLOATMINUS_INSTR     =  0x47,**
  - **FLOATMUL_INSTR       =  0x48,**
  - **FLOATDIV_INSTR       =  0x49,**
  - **FLOATEQUAL_INSTR     =  0x4A,**
  - **FLOATNOTEQUAL_INSTR  =  0x4B,**
  - **FLOATLESSER_INSTR    =  0x4C,**
  - **FLOATLESSEREQUAL_INSTR= 0x4D,**
  - **FLOATGREATER_INSTR   =  0x4E,**
  - **FLOATGREATEREQUAL_INSTR=0x4F,**
  - **MVREGREG_INSTR       =  0x50,**
  - **BOOLEQUAL_INSTR      =  0x51,**
  - **BOOLNOTEQUAL_INSTR   =  0x52,**
  - HEADRR_INSTR         =  0x53,
  - HEADFR_INSTR         =  0x54,
  - HEADFF_INSTR         =  0x55,
  - HEADRF_INSTR         =  0x56,
  - HEADFFR_INSTR        =  0x57,
  - HEADRFR_INSTR        =  0x58,
  - TAILRR_INSTR         =  0x59,
  - TAILFR_INSTR         =  0x5A,
  - TAILFF_INSTR         =  0x5B,
  - TAILRF_INSTR         =  0x5C,
  - MVWORLDREG_INSTR     =  0x5D,
  - MVCONSTREG_INSTR     =  0x5E,
  - CONSRRR_INSTR        =  0x5F,
  - **IF_INSTR        =  0x60,**
  - CONSRFF_INSTR        =  0x61,
  - CONSFRF_INSTR        =  0x62,
  - CONSFFR_INSTR        =  0x63,
  - CONSRRF_INSTR        =  0x64,
  - CONSRFR_INSTR        =  0x65,
  - CONSFRR_INSTR        =  0x66,
  - CONSFFF_INSTR        =  0x67,
  - CALL0_INSTR          =  0x68,
  - CALL1_INSTR          =  0x69,
  - CALL2_INSTR          =  0x6A,
  - CALL3_INSTR          =  0x6B,
  - MVINTSTACK_INSTR     =  0x6C,
  - PUSHN_INSTR          =  0x6D,
  - MAKE_STRUCTF_INSTR   =  0x6E,
  - STRUCT_VALRR_INSTR   =  0x6F,
  - MVNILFIELD_INSTR =  0x70,
  - STRUCT_VALFR_INSTR   =  0x71,
  - STRUCT_VALRF_INSTR   =  0x72,
  - STRUCT_VALRFR_INSTR  =  0x73,
  - STRUCT_VALFF_INSTR   =  0x74,
  - STRUCT_VALFFR_INSTR  =  0x75,
  - MVFLOATSTACK_INSTR   =  0x76,
  - **ADDLINEAR_INSTR      =  0x77,**
  - **ADDPERS_INSTR        =  0x78,**
  - **RUNACTION_INSTR      =  0x79,**
  - ENQUEUE_LINEAR_INSTR =  0x7A,
  - **UPDATE_INSTR         =  0x7B,**
  - MVARGREG_INSTR       =  0x7C,
  - **INTMOD_INSTR         =  0x7D,**
  - CPU_ID_INSTR         =  0x7E,
  - NODE_PRIORITY_INSTR  =  0x7F,
  - **REMOVE_INSTR =  0x80,**
  - **IF_ELSE_INSTR        =  0x81,**
  - **JUMP_INSTR           =  0x82,**
  - ADD_PRIORITY_INSTR   =  0xA0,
  - ADD_PRIORITYH_INSTR  =  0xA1,
  - STOP_PROG_INSTR      =  0xA2,
  - **RETURN_LINEAR_INSTR  =  0xD0,**
  - **RETURN_DERIVED_INSTR =  0xF0**  

## Fix semantics issue regarding retraction

Here is an example in which this issue is involved.
The program is ends-near, and its code is:

```C
const RED = 0.
const GREEN = 3.
const BLUE = 5.

type end(addr).
type middle(addr).
type nearEnd(addr).

setColor2(X, BLUE),
middle(X) :-
   neighborCount(X, C), C > 1.

setColor2(X, RED),
end(X) :-
   neighborCount(X, 1).

setColor2(X, GREEN),
nearEnd(X) :-
   neighbor(X, Y, _), end(Y), middle(X).
```

Assume we start with 11 blocks lined up numbers 0 to 10 from left to
right.  Blocks 1 thru 9 will all derive the middle fact.  Blocks 1 and
9 will also derive the nearEnd fact.  The result looks like:

![](http://img4.hostingpics.net/pics/819739Screenshot20140730144456.png)

If we remove block 5, then blocks 4 & 6 will retract the middle fact
and derive the end fact.  blocks 3 & 7 will derive the nearEnd fact.
As this happens the blocks turn their proper colors and we get:

![](http://img4.hostingpics.net/pics/944394Screenshot20140730144958.png)

If we re-insert block 5, then 4 & 6 retract the end fact, block 3 & 7
retract the nearEnd fact.  Finally, 4, 5, and 6 will derive the middle
fact.  The resulting ensemble looks like:

![](http://img4.hostingpics.net/pics/729608Screenshot20140730145042.png)

In other words, there is no fact derived on blocks 3 & 7 which will
reset their colors to blue!  If we power of the ensemble and power it
back on, everything is fine.

## Simplify compilation process
In the current state of the repository, the VM has to be compiled and linked twice in order to compile a single program.  
1. Firstly, `make` has to be run in *meld-programs/*, it will compile an old *.meld* file, compile system files, and the VM, and put everything in *arch-$ARCH/*.  
2. Then, our actual Meld program will be compiled by *cl-meld*, and the output will be parsed by *LMParser*. The result of this is a bb file which will in turn be moved to *arch-$ARCH/meldinterp-runtime/* and will replace the former *.bb* file.  
3. Finally, `make` has to be run once again to link the file we added to the VM.  
(Have a look at *bin/meld-compile.sh* for further details.)
  
It has to be much simpler than that. Ideally, all we should have to do is to compile with *cl-meld*, parse its output, compile the VM and system files with it and voilà!
