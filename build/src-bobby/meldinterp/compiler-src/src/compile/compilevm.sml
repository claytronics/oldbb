signature COMPILEVM =
  sig
			datatype model = BB | PARALLEL

      val compile : model -> Ast4.program -> (VMst.program * string)
  end

structure CompileVM :> COMPILEVM =
  struct
    open Exceptions

		datatype model = BB | PARALLEL

    structure A = Ast4
    structure VM = VMst
	structure C = Cst
	structure T = Types

	val declsRef = (ref [] : (T.theorem * (T.agg * T.typ) list * T.persistent * int option) list ref)

	val externRef = (ref [] : ((string * int) * int) list ref)
	
	(* select architecture *)
	datatype arch = Arch64 | Arch32

   val arch = Arch32

	fun sizePointer Arch64 = 8
	  | sizePointer Arch32 = 4

	fun sizeof T.TypInt = 4
	  | sizeof T.TypFloat = 4
	  | sizeof (T.TypList _) = sizePointer arch
	  | sizeof (T.TypSet _) = sizePointer arch
	  | sizeof (T.TypUserDefined _) = sizePointer arch
	  | sizeof T.TypAddr = sizePointer arch
	  | sizeof (T.TypTuple tl) = (List.foldr op+ 0) (List.map sizeof tl)
	  | sizeof (T.TypType) = sizeof (T.TypInt)

	val sizeofArgs = (List.foldl op+ 0) o (List.map (sizeof o #2)) : (T.agg * T.typ) list -> int

	fun typId T.TypInt = 0
		| typId T.TypFloat = 1
		| typId T.TypAddr = 2
		| typId (T.TypTuple typ) = 2 (* not meaningful *)
		| typId (T.TypUserDefined str) = 2 (* idem *)
		| typId (T.TypList typ) = 3 + (typId typ)
		| typId (T.TypSet typ) = 6 + (typId typ)
		| typId (T.TypType) = 8

	fun valueSize (VM.REG _) = 0
		| valueSize VM.THE_TUPLE = 0
		| valueSize VM.HOST_ID = 0
		| valueSize (VM.CONST _) = sizeof T.TypInt
		| valueSize (VM.CONSTF _) = sizeof T.TypFloat
		| valueSize (VM.FIELD _) = 2
		| valueSize (VM.REVERSE _) = 2
		| valueSize (VM.TYPE _) = sizeof T.TypInt

	fun valuesSize ls = let
		val newLs = List.map valueSize ls
	in
		List.foldl (fn (n, acc) => 1 + n + acc) 0 newLs
	end

	fun matchListSize [] = 2
		| matchListSize matchlist = 
			let
				val lengthList = List.map (fn (fieldnum, value)
						=> 2 + (valueSize value)) matchlist
				in
					List.foldl (fn (n, acc) => n + acc) 0 lengthList
				end

	fun instructionSize (VM.IF (_, _)) = 3
		| instructionSize VM.ELSE = 1
		| instructionSize VM.ENDIF = 0
		| instructionSize (VM.ITER (_, matchlist, _)) = 4 + (matchListSize matchlist)
		| instructionSize VM.NEXT = 1
		| instructionSize (VM.SEND (_, _, value)) = 3 + (valueSize value)
		| instructionSize (VM.REMOVE _) = 1
		| instructionSize (VM.OP (_, val1, val2, _)) = 3 + (valueSize val1) + (valueSize val2)
 		| instructionSize (VM.MOVE (val1, val2)) = 2 + (valueSize val1) + (valueSize val2)
		| instructionSize (VM.ALLOC (_, value)) = 2 + (valueSize value)
		| instructionSize VM.RETURN = 1
		| instructionSize (VM.CALL (_, _, args)) = 2 + (valuesSize args)

	fun instructionSetSize ls =
		let
			val newLs = List.map instructionSize ls
		in
			List.foldl (fn (n, acc) => n + acc) 0 newLs
		end

	fun determineAgg agg typ n =
		(case agg of
		 "first" => VM.AGG_FIRST n
		 | _ =>
		 	(case typ of
			 	T.TypInt =>
				 	(case agg of
							"min" => VM.AGG_MIN n
						| "max" => VM.AGG_MAX n
						| "sum" => VM.AGG_SUM n
						| "set_union" => VM.AGG_SET_UNION_INT n
						| _ => raise (InternalError ("Unknown aggregate for int " ^ agg)))
			| T.TypFloat =>
				 	(case agg of
							"min" => VM.AGG_MIN_FLOAT n
						| "max" => VM.AGG_MAX_FLOAT n
						| "sum" => VM.AGG_SUM_FLOAT n
						| "set_union" => VM.AGG_SET_UNION_FLOAT n
						| _ => raise (InternalError ("Unknown aggregate for float " ^ agg)))
			| T.TypList typ =>
					(case agg of
					 		"sum" => 
								(case typ of
										T.TypInt => VM.AGG_SUM_LIST_INT n
									| T.TypFloat => VM.AGG_SUM_LIST_FLOAT n
									| _ => raise (InternalError ("Unsupported aggregate sum list type: " ^ (T.stringTyp typ))))
						| _ => raise (InternalError ("Unsupported aggregate type for lists: " ^ agg)))
		  | _ => raise (InternalError ("Aggregate type not supported: " ^ agg ^ (T.stringTyp typ)))))
		
	fun getAgg thm =
		(case List.filter (fn (thm', _, _, _) => T.thmeq thm thm') (!declsRef)
		  of [(T.Linear _, args, _, _)] => 
			 (case List.filter (fn (_, T.AggDefined _) => true | (_, T.AggNone) => false)
							   (ListPair.zip (List.tabulate(List.length args, fn x => x),
											  List.map #1 args))
				   of [] => VM.AGG_NONE 
					| _ => raise (InternalError "aggregates are not supported in linear facts"))
		   | [(_, args, _, _)] =>
			 (case List.filter (fn (_, T.AggDefined _) => true | (_, T.AggNone) => false)
							   (ListPair.zip (List.tabulate(List.length args, fn x => x),
											  List.map #1 args))
			   of [] => VM.AGG_NONE
				| [(n, T.AggDefined (agg, typ))] => determineAgg agg typ n
				| _ =>  raise (InternalError "multiple aggregates is unsupported"))
		   | [] => raise (InternalError ("unable to find aggregates for unknown theorem " ^ (T.thmName thm)))
		   | _ => raise InternalError ("unable to find aggregates for multiply defined theorem " ^ (T.thmName thm)))

	fun offset thm fieldnum =
		(case List.filter (fn (thm', _, _, _) => T.thmeq thm thm') (!declsRef)
		  of [(_, args, _, _)] => sizeofArgs (List.take (args, fieldnum))
		   | [] => raise (InternalError "unable to find field for unknown theorem")
		   | _ => raise InternalError ("unable to find field for multiply defined theorem " ^ (T.thmName thm)))

	fun size thm fieldnum = 
		(case List.filter (fn (thm', _, _, _) => T.thmeq thm thm') (!declsRef)
		  of [(_, args, _, _)] => (sizeof o #2) (List.nth(args, fieldnum))
		   | [] => raise (InternalError "unable to find field for unknown theorem")
		   | _ => raise InternalError ("unable to find field for multiply defined theorem " ^ (T.thmName thm)))

	fun field (thm, reg, fieldnum) = VMst.FIELD (reg, fieldnum)

	fun isOccurance thm (A.Thm (thm', _)) = T.thmeq thm thm'
	  | isOccurance thm (A.Forall (thm1, _, thm2, _)) = (T.thmeq thm thm1) orelse
														(T.thmeq thm thm2)
	fun uses (A.Rule (_, thms, _)) thm = List.exists (isOccurance thm) thms

	fun notCompilable (T.Regular name) = name = "colocated"
	| notCompilable _ = false

	fun processRule thm rule =
		let
			fun allocReg (vars, tuples, n) =
				((vars, tuples, n+1), n)
				
			fun lookup' ([], tuples, n) var = NONE
			  | lookup' ((var', val')::vars, tuples, n) var =
				if (String.compare (var, var') = EQUAL)
				then SOME val'
				else lookup' (vars, tuples, n) var

			fun lookup G var =
				(case lookup' G var of SOME val' => val'
									 | NONE => raise (InternalError ("failed to find variable '" ^ var ^ "' for lookup")))

			(* bindVars G vars - adds bindings for vars to G *)
			val bindVars =
				let
					fun bindVar ((A.Var (var, _), val'), (vars, tuples, n)) = 
						if (List.exists (fn (var', _) => String.compare(var, var') = EQUAL) vars)
						then (vars, tuples, n)
						else ((var,val')::vars, tuples, n)
				in
					foldl bindVar
				end

			fun bindThm (vars, tuples, n) (thm, reg) = (vars, (thm, reg)::tuples, n)

			fun lookupThm thm (vars, [], n) = raise InternalError ("failed to find thm")
			  | lookupThm thm (vars, (thm', reg)::l, n) =
				if thm = thm'
				then reg
				else lookupThm thm (vars, l, n)

			fun grabOccurance n =
				let
					val thms = case rule of A.Rule (_, thms, _) => thms

					fun add x (SOME (y, l)) = SOME (y, x::l)
					  | add _ NONE = NONE

					fun doSplit n [] = NONE
					  | doSplit n (x::l) =
						case (isOccurance thm x, n) of
							(true, 0) => SOME (x, l)
						  | (false, _) => add x (doSplit n l)
						  | (true, _) => add x (doSplit (n-1) l)
				in
					doSplit n thms
				end

			fun compileOp (T.TypInt, T.PLUS) = VM.INT_PLUS
			  | compileOp (T.TypInt, T.MINUS) = VM.INT_MINUS
			  | compileOp (T.TypInt, T.TIMES) = VM.INT_TIMES
			  | compileOp (T.TypInt, T.DIVIDE) = VM.INT_DIVIDE
			  | compileOp (T.TypInt, T.MOD) = VM.INT_MOD
           | compileOp (T.TypFloat, T.PLUS) = VM.FLOAT_PLUS
			  | compileOp (T.TypFloat, T.MINUS) = VM.FLOAT_MINUS
			  | compileOp (T.TypFloat, T.TIMES) = VM.FLOAT_TIMES
			  | compileOp (T.TypFloat, T.DIVIDE) = VM.FLOAT_DIVIDE
			  | compileOp (T.TypFloat, T.MOD) = VM.FLOAT_MOD
			  | compileOp (_, T.CONS) = VM.INT_PLUS (* TODO - this is BOGUS *)
			  | compileOp (_, T.APPEND) = VM.INT_PLUS (* TODO - this is BOGUS *)
			  | compileOp (_, T.EXP) = raise (InternalError "Exponents are not supported at this time")
			  | compileOp (_, T.DOTPROD) = raise (InternalError "Dot Product is not supported at this time")
			  | compileOp (_, _) = raise (InternalError "Pair type/operator not supported at this time")

			fun compileCmp (T.TypInt, T.EQ) = VM.INT_EQ
			  | compileCmp (T.TypInt, T.NEQ) = VM.INT_NEQ
			  | compileCmp (T.TypInt, T.LESS) = VM.INT_LESS
			  | compileCmp (T.TypInt, T.GREATER) = VM.INT_GREATER
			  | compileCmp (T.TypInt, T.LESSEQ) = VM.INT_LESSEQ
			  | compileCmp (T.TypInt, T.GREATEREQ) = VM.INT_GREATEREQ
			  | compileCmp (T.TypFloat, T.EQ) = VM.FLOAT_EQ
			  | compileCmp (T.TypFloat, T.NEQ) = VM.FLOAT_NEQ
			  | compileCmp (T.TypFloat, T.LESS) = VM.FLOAT_LESS
			  | compileCmp (T.TypFloat, T.GREATER) = VM.FLOAT_GREATER
			  | compileCmp (T.TypFloat, T.LESSEQ) = VM.FLOAT_LESSEQ
			  | compileCmp (T.TypFloat, T.GREATEREQ) = VM.FLOAT_GREATEREQ
			  | compileCmp (T.TypType, T.EQ) = VM.TYPE_EQ
			  | compileCmp (T.TypAddr, T.NEQ) = VM.ADDR_NEQ
			  | compileCmp (T.TypAddr, T.EQ) = VM.ADDR_EQ
			  | compileCmp (_, _) = raise (InternalError "Pair type / operator not supported at this time")

			fun compileExp G (A.Exp(A.ExpVar (A.Var (v, _)), _)) = (G, [], lookup G v)
			  | compileExp G (A.Exp(A.ExpInt n, _)) = (G, [], VM.CONST n)
			  | compileExp G (A.Exp(A.ExpFloat f, _)) = (G, [], VM.CONSTF f)
			  | compileExp G (A.Exp(A.ExpBinop (oper, exp1, exp2), typ)) = 
				let
					val (G', do1, v1) = compileExp G exp1
					val (G'', do2, v2) = compileExp G' exp2 (* TODO: LA DI DA - LIVENESS IS BROKEN HERE *)
      			val (G''', reg) = allocReg G''
				in
					(G''', do1@do2@[VM.OP (compileOp (typ, oper), v1, v2, reg)], VM.REG reg)
				end
			  | compileExp G (A.Exp(A.ExpNil, _)) = (G, [], VM.CONST 0) (* TODO - this is BOGUS *)
			  | compileExp G (A.Exp(A.ExpType t, _)) = (G, [], VM.TYPE t)
			  | compileExp G (A.Exp(A.ExpReverse e, _)) =
					(case e of
						A.Exp(A.ExpVar (A.Var (v, _)), _) =>
							let
								val val1 = lookup G v
							in
								case val1 of
									VM.FIELD (reg, index) => (G, [], VM.REVERSE (reg, index))
									| _ => raise (InternalError "expected field got something else -- please report")
							end
					 | _ => raise (InternalError "reverse expression only supports variables"))
			  | compileExp G (A.Exp(A.ExpDrop e, _)) = compileExp G e (* TODO - this is BOGUS *)
			  | compileExp G (A.Exp(A.ExpHostId, _)) = (G, [], VM.HOST_ID)
			  | compileExp G (A.Exp(A.ExpExtern (id, el), _)) =
				let
					val (dos, vs) = 
						foldr (fn (e, (dos, vs)) => 
								  let
									  val (G, do1, v) = compileExp G e
								  in
									  (do1@dos, v::vs)
								  end) ([], []) el

					val (G', reg) = allocReg G

					val idNum = case (List.filter (fn x => id = (#1 o #1) x) (!externRef)) of [(_, idNum)] => idNum
																							| _ => raise InternalError ("extern declared wrong number of times. Program is probably wrong, but error should have been caught earlier")
				in
					(G', dos@[VM.CALL(idNum, reg, vs)], VM.REG reg)
				end
			  | compileExp G e =
				let
					val _ = (TextIO.print o Print4.printExp) e
				in
					raise (InternalError "Unimplemented")
				end

			fun doConstraint (A.Constraint (oper, exp1, exp2), (G, doRest)) =
				let
					val (G', doE1, v1) = compileExp G exp1
					val (G'', doE2, v2) = compileExp G' exp2 (* TODO: LA DI DA - LIVENESS IS BROKEN HERE *)
					val (G''', reg) = allocReg G'' (* get a temporary register *)
               val typ = case exp1 of A.Exp(_, typ) => typ
				in 
					(G''', fn doSend => let
                            val ifBody = (doSend)
                            val opInstr = VM.OP (compileCmp (typ, oper), v1, v2, reg)
                            val ifDummy = [VM.IF (reg, 0)]
                            val ifSize = instructionSetSize (ifDummy@ifBody)
                           in
                              doRest (doE1@doE2@[opInstr, VM.IF (reg, ifSize)]@ifBody@[VM.ENDIF])
                           end)
				end
			  | doConstraint (A.Assign (v, e), (G, doRest)) =
				let
					val (G, code, value) = compileExp G e
					val (G, reg) = allocReg G
					val G = bindVars G [(v, VM.REG reg)]
				in
					(G, fn doSend => code@[VM.MOVE(value, VM.REG reg)]@(doRest doSend))
				end
				
			fun doConstraints G consts = foldl doConstraint (G, fn doSend => doSend)  consts

			fun processOccurances n =
				case grabOccurance n of
					NONE => []
				  | SOME (thm, thms) => 
					(let
						 fun doBinding G (A.Thm(thm, vars)) thms =
							 let
								 val (G, reg) = allocReg G

								 val G' =
									 bindVars
										 G
										 (ListPair.zip (vars, List.tabulate (List.length vars,
																		  fn id => field (thm, reg, id))))

								 val G'' = bindThm G' (A.Thm(thm, vars), reg)

								 val doBind = VM.MOVE(VM.THE_TUPLE, VM.REG reg)
							 in
								 doBind::(nextMatch G'' thms)
							 end
						   | doBinding _ (A.Forall _) _ = raise (InternalError "forall is currently unimplemented")
						 and nextMatch G [] =
							 let
								 val constrs = case rule of A.Rule (_, _, constrs) => constrs

								 val (G, doConsts) = (doConstraints G constrs)

								 val (G, reg) = allocReg G

								 val targets = case rule of A.Rule (x, _, _) => x

								 fun doTarget (A.Thm (thm, vars), target, time) = (* MPAR - TODO *)
									 let
										 val alloc =
											 VM.ALLOC(T.thmName thm, VM.REG reg)
										 val moves =
											 List.map 
												 (fn (A.Var (var, _), field) => VM.MOVE(lookup G var, field))
												 (ListPair.zip (vars, List.tabulate (List.length vars,
																				  fn id => field (thm, reg, id))))

										 val time' = case time of
														 T.TimeNow => VM.CONST 0
													   | T.TimeInMS n => VM.CONST n
										 val send =
											 case target of
												 A.LOCAL => [VM.SEND(reg, reg, time')]
											   | A.SEND (A.Var (v, _)) =>
												 (case lookup G v of
													  VM.REG reg' => [VM.SEND (reg, reg', time')]
													| value =>
													  let
														  val (_, reg') = allocReg G
													  in
														  [VM.MOVE(value, VM.REG reg'),
														   VM.SEND(reg, reg', time')]
													  end)

									 in
										 (alloc::moves@send)
									 end
								   | doTarget (A.Forall _, _, _) = raise (InternalError "forall cannot be used in the head of a rule")

								 fun doDelete (A.Thm (T.Routing _, _)) = NONE
								   | doDelete (A.Thm (T.Regular _, _)) = NONE
									| doDelete (A.Thm (T.Delta _, _)) = NONE
									| doDelete (A.Thm (T.Schedule _, _)) = NONE
									| doDelete (A.Thm (T.Delete _, _)) = NONE
									| doDelete (A.Thm (T.LinearRegular _, _)) = NONE
									| doDelete (A.Thm (T.Proved _, _)) = NONE
								   | doDelete (thm as A.Thm (T.Linear _, _)) = SOME (VM.REMOVE (lookupThm thm G))
								   | doDelete (A.Forall _) = NONE

								 val deletes = case List.mapPartial doDelete (thm::thms) of [] => []
																						  | l => l@[VM.RETURN]
							 in
								 doConsts (foldr List.@ deletes (List.map doTarget targets))
							 end
						   | nextMatch G ((theorem as A.Thm (thm, vars))::thms) =
							 let
								 val matches =
									 List.mapPartial
										 (fn (fieldnum, A.Var (var, _)) =>
											 case lookup' G var of SOME val' => SOME (fieldnum, val')
																 | NONE => NONE)
										 (ListPair.zip (List.tabulate (List.length vars, fn x => x), vars))
                        val internal = doBinding G theorem thms
                        val dummyIter = VM.ITER (T.thmName thm, matches, 0)
                        val sizeInternal = instructionSetSize ([dummyIter]@internal@[VM.NEXT])
							 in
								 [VM.ITER (T.thmName thm, matches, sizeInternal)]@internal@[VM.NEXT]
							 end
						   | nextMatch _ ((A.Forall _)::_) = raise (InternalError "forall is currently unimplemented")

						 fun checkFields (A.Thm (thm, vars)) work =
							 let
								 val vars' =
									 (ListPair.zip (vars, List.tabulate (List.length vars,
																	  fn id => field (thm, 0, id))))
									 
								 fun getConflicts [] = []
								   | getConflicts ((v, f)::l) = 
									 List.map 
										 (fn (v', f') => (f, f'))
										 (List.filter (fn (v', f') => v = v') l)
									 
								 fun testConflicts [] = work
								   | testConflicts ((f,f')::l) =
                    let
                      val ifBody = (testConflicts l)
                      val ifDummy = [VM.IF (1, 0)]
                      val ifSize = instructionSetSize (ifDummy@ifBody)
                    in
									    [VM.OP (VM.INT_EQ, f, f', 1), VM.IF (1, ifSize)]@ifBody@[VM.ENDIF]
                    end

								 val conflicts = getConflicts vars'
							 in
								 case conflicts of [] => work
												 | _ => (VM.MOVE(VM.THE_TUPLE, VM.REG 0))::(testConflicts conflicts)
							 end
						   | checkFields _ _ = raise (InternalError "UNIMPLEMENTED - forall");
					 in
						 checkFields thm (doBinding ([],[],0) thm thms)
					 end)@(processOccurances (n+1)) 
				
		in
			if notCompilable thm
			then []
			else processOccurances 0
		end

	fun createCallFunct model externs =
		let
			fun getCounts [] = ""
			  | getCounts [((_, n))] = (Int.toString n)
			  | getCounts (((_, n))::l) = (Int.toString n) ^ ", " ^ (getCounts l)

			fun getPointers [] = ""
			  | getPointers [((id, _))] = "(Register (*)())&" ^ id
			  | getPointers (((id, _))::l) = "(Register (*)())&" ^ id ^ ", " ^ (getPointers l)
		in
				(case model of BB => "#include \"extern_functions.bbh\"\n"
										 | _ =>  "#include \"extern_functions.h\"\n") ^
			("Register (*extern_functs[])() = {" ^ (getPointers externs) ^ "};\n\n") ^
			("int extern_functs_args[] = {" ^ (getCounts externs) ^ "};\n\n")
		end

   fun getArgs arglist = List.map (fn (_, typ) => typId typ) arglist
   
	fun getDeltas thm decls =
		case thm of
				T.Delta (_, _) => []
				| _ => List.mapPartial (fn (el, args, _, _) =>
						case el of
							T.Delta (deltaname, pos) => if deltaname = (T.thmName thm) then (SOME el) else NONE
						| _ => NONE) decls

   fun hasProved thm bodies =
      let
         fun hasProved rule name =
            case rule of
                 A.Thm (T.Proved nameProved, _) => name = nameProved
               | _ => false
         fun hasProvedName name =
            List.exists (fn rule => hasProved rule name) bodies
      in
         case thm of
            T.Regular name => hasProvedName name
          | T.Linear name => hasProvedName name
          | _ => false
       end

	fun stratifyDecls decls rules =
			let
					fun dependify depends [] = depends
						| dependify depends (rule::rules) =
							let
									val (headThms, bodyThms) =
											case rule of A.Rule(headThms, bodyThms, _)
																	 => (List.map #1 headThms, bodyThms)

									val dependencies = List.foldr (fn (A.Thm(thm, _), thms) => (T.thmName thm)::thms
																									| (A.Forall(thm, _, thm', _), thms) => (T.thmName thm)::(T.thmName thm')::thms) [] bodyThms

									val dependents = List.map (fn (A.Thm(thm, _)) => T.thmName thm) headThms

									val depends' = List.map
																		 (fn (ident, l) =>
																				 if (List.exists (fn dependent => String.compare(ident, dependent) = EQUAL) dependents)
																				 then (ident, l@dependencies)
																				 else (ident, l)) depends
							in 
									dependify depends' rules
							end

					fun orderize theList [] [] = theList
						| orderize theList ((curTag, _)::skippedDepends) [] = 
							(print ("Unable to stratify program; arbitrarily selecting " ^ (curTag) ^ "\n");
							 orderize (curTag::theList) [] skippedDepends)
						| orderize theList skippedDepends ((depend as (curTag, l))::depends) =
							let
									fun isSatisfied [] = true
										| isSatisfied (tag::l) = 
											(List.exists (fn tag' => String.compare(tag, tag') = EQUAL) theList)
											andalso
											(isSatisfied l)
							in
									if (isSatisfied l)
									then orderize (curTag::theList) [] (depends@skippedDepends)
									else orderize theList (depend::skippedDepends) depends
							end

					val dependencies =
							dependify (List.map (fn (thm, _, _) => (T.thmName thm, [])) decls) rules

					val order = List.rev (orderize [] [] dependencies)

			in
					List.tabulate (List.length decls, fn n => case (List.find (fn (thm, args, persists) => T.thmName thm = List.nth (order, n)) decls)
																												 of SOME (thm, args, persists) => (thm, args, persists, SOME n))
			end

    fun compile model (A.Program (decls, rules)) =
		let
			fun strip (T.Decl (thm, args, persistent)) = SOME (thm, args, persistent)
			  | strip (T.Extern _) = NONE
			  | strip (T.Type _) = raise (InternalError "external types are UNIMPLEMENTED")

			fun strip' (T.Extern (ident, t, ts)) =
				SOME (ident, List.length ts)
			  | strip' _ = NONE

			val decls' = List.mapPartial strip decls
			val externs' = List.mapPartial strip' decls

			val decls'' = stratifyDecls decls' rules

			val () = declsRef := decls''
			val () = externRef := (ListPair.zip
								   (externs',
									List.tabulate (List.length externs',
												   fn x => x)))
												   
			val bodies = List.concat (List.map (fn (A.Rule (_, body, _)) => body) rules)
		in
			(map
				 (fn (thm, args, persistent, stratOrder) =>
               ((thm, getAgg thm, getArgs args, getDeltas thm decls'', persistent, stratOrder, hasProved thm bodies),
                    foldl op@ [VM.RETURN] (map (processRule thm) rules)))
				 decls'',
			 (createCallFunct model externs'))
		end

  end
