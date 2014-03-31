functor SimpleLrValsFn (structure Token : TOKEN) = 
struct
structure ParserData=
struct
structure Header = 
struct
(*#line 1.2 "simple.grm"*)(* SIMPLE Compiler
 * SIMPLE grammar
 *)

structure A = Ast
structure T = Types
open Mark

fun marksmap f s = List.map (Mark.map f) s
fun marksmap' f s = List.map (Mark.map' f) s

fun mark (e, (lft, rght)) = Mark.mark' (e, ParseState.ext (lft, rght))

val consts = ref ([] : (string * T.const) list)

fun wrapVar name =
  let
    fun checkVar [] = A.ExpVar (A.Var name)
      | checkVar ((name', const)::l) = if name = name' then A.ExpConst const else checkVar l
  in
    checkVar (!consts)
  end

fun isVar name =
	let
		val found = List.find (fn (name', _) => name = name') (!consts)
	in
		case found of
			  SOME _ => false
			| _ => true
	end

fun findArgument (locateVar, explist') =
	let
		val explist = List.map Mark.data explist'
		val _ = case (isVar locateVar) of false => raise (Fail "delta argument is not a variable") | true => ()
		val counter = ref 0
		val found = List.find (fn exp => let
							val _ = counter := !counter + 1
						in
							case exp of
							 		A.ExpVar (A.Var name) => name = locateVar
								| _ => false
						end) explist
	in
		case found of
			  SOME result => !counter - 1
			| _ => raise (Fail "argument not found")
	end

val nextProvedId = ref (0 : int)

fun generateNewVar tuple =
   let
      val _ = (nextProvedId := !nextProvedId + 1)
   in
      A.ExpVar (A.Var ("__proved__" ^ tuple ^ (Int.toString (!nextProvedId))))
   end


(*#line 67.1 "simple.grm.sml"*)
end
structure LrTable = Token.LrTable
structure Token = Token
local open LrTable in 
val table=let val actionRows =
"\
\\001\000\001\000\000\000\000\000\
\\001\000\001\000\027\000\000\000\
\\001\000\002\000\162\000\000\000\
\\001\000\003\000\066\000\004\000\065\000\007\000\064\000\012\000\063\000\
\\015\000\062\000\022\000\061\000\029\000\060\000\044\000\059\000\
\\051\000\058\000\054\000\014\000\060\000\057\000\061\000\013\000\
\\062\000\012\000\063\000\056\000\000\000\
\\001\000\003\000\066\000\004\000\065\000\007\000\071\000\012\000\063\000\
\\015\000\062\000\022\000\061\000\029\000\060\000\030\000\143\000\
\\044\000\059\000\000\000\
\\001\000\003\000\066\000\004\000\065\000\007\000\071\000\012\000\063\000\
\\015\000\062\000\022\000\061\000\029\000\060\000\044\000\059\000\000\000\
\\001\000\003\000\066\000\004\000\065\000\012\000\063\000\022\000\061\000\000\000\
\\001\000\003\000\066\000\012\000\115\000\000\000\
\\001\000\003\000\110\000\000\000\
\\001\000\003\000\112\000\000\000\
\\001\000\003\000\112\000\004\000\111\000\000\000\
\\001\000\005\000\019\000\000\000\
\\001\000\007\000\018\000\054\000\014\000\061\000\013\000\062\000\012\000\000\000\
\\001\000\007\000\030\000\000\000\
\\001\000\007\000\031\000\000\000\
\\001\000\007\000\038\000\029\000\037\000\030\000\153\000\034\000\035\000\
\\035\000\034\000\036\000\033\000\000\000\
\\001\000\007\000\038\000\029\000\037\000\033\000\036\000\034\000\035\000\
\\035\000\034\000\036\000\033\000\000\000\
\\001\000\007\000\038\000\029\000\037\000\034\000\035\000\035\000\034\000\
\\036\000\033\000\000\000\
\\001\000\007\000\043\000\052\000\042\000\053\000\041\000\000\000\
\\001\000\007\000\043\000\052\000\042\000\053\000\041\000\055\000\040\000\000\000\
\\001\000\007\000\077\000\037\000\076\000\059\000\075\000\000\000\
\\001\000\007\000\078\000\000\000\
\\001\000\007\000\083\000\000\000\
\\001\000\007\000\084\000\000\000\
\\001\000\007\000\128\000\029\000\037\000\034\000\035\000\035\000\034\000\
\\036\000\033\000\000\000\
\\001\000\007\000\138\000\000\000\
\\001\000\007\000\147\000\000\000\
\\001\000\007\000\176\000\000\000\
\\001\000\009\000\014\001\010\000\014\001\011\000\014\001\012\000\014\001\
\\013\000\014\001\014\000\014\001\016\000\014\001\017\000\014\001\
\\018\000\238\000\020\000\238\000\023\000\014\001\024\000\014\001\
\\025\000\014\001\026\000\014\001\027\000\014\001\028\000\014\001\
\\032\000\238\000\000\000\
\\001\000\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\
\\020\000\116\000\000\000\
\\001\000\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\
\\020\000\118\000\000\000\
\\001\000\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\
\\020\000\131\000\030\000\141\000\000\000\
\\001\000\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\
\\020\000\161\000\000\000\
\\001\000\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\
\\023\000\096\000\024\000\095\000\025\000\094\000\026\000\093\000\
\\027\000\092\000\028\000\091\000\000\000\
\\001\000\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\
\\030\000\185\000\000\000\
\\001\000\011\000\069\000\000\000\
\\001\000\018\000\021\000\019\000\020\000\000\000\
\\001\000\018\000\087\000\000\000\
\\001\000\018\000\122\000\000\000\
\\001\000\018\000\151\000\000\000\
\\001\000\018\000\170\000\000\000\
\\001\000\018\000\172\000\000\000\
\\001\000\018\000\180\000\000\000\
\\001\000\018\000\181\000\000\000\
\\001\000\020\000\168\000\000\000\
\\001\000\023\000\074\000\000\000\
\\001\000\023\000\096\000\024\000\095\000\025\000\094\000\026\000\093\000\
\\027\000\092\000\028\000\091\000\000\000\
\\001\000\029\000\028\000\000\000\
\\001\000\029\000\029\000\000\000\
\\001\000\029\000\044\000\000\000\
\\001\000\029\000\073\000\000\000\
\\001\000\029\000\081\000\000\000\
\\001\000\029\000\105\000\000\000\
\\001\000\029\000\106\000\000\000\
\\001\000\029\000\121\000\000\000\
\\001\000\029\000\129\000\000\000\
\\001\000\029\000\183\000\000\000\
\\001\000\030\000\123\000\000\000\
\\001\000\030\000\130\000\000\000\
\\001\000\030\000\140\000\000\000\
\\001\000\030\000\150\000\000\000\
\\001\000\030\000\156\000\000\000\
\\001\000\030\000\163\000\000\000\
\\001\000\030\000\165\000\000\000\
\\001\000\030\000\166\000\000\000\
\\001\000\030\000\167\000\000\000\
\\001\000\030\000\169\000\000\000\
\\001\000\030\000\174\000\000\000\
\\001\000\030\000\182\000\000\000\
\\001\000\030\000\189\000\000\000\
\\001\000\031\000\139\000\000\000\
\\001\000\032\000\178\000\000\000\
\\001\000\056\000\145\000\057\000\144\000\000\000\
\\191\000\000\000\
\\192\000\000\000\
\\193\000\006\000\004\000\000\000\
\\194\000\000\000\
\\195\000\000\000\
\\196\000\000\000\
\\197\000\007\000\018\000\033\000\017\000\049\000\016\000\050\000\015\000\
\\054\000\014\000\061\000\013\000\062\000\012\000\000\000\
\\200\000\000\000\
\\201\000\000\000\
\\202\000\000\000\
\\203\000\000\000\
\\204\000\000\000\
\\205\000\000\000\
\\206\000\000\000\
\\207\000\000\000\
\\208\000\020\000\157\000\000\000\
\\209\000\000\000\
\\210\000\020\000\124\000\037\000\076\000\059\000\075\000\000\000\
\\211\000\000\000\
\\212\000\007\000\155\000\037\000\076\000\059\000\075\000\000\000\
\\213\000\037\000\076\000\059\000\075\000\000\000\
\\214\000\037\000\076\000\059\000\075\000\000\000\
\\215\000\000\000\
\\216\000\000\000\
\\217\000\000\000\
\\218\000\000\000\
\\219\000\000\000\
\\220\000\000\000\
\\221\000\000\000\
\\221\000\007\000\038\000\029\000\037\000\034\000\035\000\035\000\034\000\
\\036\000\033\000\000\000\
\\224\000\000\000\
\\225\000\000\000\
\\226\000\020\000\022\000\000\000\
\\227\000\000\000\
\\228\000\000\000\
\\229\000\021\000\024\000\000\000\
\\230\000\000\000\
\\231\000\000\000\
\\232\000\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\000\000\
\\233\000\000\000\
\\234\000\000\000\
\\235\000\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\000\000\
\\236\000\000\000\
\\237\000\000\000\
\\238\000\000\000\
\\239\000\000\000\
\\242\000\000\000\
\\243\000\000\000\
\\244\000\000\000\
\\245\000\000\000\
\\246\000\000\000\
\\247\000\000\000\
\\248\000\000\000\
\\249\000\000\000\
\\250\000\000\000\
\\251\000\020\000\088\000\000\000\
\\252\000\000\000\
\\253\000\000\000\
\\254\000\000\000\
\\255\000\000\000\
\\000\001\000\000\
\\001\001\000\000\
\\002\001\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\000\000\
\\003\001\000\000\
\\004\001\000\000\
\\005\001\000\000\
\\006\001\000\000\
\\007\001\000\000\
\\008\001\000\000\
\\009\001\029\000\113\000\000\000\
\\009\001\029\000\117\000\000\000\
\\010\001\000\000\
\\011\001\000\000\
\\012\001\000\000\
\\013\001\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\000\000\
\\014\001\000\000\
\\015\001\000\000\
\\016\001\000\000\
\\017\001\000\000\
\\018\001\009\000\104\000\010\000\103\000\011\000\102\000\012\000\101\000\
\\013\000\100\000\014\000\099\000\016\000\098\000\017\000\097\000\
\\020\000\131\000\000\000\
\\019\001\000\000\
\\020\001\000\000\
\\021\001\000\000\
\\022\001\000\000\
\\023\001\000\000\
\\024\001\000\000\
\\025\001\000\000\
\\026\001\000\000\
\\027\001\000\000\
\"
val actionRowNumbers =
"\075\000\079\000\011\000\036\000\
\\105\000\079\000\108\000\079\000\
\\079\000\001\000\047\000\048\000\
\\013\000\014\000\016\000\019\000\
\\049\000\075\000\003\000\104\000\
\\012\000\077\000\035\000\078\000\
\\076\000\073\000\005\000\005\000\
\\050\000\045\000\020\000\096\000\
\\099\000\095\000\021\000\017\000\
\\101\000\051\000\018\000\022\000\
\\023\000\085\000\005\000\074\000\
\\122\000\121\000\145\000\037\000\
\\128\000\134\000\133\000\132\000\
\\033\000\130\000\052\000\053\000\
\\012\000\144\000\005\000\008\000\
\\146\000\010\000\142\000\126\000\
\\124\000\106\000\107\000\007\000\
\\029\000\143\000\030\000\005\000\
\\006\000\098\000\097\000\054\000\
\\038\000\057\000\090\000\024\000\
\\055\000\087\000\086\000\058\000\
\\152\000\103\000\003\000\005\000\
\\005\000\141\000\140\000\139\000\
\\138\000\137\000\136\000\161\000\
\\160\000\154\000\159\000\158\000\
\\157\000\156\000\155\000\005\000\
\\025\000\070\000\059\000\031\000\
\\123\000\127\000\125\000\004\000\
\\072\000\009\000\026\000\004\000\
\\026\000\060\000\039\000\015\000\
\\084\000\100\000\017\000\092\000\
\\061\000\088\000\102\000\024\000\
\\117\000\005\000\129\000\135\000\
\\147\000\032\000\002\000\062\000\
\\113\000\003\000\151\000\150\000\
\\063\000\149\000\109\000\110\000\
\\064\000\119\000\065\000\044\000\
\\118\000\120\000\066\000\040\000\
\\091\000\017\000\041\000\024\000\
\\093\000\067\000\153\000\026\000\
\\027\000\046\000\071\000\028\000\
\\116\000\148\000\005\000\042\000\
\\083\000\094\000\080\000\089\000\
\\043\000\068\000\056\000\005\000\
\\131\000\034\000\082\000\081\000\
\\046\000\005\000\111\000\115\000\
\\005\000\069\000\114\000\112\000\
\\000\000"
val gotoT =
"\
\\001\000\188\000\002\000\001\000\000\000\
\\003\000\009\000\005\000\008\000\011\000\007\000\012\000\006\000\
\\028\000\005\000\030\000\004\000\031\000\003\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\003\000\021\000\005\000\008\000\011\000\007\000\012\000\006\000\
\\028\000\005\000\030\000\004\000\031\000\003\000\000\000\
\\000\000\
\\003\000\023\000\005\000\008\000\011\000\007\000\012\000\006\000\
\\028\000\005\000\030\000\004\000\031\000\003\000\000\000\
\\003\000\024\000\005\000\008\000\011\000\007\000\012\000\006\000\
\\028\000\005\000\030\000\004\000\031\000\003\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\008\000\030\000\000\000\
\\027\000\037\000\000\000\
\\000\000\
\\002\000\043\000\000\000\
\\012\000\053\000\016\000\052\000\019\000\051\000\022\000\050\000\
\\023\000\049\000\024\000\048\000\025\000\047\000\026\000\046\000\
\\032\000\045\000\033\000\044\000\000\000\
\\000\000\
\\012\000\006\000\030\000\004\000\031\000\065\000\000\000\
\\000\000\
\\029\000\066\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\016\000\068\000\026\000\046\000\032\000\045\000\033\000\044\000\000\000\
\\016\000\070\000\026\000\046\000\032\000\045\000\033\000\044\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\008\000\078\000\009\000\077\000\000\000\
\\000\000\
\\000\000\
\\027\000\080\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\016\000\084\000\017\000\083\000\026\000\046\000\032\000\045\000\
\\033\000\044\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\018\000\088\000\020\000\087\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\012\000\105\000\000\000\
\\000\000\
\\016\000\107\000\017\000\106\000\026\000\046\000\032\000\045\000\
\\033\000\044\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\032\000\112\000\000\000\
\\018\000\088\000\000\000\
\\000\000\
\\018\000\088\000\000\000\
\\016\000\084\000\017\000\117\000\026\000\046\000\032\000\045\000\
\\033\000\044\000\000\000\
\\026\000\118\000\032\000\045\000\033\000\044\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\006\000\125\000\007\000\124\000\008\000\123\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\018\000\088\000\000\000\
\\000\000\
\\012\000\053\000\016\000\052\000\019\000\051\000\022\000\050\000\
\\023\000\049\000\024\000\048\000\025\000\130\000\026\000\046\000\
\\032\000\045\000\033\000\044\000\000\000\
\\016\000\131\000\026\000\046\000\032\000\045\000\033\000\044\000\000\000\
\\016\000\132\000\026\000\046\000\032\000\045\000\033\000\044\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\016\000\133\000\026\000\046\000\032\000\045\000\033\000\044\000\000\000\
\\014\000\135\000\015\000\134\000\000\000\
\\000\000\
\\000\000\
\\018\000\088\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\016\000\084\000\017\000\140\000\026\000\046\000\032\000\045\000\
\\033\000\044\000\000\000\
\\000\000\
\\000\000\
\\034\000\144\000\000\000\
\\016\000\084\000\017\000\146\000\026\000\046\000\032\000\045\000\
\\033\000\044\000\000\000\
\\034\000\147\000\000\000\
\\000\000\
\\000\000\
\\008\000\078\000\009\000\150\000\000\000\
\\000\000\
\\000\000\
\\008\000\078\000\009\000\152\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\008\000\156\000\000\000\
\\006\000\125\000\007\000\157\000\008\000\123\000\000\000\
\\000\000\
\\016\000\084\000\017\000\158\000\026\000\046\000\032\000\045\000\
\\033\000\044\000\000\000\
\\000\000\
\\018\000\088\000\000\000\
\\018\000\088\000\000\000\
\\018\000\088\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\012\000\053\000\016\000\052\000\019\000\051\000\022\000\050\000\
\\023\000\049\000\024\000\048\000\025\000\162\000\026\000\046\000\
\\032\000\045\000\033\000\044\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\008\000\169\000\000\000\
\\000\000\
\\006\000\125\000\007\000\171\000\008\000\123\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\034\000\173\000\000\000\
\\000\000\
\\020\000\175\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\016\000\177\000\026\000\046\000\032\000\045\000\033\000\044\000\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\000\000\
\\016\000\182\000\026\000\046\000\032\000\045\000\033\000\044\000\000\000\
\\000\000\
\\018\000\088\000\000\000\
\\000\000\
\\000\000\
\\020\000\184\000\000\000\
\\016\000\084\000\017\000\185\000\026\000\046\000\032\000\045\000\
\\033\000\044\000\000\000\
\\018\000\088\000\000\000\
\\000\000\
\\016\000\186\000\026\000\046\000\032\000\045\000\033\000\044\000\000\000\
\\000\000\
\\018\000\088\000\000\000\
\\000\000\
\\000\000\
\"
val numstates = 189
val numrules = 93
val s = ref "" and index = ref 0
val string_to_int = fn () => 
let val i = !index
in index := i+2; Char.ord(String.sub(!s,i)) + Char.ord(String.sub(!s,i+1)) * 256
end
val string_to_list = fn s' =>
    let val len = String.size s'
        fun f () =
           if !index < len then string_to_int() :: f()
           else nil
   in index := 0; s := s'; f ()
   end
val string_to_pairlist = fn (conv_key,conv_entry) =>
     let fun f () =
         case string_to_int()
         of 0 => EMPTY
          | n => PAIR(conv_key (n-1),conv_entry (string_to_int()),f())
     in f
     end
val string_to_pairlist_default = fn (conv_key,conv_entry) =>
    let val conv_row = string_to_pairlist(conv_key,conv_entry)
    in fn () =>
       let val default = conv_entry(string_to_int())
           val row = conv_row()
       in (row,default)
       end
   end
val string_to_table = fn (convert_row,s') =>
    let val len = String.size s'
        fun f ()=
           if !index < len then convert_row() :: f()
           else nil
     in (s := s'; index := 0; f ())
     end
local
  val memo = Array.array(numstates+numrules,ERROR)
  val _ =let fun g i=(Array.update(memo,i,REDUCE(i-numstates)); g(i+1))
       fun f i =
            if i=numstates then g i
            else (Array.update(memo,i,SHIFT (STATE i)); f (i+1))
          in f 0 handle Subscript => ()
          end
in
val entry_to_action = fn 0 => ACCEPT | 1 => ERROR | j => Array.sub(memo,(j-2))
end
val gotoT=Array.fromList(string_to_table(string_to_pairlist(NT,STATE),gotoT))
val actionRows=string_to_table(string_to_pairlist_default(T,entry_to_action),actionRows)
val actionRowNumbers = string_to_list actionRowNumbers
val actionT = let val actionRowLookUp=
let val a=Array.fromList(actionRows) in fn i=>Array.sub(a,i) end
in Array.fromList(map actionRowLookUp actionRowNumbers)
end
in LrTable.mkLrTable {actions=actionT,gotos=gotoT,numRules=numrules,
numStates=numstates,initialState=STATE 0}
end
end
local open Header in
type pos = int
type arg = unit
structure MlyValue = 
struct
datatype svalue = VOID | ntVOID of unit | IDENT of  (string) | STR of  (string) | FLOATNUM of  (real) | INTNUM of  (Int32.int) | tuple of  (string) | floatval of  (real) | intval of  (Int32.int) | thmTimes of  ( ( A.thm * T.time )  list) | thmTime of  (A.thm*T.time) | time of  (T.time) | constdecl of  (unit) | theorem of  (T.theorem) | const of  (T.const) | clauses of  (A.clause list) | clause of  (A.clause list) | provedexp of  (A.clause list) | deltaexp of  (A.clause list) | var of  (A.var) | cmpop of  (T.compare) | constraint of  (A.clause) | binop of  (T.binop) | exps of  (A.exp list) | exp of  (A.exp) | deltavar of  (string) | deltathm of  (A.exp*A.thm) | thms of  (A.thm list) | thm of  (A.thm) | rule of  (A.rule) | rules of  (A.rule list) | typs of  (T.typ list) | typ of  (T.typ) | aggtyps of  ( ( T.agg * T.typ )  list) | aggtyp of  (T.agg*T.typ) | decl of  (T.decl marked) | decls of  (T.decl marked list) | parts of  (T.decl marked list*A.rule list) | uses of  (string list) | program of  (string list*A.program)
end
type svalue = MlyValue.svalue
type result = string list*A.program
end
structure EC=
struct
open LrTable
infix 5 $$
fun x $$ y = y::x
val is_keyword =
fn _ => false
val preferred_change : (term list * term list) list = 
nil
val noShift = 
fn (T 0) => true | _ => false
val showTerminal =
fn (T 0) => "EOF"
  | (T 1) => "SEMI"
  | (T 2) => "INTNUM"
  | (T 3) => "FLOATNUM"
  | (T 4) => "STR"
  | (T 5) => "USE"
  | (T 6) => "IDENT"
  | (T 7) => "RETURN"
  | (T 8) => "DIVIDE"
  | (T 9) => "TIMES"
  | (T 10) => "PLUS"
  | (T 11) => "MINUS"
  | (T 12) => "MOD"
  | (T 13) => "CONS"
  | (T 14) => "NIL"
  | (T 15) => "EXP"
  | (T 16) => "DOTPROD"
  | (T 17) => "DOT"
  | (T 18) => "PROVES"
  | (T 19) => "COMMA"
  | (T 20) => "AT"
  | (T 21) => "HASH"
  | (T 22) => "EQUAL"
  | (T 23) => "NEQUAL"
  | (T 24) => "LESS"
  | (T 25) => "GREATER"
  | (T 26) => "LESSEQ"
  | (T 27) => "GREATEREQ"
  | (T 28) => "LPAREN"
  | (T 29) => "RPAREN"
  | (T 30) => "LBRACKET"
  | (T 31) => "RBRACKET"
  | (T 32) => "TYPE"
  | (T 33) => "INT"
  | (T 34) => "FLOAT"
  | (T 35) => "ADDR"
  | (T 36) => "LIST"
  | (T 37) => "MAX"
  | (T 38) => "MIN"
  | (T 39) => "TRASH"
  | (T 40) => "FIRST"
  | (T 41) => "APPEND"
  | (T 42) => "ARROW"
  | (T 43) => "ANY"
  | (T 44) => "TILDE"
  | (T 45) => "MANUAL"
  | (T 46) => "UNARY"
  | (T 47) => "ASNOP"
  | (T 48) => "EXTERN"
  | (T 49) => "CONST"
  | (T 50) => "FORALL"
  | (T 51) => "VIRTUALNEIGHBOR"
  | (T 52) => "LINEAR"
  | (T 53) => "BANG"
  | (T 54) => "PERSISTENT"
  | (T 55) => "SECS"
  | (T 56) => "MILLISECS"
  | (T 57) => "SET_UNION"
  | (T 58) => "SET"
  | (T 59) => "DELTA"
  | (T 60) => "SCHEDULE"
  | (T 61) => "DELETE"
  | (T 62) => "PROVED"
  | _ => "bogus-term"
local open Header in
val errtermvalue=
fn _ => MlyValue.VOID
end
val terms : term list = nil
 $$ (T 62) $$ (T 61) $$ (T 60) $$ (T 59) $$ (T 58) $$ (T 57) $$ (T 56) $$ (T 55) $$ (T 54) $$ (T 53) $$ (T 52) $$ (T 51) $$ (T 50) $$ (T 49) $$ (T 48) $$ (T 47) $$ (T 46) $$ (T 45) $$ (T 44) $$ (T 43) $$ (T 42) $$ (T 41) $$ (T 40) $$ (T 39) $$ (T 38) $$ (T 37) $$ (T 36) $$ (T 35) $$ (T 34) $$ (T 33) $$ (T 32) $$ (T 31) $$ (T 30) $$ (T 29) $$ (T 28) $$ (T 27) $$ (T 26) $$ (T 25) $$ (T 24) $$ (T 23) $$ (T 22) $$ (T 21) $$ (T 20) $$ (T 19) $$ (T 18) $$ (T 17) $$ (T 16) $$ (T 15) $$ (T 14) $$ (T 13) $$ (T 12) $$ (T 11) $$ (T 10) $$ (T 9) $$ (T 8) $$ (T 7) $$ (T 5) $$ (T 1) $$ (T 0)end
structure Actions =
struct 
exception mlyAction of int
local open Header in
val actions = 
fn (i392,defaultPos,stack,
    (()):arg) =>
case (i392,stack)
of  ( 0, ( ( _, ( _, _, EOF1right)) :: ( _, ( MlyValue.parts parts, _, _)) :: ( _, ( MlyValue.uses uses, uses1left, _)) :: rest671)) => let val  result = MlyValue.program ((*#line 149.49 "simple.grm"*)(uses, A.Program (!consts, #1 parts, #2 parts))(*#line 689.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 0, ( result, uses1left, EOF1right), rest671)
end
|  ( 1, ( ( _, ( MlyValue.uses uses, _, uses1right)) :: ( _, ( MlyValue.STR STR, _, _)) :: ( _, ( _, USE1left, _)) :: rest671)) => let val  result = MlyValue.uses ((*#line 151.49 "simple.grm"*)(STR::uses)(*#line 693.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 1, ( result, USE1left, uses1right), rest671)
end
|  ( 2, ( rest671)) => let val  result = MlyValue.uses ((*#line 152.49 "simple.grm"*)([])(*#line 697.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 1, ( result, defaultPos, defaultPos), rest671)
end
|  ( 3, ( ( _, ( MlyValue.parts parts, _, parts1right)) :: ( _, ( MlyValue.decl decl, decl1left, _)) :: rest671)) => let val  result = MlyValue.parts ((*#line 154.49 "simple.grm"*)(decl::(#1 parts), #2 parts)(*#line 701.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 2, ( result, decl1left, parts1right), rest671)
end
|  ( 4, ( ( _, ( MlyValue.parts parts, _, parts1right)) :: ( _, ( _, constdecl1left, _)) :: rest671)) => let val  result = MlyValue.parts ((*#line 155.49 "simple.grm"*)parts(*#line 705.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 2, ( result, constdecl1left, parts1right), rest671)
end
|  ( 5, ( ( _, ( MlyValue.parts parts, _, parts1right)) :: ( _, ( MlyValue.rule rule, rule1left, _)) :: rest671)) => let val  result = MlyValue.parts ((*#line 156.49 "simple.grm"*)(#1 parts, rule::(#2 parts))(*#line 709.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 2, ( result, rule1left, parts1right), rest671)
end
|  ( 6, ( rest671)) => let val  result = MlyValue.parts ((*#line 157.49 "simple.grm"*)([],[])(*#line 713.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 2, ( result, defaultPos, defaultPos), rest671)
end
|  ( 7, ( ( _, ( MlyValue.decl decl, decl1left, decl1right)) :: rest671)) => let val  result = MlyValue.decls ((*#line 160.49 "simple.grm"*)[decl](*#line 717.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 3, ( result, decl1left, decl1right), rest671)
end
|  ( 8, ( ( _, ( MlyValue.decls decls, _, decls1right)) :: ( _, ( MlyValue.decl decl, decl1left, _)) :: rest671)) => let val  result = MlyValue.decls ((*#line 161.49 "simple.grm"*)decl::decls(*#line 721.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 3, ( result, decl1left, decls1right), rest671)
end
|  ( 9, ( ( _, ( _, _, (DOTright as DOT1right))) :: _ :: ( _, ( MlyValue.aggtyps aggtyps, _, _)) :: _ :: ( _, ( MlyValue.theorem theorem, _, _)) :: ( _, ( _, (TYPEleft as TYPE1left), _)) :: rest671)) => let val  result = MlyValue.decl ((*#line 163.55 "simple.grm"*)mark (T.Decl (theorem, aggtyps, T.NotPersistent), (TYPEleft, DOTright))(*#line 725.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 4, ( result, TYPE1left, DOT1right), rest671)
end
|  ( 10, ( ( _, ( _, _, (DOTright as DOT1right))) :: _ :: ( _, ( MlyValue.aggtyps aggtyps, _, _)) :: _ :: ( _, ( MlyValue.theorem theorem, _, _)) :: _ :: ( _, ( _, (TYPEleft as TYPE1left), _)) :: rest671)) => let val  result = MlyValue.decl ((*#line 164.65 "simple.grm"*)mark (T.Decl (theorem, aggtyps, T.Persistent), (TYPEleft, DOTright))(*#line 729.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 4, ( result, TYPE1left, DOT1right), rest671)
end
|  ( 11, ( ( _, ( _, _, (DOTright as DOT1right))) :: _ :: ( _, ( MlyValue.typs typs, _, _)) :: _ :: ( _, ( MlyValue.IDENT IDENT, _, _)) :: ( _, ( MlyValue.typ typ, _, _)) :: ( _, ( _, (EXTERNleft as EXTERN1left), _)) :: rest671)) => let val  result = MlyValue.decl ((*#line 165.56 "simple.grm"*)mark (T.Extern (IDENT, typ, typs), (EXTERNleft, DOTright))(*#line 733.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 4, ( result, EXTERN1left, DOT1right), rest671)
end
|  ( 12, ( ( _, ( _, _, (DOTright as DOT1right))) :: _ :: _ :: ( _, ( MlyValue.IDENT IDENT, _, _)) :: ( _, ( MlyValue.typ typ, _, _)) :: ( _, ( _, (EXTERNleft as EXTERN1left), _)) :: rest671)) => let val  result = MlyValue.decl ((*#line 166.52 "simple.grm"*)mark (T.Extern (IDENT, typ, []), (EXTERNleft, DOTright))(*#line 737.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 4, ( result, EXTERN1left, DOT1right), rest671)
end
|  ( 13, ( ( _, ( _, _, (DOTright as DOT1right))) :: ( _, ( MlyValue.IDENT IDENT, _, _)) :: _ :: ( _, ( _, (EXTERNleft as EXTERN1left), _)) :: rest671)) => let val  result = MlyValue.decl ((*#line 167.52 "simple.grm"*)mark (T.Type IDENT, (EXTERNleft, DOTright))(*#line 741.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 4, ( result, EXTERN1left, DOT1right), rest671)
end
|  ( 14, ( ( _, ( MlyValue.IDENT IDENT, IDENT1left, IDENT1right)) :: rest671)) => let val  result = MlyValue.theorem ((*#line 169.31 "simple.grm"*)T.Regular IDENT(*#line 745.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 26, ( result, IDENT1left, IDENT1right), rest671)
end
|  ( 15, ( ( _, ( MlyValue.IDENT IDENT, _, IDENT1right)) :: ( _, ( _, VIRTUALNEIGHBOR1left, _)) :: rest671)) => let val  result = MlyValue.theorem ((*#line 170.49 "simple.grm"*)T.Routing IDENT(*#line 749.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 26, ( result, VIRTUALNEIGHBOR1left, IDENT1right), rest671)
end
|  ( 16, ( ( _, ( MlyValue.IDENT IDENT, _, IDENT1right)) :: ( _, ( _, LINEAR1left, _)) :: rest671)) => let val  result = MlyValue.theorem ((*#line 171.49 "simple.grm"*)T.Linear IDENT(*#line 753.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 26, ( result, LINEAR1left, IDENT1right), rest671)
end
|  ( 17, ( ( _, ( MlyValue.aggtyp aggtyp, aggtyp1left, aggtyp1right)) :: rest671)) => let val  result = MlyValue.aggtyps ((*#line 173.49 "simple.grm"*)[aggtyp](*#line 757.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 6, ( result, aggtyp1left, aggtyp1right), rest671)
end
|  ( 18, ( ( _, ( MlyValue.aggtyps aggtyps, _, aggtyps1right)) :: _ :: ( _, ( MlyValue.aggtyp aggtyp, aggtyp1left, _)) :: rest671)) => let val  result = MlyValue.aggtyps ((*#line 174.49 "simple.grm"*)aggtyp::aggtyps(*#line 761.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 6, ( result, aggtyp1left, aggtyps1right), rest671)
end
|  ( 19, ( ( _, ( MlyValue.typ typ, typ1left, typ1right)) :: rest671)) => let val  result = MlyValue.typs ((*#line 176.49 "simple.grm"*)[typ](*#line 765.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 8, ( result, typ1left, typ1right), rest671)
end
|  ( 20, ( ( _, ( MlyValue.typs typs, _, typs1right)) :: _ :: ( _, ( MlyValue.typ typ, typ1left, _)) :: rest671)) => let val  result = MlyValue.typs ((*#line 177.49 "simple.grm"*)typ::typs(*#line 769.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 8, ( result, typ1left, typs1right), rest671)
end
|  ( 21, ( ( _, ( MlyValue.typ typ, typ1left, typ1right)) :: rest671)) => let val  result = MlyValue.aggtyp ((*#line 179.49 "simple.grm"*)(T.AggNone, typ)(*#line 773.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 5, ( result, typ1left, typ1right), rest671)
end
|  ( 22, ( ( _, ( MlyValue.typ typ, _, typ1right)) :: ( _, ( MlyValue.IDENT IDENT, IDENT1left, _)) :: rest671)) => let val  result = MlyValue.aggtyp ((*#line 180.49 "simple.grm"*)(T.AggDefined (IDENT, typ), typ)(*#line 777.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 5, ( result, IDENT1left, typ1right), rest671)
end
|  ( 23, ( ( _, ( MlyValue.typ typ2, _, typ2right)) :: ( _, ( MlyValue.IDENT IDENT, _, _)) :: ( _, ( MlyValue.typ typ1, typ1left, _)) :: rest671)) => let val  result = MlyValue.aggtyp ((*#line 181.49 "simple.grm"*)(T.AggDefined (IDENT, typ2), typ1)(*#line 781.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 5, ( result, typ1left, typ2right), rest671)
end
|  ( 24, ( ( _, ( _, INT1left, INT1right)) :: rest671)) => let val  result = MlyValue.typ ((*#line 183.49 "simple.grm"*)T.TypInt(*#line 785.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 7, ( result, INT1left, INT1right), rest671)
end
|  ( 25, ( ( _, ( _, ADDR1left, ADDR1right)) :: rest671)) => let val  result = MlyValue.typ ((*#line 184.49 "simple.grm"*)T.TypAddr(*#line 789.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 7, ( result, ADDR1left, ADDR1right), rest671)
end
|  ( 26, ( ( _, ( _, _, LIST1right)) :: ( _, ( MlyValue.typ typ, typ1left, _)) :: rest671)) => let val  result = MlyValue.typ ((*#line 185.49 "simple.grm"*)T.TypList typ(*#line 793.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 7, ( result, typ1left, LIST1right), rest671)
end
|  ( 27, ( ( _, ( _, _, SET1right)) :: ( _, ( MlyValue.typ typ, typ1left, _)) :: rest671)) => let val  result = MlyValue.typ ((*#line 186.49 "simple.grm"*)T.TypSet typ(*#line 797.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 7, ( result, typ1left, SET1right), rest671)
end
|  ( 28, ( ( _, ( _, FLOAT1left, FLOAT1right)) :: rest671)) => let val  result = MlyValue.typ ((*#line 187.49 "simple.grm"*)T.TypFloat(*#line 801.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 7, ( result, FLOAT1left, FLOAT1right), rest671)
end
|  ( 29, ( ( _, ( _, _, RPAREN1right)) :: ( _, ( MlyValue.typs typs, _, _)) :: ( _, ( _, LPAREN1left, _)) :: rest671)) => let val  result = MlyValue.typ ((*#line 188.49 "simple.grm"*)T.TypTuple typs(*#line 805.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 7, ( result, LPAREN1left, RPAREN1right), rest671)
end
|  ( 30, ( ( _, ( MlyValue.IDENT IDENT, IDENT1left, IDENT1right)) :: rest671)) => let val  result = MlyValue.typ ((*#line 189.49 "simple.grm"*)T.TypUserDefined IDENT(*#line 809.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 7, ( result, IDENT1left, IDENT1right), rest671)
end
|  ( 31, ( ( _, ( MlyValue.rule rule, rule1left, rule1right)) :: rest671)) => let val  result = MlyValue.rules ((*#line 191.49 "simple.grm"*)[rule](*#line 813.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 9, ( result, rule1left, rule1right), rest671)
end
|  ( 32, ( ( _, ( MlyValue.rules rules, _, rules1right)) :: ( _, ( MlyValue.rule rule, rule1left, _)) :: rest671)) => let val  result = MlyValue.rules ((*#line 192.49 "simple.grm"*)rule::rules(*#line 817.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 9, ( result, rule1left, rules1right), rest671)
end
|  ( 33, ( ( _, ( _, _, (DOTright as DOT1right))) :: ( _, ( MlyValue.clauses clauses, _, _)) :: _ :: ( _, ( MlyValue.thmTimes thmTimes, (thmTimesleft as thmTimes1left), _)) :: rest671)) => let val  result = MlyValue.rule ((*#line 194.49 "simple.grm"*)mark (A.Rule (thmTimes, clauses), (thmTimesleft, DOTright))(*#line 821.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 10, ( result, thmTimes1left, DOT1right), rest671)
end
|  ( 34, ( ( _, ( _, _, (DOTright as DOT1right))) :: ( _, ( MlyValue.thmTimes thmTimes, (thmTimesleft as thmTimes1left), _)) :: rest671)) => let val  result = MlyValue.rule ((*#line 195.49 "simple.grm"*)mark (A.Rule (thmTimes, []), (thmTimesleft, DOTright))(*#line 825.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 10, ( result, thmTimes1left, DOT1right), rest671)
end
|  ( 35, ( ( _, ( MlyValue.thmTime thmTime, thmTime1left, thmTime1right)) :: rest671)) => let val  result = MlyValue.thmTimes ((*#line 198.49 "simple.grm"*)[thmTime](*#line 829.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 30, ( result, thmTime1left, thmTime1right), rest671)
end
|  ( 36, ( ( _, ( MlyValue.thmTimes thmTimes, _, thmTimes1right)) :: _ :: ( _, ( MlyValue.thmTime thmTime, thmTime1left, _)) :: rest671)) => let val  result = MlyValue.thmTimes ((*#line 199.49 "simple.grm"*)thmTime::thmTimes(*#line 833.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 30, ( result, thmTime1left, thmTimes1right), rest671)
end
|  ( 37, ( ( _, ( MlyValue.time time, _, time1right)) :: _ :: ( _, ( MlyValue.thm thm, thm1left, _)) :: rest671)) => let val  result = MlyValue.thmTime ((*#line 201.49 "simple.grm"*)(thm, time)(*#line 837.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 29, ( result, thm1left, time1right), rest671)
end
|  ( 38, ( ( _, ( MlyValue.thm thm, thm1left, thm1right)) :: rest671)) => let val  result = MlyValue.thmTime ((*#line 202.49 "simple.grm"*)(thm, T.TimeNow)(*#line 841.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 29, ( result, thm1left, thm1right), rest671)
end
|  ( 39, ( ( _, ( _, _, MILLISECS1right)) :: ( _, ( MlyValue.intval intval, _, _)) :: ( _, ( _, PLUS1left, _)) :: rest671)) => let val  result = MlyValue.time ((*#line 204.49 "simple.grm"*)T.TimeInMS intval(*#line 845.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 28, ( result, PLUS1left, MILLISECS1right), rest671)
end
|  ( 40, ( ( _, ( _, _, SECS1right)) :: ( _, ( MlyValue.intval intval, _, _)) :: ( _, ( _, PLUS1left, _)) :: rest671)) => let val  result = MlyValue.time ((*#line 205.49 "simple.grm"*)T.TimeInMS (intval * 1000)(*#line 849.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 28, ( result, PLUS1left, SECS1right), rest671)
end
|  ( 41, ( ( _, ( MlyValue.exp exp, _, (expright as exp1right))) :: ( _, ( MlyValue.cmpop cmpop, _, _)) :: ( _, ( _, _, RPARENright)) :: ( _, ( MlyValue.deltathm deltathm, _, _)) :: _ :: ( _, ( _, (DELTAleft as DELTA1left), _)) :: rest671)) => let val  result = MlyValue.deltaexp ((*#line 207.55 "simple.grm"*)[mark (A.ThmClause (#2 deltathm), (DELTAleft, RPARENright)),
																mark (A.Constraint (cmpop, #1 deltathm, exp), (DELTAleft, expright))](*#line 853.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 21, ( result, DELTA1left, exp1right), rest671)
end
|  ( 42, ( ( _, ( _, _, (RPARENright as RPAREN1right))) :: ( _, ( MlyValue.exps exps, _, _)) :: _ :: ( _, ( MlyValue.IDENT IDENT, IDENTleft, _)) :: _ :: ( _, ( MlyValue.deltavar deltavar, (deltavarleft as deltavar1left), deltavarright)) :: rest671)) => let val  result = MlyValue.deltathm ((*#line 210.55 "simple.grm"*)(mark (A.ExpVar (A.Var deltavar), (deltavarleft, deltavarright)),
																													mark (A.Thm(T.Delta (IDENT, findArgument (deltavar, exps)), exps), (IDENTleft, RPARENright)))(*#line 858.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 13, ( result, deltavar1left, RPAREN1right), rest671)
end
|  ( 43, ( ( _, ( MlyValue.IDENT IDENT, IDENT1left, IDENT1right)) :: rest671)) => let val  result = MlyValue.deltavar ((*#line 213.31 "simple.grm"*)IDENT(*#line 863.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 14, ( result, IDENT1left, IDENT1right), rest671)
end
|  ( 44, ( ( _, ( MlyValue.exp exp2, _, exp2right)) :: ( _, ( MlyValue.cmpop cmpop, _, _)) :: _ :: ( _, ( MlyValue.tuple tuple, tupleleft, tupleright)) :: _ :: ( _, ( MlyValue.exp exp1, _, _)) :: _ :: ( _, ( _, (PROVEDleft as PROVED1left), _)) :: rest671)) => let val  result = MlyValue.provedexp ((*#line 215.62 "simple.grm"*)let
                                                               val generatedVar = generateNewVar tuple
                                                               val generatedVar = mark (generatedVar, (tupleleft, tupleright))
                                                             in
                                                               [
                                                                     mark (A.ThmClause
                                                                              (mark (A.Thm(T.Proved tuple,
                                                                                    [exp1, mark (A.ExpType tuple, (tupleleft, tupleright)),
                                                                                       generatedVar]),
                                                                                          (PROVEDleft, exp2right))),
                                                                                          (PROVEDleft, exp2right)),
                                                                     mark (A.Constraint (cmpop, generatedVar, exp2), (PROVEDleft, exp2right))]
                                                             end(*#line 867.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 22, ( result, PROVED1left, exp2right), rest671)
end
|  ( 45, ( ( _, ( _, _, (RPARENright as RPAREN1right))) :: ( _, ( MlyValue.exp exp2, _, _)) :: _ :: ( _, ( MlyValue.tuple tuple, tupleleft, tupleright)) :: _ :: ( _, ( MlyValue.exp exp1, _, _)) :: _ :: ( _, ( _, (SCHEDULEleft as SCHEDULE1left), _)) :: rest671)) => let val  result = MlyValue.thm ((*#line 229.64 "simple.grm"*)mark (A.Thm(T.Schedule tuple,
												[exp1, mark (A.ExpType tuple, (tupleleft, tupleright)), exp2]), (SCHEDULEleft, RPARENright))(*#line 883.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 11, ( result, SCHEDULE1left, RPAREN1right), rest671)
end
|  ( 46, ( ( _, ( _, _, (RPARENright as RPAREN1right))) :: ( _, ( MlyValue.tuple tuple, tupleleft, tupleright)) :: _ :: ( _, ( MlyValue.exp exp, _, _)) :: _ :: ( _, ( _, (DELETEleft as DELETE1left), _)) :: rest671)) => let val  result = MlyValue.thm ((*#line 231.47 "simple.grm"*)mark (A.Thm(T.Delete tuple,
			                           [exp, mark (A.ExpType tuple, (tupleleft, tupleright))]), (DELETEleft, RPARENright))(*#line 888.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 11, ( result, DELETE1left, RPAREN1right), rest671)
end
|  ( 47, ( ( _, ( _, _, (RPARENright as RPAREN1right))) :: ( _, ( MlyValue.exps exps, _, _)) :: _ :: ( _, ( MlyValue.IDENT IDENT, (IDENTleft as IDENT1left), _)) :: rest671)) => let val  result = MlyValue.thm ((*#line 233.51 "simple.grm"*)mark (A.Thm(T.Regular IDENT, exps), (IDENTleft, RPARENright))(*#line 893.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 11, ( result, IDENT1left, RPAREN1right), rest671)
end
|  ( 48, ( ( _, ( _, _, (RPARENright as RPAREN1right))) :: ( _, ( MlyValue.exps exps, _, _)) :: _ :: ( _, ( MlyValue.IDENT IDENT, IDENTleft, _)) :: ( _, ( _, BANG1left, _)) :: rest671)) => let val  result = MlyValue.thm ((*#line 234.57 "simple.grm"*)mark (A.Thm(T.Linear IDENT, exps), (IDENTleft, RPARENright))(*#line 897.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 11, ( result, BANG1left, RPAREN1right), rest671)
end
|  ( 49, ( ( _, ( MlyValue.thm thm, thm1left, thm1right)) :: rest671)) => let val  result = MlyValue.thms ((*#line 236.49 "simple.grm"*)[thm](*#line 901.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 12, ( result, thm1left, thm1right), rest671)
end
|  ( 50, ( ( _, ( MlyValue.thms thms, _, thms1right)) :: _ :: ( _, ( MlyValue.thm thm, thm1left, _)) :: rest671)) => let val  result = MlyValue.thms ((*#line 237.49 "simple.grm"*)thm::thms(*#line 905.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 12, ( result, thm1left, thms1right), rest671)
end
|  ( 51, ( ( _, ( MlyValue.IDENT IDENT, IDENT1left, IDENT1right)) :: rest671)) => let val  result = MlyValue.tuple ((*#line 239.31 "simple.grm"*)IDENT(*#line 909.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 33, ( result, IDENT1left, IDENT1right), rest671)
end
|  ( 52, ( ( _, ( _, _, DOT1right)) :: ( _, ( MlyValue.const const, _, _)) :: _ :: ( _, ( MlyValue.IDENT IDENT, _, _)) :: ( _, ( _, CONST1left, _)) :: rest671)) => let val  result = MlyValue.constdecl ((*#line 241.49 "simple.grm"*)consts := (IDENT, const)::(!consts)(*#line 913.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 27, ( result, CONST1left, DOT1right), rest671)
end
|  ( 53, ( ( _, ( MlyValue.intval intval, intval1left, intval1right)) :: rest671)) => let val  result = MlyValue.const ((*#line 243.49 "simple.grm"*)T.ConstInt(intval)(*#line 917.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 25, ( result, intval1left, intval1right), rest671)
end
|  ( 54, ( ( _, ( MlyValue.floatval floatval, floatval1left, floatval1right)) :: rest671)) => let val  result = MlyValue.const ((*#line 244.49 "simple.grm"*)T.ConstFloat(floatval)(*#line 921.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 25, ( result, floatval1left, floatval1right), rest671)
end
|  ( 55, ( ( _, ( MlyValue.INTNUM INTNUM, _, INTNUM1right)) :: ( _, ( _, HASH1left, _)) :: rest671)) => let val  result = MlyValue.const ((*#line 245.49 "simple.grm"*)T.ConstAddr INTNUM(*#line 925.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 25, ( result, HASH1left, INTNUM1right), rest671)
end
|  ( 56, ( ( _, ( MlyValue.INTNUM INTNUM, INTNUM1left, INTNUM1right)) :: rest671)) => let val  result = MlyValue.intval ((*#line 247.49 "simple.grm"*)INTNUM(*#line 929.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 31, ( result, INTNUM1left, INTNUM1right), rest671)
end
|  ( 57, ( ( _, ( MlyValue.INTNUM INTNUM, _, INTNUM1right)) :: ( _, ( _, MINUS1left, _)) :: rest671)) => let val  result = MlyValue.intval ((*#line 248.49 "simple.grm"*)~INTNUM(*#line 933.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 31, ( result, MINUS1left, INTNUM1right), rest671)
end
|  ( 58, ( ( _, ( MlyValue.FLOATNUM FLOATNUM, FLOATNUM1left, FLOATNUM1right)) :: rest671)) => let val  result = MlyValue.floatval ((*#line 250.49 "simple.grm"*)FLOATNUM(*#line 937.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 32, ( result, FLOATNUM1left, FLOATNUM1right), rest671)
end
|  ( 59, ( ( _, ( MlyValue.FLOATNUM FLOATNUM, _, FLOATNUM1right)) :: ( _, ( _, MINUS1left, _)) :: rest671)) => let val  result = MlyValue.floatval ((*#line 251.49 "simple.grm"*)~FLOATNUM(*#line 941.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 32, ( result, MINUS1left, FLOATNUM1right), rest671)
end
|  ( 60, ( ( _, ( MlyValue.clause clause, clause1left, clause1right)) :: rest671)) => let val  result = MlyValue.clauses ((*#line 253.49 "simple.grm"*)clause(*#line 945.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 24, ( result, clause1left, clause1right), rest671)
end
|  ( 61, ( ( _, ( MlyValue.clauses clauses, _, clauses1right)) :: _ :: ( _, ( MlyValue.clause clause, clause1left, _)) :: rest671)) => let val  result = MlyValue.clauses ((*#line 254.49 "simple.grm"*)clause@clauses(*#line 949.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 24, ( result, clause1left, clauses1right), rest671)
end
|  ( 62, ( ( _, ( MlyValue.thm thm, (thmleft as thm1left), (thmright as thm1right))) :: rest671)) => let val  result = MlyValue.clause ((*#line 256.51 "simple.grm"*)[mark (A.ThmClause (thm), (thmleft, thmright))](*#line 953.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 23, ( result, thm1left, thm1right), rest671)
end
|  ( 63, ( ( _, ( _, _, (RBRACKETright as RBRACKET1right))) :: ( _, ( MlyValue.clauses clauses, _, _)) :: _ :: ( _, ( MlyValue.thm thm, _, _)) :: ( _, ( _, (FORALLleft as FORALL1left), _)) :: rest671)) => let val  result = MlyValue.clause ((*#line 257.52 "simple.grm"*)[mark (A.Forall (thm, clauses), (FORALLleft, RBRACKETright))](*#line 957.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 23, ( result, FORALL1left, RBRACKET1right), rest671)
end
|  ( 64, ( ( _, ( MlyValue.constraint constraint, constraint1left, constraint1right)) :: rest671)) => let val  result = MlyValue.clause ((*#line 258.51 "simple.grm"*)[constraint](*#line 961.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 23, ( result, constraint1left, constraint1right), rest671)
end
|  ( 65, ( ( _, ( MlyValue.deltaexp deltaexp, deltaexp1left, deltaexp1right)) :: rest671)) => let val  result = MlyValue.clause ((*#line 259.45 "simple.grm"*)deltaexp(*#line 965.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 23, ( result, deltaexp1left, deltaexp1right), rest671)
end
|  ( 66, ( ( _, ( MlyValue.provedexp provedexp, provedexp1left, provedexp1right)) :: rest671)) => let val  result = MlyValue.clause ((*#line 260.47 "simple.grm"*)provedexp(*#line 969.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 23, ( result, provedexp1left, provedexp1right), rest671)
end
|  ( 67, ( ( _, ( MlyValue.exp exp2, _, exp2right)) :: ( _, ( MlyValue.cmpop cmpop, _, _)) :: ( _, ( MlyValue.exp exp1, exp1left, _)) :: rest671)) => let val  result = MlyValue.constraint ((*#line 262.49 "simple.grm"*)mark (A.Constraint (cmpop, exp1, exp2), (exp1left, exp2right))(*#line 973.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 18, ( result, exp1left, exp2right), rest671)
end
|  ( 68, ( ( _, ( _, EQUAL1left, EQUAL1right)) :: rest671)) => let val  result = MlyValue.cmpop ((*#line 264.33 "simple.grm"*)T.EQ(*#line 977.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 19, ( result, EQUAL1left, EQUAL1right), rest671)
end
|  ( 69, ( ( _, ( _, NEQUAL1left, NEQUAL1right)) :: rest671)) => let val  result = MlyValue.cmpop ((*#line 265.31 "simple.grm"*)T.NEQ(*#line 981.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 19, ( result, NEQUAL1left, NEQUAL1right), rest671)
end
|  ( 70, ( ( _, ( _, LESS1left, LESS1right)) :: rest671)) => let val  result = MlyValue.cmpop ((*#line 266.30 "simple.grm"*)T.LESS(*#line 985.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 19, ( result, LESS1left, LESS1right), rest671)
end
|  ( 71, ( ( _, ( _, GREATER1left, GREATER1right)) :: rest671)) => let val  result = MlyValue.cmpop ((*#line 267.31 "simple.grm"*)T.GREATER(*#line 989.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 19, ( result, GREATER1left, GREATER1right), rest671)
end
|  ( 72, ( ( _, ( _, LESSEQ1left, LESSEQ1right)) :: rest671)) => let val  result = MlyValue.cmpop ((*#line 268.31 "simple.grm"*)T.LESSEQ(*#line 993.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 19, ( result, LESSEQ1left, LESSEQ1right), rest671)
end
|  ( 73, ( ( _, ( _, GREATEREQ1left, GREATEREQ1right)) :: rest671)) => let val  result = MlyValue.cmpop ((*#line 269.32 "simple.grm"*)T.GREATEREQ(*#line 997.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 19, ( result, GREATEREQ1left, GREATEREQ1right), rest671)
end
|  ( 74, ( ( _, ( MlyValue.IDENT IDENT, (IDENTleft as IDENT1left), (IDENTright as IDENT1right))) :: rest671)) => let val  result = MlyValue.exp ((*#line 271.49 "simple.grm"*)mark (wrapVar IDENT, (IDENTleft, IDENTright))(*#line 1001.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 15, ( result, IDENT1left, IDENT1right), rest671)
end
|  ( 75, ( ( _, ( _, (ANYleft as ANY1left), (ANYright as ANY1right))) :: rest671)) => let val  result = MlyValue.exp ((*#line 272.49 "simple.grm"*)mark (A.ExpVar A.VarAny, (ANYleft, ANYright))(*#line 1005.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 15, ( result, ANY1left, ANY1right), rest671)
end
|  ( 76, ( ( _, ( MlyValue.const const, (constleft as const1left), (constright as const1right))) :: rest671)) => let val  result = MlyValue.exp ((*#line 273.49 "simple.grm"*)mark (A.ExpConst(const), (constleft, constright))(*#line 1009.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 15, ( result, const1left, const1right), rest671)
end
|  ( 77, ( ( _, ( _, (NILleft as NIL1left), (NILright as NIL1right))) :: rest671)) => let val  result = MlyValue.exp ((*#line 274.47 "simple.grm"*)mark (A.ExpNil, (NILleft, NILright))(*#line 1013.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 15, ( result, NIL1left, NIL1right), rest671)
end
|  ( 78, ( ( _, ( MlyValue.exp exp2, _, exp2right)) :: ( _, ( MlyValue.binop binop, _, _)) :: ( _, ( MlyValue.exp exp1, exp1left, _)) :: rest671)) => let val  result = MlyValue.exp ((*#line 275.47 "simple.grm"*)mark (A.ExpBinop(binop, exp1, exp2), (exp1left, exp2right))(*#line 1017.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 15, ( result, exp1left, exp2right), rest671)
end
|  ( 79, ( ( _, ( _, _, (RPARENright as RPAREN1right))) :: ( _, ( MlyValue.exps exps, _, _)) :: _ :: ( _, ( MlyValue.IDENT IDENT, (IDENTleft as IDENT1left), _)) :: rest671)) => let val  result = MlyValue.exp ((*#line 276.49 "simple.grm"*)mark (A.ExpExtern(IDENT, exps), (IDENTleft, RPARENright))(*#line 1021.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 15, ( result, IDENT1left, RPAREN1right), rest671)
end
|  ( 80, ( ( _, ( _, _, (RPARENright as RPAREN1right))) :: _ :: ( _, ( MlyValue.IDENT IDENT, (IDENTleft as IDENT1left), _)) :: rest671)) => let val  result = MlyValue.exp ((*#line 277.49 "simple.grm"*)mark (A.ExpExtern(IDENT, []), (IDENTleft, RPARENright))(*#line 1025.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 15, ( result, IDENT1left, RPAREN1right), rest671)
end
|  ( 81, ( ( _, ( _, _, RPAREN1right)) :: ( _, ( MlyValue.exp exp, _, _)) :: ( _, ( _, LPAREN1left, _)) :: rest671)) => let val  result = MlyValue.exp ((*#line 278.49 "simple.grm"*)exp(*#line 1029.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 15, ( result, LPAREN1left, RPAREN1right), rest671)
end
|  ( 82, ( ( _, ( _, _, (RPARENright as RPAREN1right))) :: ( _, ( MlyValue.exps exps, _, _)) :: ( _, ( _, (LPARENleft as LPAREN1left), _)) :: rest671)) => let val  result = MlyValue.exp ((*#line 279.49 "simple.grm"*)mark ((A.ExpTuple exps), (LPARENleft, RPARENright))(*#line 1033.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 15, ( result, LPAREN1left, RPAREN1right), rest671)
end
|  ( 83, ( ( _, ( MlyValue.exp exp, exp1left, exp1right)) :: rest671)) => let val  result = MlyValue.exps ((*#line 281.49 "simple.grm"*)[exp](*#line 1037.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 16, ( result, exp1left, exp1right), rest671)
end
|  ( 84, ( ( _, ( MlyValue.exps exps, _, exps1right)) :: _ :: ( _, ( MlyValue.exp exp, exp1left, _)) :: rest671)) => let val  result = MlyValue.exps ((*#line 282.49 "simple.grm"*)exp::exps(*#line 1041.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 16, ( result, exp1left, exps1right), rest671)
end
|  ( 85, ( ( _, ( _, CONS1left, CONS1right)) :: rest671)) => let val  result = MlyValue.binop ((*#line 284.33 "simple.grm"*)T.CONS(*#line 1045.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 17, ( result, CONS1left, CONS1right), rest671)
end
|  ( 86, ( ( _, ( _, DIVIDE1left, DIVIDE1right)) :: rest671)) => let val  result = MlyValue.binop ((*#line 285.31 "simple.grm"*)T.DIVIDE(*#line 1049.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 17, ( result, DIVIDE1left, DIVIDE1right), rest671)
end
|  ( 87, ( ( _, ( _, TIMES1left, TIMES1right)) :: rest671)) => let val  result = MlyValue.binop ((*#line 286.30 "simple.grm"*)T.TIMES(*#line 1053.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 17, ( result, TIMES1left, TIMES1right), rest671)
end
|  ( 88, ( ( _, ( _, PLUS1left, PLUS1right)) :: rest671)) => let val  result = MlyValue.binop ((*#line 287.30 "simple.grm"*)T.PLUS(*#line 1057.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 17, ( result, PLUS1left, PLUS1right), rest671)
end
|  ( 89, ( ( _, ( _, MINUS1left, MINUS1right)) :: rest671)) => let val  result = MlyValue.binop ((*#line 288.30 "simple.grm"*)T.MINUS(*#line 1061.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 17, ( result, MINUS1left, MINUS1right), rest671)
end
|  ( 90, ( ( _, ( _, MOD1left, MOD1right)) :: rest671)) => let val  result = MlyValue.binop ((*#line 289.29 "simple.grm"*)T.MOD(*#line 1065.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 17, ( result, MOD1left, MOD1right), rest671)
end
|  ( 91, ( ( _, ( _, EXP1left, EXP1right)) :: rest671)) => let val  result = MlyValue.binop ((*#line 290.29 "simple.grm"*)T.EXP(*#line 1069.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 17, ( result, EXP1left, EXP1right), rest671)
end
|  ( 92, ( ( _, ( _, DOTPROD1left, DOTPROD1right)) :: rest671)) => let val  result = MlyValue.binop ((*#line 291.31 "simple.grm"*)T.DOTPROD(*#line 1073.1 "simple.grm.sml"*)
)
 in ( LrTable.NT 17, ( result, DOTPROD1left, DOTPROD1right), rest671)
end
| _ => raise (mlyAction i392)
end
val void = MlyValue.VOID
val extract = fn a => (fn MlyValue.program x => x
| _ => let exception ParseInternal
	in raise ParseInternal end) a 
end
end
structure Tokens : Simple_TOKENS =
struct
type svalue = ParserData.svalue
type ('a,'b) token = ('a,'b) Token.token
fun EOF (p1,p2) = Token.TOKEN (ParserData.LrTable.T 0,(ParserData.MlyValue.VOID,p1,p2))
fun SEMI (p1,p2) = Token.TOKEN (ParserData.LrTable.T 1,(ParserData.MlyValue.VOID,p1,p2))
fun INTNUM (i,p1,p2) = Token.TOKEN (ParserData.LrTable.T 2,(ParserData.MlyValue.INTNUM i,p1,p2))
fun FLOATNUM (i,p1,p2) = Token.TOKEN (ParserData.LrTable.T 3,(ParserData.MlyValue.FLOATNUM i,p1,p2))
fun STR (i,p1,p2) = Token.TOKEN (ParserData.LrTable.T 4,(ParserData.MlyValue.STR i,p1,p2))
fun USE (p1,p2) = Token.TOKEN (ParserData.LrTable.T 5,(ParserData.MlyValue.VOID,p1,p2))
fun IDENT (i,p1,p2) = Token.TOKEN (ParserData.LrTable.T 6,(ParserData.MlyValue.IDENT i,p1,p2))
fun RETURN (p1,p2) = Token.TOKEN (ParserData.LrTable.T 7,(ParserData.MlyValue.VOID,p1,p2))
fun DIVIDE (p1,p2) = Token.TOKEN (ParserData.LrTable.T 8,(ParserData.MlyValue.VOID,p1,p2))
fun TIMES (p1,p2) = Token.TOKEN (ParserData.LrTable.T 9,(ParserData.MlyValue.VOID,p1,p2))
fun PLUS (p1,p2) = Token.TOKEN (ParserData.LrTable.T 10,(ParserData.MlyValue.VOID,p1,p2))
fun MINUS (p1,p2) = Token.TOKEN (ParserData.LrTable.T 11,(ParserData.MlyValue.VOID,p1,p2))
fun MOD (p1,p2) = Token.TOKEN (ParserData.LrTable.T 12,(ParserData.MlyValue.VOID,p1,p2))
fun CONS (p1,p2) = Token.TOKEN (ParserData.LrTable.T 13,(ParserData.MlyValue.VOID,p1,p2))
fun NIL (p1,p2) = Token.TOKEN (ParserData.LrTable.T 14,(ParserData.MlyValue.VOID,p1,p2))
fun EXP (p1,p2) = Token.TOKEN (ParserData.LrTable.T 15,(ParserData.MlyValue.VOID,p1,p2))
fun DOTPROD (p1,p2) = Token.TOKEN (ParserData.LrTable.T 16,(ParserData.MlyValue.VOID,p1,p2))
fun DOT (p1,p2) = Token.TOKEN (ParserData.LrTable.T 17,(ParserData.MlyValue.VOID,p1,p2))
fun PROVES (p1,p2) = Token.TOKEN (ParserData.LrTable.T 18,(ParserData.MlyValue.VOID,p1,p2))
fun COMMA (p1,p2) = Token.TOKEN (ParserData.LrTable.T 19,(ParserData.MlyValue.VOID,p1,p2))
fun AT (p1,p2) = Token.TOKEN (ParserData.LrTable.T 20,(ParserData.MlyValue.VOID,p1,p2))
fun HASH (p1,p2) = Token.TOKEN (ParserData.LrTable.T 21,(ParserData.MlyValue.VOID,p1,p2))
fun EQUAL (p1,p2) = Token.TOKEN (ParserData.LrTable.T 22,(ParserData.MlyValue.VOID,p1,p2))
fun NEQUAL (p1,p2) = Token.TOKEN (ParserData.LrTable.T 23,(ParserData.MlyValue.VOID,p1,p2))
fun LESS (p1,p2) = Token.TOKEN (ParserData.LrTable.T 24,(ParserData.MlyValue.VOID,p1,p2))
fun GREATER (p1,p2) = Token.TOKEN (ParserData.LrTable.T 25,(ParserData.MlyValue.VOID,p1,p2))
fun LESSEQ (p1,p2) = Token.TOKEN (ParserData.LrTable.T 26,(ParserData.MlyValue.VOID,p1,p2))
fun GREATEREQ (p1,p2) = Token.TOKEN (ParserData.LrTable.T 27,(ParserData.MlyValue.VOID,p1,p2))
fun LPAREN (p1,p2) = Token.TOKEN (ParserData.LrTable.T 28,(ParserData.MlyValue.VOID,p1,p2))
fun RPAREN (p1,p2) = Token.TOKEN (ParserData.LrTable.T 29,(ParserData.MlyValue.VOID,p1,p2))
fun LBRACKET (p1,p2) = Token.TOKEN (ParserData.LrTable.T 30,(ParserData.MlyValue.VOID,p1,p2))
fun RBRACKET (p1,p2) = Token.TOKEN (ParserData.LrTable.T 31,(ParserData.MlyValue.VOID,p1,p2))
fun TYPE (p1,p2) = Token.TOKEN (ParserData.LrTable.T 32,(ParserData.MlyValue.VOID,p1,p2))
fun INT (p1,p2) = Token.TOKEN (ParserData.LrTable.T 33,(ParserData.MlyValue.VOID,p1,p2))
fun FLOAT (p1,p2) = Token.TOKEN (ParserData.LrTable.T 34,(ParserData.MlyValue.VOID,p1,p2))
fun ADDR (p1,p2) = Token.TOKEN (ParserData.LrTable.T 35,(ParserData.MlyValue.VOID,p1,p2))
fun LIST (p1,p2) = Token.TOKEN (ParserData.LrTable.T 36,(ParserData.MlyValue.VOID,p1,p2))
fun MAX (p1,p2) = Token.TOKEN (ParserData.LrTable.T 37,(ParserData.MlyValue.VOID,p1,p2))
fun MIN (p1,p2) = Token.TOKEN (ParserData.LrTable.T 38,(ParserData.MlyValue.VOID,p1,p2))
fun TRASH (p1,p2) = Token.TOKEN (ParserData.LrTable.T 39,(ParserData.MlyValue.VOID,p1,p2))
fun FIRST (p1,p2) = Token.TOKEN (ParserData.LrTable.T 40,(ParserData.MlyValue.VOID,p1,p2))
fun APPEND (p1,p2) = Token.TOKEN (ParserData.LrTable.T 41,(ParserData.MlyValue.VOID,p1,p2))
fun ARROW (p1,p2) = Token.TOKEN (ParserData.LrTable.T 42,(ParserData.MlyValue.VOID,p1,p2))
fun ANY (p1,p2) = Token.TOKEN (ParserData.LrTable.T 43,(ParserData.MlyValue.VOID,p1,p2))
fun TILDE (p1,p2) = Token.TOKEN (ParserData.LrTable.T 44,(ParserData.MlyValue.VOID,p1,p2))
fun MANUAL (p1,p2) = Token.TOKEN (ParserData.LrTable.T 45,(ParserData.MlyValue.VOID,p1,p2))
fun UNARY (p1,p2) = Token.TOKEN (ParserData.LrTable.T 46,(ParserData.MlyValue.VOID,p1,p2))
fun ASNOP (p1,p2) = Token.TOKEN (ParserData.LrTable.T 47,(ParserData.MlyValue.VOID,p1,p2))
fun EXTERN (p1,p2) = Token.TOKEN (ParserData.LrTable.T 48,(ParserData.MlyValue.VOID,p1,p2))
fun CONST (p1,p2) = Token.TOKEN (ParserData.LrTable.T 49,(ParserData.MlyValue.VOID,p1,p2))
fun FORALL (p1,p2) = Token.TOKEN (ParserData.LrTable.T 50,(ParserData.MlyValue.VOID,p1,p2))
fun VIRTUALNEIGHBOR (p1,p2) = Token.TOKEN (ParserData.LrTable.T 51,(ParserData.MlyValue.VOID,p1,p2))
fun LINEAR (p1,p2) = Token.TOKEN (ParserData.LrTable.T 52,(ParserData.MlyValue.VOID,p1,p2))
fun BANG (p1,p2) = Token.TOKEN (ParserData.LrTable.T 53,(ParserData.MlyValue.VOID,p1,p2))
fun PERSISTENT (p1,p2) = Token.TOKEN (ParserData.LrTable.T 54,(ParserData.MlyValue.VOID,p1,p2))
fun SECS (p1,p2) = Token.TOKEN (ParserData.LrTable.T 55,(ParserData.MlyValue.VOID,p1,p2))
fun MILLISECS (p1,p2) = Token.TOKEN (ParserData.LrTable.T 56,(ParserData.MlyValue.VOID,p1,p2))
fun SET_UNION (p1,p2) = Token.TOKEN (ParserData.LrTable.T 57,(ParserData.MlyValue.VOID,p1,p2))
fun SET (p1,p2) = Token.TOKEN (ParserData.LrTable.T 58,(ParserData.MlyValue.VOID,p1,p2))
fun DELTA (p1,p2) = Token.TOKEN (ParserData.LrTable.T 59,(ParserData.MlyValue.VOID,p1,p2))
fun SCHEDULE (p1,p2) = Token.TOKEN (ParserData.LrTable.T 60,(ParserData.MlyValue.VOID,p1,p2))
fun DELETE (p1,p2) = Token.TOKEN (ParserData.LrTable.T 61,(ParserData.MlyValue.VOID,p1,p2))
fun PROVED (p1,p2) = Token.TOKEN (ParserData.LrTable.T 62,(ParserData.MlyValue.VOID,p1,p2))
end
end
