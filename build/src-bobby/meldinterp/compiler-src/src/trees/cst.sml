structure Cst =
  struct
	open Exceptions

    type ident = string

    datatype binop =
	     PLUS | MINUS | TIMES | DIVIDE | MOD |
	     EQ | NEQ | LESS | GREATER | LESSEQ | GREATEREQ |
	     AND | OR | OUT

    datatype inc = Library of string
		 | Local of string
	 and dec = Struct of ident * var list * function list
		   | Class of ident * ident option * var list * functionProto list * function list
		   | Enum of ident * (ident * exp option) list
		   | Typedef of ident * ident
		   | DecExp of exp (* hack for CODE_MODULE macros *)
	 and function = Function of typ * ident option * ident * var list * stmt list
		      | StaticFunction of typ * ident option * ident * var list * stmt list
		      | Constructor of ident option * ident * var list * exp list option * stmt list
	 and functionProto = Proto of typ * ident * var list
			   | StaticProto of typ * ident * var list
	 and var = Var of ident * typ
		 | Const of var
		 | Static of var
	 and typ = TypInt
		 | TypFloat
		 | TypBool
		 | TypAddr
		 | TypString
		 | TypList of typ
		 | TypListIt of typ
		 | TypVector of typ
		 | TypVectorIt of typ
		 | TypVoid
		 | TypDefined of ident
		 | TypRef of typ  (* pointer *)
		 | TypRref of typ (* reference *)
		 | TypNone
		 | TypPoint
		 | TypQuaternion
		 | TypTemplate of ident * typ list
	 and exp = ExpBinop of binop * exp * exp
		 | ExpAssign of exp * exp
		 | ExpCall of exp option * ident * exp list
		 | ExpVar of ident
		 | ExpConst of const
		 | ExpDeref of exp
		 | ExpRef of exp
		 | ExpField of exp * ident
		 | ExpRefField  of exp * ident
		 | ExpCast of typ * exp
		 | ExpNew of typ * exp list
	         | ExpDelete of exp
	         | ExpIndex of exp * exp
		 | ExpComment of string * exp
		 | ExpPlusPlus of exp
	         | ExpMinusMinus of exp
	         | ExpNot of exp
		 | ExpSizeof of typ
	 and stmt = StmtSwitch of exp * (const * stmt) list
		  | StmtIf of exp * stmt * stmt option
		  | StmtExp of exp
	          | StmtReturn of exp option
	          | StmtBreak
	          | StmtContinue
		  | StmtDecl of var
		  | StmtBlock of stmt list
		  | StmtFor of exp * exp * exp * stmt
		  | StmtComment of string
	 and const = Int of Int32.int
		   | Float of real
		   | Bool of bool
		   | Define of ident
		   | String of string
		   | Null
	 and header = Header of inc list * string list * dec list
	 and code = Code of dec list * function list

    val StmtAssign = StmtExp o ExpAssign
    val StmtCall = StmtExp o ExpCall
    val StmtDelete = StmtExp o ExpDelete
    val StmtBinop = StmtExp o ExpBinop
    val ExpInt = ExpConst o Int o Int32.fromInt
    val ExpBool = ExpConst o Bool
    val ExpFalse = ExpBool false
    val ExpTrue = ExpBool true

    fun And [e] = e
      | And (e::l) = ExpBinop(AND, e, And l)
	  | And [] = raise (InternalError "attempted to and zero things together")
    fun Or [e] = e
      | Or (e::l) = ExpBinop(OR, e, Or l)
	  | Or [] = raise (InternalError "attempted to or zero things together")
    fun op= e1 e2 = ExpBinop(EQ, e1, e2)
    fun op>= e1 e2 = ExpBinop(GREATEREQ, e1, e2)
    fun op> e1 e2 = ExpBinop(GREATER, e1, e2)
    fun op<= e1 e2 = ExpBinop(LESSEQ, e1, e2)
    fun op< e1 e2 = ExpBinop(LESS, e1, e2)
    fun op!= e1 e2 = ExpBinop(NEQ, e1, e2)
    fun op! e = ExpNot e

    fun op+ (e1, e2) = ExpBinop(PLUS, e1, e2)
    fun op* e1 e2 = ExpBinop(TIMES, e1, e2)
    fun op+= e1 e2 = StmtAssign(e1, (e1 + e2))

    val Zero = ExpConst (Int 0)
    val One = ExpConst (Int 1)
    val Two = ExpConst (Int 2)

    type program = header * code
  end
