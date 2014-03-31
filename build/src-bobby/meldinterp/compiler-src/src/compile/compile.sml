signature COMPILE =
  sig
			val compile : Ast3.program -> Cst.program

			exception ModeError of string
  end
