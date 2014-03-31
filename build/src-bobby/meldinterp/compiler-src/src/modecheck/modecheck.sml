signature MODECHECK =
   sig
      val modecheck : Ast2.program -> Ast3.program

	  exception ModeError of string
  end

structure ModeCheck :> MODECHECK =
  struct
    structure A = Ast2
	structure A2 = Ast3
	structure T = Types

	exception ModeError of string
	open Exceptions

	structure C =
	  struct
  	    fun new () = ref []
					 
		fun lookup G y =
			let
				fun lookup' [] = false
				  | lookup' (x::l) = (x = y) orelse (lookup' l)
			in
				lookup' (!G)
			end

		fun add l y = l := y::(!l)
					  
		fun addAll l l' = l := l'@(!l)

		fun print G =
			let
				fun print' [] = "\n"
				  | print' ((x, _)::l) = x ^ ", " ^ (print' l)
			in
				TextIO.print ("G: " ^ (print' (!G)))
			end
	  end

	val newVar =
		let
			val varRef = ref 0
		in
			fn t => (varRef := !varRef + 1; (A.Var("_mode" ^ (Int.toString (!varRef)), t),
											 A2.Var ("_mode" ^ (Int.toString (!varRef)), t)))
		end

	fun isPure (exp, typ) =
		case (Mark.data exp) of
			A.ExpBinop _ => raise ModeError (Mark.error exp "binop not permitted in tuple")
		  | A.ExpExtern _ => raise ModeError (Mark.error exp "function call not permitted in tuple")
		  | A.ExpTuple el => List.app isPure el
		  | A.ExpVar _ => ()
		  | A.ExpNil => ()
		  | A.ExpConst _ => ()
		  | A.ExpType _ => ()
		  | A.ExpField (e, n) => raise ModeError (Mark.error exp "field not permitted in tuple")

	fun convertVar (A.VarAny t) = #2 (newVar t)
	  | convertVar (A.Var (s, t)) = (A2.Var (s, t))

	fun modecheckThm' isOK (A.Thm (thm, exps)) = 
		let
			fun varify (exp, typ) =
				case (Mark.data exp) of
						A.ExpVar v => (convertVar v, NONE)
					| e =>
						let
								val () = isOK (exp, typ)
								val markings = Mark.ext exp
								val (v1, v2) = newVar typ
						in
								(v2, SOME (Mark.mark' (A.Constraint (T.EQ,
																										 (Mark.mark' (A.ExpVar v1, markings), typ), 
																										 (Mark.mark' (e, markings), typ)),
																			 markings)))
						end

			val results = List.map varify exps
			val vars = List.map #1 results
			val cons = List.mapPartial #2 results
			val strip = List.map (fn A2.Var st => st)
		in
			(strip vars, A2.Thm (thm, vars), cons)
		end

	fun modecheckThm isOK thm =
		let
			val (vars, thm', cons) = modecheckThm' isOK (Mark.data thm)
		in
			(vars, Mark.mark' (thm', Mark.ext thm), cons)
		end

	fun modecheckThmTime isOK (thm, time) =
		let
			val (vars, thm', cons) = modecheckThm' isOK (Mark.data thm)
		in
			(vars, (Mark.mark' (thm', Mark.ext thm), time), cons)
		end

	fun modecheckThms isOK =
			(fn l =>
				(List.foldr List.@ [] (List.map #1 l),
				 List.map #2 l,
				 List.foldr List.@ [] (List.map #3 l)))
				o (List.map (modecheckThm isOK))

	fun modecheckThmTimes isOK =
			(fn l =>
				(List.foldr List.@ [] (List.map #1 l),
				 List.map #2 l,
				 List.foldr List.@ [] (List.map #3 l)))
				o (List.map (modecheckThmTime isOK))

	fun modecheckExp (ks : A2.exp -> 'a) (kf : A.exp -> 'a) G (exp, typ) =
		let
			val m = Mark.ext exp

			fun checkList ks kf [] = ks []
			  | checkList ks kf (x::l) = modecheckExp (fn x' => checkList (fn l' => ks (x'::l')) kf l) kf G x
		in
			case (Mark.data exp) of
				A.ExpVar (A.Var v) => if (C.lookup G v) then ks (Mark.mark' (A2.ExpVar (A2.Var v), m), typ) else kf (exp, typ)
			  | A.ExpVar (A.VarAny _) => kf (exp, typ)
			  | A.ExpNil => ks (Mark.mark' (A2.ExpNil, m), typ)
				| A.ExpType t => ks (Mark.mark' (A2.ExpType t, m), typ)
			  | A.ExpConst c => ks (Mark.mark' (A2.ExpConst c, m), typ)
			  | A.ExpBinop (b, e1, e2) =>
				modecheckExp (fn x1 => modecheckExp (fn x2 => ks (Mark.mark' (A2.ExpBinop (b, x1, x2), m), typ))
													kf G e2)
							 kf G e1
			  | A.ExpExtern (name, el) => checkList (fn el' => ks (Mark.mark' (A2.ExpExtern(name, el'), m), typ))
													kf el
			  | A.ExpTuple el => checkList (fn el' => ks (Mark.mark' (A2.ExpTuple el', m), typ)) kf el
			  | A.ExpField (e, n) => modecheckExp (fn x => ks (Mark.mark' (A2.ExpField (x, n), m), typ))
												  kf G e
		end

	fun modecheckConstraint' G (A.Constraint (cmp, e1, e2)) =
		let
			val fail = fn (e, t) => raise ModeError (Mark.error e ("ill-moded variable " ^
																   "- needs to have had a value assigned"))
			fun tryAssign (assignee, typ) exp =
				case Mark.data assignee of
					A.ExpTuple el =>
					modecheckExp
					(fn e =>
						let
							val (v1, v2) = newVar typ
							val () = C.add G (case v1 of (A.Var st) => st
													   | A.VarAny _ => raise InternalError "newVar returned _, but it should create a new variable with a real name")

							val el' = ListPair.zip (el, List.tabulate(List.length el, fn x => x))

							val cons =
								List.map
									(fn (e, n : int) => A.Constraint(T.EQ,
																	 e,
																	 (Mark.naked
																		  (A.ExpField((Mark.naked (A.ExpVar(v1)),
																					   typ),
																					  n)),
																	  #2 e)
																		 )
																	)
									el'
						in
							[A2.Assign(v2, e)]@(List.foldr List.@ [] (List.map (modecheckConstraint' G) cons))
						end)
					fail
					G
					exp
				  | A.ExpVar v =>
					modecheckExp
					(fn x => ((case v of (A.Var st) => C.add G st | A.VarAny _ => ()); [A2.Assign (convertVar v, x)]))
					fail
					G
					exp
				  | _ =>
					modecheckExp
						(fn _ => raise InternalError "impossible condition reached in the mode checker")
						fail
						G						 
						(assignee, typ)

		in
			modecheckExp
				(fn x1 => modecheckExp
							  (fn x2 => [A2.Constraint (cmp, x1, x2)])
							  (fn _ => tryAssign e2 e1)
							  G
							  e2)
				(fn _ => tryAssign e1 e2)
				G
				e1
		end
	  | modecheckConstraint' _ (A.Forall _) = raise InternalError "forall is not currently supported"

	fun modecheckConstraint G con = 
		let
			val m = Mark.ext con
			val con' = Mark.data con
		in
			List.map (fn c => Mark.mark' (c, m)) (modecheckConstraint' G con')
		end
		
	fun simplifyConstraints G [] = ([], [])
	  | simplifyConstraints G (con::cons) =
			let
					val (cons', eqs) = simplifyConstraints G cons
														 
					val A.Constraint(compare, (e1, t1), (e2, t2)) = Mark.data con
																										
					fun findVar e [] = NONE
						| findVar e (con::cons) =
							case Mark.data con of
									A.Constraint (T.EQ, (e1, t1), (e2, t2)) =>
									(case (Mark.data e1, Mark.data e2) of
											 (A.ExpVar (A.Var v), e') =>
											 if (A.exp'Eq (e, e'))
											 then SOME v
											 else findVar e cons
										 | (e', A.ExpVar (A.Var v)) => 
											 if (A.exp'Eq (e, e'))
											 then SOME v
											 else findVar e cons
										 | _ => findVar e cons)
								| _ => findVar e cons
											 
					fun varPair (v1, v2) = case  (C.lookup G v1, C.lookup G v2)
																	of (true, true) => (v1, v2)
																	 | (false, false) => (v1, v2)
																	 | (true, false) => (C.add G v2; (v1, v2))
																	 | (false, true) => (C.add G v1; (v1, v2))
			in
					case (compare, Mark.data e1, Mark.data e2) of
							(T.EQ, A.ExpVar (A.Var v1), A.ExpVar (A.Var v2)) =>
							(cons, varPair(v1, v2)::eqs)
						| (T.EQ, A.ExpVar (A.Var v1), e2) =>
							(case findVar e2 cons
								of NONE => (con::cons, eqs)
								 | SOME v2 => (cons, varPair(v1, v2)::eqs))
						| (T.EQ, e1, A.ExpVar (A.Var v2)) =>
							(case findVar e1 cons
								of NONE => (con::cons, eqs)
								 | SOME v1 => (cons, varPair(v1, v2)::eqs))
						| _ => (con::cons, eqs)
			end

		(* substitute v2 for v1 in v *)
	fun substVar (v1, v2) (A2.Var v) =
		if v = v1
		then A2.Var v2
		else A2.Var v

	fun substExp sub (e, t) =
		(Mark.map (substExp' sub) e, t)
	and substExp' sub (A2.ExpVar v) = A2.ExpVar (substVar sub v)
	  | substExp' sub A2.ExpNil = A2.ExpNil
	  | substExp' sub (A2.ExpType t) = A2.ExpType t
	  | substExp' sub (A2.ExpConst c) = A2.ExpConst c
	  | substExp' sub (A2.ExpBinop (oper, e1, e2)) = A2.ExpBinop (oper, substExp sub e1, substExp sub e2)
	  | substExp' sub (A2.ExpExtern (name, el)) = A2.ExpExtern (name, List.map (substExp sub) el)
	  | substExp' sub (A2.ExpTuple el) = A2.ExpTuple (List.map (substExp sub) el)
	  | substExp' sub (A2.ExpField (e, n)) = A2.ExpField (substExp sub e, n)

	fun substThm ((v1, v2), thm) = 
		Mark.map
			(fn A2.Thm (theorem, vars) =>
				A2.Thm(theorem, List.map (substVar (v1, v2)) vars)) thm
	 
	fun substThmTime ((v1, v2), (thm, time)) = 
		(Mark.map
			 (fn A2.Thm (theorem, vars) =>
				 A2.Thm(theorem, List.map (substVar (v1, v2)) vars)) thm,
		 time)
	 
	fun substConstraint (sub, constraint) =
		Mark.mark' (substConstraint' (sub, Mark.data constraint), Mark.ext constraint)
	and substConstraint' (sub, A2.Constraint (oper, e1, e2)) = A2.Constraint(oper, substExp sub e1, substExp sub e2)
	  | substConstraint' (sub, A2.Assign (v, e)) = A2.Assign (substVar sub v, substExp sub e)

	fun subst' l (A2.Rule (thms1, thms2, constraints)) =
		A2.Rule(List.map (fn thm => List.foldl substThmTime thm l) thms1,
				List.map (fn thm => List.foldl substThm thm l) thms2,
				List.map (fn const => List.foldl substConstraint const l) constraints)


	fun modecheckRule' (A.Rule (thms1, thms2, constraints)) =
		let
			val (vars1, thms1', cons1) = modecheckThmTimes ignore thms1

			val (vars2, thms2', cons2) = modecheckThms isPure thms2

			val G = C.new ()

			val () = C.addAll G vars2

			val (constraints', varEqs) = simplifyConstraints G (cons2@constraints@cons1)

			val constraints'' = List.foldr List.@ [] (List.map (modecheckConstraint G) constraints')

(*
			val () = TextIO.print "\n\n"
			val () = C.print G
			val () = TextIO.print "\n\n"
			val () = List.app (TextIO.print o Print3.stringConstraint) constraints''
			val () = TextIO.print "\n\n"
*)
			val () = List.app (fn x => if C.lookup G x
									   then ()
									   else raise ModeError (Mark.error (#1 (List.hd thms1)) ("ill-moded variable '" ^ (#1 x) ^ "' in head"))) vars1
		in
			subst' varEqs (A2.Rule (thms1', thms2', constraints''))
		end


	val modecheckRule = Mark.map modecheckRule'
						   
	fun modecheck' (A.Program (decls, rules)) =
		A2.Program(decls, List.map modecheckRule rules)

	val modecheck = Mark.map modecheck'

  end
