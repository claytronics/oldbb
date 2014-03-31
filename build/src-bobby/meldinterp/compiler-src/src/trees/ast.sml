structure Ast =
  struct
	structure T = Types

  datatype rule' = Rule of (thm * T.time) list * clause list
	   and thm' = Thm of T.theorem * exp list
	   and exp' = ExpVar of var
				| ExpNil
				| ExpType of string
				| ExpConst of T.const
				| ExpBinop of T.binop * exp * exp
				| ExpExtern of string * exp list
				| ExpTuple of exp list
	   and var = Var of string
						 | VarAny
	   and clause' = ThmClause of thm
				   | Constraint of T.compare * exp * exp
				   | Forall of thm * clause list
  withtype rule = rule' Mark.marked
	   and thm = thm' Mark.marked
	   and exp = exp' Mark.marked
	   and clause = clause' Mark.marked
					
  datatype program = Program of (string * T.const) list * T.decl Mark.marked list * rule list

  fun expEq _ = true

  end


