signature TOP =
  sig
	  val compile  : string -> string -> bool
	  val compileVM  : CompileVM.model -> string -> string -> bool
		val compileParallel : string -> string -> bool

	  val export  : unit -> unit
  end

structure Top :> TOP =
struct
  val compile = Compiler.compile
  val compileVM = Compiler.compileVM
	val compileParallel = Compiler.compileParallel

  fun export () = SMLofNJ.exportFn ("meldCompiler", Compiler.compile')
end
																						 
