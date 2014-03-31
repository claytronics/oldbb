structure BlinkyBlockModel :> MODEL =
  struct
    structure T = Types
	structure A = Ast

	exception ModelError of string
  
    fun applyModel' (A.Program (consts, decls, rules)) =
		let
			val nDecl = Mark.naked
                             (T.Decl(T.Routing "neighbor",
                                     [(T.AggNone, T.TypAddr),
                                      (T.AggNone, T.TypAddr),
                                      (T.AggNone, T.TypInt)],
                                      T.NotPersistent))

			val vDecl = Mark.mark' 
                            (T.Decl(T.Regular "vacant",
                                    [(T.AggNone, T.TypAddr),
                                     (T.AggNone, T.TypInt)],
                                     T.NotPersistent),
                             NONE)


			val cDecl = Mark.mark' 
                        (T.Decl(T.Regular "neighborCount",
                                [(T.AggNone, T.TypAddr),
                                 (T.AggNone, T.TypInt)],
                                 T.NotPersistent),
						 NONE)
						 
			val sDecl = Mark.naked
							(T.Decl(T.Regular "setColor",
                                    [(T.AggNone, T.TypAddr),
                                     (T.AggNone, T.TypInt),
                                     (T.AggNone, T.TypInt),
                                     (T.AggNone, T.TypInt),
                                     (T.AggNone, T.TypInt)],
                                     T.NotPersistent))
						
            val sDecl' = Mark.naked
                             (T.Decl(T.Regular "setColor2",
                                     [(T.AggNone, T.TypAddr),
                                      (T.AggNone, T.TypInt)],
                                      T.NotPersistent))

			val tDecl = Mark.naked
						(T.Decl(T.Linear "tap",
								[(T.AggNone, T.TypAddr)],
								T.NotPersistent))

			val newDecls = nDecl::vDecl::cDecl::sDecl::sDecl'::tDecl::decls

			fun initifyAxioms (decls, rules) =
				let
					val iDecl = Mark.naked
									(T.Decl(T.Linear "_init",
											[(T.AggNone, T.TypAddr)],
											T.NotPersistent))
								
					val iDecl' = Mark.naked
									 (T.Decl(T.Linear "__init",
											 [(T.AggNone, T.TypAddr),
											  (T.AggNone, T.TypInt)],
											  T.NotPersistent))
		
					fun notConstraint clause =
						case Mark.data clause
							 of A.Constraint _ => false
							  | A.ThmClause _ => true
							  | A.Forall _ => true

					fun isAxiom rule =
						case Mark.data rule
						 of A.Rule (_, clauses) =>
							not (List.exists notConstraint clauses)

					val (axioms, rules) = List.partition isAxiom rules

					fun addReq (clause, rule) =
						Mark.map (fn A.Rule(thms, clauses) => 
									 case thms of
										 [] => raise ModelError (Mark.error rule ("Rule contains no head theorems"))
									   | (thm, _)::_ => 
										 case Mark.data thm of 
										     A.Thm (_, x::_) => 
											 (case Mark.data x of
												  A.ExpVar _ =>  A.Rule(thms, (clause x)::clauses)
												| _ =>
											 let
												 val v = Mark.naked (A.ExpVar (A.Var "_initVar"))
												 val clause' = Mark.naked (A.Constraint (T.EQ, v, x))
															   
												 fun replace (A.Thm(thmName, []), t) = raise ModelError (Mark.error thm ("tuple contains no arguments"))
												   | replace (A.Thm(thmName, y::l), t) =
													 if A.expEq (x, y)
													 then (A.Thm(thmName, v::l), t) 
													 else raise ModelError (Mark.error thm ("Oops, axiom had results at multiple nodes"))

												 val thms' = List.map (Mark.mapFirst' replace) thms
											 in
												 A.Rule(thms', clause'::(clause v)::clauses)
											 end)
										   | _ => raise ModelError (Mark.error thm ("tuple contains no arguments"))) rule

					fun getInit n v =
						Mark.naked
							(A.ThmClause
								 (Mark.naked 
									  (A.Thm(T.Linear "__init",
											 [v,
											  Mark.naked (A.ExpConst (T.ConstInt (Int32.fromInt n)))]
							))))
						
					val axioms' = List.map addReq (ListPair.zip (List.tabulate(List.length axioms, getInit), axioms))

					val doInits =
						let
							val var = Mark.naked (A.ExpVar (A.Var "X"))
							fun doOut n =
								(Mark.naked 
									 (A.Thm 
										  (T.Linear "__init",
										   [var, (Mark.naked o A.ExpConst o T.ConstInt o Int32.fromInt) n])),
								 T.TimeNow)
						in
							Mark.naked
								(A.Rule
									 (List.tabulate (List.length axioms', doOut),
									  [Mark.naked
										   (A.ThmClause 
												(Mark.naked
													 (A.Thm (T.Linear "_init", [var])
								)))]))
						end
				in
					if (List.length axioms' > 0)
					then (consts, iDecl::iDecl'::decls, doInits::axioms'@rules)
					else (consts, decls, rules)
				end

		in
			A.Program (initifyAxioms (newDecls, rules))
		end
		
	val applyModel = applyModel'
			
  end