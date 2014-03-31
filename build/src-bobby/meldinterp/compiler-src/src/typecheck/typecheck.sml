signature TYPECHECK =
  sig
      val typecheck : Ast.program -> Ast2.program

	  exception TypeError of string
  end

structure TypeCheck :> TYPECHECK =
  struct
	structure A = Ast
	structure A2 = Ast2
	structure T = Types
	structure P = Print

	open Exceptions

	exception TypeError of string

	val constants = ref [] : (string * T.const) list ref

	fun error d e = raise TypeError (Mark.error d e)

	fun wrongTypeError d t t' =
		error d ("expected type " ^ (T.stringTyp t) ^ ", but found type " ^ (T.stringTyp t'))
	fun wrongTypeError' d t =
		error d ("expected type " ^ (T.stringTyp t) ^ ", but found other type")
	fun untypeableError e =
		error e ("unable to determine type of " ^ (P.stringExp e))
	fun invalidTypeError d s =
		error d ("invalid type " ^ s)
	fun undefinedFunctionError d s =
		error d ("undefined function " ^ s)

	fun confirmType d t t' e =
		if T.typSub (t', t)
		then e
		else wrongTypeError d t t'


	structure C =
	struct
 	  fun new () = ref []

	  fun lookupVar' _ A.VarAny = NONE
		| lookupVar' [] _ = NONE
		| lookupVar' ((s',t')::l) (v as (A.Var s)) =
		  if String.compare (s, s') = EQUAL
		  then SOME t'
		  else lookupVar' l v

	  fun lookupVar G v = lookupVar' (!G) v
	  
	  fun extend G (A.Var s, t) = G := (s,t)::(!G)
		| extend _ (A.VarAny, _) = ()

	  fun copy G = ref (!G)
	  fun replace G Old = (G := !Old)
	end

	fun typecheckType _ _ T.TypInt = ()
	  | typecheckType _ _ T.TypFloat = ()
	  | typecheckType _ _ T.TypAddr = ()
	  | typecheckType _ _ T.TypType = ()
	  | typecheckType d decls (T.TypList t) =
		typecheckType d decls t
	  | typecheckType d decls (T.TypSet t) =
		typecheckType d decls t
	  | typecheckType d decls (T.TypTuple tl) =
		List.app (typecheckType d decls) tl
	  | typecheckType d decls (T.TypUserDefined s) = 
		if (T.lookupType decls s)
		then ()
		else invalidTypeError d s

	fun typecheckDecl decls decl =
		let
			fun typecheckDecl' (T.Type _) = ()
			  | typecheckDecl' (T.Decl (_, at, _)) =
				List.app ((typecheckType decl decls) o #2) at
			  | typecheckDecl' (T.Extern (_, t, tl)) =
				List.app (typecheckType decl decls) (t::tl)
		in
			(typecheckDecl' o Mark.data) decl
		end


	fun typecheckConst (T.ConstInt _) = T.TypInt
	  | typecheckConst (T.ConstFloat _) = T.TypFloat
	  | typecheckConst (T.ConstAddr _) = T.TypAddr

	fun convertVar (A.VarAny) t = A2.ExpVar (A2.VarAny t)
	  | convertVar (A.Var s) t = 
			(case (List.find (fn (s', _) => s = s') (!constants))
					 of SOME (_, c) => A2.ExpConst c
						| NONE => A2.ExpVar (A2.Var (s, t)))

	fun typecheckExp decls G exp =
		let
			fun typecheckExp' (A.ExpVar v) = 
				let
					val t =	case (C.lookupVar G v) of
								SOME t => t
							  | NONE => untypeableError exp (A.ExpVar v)
				in
					(convertVar v t, t)
				end
			  | typecheckExp' A.ExpNil = untypeableError exp A.ExpNil
			  | typecheckExp' (A.ExpType t) = (A2.ExpType t, T.TypType)
			  | typecheckExp' (A.ExpConst c) = (A2.ExpConst c, typecheckConst c)
			  | typecheckExp' (A.ExpBinop (b, e1, e2)) =
				let
					fun docheck ((t,t1,t2)::tl) = 
						((A2.ExpBinop(b,
									  typeconfirmExp decls G (e1,t1),
									  typeconfirmExp decls G (e2,t2)),
						  t) handle e => (case tl of [] => raise e
												   | _ => docheck tl))
					  | docheck [] = raise InternalError "Operation has no supported types? - confused"
						
				in
					docheck (T.binopTyp b)
				end
			  | typecheckExp' (A.ExpExtern (s, el)) =
				(case T.lookupExtern decls s of
					 SOME (t,tl) =>
					 (A2.ExpExtern(s, List.map (typeconfirmExp decls G) (ListPair.zip (el,tl))),
					  t)
				   | NONE => undefinedFunctionError exp s)
			  | typecheckExp' (A.ExpTuple el) = 
				let
					val el' = List.map (typecheckExp decls G) el
					val tl = List.map #2 el'
				in
					(A2.ExpTuple el', T.TypTuple tl)
				end
		in
			Mark.mapFirst typecheckExp' exp
		end
	and typeconfirmExp decls G et =
		let
			fun typeconfirmExp' (A.ExpVar v, t) = 
				let
					val t =	case (C.lookupVar G v) of
								NONE => (C.extend G (v, t); t)
							  | SOME t' => confirmType (#1 et) t t' t
					val ret = (convertVar v t, t)
				in
					ret
				end
			  | typeconfirmExp' (A.ExpNil, t as (T.TypList _)) = (A2.ExpNil, t)
			  | typeconfirmExp' (A.ExpType c, a) = (A2.ExpType c, a)
			  | typeconfirmExp' (A.ExpNil, t) = raise InternalError "There might be a typing error, but I'm not sure...."
			  | typeconfirmExp' (A.ExpConst c, t) = 
				let
					val t' = typecheckConst c
				in
					confirmType (#1 et) t t' (A2.ExpConst c, t)
				end
			  | typeconfirmExp' (A.ExpBinop (b, e1, e2), t) = 
				let
				   val copy = C.copy G
				   
					fun docheck ((t',t1,t2)::tl) =
						((confirmType (#1 et) t' t
									  (A2.ExpBinop(b,
												   typeconfirmExp decls G (e1,t1),
												   typeconfirmExp decls G (e2,t2)),
									   t)) handle e => (case tl of [] => raise e
																 | _ => (C.replace G copy ; docheck tl)))
					  | docheck [] = raise InternalError "Operation has no supported types? - confused"
				in
					docheck (T.binopTyp b)
				end
			  | typeconfirmExp' (A.ExpExtern (s, el), t) =
				(case T.lookupExtern decls s of
					 SOME (t',tl) =>
					 confirmType (#1 et) t t'
								 (A2.ExpExtern(s, List.map (typeconfirmExp decls G) (ListPair.zip (el,tl))),
								  t)
				   | NONE => undefinedFunctionError (#1 et) s)
			  | typeconfirmExp' (A.ExpTuple el, t as (T.TypTuple tl)) =
				(A2.ExpTuple(List.map (typeconfirmExp decls G) (ListPair.zip (el,tl))), t)
			  | typeconfirmExp' (A.ExpTuple el, t) = raise InternalError "There might be a typing error, but I'm not sure...."
		in
			Mark.mapFirst' typeconfirmExp' et
		end

  fun convertAggregateType (agg, typ) =
    case agg of
      T.AggNone => (agg, typ)
     | T.AggDefined (aggtyp, typ) =>
         case aggtyp of
              "set_union" => (T.AggNone, (T.TypSet typ))
             | _ => (T.AggNone, typ)

	fun typecheckThm decls G isClause thm =
		let
			fun typecheckThm'
				(A.Thm (theorem, args)) =
				let
					val (theorem', typs) =
					   case theorem of
					      (* proved theorems must be handled with care *)
					      T.Proved name => (case T.lookupThm' decls "proved"
					                        of NONE => raise InternalError "No declaration found for proved: is your model missing the proved declaration?"
					                         | SOME (_, args) => (theorem, #2(ListPair.unzip args)))
					      | _ =>
								(case T.lookupThm' decls (T.thmName theorem)
								  of NONE => raise TypeError (Mark.error thm ("No declaration found for " ^ (T.thmName theorem)))
									| SOME (theorem', l) => let
										val argList = if isClause then (map convertAggregateType l) else l
										in
											(theorem', #2(ListPair.unzip argList))
										end)

					val () = case (theorem, theorem') of
								 (T.Linear _, T.Linear _) => ()
								 | (T.Linear _, T.Delta _) => ()
								 | (T.Delta _, T.Linear _) => ()
							   | (T.Linear _, _) => raise TypeError (Mark.error thm ("linear use of regular fact " ^ (T.thmName theorem)))
							   | (_, T.Linear _) => raise TypeError (Mark.error thm ("regular use of linear fact " ^ (T.thmName theorem)))
							   | _ => ()

					val args' = 
						if List.length args = List.length typs
						then List.map (typeconfirmExp decls G) (ListPair.zip (args, typs))
						else raise TypeError (Mark.error thm ("expected tuple to have " ^
															  (Int.toString (List.length typs)) ^
															  " fields, but found " ^
															  (Int.toString (List.length args)) ^
															  " fields"))
				in
					(A2.Thm (theorem', args'))
				end
		in
			Mark.map typecheckThm' thm
		end

	fun typecheckThmTime decls G (thm, time) = (typecheckThm decls G false thm, time)
	fun typecheckThmTimes decls G = List.map (typecheckThmTime decls G)

	fun typecheckThms decls G = List.map (typecheckThm decls G false)

	fun typecheckConstraint decls G con =
		let
			fun typecheckConstraint' (comp, e1, e2) =
				if T.isEqTest comp
				then
					let
						val (e, t) = typecheckExp decls G e1
					in
						A2.Constraint(comp, (e,t), typeconfirmExp decls G (e2,t))
					end handle e => 
							   let
								   val (e', t') = typecheckExp decls G e2
							   in
								   A2.Constraint(comp, typeconfirmExp decls G (e1,t'), (e',t'))
							   end
				else
					let
						fun docheck ((t1,t2)::tl) = 
							((A2.Constraint(comp,
											typeconfirmExp decls G (e1,t1),
											typeconfirmExp decls G (e2,t2)))
							 handle e => (case tl of [] => raise e
												   | _ => docheck tl))
						  | docheck [] = raise InternalError "Operation has no supported types? - confused"
					in
						docheck (T.compareTyp comp)
					end
		in
			typecheckConstraint' con
		end
			
	datatype result = ResultThm of A2.thm
					| ResultConstraint of A2.constraint

	fun typecheckClause decls G clause =
		let
			fun typecheckClause' (A.ThmClause thm) =
				ResultThm (typecheckThm decls G true thm)
			  | typecheckClause' (A.Constraint con) = 
				ResultConstraint (Mark.mark' ((typecheckConstraint decls G con), Mark.ext clause))
			  | typecheckClause'
					(A.Forall _) = raise InternalError "forall is not presently supported"
		in
			(typecheckClause' o Mark.data) clause
		end

	fun typecheckRule decls rule =
		let
			fun typecheckRule'
				(A.Rule (thms, cl)) =
				let
					val G = C.new ()
					val thms' = typecheckThmTimes decls G thms
							   
					val (cl', tl') =
						foldl
						(fn (ResultThm t, (cl, tl)) => (cl, t::tl)
						  | (ResultConstraint c, (cl, tl)) => (c::cl, tl))
						([],[])
						(List.map (typecheckClause decls G)
								  (ListUtil.sort ((fn (A.ThmClause _, A.ThmClause _) => EQUAL
													| (A.ThmClause _, _) => LESS
													| (_, A.ThmClause _) => GREATER
													| (A.Forall _, A.Constraint _) => LESS
													| (A.Constraint _, A.Forall _) => GREATER
													| _ => EQUAL)
												  o (fn (a,b) => (Mark.data a, Mark.data b)))
												 cl))
				in
					A2.Rule (thms', tl', cl')
				end
		in
			Mark.map typecheckRule' rule
		end

	fun getNewDecls clause decls =
		case clause of
			A.ThmClause thm' =>
				let
					val A.Thm (theorem, args) = Mark.data thm'
				in
					(case theorem of
						  T.Delta (name, _) =>
								(case (T.lookupThm' decls (T.thmName theorem)) of
									  SOME _ => []
									| NONE =>
										let
											val result = T.lookupThm' decls name
										in
											case result of
												SOME (baseTheorem, baseArgTypes) =>
														[(T.Decl (theorem, baseArgTypes, T.NotPersistent))]
											| _ => []
										end)
						| _ => [])
				end
			| _ => []

	fun getRuleBody (A.Rule (_, bodylist)) = List.map Mark.data bodylist

	fun getRuleHead (A.Rule (head, _)) = head
	
	fun addScheduleDecl rules decls =
		let
			val headList = List.concat (List.map getRuleHead (List.map Mark.data rules))
			fun isScheduleRule (A.Thm (thm, _)) =
				case thm of
					  T.Schedule _ => true
					| _ => false
			val found = List.exists (fn (thm, _) => isScheduleRule (Mark.data thm)) headList
		in
			if found
			then decls@[T.Decl (T.Schedule "schedule", [(T.AggNone, T.TypAddr), (T.AggNone, T.TypType),
				         (T.AggNone, T.TypInt)], T.NotPersistent)]
			else decls
		end
	
	fun addDeleteDecl rules decls =
	   let
	      val headList = List.concat (List.map getRuleHead (List.map Mark.data rules))
	      fun isDeleteRule (A.Thm (thm, _)) =
	         case thm of
	              T.Delete _ => true
	            | _ => false
	      val found = List.exists (fn (thm, _) => isDeleteRule (Mark.data thm)) headList
	   in
	      if found
	      then decls@[T.Decl (T.Delete "delete", [(T.AggNone, T.TypAddr), (T.AggNone, T.TypType)], T.NotPersistent)]
	      else decls
      end

	val typecheck =
		let
			fun typecheck' (A.Program (consts, decls, rules)) =
				let
					val _ = constants := consts
					val decls' = List.map Mark.data decls
					val ruleList = List.concat (List.map getRuleBody (List.map Mark.data rules))
					val decls'' = foldl (fn (rule, newDecls) => (getNewDecls rule newDecls)@newDecls) decls' ruleList
					val decls'' = addScheduleDecl rules decls''
					val decls'' = addDeleteDecl rules decls''
					val () = List.app (typecheckDecl decls'') decls
				in
					A2.Program(decls'',
							   List.map (typecheckRule decls'') rules)
				end
		in
			Mark.naked o typecheck'
		end
  end
