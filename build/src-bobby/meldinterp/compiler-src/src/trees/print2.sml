signature PRINT2 =
  sig

      val print : Ast2.program -> string

  end

structure Print2 :> PRINT2 =
  struct

    structure A = Ast2
    structure T = Types
	structure U = StringUtil

	fun concat s f l = U.concatify s (List.map f l)

	val stringBinop = T.stringBinop
		
    fun stringVar var =
		case var
		 of A.Var (ident, _) => (" " ^  ident)
		  | A.VarAny _ => (" _")
						
	fun stringConst (T.ConstInt i) =  (" " ^ Int32.toString i)
	  | stringConst (T.ConstFloat f) =  (" " ^ Real.toString f)
	  | stringConst (T.ConstAddr a) =  (" #" ^ Int32.toString a)

    fun stringExp (exp, tp) =
		(case Mark.data exp
		  of A.ExpVar var => stringVar var
	       | A.ExpNil =>  " []"
				 | A.ExpType typ => " " ^ typ
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
		   | A.ExpField (e, n) => "(" ^ (stringExp e) ^ "." ^ (Int.toString n) ^ ")")
		^ " : " ^ (T.stringTyp tp)

	val stringThmName = T.stringThm

    fun stringThm thm =
		case Mark.data thm
		 of A.Thm(thmName, vars) =>
			(" (thm " ^
			 (stringThmName thmName) ^
			 (concat ", " stringExp vars) ^
			 ")")

	fun stringThmTime (thm, T.TimeNow) = stringThm thm
	  | stringThmTime (thm, T.TimeInMS n) = (stringThm thm) ^ "@+" ^ (Int32.toString n) ^ "ms"

    fun stringCompare compare =
		(case compare
		  of T.EQ => "="
	       | T.NEQ => "!="
	       | T.LESS => "<"
	       | T.GREATER => ">"
	       | T.LESSEQ => "<="
	       | T.GREATEREQ => ">=")

    fun stringConstraint con =
		case Mark.data con of
			A.Constraint(compare, exp1, exp2) =>
			(" (constraint " ^
			 (stringCompare compare) ^
			 (stringExp exp1) ^
			 (stringExp exp2) ^
			 ")")
		  | A.Forall _ => "(forall is not currently printed)"
(*
		  | A.Forall (thm, cl) =>
			("Forall " ^
			 stringThm thm ^
			 " [" ^
			 (concat ", " stringClause cl) ^
			 "]")
*)

	val stringTyp = T.stringTyp

    fun stringAggTyp (T.AggNone, typ) = stringTyp typ
      | stringAggTyp (T.AggDefined (ident, typ), typ2) = (stringTyp typ ^ " " ^ ident ^ (stringTyp typ2))

    fun stringRule rule =
		case Mark.data rule
		 of A.Rule(thm, thms, cons) =>
			(" (rule" ^
			 (concat ", " stringThmTime thm) ^ " :- " ^
			 (concat ", " stringThm thms) ^
			 (concat ", " stringConstraint cons) ^
			 ")")
			 
    fun stringDecl decl =
		case decl
		 of T.Decl(ident, typs, persistent) =>
			(" (decl" ^ T.persistentString persistent ^ " " ^
			 stringThmName ident ^
			 (concat " " stringAggTyp typs) ^
			 ")")
		  | T.Extern (str, typ, typs) => " (extern " ^ (stringTyp typ) ^ " " ^ str ^ (concat " " stringTyp typs) ^ ")"
		  | T.Type (str) => " (type " ^ str ^ ")"

    fun stringProg program =
		case Mark.data program
		 of A.Program(decls, rules) =>
			("(program\n  (decls\n    " ^
			 (concat "\n    " stringDecl decls) ^
			 ")\n  (rules\n    " ^
			 (concat "\n    " stringRule rules) ^
			 "))")

	val print = stringProg
  end
