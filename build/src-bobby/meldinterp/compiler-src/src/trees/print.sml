signature PRINT =
  sig

	  val stringProg : Ast.program -> string
	  val stringTyp : Types.typ -> string
	  val stringExp : Ast.exp -> string

      val print : Ast.program -> string
      
  end

structure Print :> PRINT =
  struct

    structure A = Ast
    structure T = Types
	structure U = StringUtil

	fun concat s f l = U.concatify s (List.map f l)
					   
	val stringBinop = T.stringBinop

    fun stringVar var =
		case var
		 of A.Var ident => (" " ^  ident)
		  | A.VarAny => (" _")
						
	fun stringConst (T.ConstInt i) =  (" " ^ Int32.toString i)
	  | stringConst (T.ConstFloat f) =  (" " ^ Real.toString f)
	  | stringConst (T.ConstAddr a) =  (" #" ^ Int32.toString a)

    fun stringExp' exp =
      case exp of
         A.ExpVar var => stringVar var
   	    | A.ExpNil =>  " []"
   			| A.ExpType el => el
   	    | A.ExpBinop (binop, exp1, exp2) =>
   	      ( " (" ^
   	       (stringBinop binop) ^
   	       (stringExp exp1) ^
   	       (stringExp exp2) ^
   	        ")")
   		| A.ExpConst c => stringConst c
   	    | A.ExpExtern(f, el) => 
   	      " " ^ f ^ "(" ^ ( concat ", " stringExp el) ^ ")"
           | A.ExpTuple el =>
   		  "(" ^ (concat ", " stringExp el) ^ ")"
   and stringExp exp = stringExp' (Mark.data exp)

	val stringThmName = T.stringThm

    fun stringThm thm =
		case Mark.data thm
		 of A.Thm(thmName, vars) =>
			(" (thm " ^
			 (stringThmName thmName) ^
			 (concat ", " stringExp vars) ^
			 ")")

	fun stringThmTime (thm, T.TimeNow) =
		stringThm thm
	  | stringThmTime (thm, T.TimeInMS n) =
		stringThm thm ^ (" @ +" ^ (Int32.toString n) ^ "ms")

    fun stringCompare compare =
		(case compare
		  of T.EQ => "="
	       | T.NEQ => "!="
	       | T.LESS => "<"
	       | T.GREATER => ">"
	       | T.LESSEQ => "<="
	       | T.GREATEREQ => ">=")

    fun stringClause clause =
		case Mark.data clause
		 of A.ThmClause thm => stringThm thm
		  | A.Constraint(compare, exp1, exp2) =>
			(" (constraint " ^
			 (stringCompare compare) ^
			 (stringExp exp1) ^
			 (stringExp exp2) ^
			 ")")
		  | A.Forall (thm, cl) =>
			("Forall " ^
			 stringThm thm ^
			 " [" ^
			 (concat ", " stringClause cl) ^
			 "]")

	val stringTyp = T.stringTyp

    fun stringAggTyp (T.AggNone, typ) = stringTyp typ
      | stringAggTyp (T.AggDefined (ident, typ), typ2) = (stringTyp typ ^ " " ^ ident ^ (stringTyp typ2))

    fun stringRule rule =
		case Mark.data rule
		 of A.Rule(thmTimes, clauses) =>
			(" (rule" ^
			 (concat ", " stringThmTime thmTimes) ^ ":-" ^ 
			 (concat ", " stringClause clauses) ^
			 ")")

    fun stringDecl decl =
		case decl
		 of T.Decl (ident, typs, persistent) =>
			(" (decl" ^ T.persistentString persistent ^ " " ^
			 stringThmName ident ^
			 (concat " " stringAggTyp typs) ^
			 ")")
		  | T.Extern (str, typ, aggtyps) => " (extern " ^ (stringTyp typ) ^ " " ^ str ^ (concat " " stringTyp aggtyps) ^ ")"
		  | T.Type (str) => " (type " ^ str ^ ")"

    fun stringProg (A.Program(_, decls, rules)) =
			("(program\n  (decls\n    " ^
			 (concat "\n    " (stringDecl o Mark.data) decls) ^
			 ")\n  (rules\n    " ^
			 (concat "\n    " stringRule rules) ^
			 "))")

	val print = stringProg

  end
