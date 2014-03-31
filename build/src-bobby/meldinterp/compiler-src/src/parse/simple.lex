(* SIMPLE Compiler
 * SIMPLE lexer
 *)

structure A = Ast

type pos = int
type svalue = Tokens.svalue
type ('a,'b) token = ('a,'b) Tokens.token
type lexresult = (svalue,pos) Tokens.token

fun strip s = String.extract (s, 1, SOME (String.size s - 2))

local
  val commentLevel = ref 0
  val commentPos = ref 0
  val linCom = ref false
  val varNum = ref 0
in
  fun enterComment yypos = (commentLevel := !commentLevel + 1; commentPos := yypos)
    
  fun linComStart yypos = (linCom := true; commentPos := yypos)
  fun isLinCom () = !linCom
  fun linComEnd () = linCom := false

  fun exitComment () =
   let val _ = commentLevel := !commentLevel - 1 in
     !commentLevel = 0
   end

fun number (base, yyt, yyp) =
    let
      val ext = ParseState.ext (yyp, yyp + size yyt)
      fun ls ts =
          let
            val txt = String.implode (rev ts)
          in 
            case StringCvt.scanString (Int32.scan base) txt of
              NONE =>
                (ErrorMsg.error ext ("cannot parse integral constant \"" ^ yyt ^ "\"; replacing with 0");
                 Tokens.INTNUM (0, yyp, yyp + size yyt))
            | SOME n => 
	      Tokens.INTNUM (n,yyp,yyp + size yyt)
          end
    in
      ls (rev (String.explode yyt))
    end

fun floatnumber (base, yyt, yyp) =
    let
      val ext = ParseState.ext (yyp, yyp + size yyt)
      fun ls ts =
          let
            val txt = String.implode (rev ts)
          in 
            case StringCvt.scanString Real.scan txt of
              NONE =>
                (ErrorMsg.error ext ("cannot parse float constant \"" ^ yyt ^ "\"; replacing with 0");
                 Tokens.FLOATNUM (0.0, yyp, yyp + size yyt))
            | SOME f =>
                Tokens.FLOATNUM (f,yyp,yyp + size yyt)
          end
    in
      ls (rev (String.explode yyt))
    end

fun eof () = 
  (let
        val yypos = ParseState.curline ()
      in
        if (!commentLevel > 0) then
          (ErrorMsg.error (ParseState.ext (!commentPos,!commentPos)) "Unterminated comment")
        else ();
        Tokens.EOF (yypos,yypos)
   end handle e => (print "EOF"; raise e))
end

val str : string list ref = ref []
val strstart = ref (~1)



%%
%header (functor SimpleLexFn(structure Tokens : Simple_TOKENS));
%full
%s COMMENT STR;

