structure Ast3 =
  struct
	structure T = Types

    datatype rule' = Rule of (thm * T.time) list * thm list * constraint list
		 and thm' = Thm of T.theorem * var list
		 and exp' = ExpVar of var
				  | ExpNil
				  | ExpConst of T.const
					| ExpType of string
				  | ExpBinop of T.binop * exp * exp
				  | ExpExtern of string * exp list
				  | ExpTuple of exp list
				  | ExpField of exp * int
		 and var = Var of string * T.typ
		 and constraint' =
		     Constraint of T.compare * exp * exp
		   | Assign of var * exp
		   | Forall of thm * thm list * constraint list
    withtype rule = rule' Mark.marked
		 and thm = thm' Mark.marked
		 and exp = exp' Mark.marked * T.typ
		 and constraint = constraint' Mark.marked
						  
    datatype program' = Program of T.decl list * rule list
								   
    withtype program = program' Mark.marked
  end
