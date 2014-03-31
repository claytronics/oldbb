structure Print4 =
  struct

    structure A = Ast4
    structure T = Types

    fun joinmap f l = List.foldr (fn (e, s) => f e ^ s) "" l

    fun printBinop binop =
	case binop
	  of T.PLUS => "+"
	   | T.MINUS => "-"
	   | T.TIMES => "*"
	   | T.DIVIDE => "/"
	   | T.MOD => "%"
	   | T.CONS => "::"
	   | T.EXP => "^"
	   | T.DOTPROD => "dot"
       | T.APPEND => "@"

    fun printVar (A.Var (ident, _)) = " " ^ ident

    fun printExp (A.Exp(exp, _)) =
	 case exp
	   of A.ExpVar var => printVar var
	    | A.ExpNil => " []"
			| A.ExpType t => " " ^ t
	    | A.ExpInt i => " " ^ Int32.toString i
	    | A.ExpBinop (binop, exp1, exp2) =>
	      " (" ^
	      printBinop binop ^
	      printExp exp1 ^
	      printExp exp2 ^
	      ")"
	    | A.ExpFloat f => " " ^ Real.toString f
	    | A.ExpExtern (id, el) =>
	      " " ^
	      ( id) ^
	      " (" ^
	      (joinmap printExp el) ^
	      ")"
	    | A.ExpField(e, v) =>
	      " " ^
	      (printExp e) ^
	      "." ^
	      (printVar v)
	    | A.ExpReverse(e) =>
	      " rev " ^ (printExp e)
            | A.ExpDrop(e) =>
	      " drop " ^ (printExp e)
            | A.ExpPoint(e1, e2, e3) => 
	      "(Point: (" ^ (printExp e1) ^ ", " ^ (printExp e2) ^ ", " ^ (printExp e3) ^ "))"
	    | A.ExpHostId => " host_id"

    fun printTheorem (T.Routing id) = "(-->)" ^ (T.stringThm (T.Routing id))
	  | printTheorem id = T.stringThm id

    fun printThm thm =
	    "\n      " ^ (printThm' thm)
    and printThm' thm =
	case thm
	 of A.Thm(theorem, vars) =>
	    "(thm " ^ (printTheorem theorem) ^
	    (joinmap printVar vars) ^
	    ")"
	  | A.Forall(theorem, vars, theorem', vars') =>
	    "(forall" ^ (printThm' (A.Thm(theorem, vars))) ^ " " ^ (printThm' (A.Thm(theorem', vars'))) ^ ")"

	fun printTime T.TimeNow = ""
	  | printTime (T.TimeInMS n) = "@+" ^ (Int32.toString n) ^ "ms"

	fun printThmDest (thm, A.LOCAL, time) = (printThm thm) ^ (printTime time) ^ " LOCAL "
	  | printThmDest (thm, A.SEND x, time) = (printThm thm) ^ (printTime time) ^ " via " ^ (printVar x)
		

    fun printCompare compare =
	case compare
	  of T.EQ => "="
	   | T.NEQ => "!="
	   | T.LESS => "<"
	   | T.GREATER => ">"
	   | T.LESSEQ => "<="
	   | T.GREATEREQ => ">="

    fun printConstraint' constraint =
	case constraint
	   of A.Constraint(compare, exp1, exp2) =>
	      "    (constraint " ^
	      printCompare compare ^
	      printExp exp1 ^
	      printExp exp2 ^
	      ")"
		| A.Assign (v, e) =>
		  ("    (assign " ^
		   (printVar v) ^
		   (printExp e) ^
		   ")")

    and printConstraint constraint = "\n  " ^ printConstraint' constraint

    fun printTyp typ =
	case typ
	  of T.TypInt => " int"
	   | T.TypAddr => " addr"
	   | T.TypFloat => " float"
		 | T.TypType => " type"
	   | T.TypTuple tl => " tuple(" ^ (joinmap printTyp tl)  ^ ")"
	   | T.TypSet typ =>
         " (set" ^
         printTyp typ ^
         ")"
	   | T.TypList typ =>
	     " (list" ^
	     printTyp typ ^
	     ")"
	   | T.TypUserDefined id => " " ^ ( id)

    fun printAggTyp (T.AggNone, typ) = printTyp typ
      | printAggTyp (T.AggDefined(ident, typ), typ2) = (printTyp typ) ^ " " ^ ( ident) ^ " " ^ (printTyp typ2)

    fun printDecl decl =
	case decl
	  of T.Decl(theorem, aggtyps, persistent) =>
	      "    (decl" ^ T.persistentString persistent ^ " " ^
	      printTheorem theorem ^
	      joinmap printAggTyp aggtyps ^
	      ")\n"
	   | T.Extern(id, typ, typs) =>
	      "    (extern " ^ ( id) ^ (printTyp typ) ^
	      joinmap printTyp typs ^ ")\n"
	   | T.Type (str) =>
		  "    (type " ^ str ^ ")\n"

    fun printRule
	    (A.Rule(thms', thms, constraints)) =
	     "    (rule" ^
	     joinmap printThmDest thms' ^
		 " :- " ^
	     joinmap printThm thms ^
	     joinmap printConstraint constraints ^
	     ")\n"

    fun print program =
	case program
	  of A.Program(decls, rules) =>
	     "(program\n  (decls\n" ^
	     joinmap printDecl decls ^
	     ")\n  (rules\n" ^
	     joinmap printRule rules ^
	     "))"
  end
