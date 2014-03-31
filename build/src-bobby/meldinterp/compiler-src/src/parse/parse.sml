(* L1 Compiler
 * Parsing
 *)

signature PARSE =
  sig 
    val parse : string -> Ast.program * bool (* grabs the program, returns the AST and a boolean value that indicates error *)
  end

structure Parse :> PARSE =
  struct 

    structure SimpleLrVals = SimpleLrValsFn (structure Token = LrParser.Token)
    structure Lex = SimpleLexFn (structure Tokens = SimpleLrVals.Tokens)
    structure SimpleP = Join (structure ParserData = SimpleLrVals.ParserData
                              structure Lex = Lex
                              structure LrParser = LrParser)

  (* The main parsing routine *)
  fun parse filename =
    let
       val _ = (ErrorMsg.reset(); ParseState.reset (); ParseState.setfile ("# 1 " ^ filename))
	     val file = TextIO.openIn filename
	    
	     fun get _ = TextIO.input file
       val anyerrors = ref false
	     fun parseerror(s,p1,p2) = ErrorMsg.error (ParseState.ext (p1,p2)) s before anyerrors := true
	     val lexer = LrParser.Stream.streamify (Lex.makeLexer get)
	     val ((files, absyn), _) = SimpleP.parse(30, lexer, parseerror, ())
	     val _ = TextIO.closeIn file

			 val path = case (String.concatWith "/" (List.rev (List.drop (List.rev (String.tokens (fn c => c = #"\\" orelse c = #"/") filename), 1))))
									 of "" => ""
										| s => s ^ "/"
    in
				foldl (fn ((Ast.Program (consts1, decls1, rules1), errors1),
									 (Ast.Program (consts2, decls2, rules2), errors2)) =>
									(Ast.Program (consts1@consts2, decls1@decls2, rules1@rules2), errors1 orelse errors2))
							(absyn, !anyerrors)
							(List.map parse (List.map (fn f => path ^ f) files))
    end
      handle LrParser.ParseError => raise ErrorMsg.Error
           | e as IO.Io _ => (ErrorMsg.error NONE (exnMessage e);
                              raise ErrorMsg.Error)
end
