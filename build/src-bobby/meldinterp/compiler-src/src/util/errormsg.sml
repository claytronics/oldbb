(* L1 Compiler
 * Error messages
 *)

signature ERRORMSG =
sig
    
(* clears out all errors from the system *)
val reset : unit -> unit
	
(* global flag that indicates whether there were errors *)
val anyErrors : bool ref
		
val sourceStream : TextIO.instream ref
		   
(* sets the error flag and prints out an error message, does NOT raise ERROR*)
val error : Mark.ext option -> string -> unit
(* same, but different message *)
val warn : Mark.ext option -> string -> unit
					
(* generic code stopping exception *)
exception Error
(* use for compiler bugs, always returns Error exception *)
val impossible : string -> 'a
end

structure ErrorMsg :> ERRORMSG =
struct
(* Initial values of compiler state variables *)
val anyErrors = ref false
val sourceStream = ref TextIO.stdIn
		   
fun reset() = (anyErrors:=false; sourceStream:=TextIO.stdIn)
	      
fun s2dig n = String.concat [if n < 0 then "-" else "",
                             Int.toString (Int.abs n)]
	      
fun msg str ext note =
    (anyErrors := true;
     Option.map (TextIO.print o Mark.show) ext;
     List.app TextIO.print [":", str, ":", note, "\n"])
    
val error = msg "error"
val warn = msg "warning"
	       
	       (* Print the given error message and then abort compilation *)
exception Error
fun impossible msg =
    (app print ["error: Compiler bug: ", msg, "\n"];
     TextIO.flushOut TextIO.stdOut;
     raise Error)
end
