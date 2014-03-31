structure Ast4 =
  struct
	structure T = Types
			  
	(* NB: the list of variables may contain more variables than actually appear in the rule *)

    datatype rule = Rule of (thm * target * T.time) list * thm list * constraint list
    and thm = Thm of T.theorem * var list
			| Forall of T.theorem * var list * T.theorem * var list
	and exp' = ExpVar of var
			 | ExpNil
			 | ExpType of string
			 | ExpInt of Int32.int
			 | ExpFloat of real
			 | ExpBinop of T.binop * exp * exp
			 | ExpExtern of string * exp list
			 | ExpField of exp * var
	       | ExpPoint of exp * exp * exp
	       | ExpReverse of exp
	       | ExpDrop of exp
	       | ExpHostId
	and exp = Exp of exp' * T.typ
	and var = Var of string * T.typ
	and constraint = Constraint of T.compare * exp * exp
				   | Assign of var * exp
	and target = LOCAL | SEND of var

    datatype program = Program of T.decl list * rule list
  end
