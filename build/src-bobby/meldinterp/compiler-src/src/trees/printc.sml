signature PRINTC =
  sig
    val print_string : Cst.program -> string
    val print_header : Cst.header -> string
    val print_code : Cst.code -> string
    val print : Cst.program -> unit
  end

structure PrintC :> PRINTC =
  struct

    open Cst
	open Exceptions

    fun commafy [] = ""
      | commafy [s] = s
      | commafy (s::l) = s ^ ", " ^ (commafy l)

    val appendinate = foldl (fn (a, b) => b ^ a) ""

    fun printInclude (Library s) = "#include <" ^ s ^ ">\n"
      | printInclude (Local s) = "#include \"" ^ s ^ "\"\n"

    fun printTyp TypNone = ""
      | printTyp t = 
	let
	    fun printTyp' TypInt = "int"
	      | printTyp' TypFloat = "double"
	      | printTyp' TypAddr = "addr"
	      | printTyp' (TypList t) = "list<" ^ (printTyp' t) ^ " >"
	      | printTyp' (TypListIt t) = "list<" ^ (printTyp' t) ^ " >::iterator"
	      | printTyp' (TypVector t) = "vector<" ^ (printTyp' t) ^ " >"
	      | printTyp' (TypVectorIt t) = "vector<" ^ (printTyp' t) ^ " >::iterator"
	      | printTyp' TypVoid = "void"
	      | printTyp' (TypDefined id) =  id
	      | printTyp' (TypRef (TypRef t)) = (printTyp' (TypRef t)) ^ "*"
	      | printTyp' (TypRef t) = (printTyp' t) ^ " *"
	      | printTyp' (TypRref t) = (printTyp' t) ^ " &"
	      | printTyp' TypBool = "bool"
	      | printTyp' TypNone = ""
	      | printTyp' TypPoint = "Point"
	      | printTyp' TypQuaternion = "Quaternion"
	      | printTyp' (TypTemplate(id, typ)) = ( id ^ "<") ^ 
						   (commafy (List.map printTyp' typ)) ^
						   " >"
		  | printTyp' TypString = "char *"
	in
	    case t of (TypRef _) => printTyp' t
		    | _ => (printTyp' t) ^ " "
	end

    fun printBinop PLUS = "+"
      | printBinop MINUS = "-"
      | printBinop TIMES = "*"
      | printBinop DIVIDE = "/"
      | printBinop MOD = "%"
      | printBinop EQ = "=="
      | printBinop NEQ = "!="
      | printBinop LESS = "<"
      | printBinop GREATER = ">"
      | printBinop LESSEQ = "<="
      | printBinop GREATEREQ = ">="
      | printBinop AND = "&&"
      | printBinop OR = "||"
      | printBinop OUT = "<<"

    fun printVar (Var (name, typ)) = (printTyp typ) ^ ( name)
      | printVar (Const v) = "const " ^ printVar v
      | printVar (Static v) = "static " ^ printVar v

    fun printConst (Int i) = Int32.toString i
      | printConst (Float f) = Real.toString f
      | printConst (Define s) =  s
      | printConst (String s) = "\"" ^ s ^ "\""
      | printConst (Null) = "NULL"
      | printConst (Bool b) = Bool.toString b

    fun printExp'' (ExpBinop (AND, ExpConst(Int 1), e)) (l, r) =
	printExp'' e (l, r)
      | printExp'' (ExpBinop (b, e1, e2)) (l,r) = 
	let 
	    val s =
		(printExp' e1 (false, true)) ^ " " ^
		(printBinop b) ^ " " ^
		(printExp' e2 (true, false))
	in
	    (l orelse r, s)
	end
      | printExp'' (ExpAssign (e1, e2)) (l,r) = 
	let
	    val s = (printExp' e1 (false, true)) ^ " = " ^ (printExp' e2 (true, false))
	in
	    (l orelse r, s)
	end
      | printExp'' (ExpCall (e, f, l)) (b, _) =
	(b,
	 (case e of NONE => "" | SOME e => printExp' e (false, true) ^ ".") ^
	      ( f) ^ "(" ^
	      (commafy (List.map (fn e => printExp' e (false, false)) l)) ^ ")")
      | printExp'' (ExpVar v) _ = (false,  v)
      | printExp'' (ExpConst c) _ = (false, printConst c)
      | printExp'' (ExpDeref e) (_, r) = 
	let
	    val s = "*" ^ (printExp' e (true, false))
	in
	    (r, s)
	end  
      | printExp'' (ExpRef e) (_, r) =
	let
	    val s = "&" ^ (printExp' e (true, false))
	in
	    (r, s)
	end
      | printExp'' (ExpField (e, i)) (l, _) =
	let
	    val s = (printExp' e (false, true)) ^ "." ^ ( i)
	in
	    (l, s)
	end
      | printExp'' (ExpRefField (e, i)) (l, _) =
	let
	    val s = (printExp' e (false, true)) ^ "->" ^ ( i)
	in
	    (l, s)
	end
      | printExp'' (ExpCast (t, e)) (_, r) =
	let
	    val s = "(" ^ (printTyp t) ^ ")" ^ (printExp' e (true, false))
	in
	    (r, s)
	end
      | printExp'' (ExpNew (t, es)) (_, r) = 
	let
	    val s = "new " ^ printTyp t ^ "(" ^
		commafy (List.map (fn e => printExp' e (false, false)) es) ^ ")"
	in
	    (r, s)
	end
      | printExp'' (ExpDelete e) (_, r) =
	let
	    val s = "delete " ^ (printExp' e (true, false))
	in
	    (r, s)
	end
      | printExp'' (ExpComment (comment, e)) (l, r) = 
	let
	    val (b, s) = printExp'' e (l, r)
	    val s = "/* " ^ comment ^ " */" ^ s
	in
	    (b, s)
	end
      | printExp'' (ExpPlusPlus e) (l, _) =
	let
	    val s = (printExp' e (false, true)) ^ "++"
	in
	    (l, s)
	end
      | printExp'' (ExpNot e) (_, r) =
	let
	    val s = "!" ^ (printExp' e (true, false))
	in
	    (r, s)
	end
      | printExp'' (ExpMinusMinus e) (l, _) = 
	let
	    val s = (printExp' e (false, true)) ^ "--"
	in
	    (l, s)
	end
      | printExp'' (ExpSizeof t) _ = 
	let
	    val s = "sizeof(" ^ printTyp t ^ ")"
	in
	    (false, s)
	end
      | printExp'' (ExpIndex (e1, e2)) (l, _) =
	let
	    val s = (printExp' e1 (false, true)) ^
		"[" ^ (printExp' e2 (false, false)) ^ "]"
	in
	    (l, s)
	end
    and printExp' e (l,r) =
	case printExp'' e (l,r)
	    of (true, s) => "(" ^ s ^ ")"
             | (false, s) => s


    fun printExp e = printExp' e (true, true)
    val printExp' = (fn e => printExp' e (false, false))


(*
    fun printExp' (ExpBinop (b, e1, e2)) = (printExp e1) ^ " " ^
					   (printBinop b) ^ " " ^
					   (printExp e2)
      | printExp' (ExpAssign (v, e)) = ( v) ^ " = " ^
				       (printExp e)
      | printExp' (ExpCall (e, f, l)) =
	(case e of NONE => "" | SOME e => printExp e ^ ".") ^
	( f) ^ "(" ^
	(commafy (List.map printExp' l)) ^ ")"
      | printExp' (ExpVar v) =  v
      | printExp' (ExpConst c) = printConst c
      | printExp' (ExpDeref e) = "*" ^ (printExp e)
      | printExp' (ExpRef e) = "&" ^ (printExp e)
      | printExp' (ExpField (e, i)) = (printExp e) ^ "." ^ ( i)
      | printExp' (ExpRefField (e, i)) = (printExp e) ^ "->" ^ ( i)
      | printExp' (ExpCast (t, e)) = "(" ^ (printTyp t) ^ ")" ^ (printExp e)
      | printExp' (ExpNew (t, es)) = "new " ^ printTyp t ^ "(" ^
				     commafy (List.map printExp' es) ^ ")"
      | printExp' (ExpDelete e) = "delete " ^ (printExp e)
      | printExp' (ExpComment (s, e)) = "/* " ^ s ^ " */" ^ printExp' e
      | printExp' (ExpPlusPlus e) = (printExp e) ^ "++"
      | printExp' (ExpNot e) = "!" ^ (printExp e)
      | printExp' (ExpMinusMinus e) = (printExp e) ^ "--"
      | printExp' (ExpSizeof t) = "sizeof(" ^ printTyp t ^ ")"
      | printExp' (ExpIndex (e1, e2)) = (printExp e1) ^ "[" ^ (printExp' e2) ^ "]"

    and printExp (e as (ExpVar _)) = printExp' e
      | printExp (e as ExpConst _) = printExp' e
      | printExp (e as ExpSizeof _) = printExp' e
      | printExp (e as ExpCall _) = printExp' e
      | printExp e = "(" ^ (printExp' e) ^ ")"
*)

    fun getNewsDeletes (ExpBinop (_, e1, e2)) =
	let
	    val (n1, d1) = getNewsDeletes e1
	    val (n2, d2) = getNewsDeletes e2
	in
	    (n1@n2, d1@d2)
	end
      | getNewsDeletes (ExpAssign (e1, e2)) =
	let
	    val (n1, d1) = getNewsDeletes e1
	    val (n2, d2) = getNewsDeletes e2
	in
	    (n1@n2, d1@d2)
	end
      | getNewsDeletes (ExpCall (eopt, _, es)) =
	let
	    val (nopt, dopt) = case eopt of NONE => ([], [])
					  | SOME e => getNewsDeletes e

	    val (ns, ds) = ListPair.unzip (List.map getNewsDeletes es)
	    val n = foldl List.@ nopt ns
	    val d = foldl List.@ dopt ds
	in
	    (n, d)
	end
      | getNewsDeletes (ExpVar _) = ([], [])
      | getNewsDeletes (ExpConst _) = ([], [])
      | getNewsDeletes (ExpDeref e) = getNewsDeletes e
      | getNewsDeletes (ExpRef e) = getNewsDeletes e
      | getNewsDeletes (ExpField (e, _)) = getNewsDeletes e
      | getNewsDeletes (ExpRefField (e, _)) = getNewsDeletes e
      | getNewsDeletes (ExpCast (_, e)) = getNewsDeletes e
      | getNewsDeletes (ExpNew (t, es)) =
	let
	    val (ns, ds) = ListPair.unzip (List.map getNewsDeletes es)
	    val n = foldl List.@ [] ns
	    val d = foldl List.@ [] ds
	in
	    (t::n, d)
	end
      | getNewsDeletes (ExpDelete e) =
	let
	    val (n, d) = getNewsDeletes e
	in
	    (n, e::d)
	end
      | getNewsDeletes (ExpIndex (e1, e2)) = 
	let
	    val (n1, d1) = getNewsDeletes e1
	    val (n2, d2) = getNewsDeletes e2
	in
	    (n1@n2, d1@d2)
	end
      | getNewsDeletes (ExpComment (_, e)) = getNewsDeletes e
      | getNewsDeletes (ExpPlusPlus e) = getNewsDeletes e
      | getNewsDeletes (ExpMinusMinus e) = getNewsDeletes e
      | getNewsDeletes (ExpNot e) = getNewsDeletes e
      | getNewsDeletes (ExpSizeof _) = ([], [])

    fun logNewsDeletes indent e =
	let
	    val (ns, ds) = getNewsDeletes e

	    val indent' = indent ^ "  "

	    fun logNew t =
		    indent' ^
		    "cout << \"allocated \" << sizeof(" ^
		    (printTyp t) ^
		    ") << endl;\n"

	    fun logDelete e =
		    indent' ^
		    "cout << \"freed \" << sizeof(" ^
		    (printExp (ExpDeref e)) ^
		    ") << endl;\n"

	    val logStuff = (List.map logNew ns)@(List.map logDelete ds)
	in
	    case logStuff of [] => ""
			   | _::_ => indent ^ "{\n" ^
				     indent ^ "// logging\n" ^
				     (foldl String.^ "" logStuff) ^
				     indent ^ "}\n"
	end

    fun printStatement indent (StmtExp e) = 
	(*
	indent ^ "{\n" ^
	(logNewsDeletes (indent ^ "  ") e) ^ (indent ^ "  ")^ (printExp' e) ^ ";\n" ^
	indent ^ "}\n"
	 *)
	indent ^ (printExp' e) ^ ";\n"
      | printStatement indent (StmtIf (ExpConst (Int 1), s1, _)) =
	printStatement indent s1
      | printStatement indent (StmtIf (c, s1, s2)) =
	(indent ^ "if (" ^ (printExp' c) ^ ")\n") ^
	(case s1 of StmtIf _ => indent ^ "  {\n" ^ (printStatement (indent ^ "    ") s1) ^ indent ^ "  }\n"
	          | StmtFor _ => indent ^ "  {\n" ^ (printStatement (indent ^ "    ") s1) ^ indent ^ "  }\n"
                  | _ => (printStatement (indent ^ "  ") s1)) ^
	(case s2 of
	     NONE => ""
	   | SOME s2 =>
	     (indent ^ "else\n") ^
	     (printStatement (indent ^ "  ") s2) ^
	    (indent ^ "\n"))
      | printStatement indent (StmtSwitch (e, l)) =
	let
	    fun printCase (c, s) =
		(indent ^ "  case " ^ (printConst c) ^ ":\n") ^
		(printStatement (indent ^ "    ") s) ^
		(indent ^ "    break;\n\n")
	in
	    (indent ^ "switch (" ^ (printExp' e) ^ ") {\n") ^
	    (appendinate (List.map printCase l)) ^
	    indent ^ "};\n"
	end
      | printStatement indent (StmtReturn e) =
	indent ^ "return" ^ (case e of NONE => "" | SOME e => " " ^ printExp' e) ^ ";\n"
	(*
	indent ^ "{\n" ^
	(case e of NONE => "" | SOME e => logNewsDeletes indent e) ^
	indent ^ "return" ^ (case e of NONE => "" | SOME e => " " ^ printExp' e) ^ ";\n" ^
	indent ^ "}\n"
	 *)
      | printStatement indent (StmtDecl var) = indent ^ (printVar var) ^ ";\n"
      | printStatement indent (StmtBlock stmts) =
	indent ^ "{\n" ^
	appendinate (List.map (printStatement (indent ^ "  ")) stmts) ^
	indent ^ "}\n\n"
      | printStatement indent (StmtFor (e1, c, e2, body)) =
	indent ^ "for (" ^ printExp' e1 ^ "; " ^ printExp' c ^ "; " ^ printExp' e2 ^ ")\n" ^
	printStatement (indent ^ "  ") body
      | printStatement indent (StmtComment s) =
	let
	    fun indentify #"\n" = "\n" ^ indent ^ " * "
	      | indentify c = String.str c

	    val s' = indent ^ "/* " ^ String.translate indentify s
	in
	    if String.isSuffix "\n" s then
		String.substring (s', 0, String.size s' - 2) ^ "*/\n"
	    else
		s' ^ " */\n"
	end
      | printStatement indent StmtBreak =
	indent ^ "break;\n"
      | printStatement indent StmtContinue =
	indent ^ "continue;\n"

    fun printFunction indent (Function (t, c, name, vars, stmts)) =
	(indent ^ 
	 (printTyp t) ^
	 (case c of NONE => ""
		  | SOME c => ( c) ^ "::") ^
	 ( name) ^ " (" ^ 
	 (commafy (List.map printVar vars)) ^ ")\n") ^
	(indent ^ "{\n") ^
	(appendinate (List.map (printStatement (indent ^ "  ")) stmts)) ^
	(indent ^ "}\n\n")
      | printFunction indent (Constructor (c, name, vars, inits, stmts)) =
	(indent ^ 
	 (case c of NONE => ""
		  | SOME c => ( c) ^ "::") ^
	 ( name) ^ " (" ^ 
	 (commafy (List.map printVar vars)) ^ ")\n") ^
	(case inits of NONE => ""
			       (*
                                  here we use printExp' because extra
                                  parens are an "anachronistic old-style base class initializer"
                                  according to g++.
                               *)
		     | SOME inits => " : " ^ commafy (List.map printExp' inits)) ^
	(indent ^ "{\n") ^
	(appendinate (List.map (printStatement (indent ^ "  ")) stmts)) ^
	(indent ^ "}\n\n")
      | printFunction indent (StaticFunction (t, c, name, vars, stmts)) =
	(indent ^ 
	 "static " ^
	 (printTyp t) ^
	 (case c of NONE => ""
		  | SOME c => ( c) ^ "::") ^
	 ( name) ^ " (" ^ 
	 (commafy (List.map printVar vars)) ^ ")\n") ^
	(indent ^ "{\n") ^
	(appendinate (List.map (printStatement (indent ^ "  ")) stmts)) ^
	(indent ^ "}\n\n")

	
    fun printProto (Proto (t, name, vars)) = (printTyp t) ^
					     ( name) ^ " (" ^
					     (commafy (List.map printVar vars)) ^
					     ");\n"
      | printProto (StaticProto (t, name, vars)) = "static " ^
						   (printTyp t) ^
						   ( name) ^ " (" ^
						   (commafy (List.map printVar vars)) ^
						   ");\n"

    fun printDec (Struct (name, vars, functions)) =
	"struct " ^
	( name) ^ " {\n" ^
	(appendinate (List.map (fn s => "  " ^ (printVar s) ^ ";\n") vars)) ^
	"\n" ^ 
	(appendinate (List.map (fn f => printFunction "  " f) functions)) ^
	"};\n\n"
      | printDec (Class (name, super, vars, protos, funcs)) =
	"class " ^
	( name) ^
	(case super of NONE => "" | SOME(super) => " : public " ^ ( super)) ^
	" { public:\n" ^
	(appendinate (List.map (fn v => "  " ^ (printVar v) ^ ";\n") vars)) ^
	"\n" ^
	(appendinate (List.map (fn p => "  " ^ (printProto p)) protos)) ^
	"\n" ^
	(appendinate (List.map (fn p => printFunction "  " p) funcs)) ^
	"};\n\n"
      | printDec (Enum (name, typs)) = 
	let
	    fun printEnumVal (t, NONE) =  t
	      | printEnumVal (t, SOME v) = ( t) ^ " = " ^ (printExp' v)
	in
	    "enum " ^  name ^ " {" ^
	    (commafy (List.map printEnumVal typs)) ^
	    "} ;\n\n"
	end
      | printDec (Typedef (typ, name)) = "typedef " ^
					 ( typ) ^ " " ^
					 ( name) ^ ";\n\n"
      | printDec (DecExp(e)) = (printExp' e ^ ";\n\n")
    (* nonexhaustive--oops *)

    fun printUsing s = "using namespace " ^ s ^ ";\n"

    fun print_header (Header(includes, usings, decs)) =
	let
	    val includes' = appendinate (List.map printInclude includes)
	    val usings' = appendinate (List.map printUsing usings)
	    val decs' = appendinate (List.map printDec decs)
	in
	    includes' ^ "\n" ^ usings' ^ "\n" ^ decs'
	end


    fun print_code (Code (decs, fl)) =
	appendinate (List.map printDec decs) ^
	appendinate (List.map (printFunction "") fl)



    fun print_program (h, c) = (print_header h) ^ (print_code c)


    val print_string = print_program
    val print = TextIO.print o print_string
  end
