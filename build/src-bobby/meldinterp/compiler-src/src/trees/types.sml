structure Types =
  struct
    open Exceptions

    datatype typ = TypInt
				 | TypFloat
				 | TypAddr
				 | TypList of typ
				 | TypTuple of typ list
         | TypSet of typ
				 | TypUserDefined of string
				 | TypType

	datatype time = TimeNow
				  | TimeInMS of Int32.int

	 datatype agg = AggNone
				  | AggDefined of string * typ

	 datatype theorem = Routing of string
					  | Linear of string
					  | Regular of string
					  | Delta of string * int
					  | Schedule of string
					  | Delete of string
					  | Proved of string
					  | LinearRegular of string

	 datatype const = ConstInt of Int32.int
					| ConstFloat of real
					| ConstAddr of Int32.int

     datatype compare =
			  EQ | NEQ | LESS | GREATER | LESSEQ | GREATEREQ
												   
     datatype binop = PLUS | MINUS | TIMES | DIVIDE | MOD | EXP | DOTPROD |
			  CONS | APPEND
			  
	  datatype persistent = Persistent | NotPersistent

     datatype decl = Decl of theorem * (agg * typ) list * persistent
				   | Extern of string * typ * typ list
				   | Type of string

	 val TypPoint = TypTuple [TypFloat, TypFloat, TypFloat]

	 fun isEqTest EQ = true
	   | isEqTest NEQ = true
	   | isEqTest _ = false

	 fun compareTyp LESS = [(TypInt, TypInt), (TypFloat, TypFloat)]
	   | compareTyp GREATER = [(TypInt, TypInt), (TypFloat, TypFloat)]
	   | compareTyp LESSEQ = [(TypInt, TypInt), (TypFloat, TypFloat)]
	   | compareTyp GREATEREQ = [(TypInt, TypInt), (TypFloat, TypFloat)]
	   | compareTyp EQ = raise (InternalError "Attempted to get the type of EQ. Should use isEqTest to avoid.")
	   | compareTyp NEQ = raise (InternalError "Attempted to get the type of NEQ. Should use isEqTest to avoid.")

	 fun typEq (TypInt, TypInt) = true
	   | typEq (TypFloat, TypFloat) = true
	   | typEq (TypAddr, TypAddr) = true
	   | typEq (TypList t, TypList t') = typEq (t, t')
     | typEq (TypSet t, TypSet t') = typEq (t, t')
	   | typEq (TypTuple tl, TypTuple tl') =
		 not (List.exists (not o typEq) (ListPair.zip (tl, tl')))
	   | typEq (TypUserDefined s, TypUserDefined s') =
		 String.compare(s, s') = EQUAL
	   | typEq (_, _) = false

	 fun typSub (TypInt, TypFloat) = false
	   | typSub (TypList t, TypList t') = typSub (t, t')
     | typSub (TypSet t, TypSet t') = typSub (t, t')
	   | typSub (TypTuple tl, TypTuple tl') =
		 not (List.exists (not o typSub) (ListPair.zip (tl, tl')))
	   | typSub (t, t') = typEq (t, t')

	 fun constEq (ConstInt n, ConstInt n') = n = n'
	   | constEq (ConstFloat r, ConstFloat r') = Real.compare(r, r') = EQUAL
	   | constEq (ConstAddr n, ConstAddr n') = n = n'
	   | constEq _ = false

	 fun binopTyp PLUS = [(TypInt, TypInt, TypInt),
						  (TypFloat, TypFloat, TypFloat)]
	   | binopTyp MINUS = [(TypInt, TypInt, TypInt),
						   (TypFloat, TypFloat, TypFloat)]
	   | binopTyp TIMES = [(TypInt, TypInt, TypInt),
						   (TypFloat, TypFloat, TypFloat)]
	   | binopTyp DIVIDE = [(TypInt, TypInt, TypInt),
							(TypFloat, TypFloat, TypFloat)]
	   | binopTyp MOD = [(TypInt, TypInt, TypInt),
						 (TypFloat, TypFloat, TypFloat)]
	   | binopTyp EXP = [(TypInt, TypInt, TypInt),
						 (TypFloat, TypFloat, TypFloat)]
	   | binopTyp DOTPROD = raise InternalError "dot product is not currently implemented"
	   | binopTyp CONS = raise InternalError "cons is not currently implemented"
	   | binopTyp APPEND = raise InternalError "append is not currently implemented"

	 fun thmeq (Linear s) (Linear s') =
		 String.compare(s, s') = EQUAL
	   | thmeq (Regular s) (Regular s') =
		 String.compare(s, s') = EQUAL
	   | thmeq (Routing s) (Routing s') =
		 String.compare(s, s') = EQUAL
		 | thmeq (Delta (s, pos)) (Delta (s', pos')) =
		 String.compare(s, s') = EQUAL andalso pos = pos'
		 | thmeq (Delete s) (Delete s') =
		 String.compare(s, s') = EQUAL
		 | thmeq (Schedule s) (Schedule s') =
		 String.compare(s, s') = EQUAL
		 | thmeq (LinearRegular s) (Linear s') =
		 String.compare(s, s') = EQUAL
		 | thmeq (Linear s) (LinearRegular s') =
		 String.compare(s, s') = EQUAL
		 | thmeq (Proved _) (Proved _) = true
	    | thmeq _ _ = false

		fun generateDelta name pos = "__delta_" ^ name ^ (Int.toString pos)

    fun thmName (Regular name) = name
      | thmName (Routing name) = name
		| thmName (Linear name) = name
		| thmName (Delta (name, pos)) = generateDelta name pos
		| thmName (Schedule name) = "schedule"
		| thmName (Delete name) = "delete"
		| thmName (LinearRegular name) = name
		| thmName (Proved _) = "proved"

    fun persistentString Persistent = "p"
      | persistentString _ = ""

    fun stringThm (Regular name) = "<regular>" ^ name
      | stringThm (Routing name) = "<routing>" ^ name
			| stringThm (Linear name) = "<linear>" ^ name
			| stringThm (Delta (name, par)) = "<delta>" ^ (generateDelta name par)
			| stringThm (Schedule name) = "<schedule>"
			| stringThm (Delete name) = "<delete>"
			| stringThm (LinearRegular name) = "<linear-regular>" ^ name
			| stringThm (Proved name) = "<proved>"

    fun stringTyp typ =
		case typ
		 of TypInt => " int"
		  | TypAddr => " addr"
		  | TypFloat => " float"
		  | TypType => " type"
        | TypSet typ => " (set" ^ (stringTyp typ) ^ ")"
		  | TypTuple typs => "(" ^ (StringUtil.concatify ", " (List.map stringTyp typs)) ^ ")"
		  | TypList typ =>
			(" (list" ^ 
			 (stringTyp typ) ^
			 ")")
		  | TypUserDefined ident =>
			" " ^ ( ident)

	fun lookupThm [] thm = NONE
	  | lookupThm ((Type _)::decls) thm =
		lookupThm decls thm
	  | lookupThm ((Extern _)::decls) thm =
		lookupThm decls thm
	  | lookupThm ((Decl (thm', ats, _))::decls) thm =
		if thmeq thm thm'
		then SOME ats
		else lookupThm decls thm

	fun lookupThm' [] thm = NONE
	  | lookupThm' ((Type _)::decls) thm =
		lookupThm' decls thm
	  | lookupThm' ((Extern _)::decls) thm =
		lookupThm' decls thm
	  | lookupThm' ((Decl (thm', ats, _))::decls) thm =
		if (thmName thm') = thm
		then SOME (thm', ats)
		else lookupThm' decls thm

	 fun lookupExtern [] extern = NONE
	   | lookupExtern ((Type _)::decls) extern =
		 lookupExtern decls extern
	   | lookupExtern ((Extern (s,t,tl))::decls) extern =
		 if (String.compare(s, extern) = EQUAL)
		 then SOME (t,tl)
		 else lookupExtern decls extern
	   | lookupExtern ((Decl _)::decls) extern =
		 lookupExtern decls extern

	 fun lookupType [] s = false
	   | lookupType ((Type s')::decls) s =
		 (String.compare(s,s') = EQUAL) orelse
		 (lookupType decls s)
	   | lookupType ((Extern _)::decls) s =
		 lookupType decls s
	   | lookupType ((Decl _)::decls) s =
		 lookupType decls s

	 val newVar = 
			 let
					 val n = ref 0
			 in
					 fn () => (n := !n + 1; "_" ^ (Int.toString (!n)))
			 end

    fun stringBinop binop =
		(case binop
		  of PLUS => "+"
	       | MINUS => "-"
	       | TIMES => "*"
	       | DIVIDE => "/"
	       | MOD => "%"
	       | CONS => "::"
	       | EXP => "^"
           | DOTPROD => "`"
		   | APPEND => "@")
  end
