signature WARNINGS =
  sig
	  val warn : Ast.program -> string list * bool
	  exception WarningError of string
  end

structure Warnings :> WARNINGS =
  struct

	structure A = Ast
	structure T = Types

	exception WarningError of string

	val error = ref false

	datatype thmType = LINEAR | REGULAR

	fun thmName thm =
		case Mark.data thm of A.Thm (name, _) => T.thmName name

	fun uses name rule =
		let
			val clauses = case Mark.data rule of A.Rule (_, clauses) => clauses
			
			fun user clause =
				case Mark.data clause of
					A.ThmClause thm => (thmName thm) = name
				  | A.Constraint _ => false
				  | A.Forall (thm, clauses) => ((thmName thm) = name) orelse (List.exists user clauses)

		in
			List.exists user clauses
		end

	fun produces name rule =
		let
			val thms = case Mark.data rule of A.Rule (thms, _) => thms

			fun producer thm = (thmName thm) = name
		in
			List.exists producer (List.map #1 thms)
		end

	fun thmType thm =
		case thm
		 of T.Linear _ => LINEAR
		  | T.Regular _ => REGULAR
		  | T.Routing _ => REGULAR
		  | T.Delta _ => REGULAR
		  | T.Schedule _ => REGULAR
		  | T.Delete _ => REGULAR
		  | T.LinearRegular _ => REGULAR
		  | T.Proved _ => REGULAR

	fun producesTyp typ decls rule =
		let
			val thms = case Mark.data rule of A.Rule (thms, _) => thms
																  
			fun producer thm = 
				case Mark.data thm of
					A.Thm (name, _) =>
				case T.lookupThm' decls (T.thmName name) of
					NONE => NONE
				  | SOME (theorem, _) =>
					if (thmType theorem) = typ
					then SOME thm
					else NONE
		in
			case List.mapPartial producer (List.map #1 thms) of
				[] => NONE
			  | x::_ => SOME x
		end

	fun usesTyp typ decls rule =
		let
			val clauses = case Mark.data rule of A.Rule (_, clauses) => clauses

			fun use thm =
				case Mark.data thm of
					A.Thm (name, _) =>
				case T.lookupThm' decls (T.thmName name) of
					NONE => NONE
				  | SOME (theorem, _) =>
					if thmType theorem = typ
					then SOME thm
					else NONE
																  
			fun user clause =
				case Mark.data clause of
					A.ThmClause thm => use thm
				  | A.Constraint _ => NONE
				  | A.Forall (thm, clauses) => 
					(case (use thm, List.mapPartial user clauses)
					  of (_, thm::_) => SOME thm
					   | (useOption, _) => useOption)
					
		in
			case List.mapPartial user clauses of
				[] => NONE
			  | x::_ => SOME x
		end


	fun forallLinear decls rule = 
		case Mark.data rule of
			(A.Rule (thms, clauses)) =>
			let
				fun checkClause clause =
					case Mark.data clause
					 of A.ThmClause _ => NONE
					  | A.Constraint _ => NONE
					  | A.Forall (thm, clauses) =>
						let
							val fakeRule = Mark.naked (A.Rule ([(thm, T.TimeNow)], clauses))
						in
							case (usesTyp LINEAR decls fakeRule, producesTyp LINEAR decls fakeRule) of
								(SOME thm, _) => SOME thm
							  | (NONE, thmOpt) => thmOpt
						end
			in
				case List.mapPartial checkClause clauses of
					[] => NONE
				  | x::_ => SOME x
			end

	fun negativeTime rule =
		case Mark.data rule of
			A.Rule (thms, _) =>
			(case List.mapPartial (fn (_, T.TimeNow) => NONE
									| (thm, T.TimeInMS n) => if n > 0 then NONE else SOME thm) thms
			  of [] => NONE
			   | (x::_) => SOME x)
			
		

	(*
	 * -underivable fact
	 * -aggregates in linear facts
	 *)
	fun warnDecl rules decl = 
		case Mark.data decl of
			T.Type _ => []
		  | T.Extern _ => []
		  | T.Decl (thm, args, _) => 
			let
				val name = T.thmName thm
			in
			List.mapPartial
				(fn SOME x => SOME (Mark.error decl x) | NONE => NONE)
			    [case (List.exists (produces name) rules, List.exists (uses name) rules) of
					 (false, false) => SOME ("WARNING: (useless) unused fact '" ^ name ^ "' declared")
				   | (false, true) => SOME ("WARNING: (underivable) fact '" ^ name ^ "' is used, but never derived. Declared")
				   | (true, false) => SOME ("WARNING: (unusable) fact '" ^ name ^ "' is derived, but never used. Declared")
				   | (true, true) => NONE,
				 case (thm, List.exists (fn (T.AggNone, _) => false | _ => true) args) of
					 (T.Linear _, true) => (error := true; SOME ("ERROR: linear fact '" ^ name ^ "' uses aggregate"))
				   | (T.Routing _, true) => (error := true; SOME ("ERROR: routing fact '" ^ name ^ "' uses aggregate"))
				   | _ => NONE]
			end
			 
							  
	fun warnDecls rules [] = []
	  | warnDecls rules (decl::decls) = (warnDecl rules decl)@(warnDecls rules decls)

    (*
	 * -Linear misuse
	 *)
	fun warnRule decls rule = 
		List.mapPartial
			(fn x => x)
		    [case forallLinear decls rule of
				 SOME thm => SOME (error := true; Mark.error thm "ERROR: forall uses linear fact")
			   | NONE => NONE,
			 case (usesTyp REGULAR decls rule,
				   producesTyp REGULAR decls rule,
				   usesTyp LINEAR decls rule,
				   producesTyp LINEAR decls rule) of
			     (SOME _, _, NONE, SOME thm) => SOME (error := true; Mark.error thm "ERROR: rule produces infinite quantity of linear facts")
			   | (_, SOME thm', SOME _, _) => SOME (Mark.error thm' "WARNING: produces regular fact from linear fact")
			   | (_, _, SOME thm, NONE) => SOME (Mark.error thm "WARNING: rule consumes a linear fact, but does not produce any")
			   | _ => NONE,
			 case (negativeTime rule) of
				 NONE => NONE
			   | SOME t => SOME (error := true; Mark.error t "ERROR: rule attempts to derive theorem in the past")]

	fun warnRules decls [] = []
	  | warnRules decls (rule::rules) = (warnRule decls rule)@(warnRules decls rules)
		
				  
	fun warn (A.Program (_, decls, rules)) =
		(error := false;
		 (List.map (fn s => s ^ "\n") ((warnDecls rules decls)@(warnRules (List.map Mark.data decls) rules)),
		  !error))

  end

