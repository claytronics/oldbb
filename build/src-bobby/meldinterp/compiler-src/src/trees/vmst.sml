structure VMst =
  struct

	structure T = Types

  type ident = string

  datatype binop =
		   INT_PLUS | INT_MINUS | INT_TIMES | INT_DIVIDE | INT_MOD |
		   FLOAT_PLUS | FLOAT_MINUS | FLOAT_TIMES | FLOAT_DIVIDE | FLOAT_MOD |
		   INT_EQ | INT_NEQ | INT_LESS | INT_GREATER | INT_LESSEQ | INT_GREATEREQ |
		   FLOAT_EQ | FLOAT_NEQ | FLOAT_LESS | FLOAT_GREATER | FLOAT_LESSEQ | FLOAT_GREATEREQ |
		   TYPE_EQ | ADDR_NEQ | ADDR_EQ

  type register = int

  datatype value =
		   REG of register
		 | THE_TUPLE
		 | CONST of Int32.int
		 | CONSTF of real
		 | FIELD of register * int (* register of tuple, fieldnum *)
		 | TYPE of string
		 | HOST_ID
		 | REVERSE of register * int (* register of tuple, fieldnum *)

  type matchelem = (int * value) (* field's number and value to be matched *)

  datatype instruction =
		   IF of register * int
		 | ELSE
		 | ENDIF
		 | ITER of ident * matchelem list * int
		 | NEXT
		 | SEND of register * register * value (* tuple, route *)
		 | REMOVE of register
		 | OP of binop * value * value * register
		 | MOVE of value * value
		 | ALLOC of ident * value
		 | RETURN
		 | CALL of int * register * value list

  datatype agg =
		   AGG_NONE
		 | AGG_FIRST of int
		 | AGG_MAX of int
		 | AGG_MIN of int
		 | AGG_SUM of int
		 | AGG_MAX_FLOAT of int
		 | AGG_MIN_FLOAT of int
		 | AGG_SUM_FLOAT of int
		 | AGG_SET_UNION_INT of int
		 | AGG_SET_UNION_FLOAT of int
		 | AGG_SUM_LIST_INT of int
		 | AGG_SUM_LIST_FLOAT of int

	type deltafield = T.theorem list
   type arguments = int list
   type stratorder = int option
   type withproved = bool
	type tupleinfo = (T.theorem * agg * arguments * deltafield * T.persistent * stratorder * withproved) * instruction list
   type program = tupleinfo list

  end
