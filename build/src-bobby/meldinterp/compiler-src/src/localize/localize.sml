signature LOCALIZE =
  sig

    exception LocalizationError of string

    val localize : Ast3.program -> bool -> Ast4.program

  end

structure Localize :> LOCALIZE =
  struct

  open Exceptions
  exception LocalizationError of string

  structure A = Ast3
  structure A2 = Ast4
  structure T = Types

  datatype tree =
	   Node of A.var * (A2.thm * A2.target * T.time) list * A.thm list * A.constraint list * (A.thm * tree) list

  datatype fullTree =
	   RNode of A.var * (A2.thm * A2.target * T.time) list * A.thm list * A.constraint list * (A.thm * fullTree) list
	 | LNode of A.var * (A2.thm * A2.target * T.time) list * A.thm list * A.constraint list * (A.thm * fullTree) list

  fun localizeVar (A.Var (name, typ)) = A2.Var (name, typ)
  val localizeVars = List.map localizeVar

  fun localizeThm t = case Mark.data t of A.Thm (thm, vars) => A2.Thm (thm, localizeVars vars)
  val localizeThms = List.map localizeThm

  fun localizeThmTime (thm, time : T.time) = (localizeThm thm, time)
  val localizeThmTimes = List.map localizeThmTime

  fun buildRoutingTree target (headThms : (A.thm * T.time) list) (bodyThms : A.thm list) =
      let
	  fun cantReach blah x =  raise LocalizationError 
					    (Mark.error blah ("No known route from " ^
							      (case localizeVar x of A2.Var (s, _) => s) ^
							      " to " ^
							      (case localizeVar target of A2.Var (s, _) => s)))

	  fun groupThms get t thms =
	      let
		  val (targetThms, otherThms) = List.partition (fn a => case Mark.data (get a) of A.Thm (_, x::_) => x = t
												| A.Thm (_, []) => raise InternalError "found theorem with no fields. Unsupported") thms
	      in
		  case otherThms of [] => [(t, targetThms)]
				  | (x::_) => case Mark.data (get x) of A.Thm (_, x::_) => (t, targetThms)::(groupThms get x otherThms)
								      | A.Thm (_, []) => raise InternalError "found theorem with no fields. Unsupported"
	      end


	  val isRouting = (fn a => case Mark.data a of A.Thm (T.Routing _, _) => true | _ => false)
	  val (routeThms, otherThms) = List.partition isRouting bodyThms
				       
	  val sortedThms = groupThms (fn x => x) target otherThms
	  val sortedHeadThms = groupThms #1 target headThms

	  fun annotateWithEnds [] = []
	    | annotateWithEnds (route::routes) = 
	      case Mark.data route of A.Thm(_, x::y::_) => (x,y,route)::(annotateWithEnds routes)
				    | A.Thm(_, _) => raise InternalError "found routing theorem with fewer than 2 fields. Where do I route to?"

	  fun buildTree root routes =
	      let
		  fun belongs (x,y, route) = x = root orelse y = root
							     
		  val (connected, unconnected)
		    = List.partition (fn x => belongs x) routes

		  fun buildSubtree ((x,y,route), (subtrees, routes')) =
		      let
			  val (subtree, routes'') = 
			      buildTree (if x = root then y else x) routes'
		      in
			  ((route, subtree)::subtrees, routes'')
		      end

		  val (subtrees, unreachables) = foldr buildSubtree ([], unconnected) connected
	      in
		  (Node(root, [], [], [], subtrees), unreachables)
	      end

	  val (tree, unreachables) = buildTree target (annotateWithEnds routeThms)

	  val () = case unreachables of
		       [] => ()
		     | ((x, y, route)::_) => cantReach route x

	  datatype thmKind = HEAD of (A.thm * T.time) list | BODY of A.thm list

	  fun impartThms tree [] = tree
	    | impartThms tree ((a,thms')::moreThms) =
	      let
		  fun impartThms' ks kf (Node (b, headThms, bodyThms, cons, subtrees)) = 
		      if (a = b)
		      then 
			  let
			      val (headThms', bodyThms') =
				  case thms' of
				      BODY thms' => (headThms, thms'@bodyThms)
				    | HEAD thms' => ((List.map (fn x => (#1 x, A2.LOCAL, #2 x))
							       (localizeThmTimes thms'))@headThms,
						     bodyThms)
			  in
			      ks (Node (b, headThms', bodyThms', cons, subtrees))
			  end
		      else
			  impartThmsTrees'
			      (fn subtrees' => ks (Node (b, headThms, bodyThms, cons, subtrees')))
			      kf
			      subtrees
		  and impartThmsTrees' ks kf [] = kf ()
		    | impartThmsTrees' ks kf ((thm,tree)::trees) = 
		      impartThms'
			  (fn tree' => ks ((thm,tree')::trees))
			  (fn () => impartThmsTrees'
					(fn trees' => ks ((thm,tree)::trees'))
					kf
					trees)
			  tree

		  fun success tree = tree
		  fun failure () = cantReach (case thms' of
						  HEAD ((thm, _)::_) => thm
						| BODY (thm::_) => thm
						| _ => raise InternalError "oops, this condition appears to be impossible") a
	      in
		  impartThms (impartThms' success failure tree) moreThms
	      end

      in
	  impartThms tree ((List.map (fn (a, l) => (a, BODY l)) sortedThms)@
			   (List.map (fn (a, l) => (a, HEAD l)) sortedHeadThms))
      end

  fun localizeExp' (A.ExpVar v) = A2.ExpVar (localizeVar v)
    | localizeExp' A.ExpNil = A2.ExpNil
    | localizeExp' (A.ExpType c) = A2.ExpType c
    | localizeExp' (A.ExpConst (T.ConstInt n)) = A2.ExpInt n
    | localizeExp' (A.ExpConst (T.ConstAddr n)) = A2.ExpInt n (* TODO: this works for now, but isn't really right.. *)
    | localizeExp' (A.ExpConst (T.ConstFloat f)) = A2.ExpFloat f 
    | localizeExp' (A.ExpBinop (oper, e1, e2)) = A2.ExpBinop (oper, localizeExp e1, localizeExp e2)
    | localizeExp' (A.ExpExtern (name, el)) = A2.ExpExtern (name, List.map localizeExp el)
    | localizeExp' (A.ExpTuple el) = raise (InternalError "tuple unimplemented")
    | localizeExp' (A.ExpField (e, n)) = raise (InternalError "field unimplemented")
  and localizeExp (e, t) = A2.Exp((localizeExp' o Mark.data) e, t)

  fun localizeConstraint' (A.Constraint (cmp, e1, e2)) = (A2.Constraint (cmp, localizeExp e1, localizeExp e2))
    | localizeConstraint' (A.Assign (v, e)) = (A2.Assign (localizeVar v, localizeExp e))
    | localizeConstraint' (A.Forall _) = raise (InternalError "forall Unimplemented")

  val localizeConstraint = localizeConstraint' o Mark.data

  val localizeConstraints = List.map localizeConstraint


				     (* TODO: push the constraints up the tree to 'good' spots *)
  fun pushConstraint con (Node (a, headThms, thms, cons, trees)) = Node(a, headThms, thms, con::cons, trees)

  fun pushConstraints ([], tree) = tree
    | pushConstraints (con::cons, tree) = pushConstraints (cons, (pushConstraint con tree))

							  (* removes assignments of unused variables *)
  fun polishConstraints (cons, tree) = 
      let
	  fun Euses' v (A.ExpVar (A.Var (v', _))) = v = v'
	    | Euses' _ (A.ExpConst _) = false
	    | Euses' _ (A.ExpType _) = false
	    | Euses' v (A.ExpBinop (_, e1, e2)) = (Euses v e1) orelse (Euses v e2)
	    | Euses' v (A.ExpExtern (_, el)) = List.exists (Euses v) el
	    | Euses' v (A.ExpTuple el) = List.exists (Euses v) el
	    | Euses' v (A.ExpField (e, _)) = Euses v e
	  and Euses v (e, _) = Euses' v (Mark.data e) 

	  fun Cuses' _ (A.Constraint _) = false
	    | Cuses' _ (A.Forall _) = false
	    | Cuses' v (A.Assign (_, e)) = Euses v e
	  and Cuses v c = Cuses' v (Mark.data c)

	  fun Tuses v (Node (a, headThms, thms, cons, trees)) =
	      (List.exists (fn (A2.Thm (_, vs)) => (List.exists (fn A2.Var (v',_) => v = v') vs)) (List.map #1 headThms)) orelse 
	      (List.exists (Tuses v) (List.map #2 trees)) orelse
	      (List.exists (Cuses v) cons)

	  fun used' (A.Constraint _) = true
	    | used' (A.Forall _) = true
	    | used' (A.Assign (A.Var (v, _), _)) =
	      (Tuses v tree) orelse (List.exists (Cuses v) cons)
	  and used c = used' (Mark.data c)

	  val cons' = List.filter used cons
      in
	  if (List.length cons = List.length cons')
	  then (cons, tree)
	  else polishConstraints (cons', tree)
      end

  val invertRouteName = (fn s => "____" ^ s) o implode o rev o explode

  val newVar = let val n = ref 0 in fn t => (n := (!n) + 1; A2.Var ("__route" ^ (Int.toString (!n)), t)) end

  fun getRoute src (A2.Thm (T.Routing name, (x::y::vars))) =
      let
	  val rt = newVar (T.TypList T.TypAddr)
      in
	  if (src = x)
	  then (A2.Thm (T.Routing name, (x::y::vars@[rt])), rt)
	  else if (src = y)
	  then (A2.Thm (T.Routing (invertRouteName name), (x::y::vars@[rt])), rt)
	  else raise (InternalError ("Confused - trying to use incorrect route??"))
      end
    | getRoute _ (A2.Thm (T.Routing _, _)) = raise InternalError "found routing theorem with fewer than 2 fields. Where do I route? - confused"
    | getRoute _ (A2.Thm (T.Linear _, _)) = raise InternalError "found linear tuple, but expected routing tuple - confused"
    | getRoute _ (A2.Thm (T.Regular _, _)) = raise InternalError "found regular tuple, but expected routing tuple - confused"
    | getRoute _ (A2.Thm (T.Delta _, _)) = raise InternalError "found delta tuple, but expected routing tuple - confused"
    | getRoute _ (A2.Thm (T.Schedule _, _)) = raise InternalError "found schedule tuple, but expected routing tuple - confused"
    | getRoute _ (A2.Thm (T.Delete _, _)) = raise InternalError "found delete tuple, but expected tuple - confused"
    | getRoute _ (A2.Thm (T.LinearRegular _, _)) = raise InternalError "linear-regular tuples not expected here"
    | getRoute _ (A2.Thm (T.Proved _, _)) = raise InternalError "proved tuples not expected here"
    | getRoute _ (A2.Forall _) = raise InternalError "found forall, but expected routing tuple - confused"

  val newThmName = let val nameCount = ref 0
		   in
		    fn () => (nameCount := (!nameCount)+1; "__fact" ^ (Int.toString (!nameCount)))
		   end

  fun quash (Node (a, headThms, thms, cons, subtrees)) =
      let
	  fun grabVarsThms [] = []
	    | grabVarsThms ((A2.Thm (_, vars))::thms) = vars@(grabVarsThms thms)
	    | grabVarsThms ((A2.Forall _)::_) = raise InternalError "forall is not currently supported"

	  fun grabVarsThmsOld' [] = []
	    | grabVarsThmsOld' ((A.Thm (_, vars))::thms) = vars@(grabVarsThmsOld' thms)
	  and grabVarsThmsOld thms = grabVarsThmsOld' (List.map Mark.data thms)

	  fun grabVarsExp' (A2.ExpVar v) = [v]
	    | grabVarsExp' A2.ExpNil = []
	    | grabVarsExp' (A2.ExpType _) = []
	    | grabVarsExp' (A2.ExpInt _) = []
	    | grabVarsExp' (A2.ExpFloat _) = []
	    | grabVarsExp' (A2.ExpBinop (_, e1, e2)) = (grabVarsExp e1)@(grabVarsExp e2)
	    | grabVarsExp' (A2.ExpExtern (_, el)) = List.foldl List.@ [] (List.map grabVarsExp el)
	    | grabVarsExp' (A2.ExpField (e, _)) = grabVarsExp e
	    | grabVarsExp' (A2.ExpPoint (e1, e2, e3)) = (grabVarsExp e1)@(grabVarsExp e2)@(grabVarsExp e3)
	    | grabVarsExp' (A2.ExpReverse e) = grabVarsExp e
	    | grabVarsExp' (A2.ExpDrop e) = grabVarsExp e
	    | grabVarsExp' A2.ExpHostId = []
	  and grabVarsExp (A2.Exp (e, _)) = grabVarsExp' e

	  fun grabVarsCons [] = []
	    | grabVarsCons ((A2.Assign (v, e))::cons) = v::(grabVarsExp e)@(grabVarsCons cons)
	    | grabVarsCons ((A2.Constraint (_, e1, e2))::cons) = (grabVarsExp e1)@(grabVarsExp e2)@(grabVarsCons cons)

	  fun grabVarsConsAssign [] = []
	    | grabVarsConsAssign ((A2.Assign (v, _))::cons) = v::(grabVarsCons cons)
	    | grabVarsConsAssign ((A2.Constraint _)::cons) = grabVarsCons cons

	  fun grabVarsExpOld' (A.ExpVar v) = [v]
	    | grabVarsExpOld' (A.ExpNil) = []
	    | grabVarsExpOld' (A.ExpConst _) = []
	    | grabVarsExpOld' (A.ExpType _) = []
	    | grabVarsExpOld' (A.ExpBinop (_, e1, e2)) = (grabVarsExpOld e1)@(grabVarsExpOld e2)
	    | grabVarsExpOld' (A.ExpExtern (_, el)) = List.foldl List.@ [] (List.map grabVarsExpOld el)
	    | grabVarsExpOld' (A.ExpTuple el) = List.foldl List.@ [] (List.map grabVarsExpOld el)
	    | grabVarsExpOld' (A.ExpField (e, _)) = grabVarsExpOld e
	  and grabVarsExpOld (e, _) = (grabVarsExpOld' o Mark.data) e

	  fun grabVarsConsOld' [] = []
	    | grabVarsConsOld' ((A.Assign (v, e))::cons) = v::(grabVarsExpOld e)@(grabVarsConsOld' cons)
	    | grabVarsConsOld' ((A.Constraint (_, e1, e2))::cons) = (grabVarsExpOld e1)@(grabVarsExpOld e2)@(grabVarsConsOld' cons)
	    | grabVarsConsOld' ((A.Forall _)::_) = raise InternalError "forall is not currently supported"
	  and grabVarsConsOld cons = grabVarsConsOld' (List.map Mark.data cons)

	  fun grabVarsSubtrees subtrees =
	      let
		  fun grabVarsSubtree' (thm, Node (a, headThms, thms, cons, subtrees)) =
		      let
			  val (neededVars, usedVars) = List.foldl (fn ((l1, l2), (r1, r2)) => (l1@r1, l2@r2))
								  ([], [])
								  (List.map grabVarsSubtree' subtrees)
		      in
			  (neededVars@(List.map (fn A2.Var (v, _) => v) (grabVarsThms (List.map #1 headThms))),
			   usedVars@(List.map (fn A.Var (v, _) => v) ((grabVarsThmsOld (thm::thms))@(grabVarsConsOld cons))))
		      end
		      
		  fun process [] = []
		    | process [_] = []
		    | process (vs::l) = (List.filter (fn v => List.exists
								(fn vs' => List.exists (fn v' => v = v') vs')
								l)
						   vs)@
				      (process l)
					
		  val (neededVars, usedVars) = ListPair.unzip (List.map grabVarsSubtree' subtrees)
	      in
		  List.foldl List.@ (process usedVars) neededVars
	      end

	  fun elimDups [] = []
	    | elimDups (x::l) = x::(elimDups (List.filter (fn y => not (x = y)) l))

	  fun routify rt (thm, A2.LOCAL, time) = ((thm, A2.SEND rt, time), NONE)
	    | routify rt (thm, A2.SEND rt', time) =
	      let
		  val t = T.TypList T.TypAddr
		  val newRt = newVar t
	      in
		  ((thm, A2.SEND newRt, time),
		   SOME(
		   A2.Assign
		       (newRt,
			A2.Exp (A2.ExpBinop(T.APPEND,
					    A2.Exp (A2.ExpVar rt, t),
					    A2.Exp (A2.ExpVar rt', t)),
				t))))
	      end

	  fun innerQuash superVars (route, (Node (a, headThms, [], cons, []))) = 
	      let
		  val rt = newVar (T.TypList T.TypAddr)
		  val route' =
		      case Mark.data route
		       of A.Thm (T.Routing name, x::y::vars) =>
			  if a = y
			  then A2.Thm (T.Routing name, (localizeVars (x::y::vars))@[rt])
			  else if a = x
			  then A2.Thm (T.Routing (invertRouteName name), (localizeVars (y::x::vars))@[rt])
			  else raise InternalError "Uncaught routing error - wrong route in tree"
			| A.Thm (T.Linear _, _) => raise InternalError "found linear fact, expected route"
			| A.Thm (T.Regular _, _) => raise InternalError "found regular fact, expected route"
			| A.Thm (T.Delta _, _) => raise InternalError "found delta fact, expected route"
			| A.Thm (T.Routing _, _) => raise InternalError "found routing fact with fewer than 2 fields. Where could it route to??"
			| A.Thm (T.Schedule _, _) => raise InternalError "found schedule fact, expected route"
			| A.Thm (T.Delete _, _) => raise InternalError "found delete fact, expected route"
			| A.Thm (T.LinearRegular _, _) => raise InternalError "linear-regular facts not expected here"
			| A.Thm (T.Proved _, _) => raise InternalError "proved fact not expected here"
									     
		  val (headThms', cons') = 
		      ListPair.unzip (List.map (routify rt) headThms)
	      in
		  (headThms',
		   [route'],
		   (List.mapPartial (fn x => x) cons') @ (localizeConstraints cons),
		   [],
		   [])
	      end
	    | innerQuash superVars (route, (Node (a, headThms, thms, cons, subtrees))) =
	      let
		  val thms = (localizeThms thms)
		  val cons = (localizeConstraints cons)

		  val (headThms', thms', cons', rules, decls) =
(* SUPERTODO: need vars that appear in more than 1 subtree *)
		      innerQuashes (elimDups (superVars@(List.map (fn A2.Var(v, _) => v) ((grabVarsThms (List.map #1 headThms))@(grabVarsThms thms)@(grabVarsCons cons)))@(grabVarsSubtrees subtrees))) subtrees

		  val rt = newVar (T.TypList T.TypAddr)
		  val rtRev = newVar (T.TypList T.TypAddr)

		  val (route', target) =
		      case Mark.data route
		       of A.Thm (T.Routing name, x::y::vars) =>
			  if a = x
			  then (A2.Thm (T.Routing name, (localizeVars (x::y::vars))@[rt]), localizeVar y)
			  else if a = y
			  then (A2.Thm (T.Routing (invertRouteName name), (localizeVars (y::x::vars))@[rt]), localizeVar x)
			  else raise InternalError "Uncaught routing error - wrong route in tree"
			| A.Thm (T.Linear _, _) => raise InternalError "found linear fact, expected route"
			| A.Thm (T.Regular _, _) => raise InternalError "found regular fact, expected route"
			| A.Thm (T.Delta _, _) => raise InternalError "found delta fact, expected route"
			| A.Thm (T.Schedule _, _) => raise InternalError "found schedule fact, expected route"
			| A.Thm (T.Delete _, _) => raise InternalError "found delete fact, expected route"
			| A.Thm (T.LinearRegular _, _) => raise InternalError "linear-regular facts not expected here"
			| A.Thm (T.Proved _, _) => raise InternalError "proved facts not expected here"
			| A.Thm (T.Routing _, _) => raise InternalError "found routing fact with fewer than 2 fields. Where could it route to??"
							  

		  val (newHeads, newCons) = ListPair.unzip (List.map (routify rtRev) (headThms@headThms'))


		  val allThms = route'::thms@thms'
		  val allCons = cons@cons'
				
		  val varTyps = 
		      List.filter
		      (fn (A2.Var(v, _), _) => List.exists (fn v' => v = v') superVars)
		      (elimDups ((target, T.TypAddr)::
				 (rt, T.TypList T.TypAddr)::
				 (List.map (fn A2.Var(s, t) => (A2.Var(s,t),t))
					   ((grabVarsThms allThms)@
					    (grabVarsConsAssign allCons)))))
		  val newName = (if List.exists (fn A2.Thm(T.Linear _,_) => true | _ => false) allThms
				 then T.Linear
				 else T.Regular) (newThmName ())

		  val newThm = A2.Thm (newName, List.map #1 varTyps)
		  val newDecl = T.Decl (newName, List.map (fn (_, typ) => (T.AggNone, typ)) varTyps, T.NotPersistent) (* not sure if not persistent *)

		  val newRule = A2.Rule ([(newThm, A2.SEND rt, T.TimeNow)],
					 allThms,
					 allCons)
	      in
		  (newHeads,
		   [newThm],
		   (if (List.length newHeads > 0)
		    then [(A2.Assign (rtRev, A2.Exp(A2.ExpReverse (A2.Exp (A2.ExpVar rt, 
									   T.TypList T.TypAddr)), 
						    T.TypList T.TypAddr)))]
		    else [])@
		   (List.mapPartial (fn x => x) newCons),
		   newRule::rules,
		   newDecl::decls)
	      end
	  and innerQuashes superVars trees =
	      foldr (fn ((a1,b1,c1,d1,e1), (a2,b2,c2,d2,e2)) => (a1@a2, b1@b2, c1@c2, d1@d2, e1@e2))
		    ([],[],[],[],[]) (List.map (innerQuash superVars) trees)


	  val superVars = 
	      elimDups
		  ((List.map (fn A2.Var(v, _) => v) (grabVarsThms (List.map #1 headThms)))@
		   (List.map (fn A.Var(v, _) => v) ((grabVarsThmsOld thms)@(grabVarsConsOld cons)))@
		   (grabVarsSubtrees subtrees))

	  val (headThms', thms', cons', rules, decls) = innerQuashes superVars subtrees
      in
	  ((A2.Rule(headThms@headThms', (localizeThms thms)@thms', (localizeConstraints cons)@cons'))::rules, decls)
      end

  fun localizeRule' (A.Rule (headThms, bodyThms, cons)) =
      let
	  (* For now we arbitrarily pick a node from among the head clauses,
	   * unless the entire body of the rule occurs at one node *)
          val root = 
	      let
		  fun getLocal thm =
		      (case Mark.data thm of A.Thm (_, x::_) => x
					   | A.Thm (_, []) => raise InternalError "found theorem with no fields -- unsupported")
		      
		  val headRoot = getLocal (#1 (List.hd headThms))
		      handle Empty => raise InternalError "Rule has no body theorems?? - confused"
					    
		  val bodyRoot = getLocal (List.hd bodyThms)
		      handle Empty => raise InternalError "Rule has no head theorems?? - confused"
					    
	      in
		  if not (List.exists (fn x => not ((getLocal x) = bodyRoot)) bodyThms)
		  then bodyRoot
		  else headRoot
	      end
	      
	  fun finalize (Node(a, headThms, thms, cons, subtrees)) =
	      let
		  val subtrees' = List.map (fn (r, t) => (r, finalize t)) subtrees
	      in
		  case (List.exists (fn (_, LNode _) => true | (_, RNode _) => false) subtrees',
			List.exists (fn (A.Thm (T.Linear _, _)) => true | _ => false) (List.map Mark.data thms)) of
		      (false, false) => RNode(a, headThms, thms, cons, subtrees')
		    | _ => LNode(a, headThms, thms, cons, subtrees')
	      end
	      
	      
	  val tree = (pushConstraints o polishConstraints) (cons, buildRoutingTree root headThms bodyThms)
      in
	  (*			quash (finalize tree) *)
	  quash tree
      end

  fun localizeRule rule = (localizeRule' o Mark.data) rule

  fun localizeDecls ((T.Decl (T.Routing name, args, persistent))::decls) =
      let
	  val rt = (T.AggNone, T.TypList T.TypAddr)
	  val decl = T.Decl (T.Routing name, args@[rt], persistent)

 			    (* we would need to swap the first two arguments, but they are both TypAddr *)
	  val args' = List.map (fn (_, tp) => (T.AggNone, tp)) args
	  val decl' = T.Decl (T.Routing (invertRouteName name), args'@[rt], persistent)

	  val varTyps =
	      List.tabulate
		  (List.length args,
   		fn n => case List.nth (args, n) of (_, tp) => (A2.Var ("X" ^ (Int.toString n), tp), tp))
	  val vars = List.map #1 varTyps
	  val vars' = (List.nth (vars,1))::(List.nth (vars,0))::(List.drop (vars, 2))
	  val (rtv, rtv2) = (A2.Var ("route", T.TypList T.TypAddr), A2.Var ("route'", T.TypList T.TypAddr))

	  val rule = A2.Rule ([(A2.Thm (T.Routing (invertRouteName name), vars'@[rtv2]),
				A2.SEND rtv,
				T.TimeNow)],
			      [A2.Thm (T.Routing name, vars@[rtv])],
			      [A2.Assign (rtv2, A2.Exp(A2.ExpReverse (A2.Exp (A2.ExpVar rtv, 
									      T.TypList T.TypAddr)), 
						       T.TypList T.TypAddr))])
		     
	  val (decls', rules') = localizeDecls decls
      in
	  (decl::decl'::decls', rule::rules')
      end
    | localizeDecls (decl::decls) = 
      let
	  val (decls', rules') = localizeDecls decls
      in 
	  (decl::decls', rules')
      end
    | localizeDecls [] = ([], [])
			     
			     (* code for dropping the first argument of each tuple *)
			 
  fun dropFirstArgumentDecl decl =
      case decl of
	  T.Decl (theorem, args,  persistent) => T.Decl (theorem, tl args, persistent)
	| _ => decl
	       
  fun dropFirstThm (A2.Thm (thm, vars)) = A2.Thm (thm, tl vars)
    | dropFirstThm (A2.Forall (thm, vars, thm2, vars2)) = A2.Forall (thm, tl vars, thm2, tl vars2)
  fun dropFirstHead (thm, target, time) = (dropFirstThm thm, target, time)
  fun dropFirstBody thm = dropFirstThm thm
			  
  fun getFirstVar (A2.Thm (thm, varlist)) = hd varlist
    | getFirstVar (A2.Forall (thm, varlist, thm2, varlist2)) = raise (InternalError "getFirstVar: not implemented")
								     
  fun lookupFirstArg [] = raise (InternalError "lookupFirstArg: unexpected error")
    | lookupFirstArg (thm::bodylist) = getFirstVar thm
				       
  fun lookupUsedHeadVariable head var =
      case head of
          A2.Thm (_, varlist) => List.exists (fn (A2.Var (name, _)) => name = var) varlist
        | _ => raise (InternalError "lookupUsedHeadVariable: not supported")
		     
  fun lookupUsedBodyVariable body var =
      case body of
          A2.Thm (_, varlist) => List.exists (fn (A2.Var (name, _)) => name = var) (tl varlist)
        | _ => raise (InternalError "not supported")
		     
		     
  fun changeThisVariable (A2.Var (name, typ)) var = if var = name
                                                    then A2.ExpHostId
                                                    else A2.ExpVar (A2.Var (name, typ))
							 
  fun changeVariable' (A2.Exp (exp, typ)) var = A2.Exp (changeVariable exp var, typ)
  and changeVariable (A2.ExpVar othervar) var = changeThisVariable othervar var
    | changeVariable (A2.ExpNil) _ = A2.ExpNil
    | changeVariable A2.ExpHostId _ = A2.ExpHostId
    | changeVariable (A2.ExpType typ) _ = (A2.ExpType typ)
    | changeVariable (A2.ExpInt it) _ = A2.ExpInt it
    | changeVariable (A2.ExpFloat fl) _ = A2.ExpFloat fl
    | changeVariable (A2.ExpBinop (oper, (A2.Exp (exp1, typ1)), (A2.Exp (exp2, typ2)))) var =
      A2.ExpBinop (oper, A2.Exp (changeVariable exp1 var, typ1), A2.Exp (changeVariable exp2 var, typ2))
    | changeVariable (A2.ExpExtern (name, explist)) var = A2.ExpExtern (name, map (fn exp => changeVariable' exp var) explist)
    | changeVariable (A2.ExpField (exp, somevar)) var = A2.ExpField (changeVariable' exp var, somevar)
    | changeVariable (A2.ExpPoint (exp1, exp2, exp3)) var = A2.ExpPoint (changeVariable' exp1 var, changeVariable' exp2 var, changeVariable' exp3 var)
    | changeVariable (A2.ExpReverse exp) var = A2.ExpReverse (changeVariable' exp var)
    | changeVariable (A2.ExpDrop exp) var = A2.ExpDrop (changeVariable' exp var)
					
  fun addHostIdConstraints constraints headlist bodylist =
      let
	  val firstVar = lookupFirstArg bodylist
	  val (A2.Var (firstArgVar, varType)) = firstVar
						    (* see if variables are used in the head arguments (minus the 1st) and on the constraints *)
	  val isUsedElsewhere = ((List.exists (fn (head, _, _) => lookupUsedHeadVariable head firstArgVar) headlist)
	                         orelse (List.exists (fn body => lookupUsedBodyVariable body firstArgVar) bodylist))
	  fun changeVariableConstraint (A2.Constraint (oper, exp1, exp2)) = (A2.Constraint (oper, changeVariable' exp1 firstArgVar, changeVariable' exp2 firstArgVar))
            | changeVariableConstraint (A2.Assign (var, exp)) = (A2.Assign (var, changeVariable' exp firstArgVar))
								
	  val newConstraintsList = map changeVariableConstraint constraints
      in
          if isUsedElsewhere then
              let
		  val newConstraint = A2.Assign (firstVar, (A2.Exp (A2.ExpHostId, varType)))
              in
		  [newConstraint]@newConstraintsList
              end
          else
              newConstraintsList
      end

  fun dropFirstArgumentRule (A2.Rule (headlist,  bodylist, constraints)) =
      let
          val newHeadList = List.map dropFirstHead headlist
          val newBodyList = List.map dropFirstBody bodylist
      in
          A2.Rule (newHeadList, newBodyList, addHostIdConstraints constraints newHeadList bodylist)
      end
	  
	  (* code to optimize linear predicate use when a predicate is used and then generated immediately *)
  fun optimizeLinearThms rule =
      let
          val (A2.Rule (headlist, bodylist, constraints)) = rule
          fun findLocals (A2.Thm (thm, varlist), A2.LOCAL, _) =
              let
              in
                  case thm of
                      T.Linear _ => SOME (thm, varlist)
                    | _ => NONE
              end
            | findLocals _ = NONE
          fun samevars [] [] = true
            | samevars ((A2.Var (name1, _))::rest1) ((A2.Var (name2, _))::rest2) =
              name1 = name2 andalso (samevars rest1 rest2)
            | samevars _ _ = false
          fun sameThm (T.Linear name1, varlist1) (T.Linear name2, varlist2) =
              name1 = name2 andalso (samevars varlist1 varlist2)
            | sameThm _ _ = false
          fun findInBody thmlocal (A2.Thm (thmother, varlist)) = sameThm thmlocal (thmother, varlist)
            | findInBody _ _ = false
          val localrules = List.mapPartial findLocals headlist
          val toFilter = List.filter (fn localrule => List.exists (fn body => findInBody localrule body) bodylist) localrules
      in
          if (List.length toFilter) > 0
          then
              let
		  fun removeMatches (A2.Thm (thm, varlist), A2.LOCAL, _) =
                      not (List.exists (fn target => sameThm target (thm, varlist)) toFilter)
                    | removeMatches _ = true
					
		  fun convertLinear (A2.Thm (T.Linear name, varlist)) =
                      if List.exists (fn target => sameThm (T.Linear name, varlist) target) toFilter
                      then (A2.Thm (T.LinearRegular name, varlist))
                      else (A2.Thm (T.Linear name, varlist))
                    | convertLinear body = body
					   
		  val newHeadlist = List.filter removeMatches headlist
		  val newBodylist = List.map convertLinear bodylist
				    
		  val newRule = A2.Rule (newHeadlist, newBodylist, constraints)
              in
		  newRule
              end
          else rule
      end

  fun localize' (A.Program (decls, rules)) dropFirstArgument =
      let
	  val (decls', newRules) = localizeDecls decls
	  val (newRules2, newDecls2) = ListPair.unzip (List.map localizeRule rules)

	  val rules' = foldl List.@ newRules newRules2
	  val decls'' = foldl List.@ decls' newDecls2
			
	  val decls''' = if dropFirstArgument
			 then List.map dropFirstArgumentDecl decls''
			 else decls''
	  val rules'' = if dropFirstArgument
		        then List.map dropFirstArgumentRule rules'
		        else rules'
			     
	  val rules'' = List.map optimizeLinearThms rules''
      in
	  A2.Program (decls''', rules'')
      end
      
  fun localize prog dropFirst = localize' (Mark.data prog) dropFirst
  end


(* TODO: add support for tuples, fields, and forall; do constraints right *)
