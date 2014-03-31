signature Simple_TOKENS =
sig
type ('a,'b) token
type svalue
val PROVED:  'a * 'a -> (svalue,'a) token
val DELETE:  'a * 'a -> (svalue,'a) token
val SCHEDULE:  'a * 'a -> (svalue,'a) token
val DELTA:  'a * 'a -> (svalue,'a) token
val SET:  'a * 'a -> (svalue,'a) token
val SET_UNION:  'a * 'a -> (svalue,'a) token
val MILLISECS:  'a * 'a -> (svalue,'a) token
val SECS:  'a * 'a -> (svalue,'a) token
val PERSISTENT:  'a * 'a -> (svalue,'a) token
val BANG:  'a * 'a -> (svalue,'a) token
val LINEAR:  'a * 'a -> (svalue,'a) token
val VIRTUALNEIGHBOR:  'a * 'a -> (svalue,'a) token
val FORALL:  'a * 'a -> (svalue,'a) token
val CONST:  'a * 'a -> (svalue,'a) token
val EXTERN:  'a * 'a -> (svalue,'a) token
val ASNOP:  'a * 'a -> (svalue,'a) token
val UNARY:  'a * 'a -> (svalue,'a) token
val MANUAL:  'a * 'a -> (svalue,'a) token
val TILDE:  'a * 'a -> (svalue,'a) token
val ANY:  'a * 'a -> (svalue,'a) token
val ARROW:  'a * 'a -> (svalue,'a) token
val APPEND:  'a * 'a -> (svalue,'a) token
val FIRST:  'a * 'a -> (svalue,'a) token
val TRASH:  'a * 'a -> (svalue,'a) token
val MIN:  'a * 'a -> (svalue,'a) token
val MAX:  'a * 'a -> (svalue,'a) token
val LIST:  'a * 'a -> (svalue,'a) token
val ADDR:  'a * 'a -> (svalue,'a) token
val FLOAT:  'a * 'a -> (svalue,'a) token
val INT:  'a * 'a -> (svalue,'a) token
val TYPE:  'a * 'a -> (svalue,'a) token
val RBRACKET:  'a * 'a -> (svalue,'a) token
val LBRACKET:  'a * 'a -> (svalue,'a) token
val RPAREN:  'a * 'a -> (svalue,'a) token
val LPAREN:  'a * 'a -> (svalue,'a) token
val GREATEREQ:  'a * 'a -> (svalue,'a) token
val LESSEQ:  'a * 'a -> (svalue,'a) token
val GREATER:  'a * 'a -> (svalue,'a) token
val LESS:  'a * 'a -> (svalue,'a) token
val NEQUAL:  'a * 'a -> (svalue,'a) token
val EQUAL:  'a * 'a -> (svalue,'a) token
val HASH:  'a * 'a -> (svalue,'a) token
val AT:  'a * 'a -> (svalue,'a) token
val COMMA:  'a * 'a -> (svalue,'a) token
val PROVES:  'a * 'a -> (svalue,'a) token
val DOT:  'a * 'a -> (svalue,'a) token
val DOTPROD:  'a * 'a -> (svalue,'a) token
val EXP:  'a * 'a -> (svalue,'a) token
val NIL:  'a * 'a -> (svalue,'a) token
val CONS:  'a * 'a -> (svalue,'a) token
val MOD:  'a * 'a -> (svalue,'a) token
val MINUS:  'a * 'a -> (svalue,'a) token
val PLUS:  'a * 'a -> (svalue,'a) token
val TIMES:  'a * 'a -> (svalue,'a) token
val DIVIDE:  'a * 'a -> (svalue,'a) token
val RETURN:  'a * 'a -> (svalue,'a) token
val IDENT: (string) *  'a * 'a -> (svalue,'a) token
val USE:  'a * 'a -> (svalue,'a) token
val STR: (string) *  'a * 'a -> (svalue,'a) token
val FLOATNUM: (real) *  'a * 'a -> (svalue,'a) token
val INTNUM: (Int32.int) *  'a * 'a -> (svalue,'a) token
val SEMI:  'a * 'a -> (svalue,'a) token
val EOF:  'a * 'a -> (svalue,'a) token
end
signature Simple_LRVALS=
sig
structure Tokens : Simple_TOKENS
structure ParserData:PARSER_DATA
sharing type ParserData.Token.token = Tokens.token
sharing type ParserData.svalue = Tokens.svalue
end
