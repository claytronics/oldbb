signature STRINGUTIL =
  sig
	  val concatify : string -> string list -> string
  end


structure StringUtil :> STRINGUTIL =
  struct

    fun concatify _ [] = ""
	  | concatify _ [s] = s
	  | concatify delim (s::l) = s ^ delim ^ (concatify delim l)

  end