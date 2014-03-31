signature COMPILER =
  sig
	  val compile  : string -> string -> bool
	  val compileVM  : CompileVM.model -> string -> string -> bool
		val compileParallel : string -> string -> bool

	  val compile' : string * string list -> OS.Process.status
  end

structure Compiler :> COMPILER =
struct
  exception ParseError
  open Exceptions

	val debug = ref true
									
  fun printError s = (print (s ^ "\n"); false)

  fun compile _ _ =
	  raise InternalError "unimplemented"

  fun compileVM model infile outfile =
	  let
	     (* drops the home argument from the tuples *)
		  val dropHomeArgument = case model of
		                              CompileVM.BB => false
		                              | CompileVM.PARALLEL => true

		  val (tree, parseerrors) = Parse.parse infile
								
		  val () = if parseerrors then raise ParseError else ()
												 
		  val () = if !debug
				   then (TextIO.print "\n\nPARSED:\n";
						 (TextIO.print o Print.print) tree;
						 TextIO.print "\n")
				   else ()

		  val (warnings, err) = Warnings.warn tree 
		  val () = case warnings of [] => ()
								  | _ => (TextIO.print "\n\n";
										  List.app TextIO.print warnings;
										  TextIO.print "\n\n")
		  val () = if err then raise Warnings.WarningError "" else ()

		  val tree = case model of CompileVM.BB => BlinkyBlockModel.applyModel tree
														 | CompileVM.PARALLEL => ParallelModel.applyModel tree

		  val () = if !debug
				   then (TextIO.print "\n\nAPPLIED MODEL:\n";
						 (TextIO.print o Print.print) tree;
						 TextIO.print "\n")
				   else ()

		  val tree'    = TypeCheck.typecheck tree

		  val () = if !debug
				   then (TextIO.print "\n\nTYPE-CHECKED:\n";
						 (TextIO.print o Print2.print) tree';
						 TextIO.print "\n")
				   else ()

		  val tree''   = ModeCheck.modecheck tree'

		  val () = if !debug
				   then (TextIO.print "\n\nMODE-CHECKED:\n";
						 (TextIO.print o Print3.print) tree'';
						 TextIO.print "\n")
				   else ()

		  val tree'''  = Localize.localize tree'' dropHomeArgument

		  val () = if !debug
				   then (TextIO.print "\n\nLOCALIZED:\n";
						 (TextIO.print o Print4.print) tree''';
						 TextIO.print "\n")
				   else ()

          val (prog, functs) = CompileVM.compile model tree'''

          fun cify v = "const unsigned char meld_prog[] = {" ^ (Word8Vector.foldr (fn (w,s) => "0x" ^ (Word8.toString w) ^ ", " ^ s) "};\n" v)

          val out = TextIO.openOut outfile
          val (vmprog, tupleNames) = PrintVM.write prog
          val () = TextIO.output (out, cify vmprog)
          val () = TextIO.output (out, "\n\nchar *tuple_names[] = {" ^
                                       (foldr
                                            String.^
                                        "};\n\n"
                                            (List.map (fn x => "\"" ^ x ^ "\", ") tupleNames)))

		  val () = TextIO.output (out, functs)
          val () = TextIO.output (out, "\n\n/*\n")
          val () = TextIO.output (out, PrintVM.printString prog)
          val () = TextIO.output (out, "*/\n\n")
          val () = TextIO.closeOut out
	  in
		  true
	  end handle InternalError s => printError ("Internal error: " ^ s)
			   | TypeCheck.TypeError s => printError ("Type error: " ^ s)
			   | ModeCheck.ModeError s => printError ("Mode error: " ^ s)
			   | Localize.LocalizationError s => printError ("Localization error: " ^ s)
			   | Warnings.WarningError s => printError ("Localization error: " ^ s)
			   | IO.Io e => printError ("IO error: " ^ #name e)

	fun usage prog =
      (TextIO.print (prog ^ " [-vm <model>] [-d] <source file> <output class name>\n");
       TextIO.print ("\t-vm <model>    target 'bb' or 'parallel' machine\n");
       TextIO.print ("\t-d             print out compiler debug info\n");
       OS.Process.exit OS.Process.failure)

	val model = ref NONE : CompileVM.model option ref

	fun processArgs prog ("-d"::args) = (debug := true; processArgs prog args)
		| processArgs prog ("-vm"::"bb"::args) = (model := SOME CompileVM.BB; processArgs prog args)
		| processArgs prog ("-vm"::"parallel"::args) = (model := SOME CompileVM.PARALLEL; processArgs prog args)
		| processArgs prog (file::args) =
			(case explode file
				of (#"-"::_) => usage prog
				 | _ => (file)::(processArgs prog args))
		| processArgs prog [] = []

	val compileParallel = compileVM CompileVM.PARALLEL

	fun compile' (prog, args) =
			((case processArgs prog args of
					 [inFile, outFile] =>
					 if (case !model of NONE => compile inFile outFile
														| SOME m => compileVM m inFile outFile)
					 then OS.Process.success
					 else OS.Process.failure
				 | _ => usage prog)
			handle InternalError msg => (TextIO.print ("ERROR: " ^ msg ^ "\n"); usage prog))
end
																						 