id = [A-Za-z][A-Za-z0-9_']*;
decnum = [0-9][0-9]*;
floatnum = [0-9][0-9]*[\.][0-9]+;
fname = ".+";

ws = [\ \t\012\r];

%%

^# .*\n                => (ParseState.setfile yytext; continue ());

\n                    => (if isLinCom () then (linComEnd (); YYBEGIN INITIAL) else ();
                          ParseState.newline yypos;
                          continue ());

<INITIAL> {ws}+       => (lex ());


<INITIAL> "("         => (Tokens.LPAREN (yypos, yypos + size yytext));
<INITIAL> ")"         => (Tokens.RPAREN (yypos, yypos + size yytext));

<INITIAL> "/"         => (Tokens.DIVIDE (yypos, yypos + size yytext));
<INITIAL> "*"         => (Tokens.TIMES (yypos, yypos + size yytext));
<INITIAL> "+"         => (Tokens.PLUS (yypos, yypos + size yytext));
<INITIAL> "-"         => (Tokens.MINUS (yypos, yypos + size yytext));
<INITIAL> "%"         => (Tokens.MOD (yypos, yypos + size yytext));
<INITIAL> "^"         => (Tokens.EXP (yypos, yypos + size yytext));
<INITIAL> "`"         => (Tokens.DOTPROD (yypos, yypos + size yytext));

<INITIAL> "."         => (Tokens.DOT (yypos, yypos + size yytext));
<INITIAL> ":-"        => (Tokens.PROVES (yypos, yypos + size yytext));
<INITIAL> ","         => (Tokens.COMMA (yypos, yypos + size yytext));
<INITIAL> ";"         => (Tokens.SEMI (yypos, yypos + size yytext));

<INITIAL> "@"         => (Tokens.AT (yypos, yypos + size yytext));
<INITIAL> "#"         => (Tokens.HASH (yypos, yypos + size yytext));

<INITIAL> "use"       => (Tokens.USE (yypos, yypos + size yytext));


<INITIAL> "="         => (Tokens.EQUAL (yypos, yypos + size yytext));
<INITIAL> "!="        => (Tokens.NEQUAL (yypos, yypos + size yytext));
<INITIAL> "<"         => (Tokens.LESS (yypos, yypos + size yytext));
<INITIAL> ">"         => (Tokens.GREATER (yypos, yypos + size yytext));
<INITIAL> "<="        => (Tokens.LESSEQ (yypos, yypos + size yytext));
<INITIAL> ">="        => (Tokens.GREATEREQ (yypos, yypos + size yytext));
<INITIAL> "::"        => (Tokens.CONS (yypos, yypos + size yytext));
<INITIAL> "[]"        => (Tokens.NIL (yypos, yypos + size yytext));
<INITIAL> "!"         => (Tokens.BANG (yypos, yypos + size yytext));

<INITIAL> "~"         => (Tokens.TILDE (yypos, yypos + size yytext));

<INITIAL> "->"        => (Tokens.ARROW (yypos, yypos + size yytext));

<INITIAL> "type"      => (Tokens.TYPE (yypos, yypos + size yytext));
<INITIAL> "int"       => (Tokens.INT (yypos, yypos + size yytext));
<INITIAL> "float"     => (Tokens.FLOAT (yypos, yypos + size yytext));
<INITIAL> "catom"     => (Tokens.ADDR (yypos, yypos + size yytext));
<INITIAL> "addr"      => (Tokens.ADDR (yypos, yypos + size yytext));
<INITIAL> "node"			=> (Tokens.ADDR (yypos, yypos + size yytext));
<INITIAL> "list"      => (Tokens.LIST (yypos, yypos + size yytext));

<INITIAL> "extern"    => (Tokens.EXTERN (yypos, yypos + size yytext));
<INITIAL> "forall"    => (Tokens.FORALL (yypos, yypos + size yytext));

<INITIAL> "virtual neighbor"   => (Tokens.VIRTUALNEIGHBOR (yypos, yypos + size yytext));
<INITIAL> "routing"   => (Tokens.VIRTUALNEIGHBOR (yypos, yypos + size yytext));
<INITIAL> "linear"    => (Tokens.LINEAR (yypos, yypos + size yytext));
<INITIAL> "persistent" => (Tokens.PERSISTENT (yypos, yypos + size yytext));
<INITIAL> "const"     => (Tokens.CONST (yypos, yypos + size yytext));
<INITIAL> "set"       => (Tokens.SET (yypos, yypos + size yytext));
<INITIAL> "delta"     => (Tokens.DELTA (yypos, yypos + size yytext));
<INITIAL> "schedule"  => (Tokens.SCHEDULE (yypos, yypos + size yytext));
<INITIAL> "delete"    => (Tokens.DELETE (yypos, yypos + size yytext));
<INITIAL> "proved"    => (Tokens.PROVED (yypos, yypos + size yytext));

<INITIAL> {decnum}    => (number (StringCvt.DEC, yytext, yypos));
<INITIAL> {floatnum}  => (floatnumber(StringCvt.DEC, yytext, yypos));

<INITIAL> "_"         => (Tokens.ANY(yypos, yypos + size yytext));

<INITIAL> "ms"        => (Tokens.MILLISECS(yypos, yypos + size yytext));
<INITIAL> "s"         => (Tokens.SECS(yypos, yypos + size yytext));

<INITIAL> "\""        => (YYBEGIN STR; str := []; strstart := yypos; continue());
<STR> "\""            => (YYBEGIN INITIAL; Tokens.STR (foldl String.^ "" (!str), !strstart, yypos + size yytext));
<STR> .               => (str := yytext::(!str); continue ());

<INITIAL> {id}        => (let
                            val id = yytext
                          in 
              							Tokens.IDENT (id, yypos, yypos + size yytext)
            							end);

<INITIAL> "/*"        => (YYBEGIN COMMENT; enterComment yypos; continue());
<INITIAL> "*/"        => (ErrorMsg.error (ParseState.ext (yypos, yypos)) "Unbalanced comments";
                             continue());
<INITIAL> "//"        => (YYBEGIN COMMENT; linComStart yypos; continue());
<COMMENT> "/*"        => (if not (isLinCom ()) then enterComment yypos else (); continue());
<COMMENT> "*/"        => (if not (isLinCom ()) andalso exitComment () then YYBEGIN INITIAL else ();
                              continue());

<COMMENT> .           => (continue());

<INITIAL> .           => (ErrorMsg.error (ParseState.ext (yypos,yypos))
                              ("illegal character: \"" ^ yytext ^ "\"");
                          continue ());
