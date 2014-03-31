structure Ast2 =
  struct
	structure T = Types

    datatype rule' = Rule of (thm * T.time) list * thm list * constraint list
	 and thm' = Thm of T.theorem * exp list
	 and exp' = ExpVar of var
			  | ExpNil
			  | ExpConst of T.const
				| ExpType of string
			  | ExpBinop of T.binop * exp * exp
			  | ExpExtern of string * exp list
			  | ExpTuple of exp list
			  | ExpField of exp * int
	 and var = Var of string * T.typ
			 | VarAny of T.typ
	 and constraint' =
		 Constraint of T.compare * exp * exp
	   | Forall of thm * thm list * constraint list
    withtype rule = rule' Mark.marked
	 and thm = thm' Mark.marked
	 and exp = exp' Mark.marked * T.typ
	 and constraint = constraint' Mark.marked

    datatype program' = Program of T.decl list * rule list

    withtype program = program' Mark.marked


  fun expEq ((e, t), (e', t')) = T.typEq (t, t') andalso exp'Eq (Mark.data e, Mark.data e')
  and exp'Eq (ExpVar v, ExpVar v') = v = v'
	| exp'Eq (ExpNil, ExpNil) = true
	| exp'Eq (ExpConst c, ExpConst c') = T.constEq(c, c')
	| exp'Eq (ExpType c, ExpType c') = (c = c')
	| exp'Eq (ExpBinop (oper, e1, e2), ExpBinop (oper', e1', e2')) = oper = oper' andalso expEq (e1, e1') andalso expEq (e2, e2')
	| exp'Eq (ExpExtern (name, el), ExpExtern (name', el')) = name = name' andalso not (List.exists (not o expEq) (ListPair.zip (el, el')))
	| exp'Eq (ExpTuple el, ExpTuple el') = not (List.exists (not o expEq) (ListPair.zip (el, el')))
	| exp'Eq (ExpField (e, n), ExpField (e', n')) = n = n' andalso expEq (e, e')
	| exp'Eq _ = false
  end
