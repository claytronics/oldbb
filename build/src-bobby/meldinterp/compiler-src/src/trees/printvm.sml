signature PRINTVM =
  sig
    val printString : VMst.program -> string
    val print : VMst.program -> unit
	val write : VMst.program -> Word8Vector.vector * string list
  end

structure PrintVM :> PRINTVM =
  struct

    open VMst
    open Exceptions

	fun sort (prog : VMst.program) =
			  ((List.@ o (List.partition (fn (info, _) => (T.thmName (#1 info)) = "neighbor"))) o
			   (List.@ o (List.partition (fn (info, _) => (T.thmName (#1 info)) = "neighborCount"))) o
			   (List.@ o (List.partition (fn (info, _) => (T.thmName (#1 info)) = "vacant"))) o
			   (List.@ o (List.partition (fn (info, _) => (T.thmName (#1 info)) = "setColor"))) o
			   (List.@ o (List.partition (fn (info, _) => (T.thmName (#1 info)) = "setColor2")))) prog

	val printStringRegister = Int.toString

	fun printStringOp oper =
		case oper of
			INT_PLUS => "INT_PLUS"
		  | INT_MINUS => "INT_MINUS"
		  | INT_TIMES => "INT_TIMES"
		  | INT_DIVIDE => "INT_DIVIDE"
		  | INT_MOD => "INT_MOD"
		  | FLOAT_PLUS => "FLOAT_PLUS"
		  | FLOAT_MINUS => "FLOAT_MINUS"
		  | FLOAT_TIMES => "FLOAT_TIMES"
		  | FLOAT_DIVIDE => "FLOAT_DIVIDE"
		  | FLOAT_MOD => "FLOAT_MOD"
		  | INT_EQ => "INT EQUAL"
		  | INT_NEQ => "INT NOT EQUAL"
		  | INT_LESS => "INT LESS THAN"
		  | INT_GREATER => "INT GREATER THAN"
		  | INT_LESSEQ => "INT LESS THAN OR EQUAL"
		  | INT_GREATEREQ => "INT GREATER THAN OR EQUAL"
		  | FLOAT_EQ => "FLOAT EQUAL"
		  | FLOAT_NEQ => "FLOAT NOT EQUAL"
		  | FLOAT_LESS => "FLOAT LESS THAN"
		  | FLOAT_GREATER => "FLOAT GREATER THAN"
		  | FLOAT_LESSEQ => "FLOAT LESS THAN OR EQUAL"
		  | FLOAT_GREATEREQ => "FLOAT GREATER THAN OR EQUAL"
		  | ADDR_NEQ => "ADDR NOT EQUAL"
		  | ADDR_EQ => "ADDR EQUAL"
		  | TYPE_EQ => printStringOp (INT_EQ)

	fun printStringValue value =
        case value of
			REG reg => "reg " ^ (printStringRegister reg)
		  | THE_TUPLE => "tuple"
		  | HOST_ID => "host_id"
		  | CONST n => Int32.toString n
		  | CONSTF r => Real.toString r
		  | TYPE t => "type[" ^ t ^ "]"
		  | FIELD (r, fieldnum) => (printStringRegister r) ^ "." ^ (Int.toString fieldnum)
			| REVERSE v => "reverse[" ^ (printStringValue (FIELD v)) ^ "]"

	fun commafy [] = ""
	  | commafy [x] = x
	  | commafy (x::l) = x ^ ", " ^ (commafy l)

	fun printStringInstruction inst =
		case inst of
			IF (value, _) => "IF (" ^ (printStringRegister value) ^ ") THEN\n"
		  | ELSE => "ELSE\n"
		  | ENDIF => "ENDIF\n"
		  | ITER (id, matches, _) => "ITERATE OVER " ^ id ^ " MATCHING " ^
								  (foldr 
									   op^ 
									   ""
									   (map (fn (fieldnum,value) => "\n  (match)." ^ (Int.toString
                       fieldnum) ^ "=" ^ (printStringValue value))
									   matches)) ^ "\n"
		  | NEXT => "NEXT\n"
		  | SEND (r1, r2, v) => "SEND " ^ (printStringValue (REG r1)) ^ " TO " ^ (printStringValue (REG r2)) ^ " IN " ^ (printStringValue v) ^ "ms\n"
		  | REMOVE r => "REMOVE " ^ (printStringValue (REG r)) ^ "\n"
		  | OP (oper, v1, v2, reg) => "SET "  ^ (printStringValue (REG reg)) ^ " TO " ^
									  (printStringValue v1) ^ " " ^ (printStringOp oper) ^ " " ^
									  (printStringValue v2) ^ "\n"
		  | MOVE (v1, v2) => "MOVE " ^ (printStringValue v1) ^ " TO " ^ (printStringValue v2) ^ "\n"
		  | ALLOC (id, v) => "ALLOC " ^ id ^ " TO " ^ (printStringValue v) ^ "\n"
		  | RETURN => "RETURN\n"
		  | CALL (idNum, reg, vs) => "CALL " ^ (Int.toString idNum) ^ " " ^ (printStringRegister reg) ^ " = (" ^
								(commafy (List.map printStringValue vs)) ^ ")\n"
	
  fun printInstructionList instlist = List.foldl (fn (inststr, result) => (result ^ inststr)) "" (List.map printStringInstruction instlist)

  fun printAggregate AGG_NONE = ""
    | printAggregate (AGG_FIRST n) = " (first " ^ (Int.toString n) ^ ") "
    | printAggregate (AGG_MAX n) = " (max " ^ (Int.toString n) ^ ") "
    | printAggregate (AGG_MIN n) = " (min " ^ (Int.toString n) ^ ") "
    | printAggregate (AGG_SUM n) = " (sum " ^ (Int.toString n) ^ ") "
    | printAggregate (AGG_MAX_FLOAT n) = " (max_float " ^ (Int.toString n) ^ ") "
    | printAggregate (AGG_MIN_FLOAT n) = " (min_float " ^ (Int.toString n) ^ ") "
    | printAggregate (AGG_SUM_FLOAT n) = " (sum_float " ^ (Int.toString n) ^ ") "
    | printAggregate (AGG_SET_UNION_INT n) = " (set_union_int " ^ (Int.toString n) ^ ") "
    | printAggregate (AGG_SET_UNION_FLOAT n) = " (set_union_float " ^ (Int.toString n) ^ ") "
    | printAggregate (AGG_SUM_LIST_INT n) = " (sum_list_int " ^ (Int.toString n) ^ ") "
    | printAggregate (AGG_SUM_LIST_FLOAT n) = " (sum_list_float " ^ (Int.toString n) ^ ") "

	fun printStringFunc ((thm, agg, _, _, _, _, _), insts) =
		"PROCESS " ^ (T.thmName thm) ^ (printAggregate agg) ^ ":\n" ^ (foldr op^ "\n\n" (map printStringInstruction insts)) 

	fun printString prog = foldr op ^ "" (map printStringFunc (sort prog))
	val print = TextIO.print o printString

	structure W = Word8

	val w = W.fromInt
	val wo = Word.fromInt
	val wp = W.+
	infix wp

  fun castGeneric sizeF setF =
    fn value => let
        open C.Ptr
        val objF = C.new' sizeF
        val objT = |*! (cast' (inject' (|&! objF)))
      in
        setF (objF, value)
        ; C.Get.uint' objT before C.discard' objF
      end

  val castFloat = castGeneric C.S.float C.Set.float'
  val castInt = castGeneric C.S.sint C.Set.sint'

  fun write32BitWord word =
    let
      val ret = List.tabulate (4, (fn n => Word32.andb(Word32.>>(word, wo (n * 8)), 0wxff)))
    in
      map (W.fromLargeWord o Word32.toLargeWord) ret
    end
					   
	fun writeInt i = write32BitWord (castInt i)
	fun writeReal r = write32BitWord (castFloat (Real32.fromLarge IEEEReal.TO_NEAREST r))
	
	fun writeShort (num:int) = [w (num mod 256), w (num div 256)]

	fun writeRegister reg = 
		if (reg < 32)
		then W.fromInt reg
		else raise (InternalError "Ran out of registers....")
	
	
   val idents = ref ([] : (string * Word8.word) list)
   	
   fun writeId id = (case List.find (fn x => (#1 x) = id) (!idents)
   					of SOME (_, n) => n
   					 | NONE => raise (InternalError "Ooops, ident missing from list"))

	fun writeValue value =
		case value of
			REG r => ((w 32) wp (writeRegister r), [])
		  | THE_TUPLE => (w 31, [])
		  | CONST i => (w 1, writeInt i)
		  | CONSTF r => (w 0, writeReal r)
		  | TYPE t => writeValue (CONST (Int32.fromInt (Word8.toInt (writeId t)))) (* use as an int *)
		  | HOST_ID => (w 3, [])
		  | FIELD (r, fieldnum) =>
			let
				val r' = writeRegister r
			in
				(w 2, [w fieldnum, r'])
			end
			| REVERSE (r, fieldnum) =>
			let
				val r' = writeRegister r
			in
				(w 4, [w fieldnum, r'])
			end
         
	fun writeOper oper =
		case oper of
		   FLOAT_NEQ => w 0
		 | INT_NEQ => w 1
		 | FLOAT_EQ => w 2
		 | INT_EQ => w 3
		 | FLOAT_LESS => w 4
		 | INT_LESS => w 5
		 | FLOAT_LESSEQ => w 6
		 | INT_LESSEQ => w 7
		 | FLOAT_GREATER => w 8
		 | INT_GREATER => w 9
		 | FLOAT_GREATEREQ => w 10
		 | INT_GREATEREQ => w 11
		 | FLOAT_MOD => w 12
		 | INT_MOD => w 13
		 | FLOAT_PLUS => w 14
		 | INT_PLUS => w 15
		 | FLOAT_MINUS => w 16
		 | INT_MINUS => w 17
		 | FLOAT_TIMES => w 18
		 | INT_TIMES => w 19
		 | FLOAT_DIVIDE => w 20
		 | INT_DIVIDE => w 21
		 | ADDR_NEQ => w 22
		 | ADDR_EQ => w 23
		 | TYPE_EQ => writeOper (INT_EQ)

	fun writeMatches matches =
		let
			fun writeMatch (fieldnum, v) = 
				let
					val (v', f) = writeValue v
				in
					(w fieldnum)::v'::f
				end
		in
			case matches of 
				[] => [w 0, w 192]
			  | [m] => (case (writeMatch m) of m1::m2::ms => m1::(m2 wp (w 64))::ms
											  | _ => raise (InternalError "output of writeMatch is not what writeMatches expects. Something must have changed"))
			  | m::ms => (writeMatch m)@(writeMatches ms)
		end

	fun writeInstruction inst =
		case inst of
			IF (reg, jump) => [(w 96) wp (writeRegister reg)]@(writeShort jump)
		  | ELSE => [w 2]
		  | ENDIF => []
		  | ITER (id, matches, jump) => [w 160, writeId id]@(writeShort jump)@(writeMatches matches)
		  | NEXT => [w 1]
		  | SEND (r1, r2, v) => 
			let
				val r1' = writeRegister r1
				val r2' = writeRegister r2
				val (v', rest) = writeValue v
			in
				[(w 8) wp (W.>> (r1', wo 3)), (W.<< (r1', wo 5)) wp r2', v']@rest
			end
		  | REMOVE r =>
			[(w 128) wp (writeRegister r)]
		  | OP (oper, v1, v2, r) =>
			let
				val oper' = writeOper oper
				val (v1', f1) = writeValue v1
				val (v2', f2) = writeValue v2
				val r' = writeRegister r
			in
			   [(w 192) wp v1', (W.<<(v2', wo 2)) wp (W.>>(r', wo 3)), (W.<<(r', wo 5)) wp oper']@f1@f2
			end
		  | MOVE (v1, v2) => 
			let
				val (v1', f1) = writeValue v1
				val (v2', f2) = writeValue v2
			in
				[(w 48) wp (W.>>(v1', wo 2)), (W.<<(v1', wo 6)) wp v2']@f1@f2
			end
		  | ALLOC (id, v) =>
			let
				val (v', f) = writeValue v
				val id' = writeId id
			in
				[(w 64) wp (W.>>(id', wo 2)), (W.<<(id', wo 6)) wp v']@f
			end
		  | RETURN => [w 0]
		  | CALL (id, reg, vs) => 
				[(w 32) wp (W.>>(w id, wo 3)), (W.<< (w id  , wo 5))wp (writeRegister reg)]@
				(List.foldr List.@ [] ((List.map (List.:: o writeValue) vs)))

  fun aggregateCode (code:int) = 16 * code
				
  fun doWriteAggregate code n =
    if (n <= 15)
    then w ((aggregateCode code) + n)
    else raise (InternalError "too many fields")

	fun writeAggregate AGG_NONE = w 0
	  | writeAggregate (AGG_FIRST n) = doWriteAggregate 1 n
     | writeAggregate (AGG_MAX n)   = doWriteAggregate 2 n
     | writeAggregate (AGG_MIN n)   = doWriteAggregate 3 n
     | writeAggregate (AGG_SUM n)   = doWriteAggregate 4 n
     | writeAggregate (AGG_MAX_FLOAT n) = doWriteAggregate 5 n
     | writeAggregate (AGG_MIN_FLOAT n) = doWriteAggregate 6 n
     | writeAggregate (AGG_SUM_FLOAT n) = doWriteAggregate 7 n
     | writeAggregate (AGG_SET_UNION_INT n) = doWriteAggregate 8 n
     | writeAggregate (AGG_SET_UNION_FLOAT n) = doWriteAggregate 9 n
     | writeAggregate (AGG_SUM_LIST_INT n) = doWriteAggregate 10 n
     | writeAggregate (AGG_SUM_LIST_FLOAT n) = doWriteAggregate 11 n

	fun writeDelta (SOME (deltaId, tuplePos)) = [w deltaId, w tuplePos]
		| writeDelta NONE = [w 0, w 0]
		
	fun writePropertyByte (thm, agg, persistent, hasProved) =
	   let
	      val start = w 0
	      val withAgg = case agg of
													AGG_NONE => start
												| _ => start wp (w 1)
	      val withPersistence = case persistent of
	                                T.Persistent => withAgg wp (w 2)
	                              | _ => withAgg
	      val withLinear = case thm of
	                           T.Linear _ => withPersistence wp (w 4)
	                         | _ => withPersistence
	      val withDelete = case thm of
	                           T.Delete _ => withLinear wp (w 8)
	                         | _ => withLinear
	      val withSchedule = case thm of
	                             T.Schedule _ => withDelete wp (w 16)
	                           | _ => withDelete
			val withRouting = case thm of
										   T.Routing _ => withSchedule wp (w 32)
										| _ => withSchedule
			val withProved = if hasProved
			                 then withRouting wp (w 64)
			                 else withRouting
	   in
	       withProved
     end

	fun writeHeader (thm, offset, (agg, persistent, stratNum, hasProved), args, deltas) =
    let
      val numArguments = List.length args
		val numDeltas = List.length deltas

		(* base header is 8 bytes *)
      val header = (writeShort offset)@
									 [writePropertyByte (thm, agg, persistent, hasProved),
										writeAggregate agg,
										case stratNum of SOME n => w (n+1) | NONE => w 0,
										w numArguments,
										w numDeltas]
		val deltaBytes = List.concat (map (fn (tupleId, tuplePos) => [w tupleId, w tuplePos]) deltas)
      val arguments = List.map (fn typid => w typid) args
    in
      Word8Vector.fromList (header@arguments@deltaBytes)
    end

	fun printDelta ((tuple_id, pos), name) =
			(TextIO.print ("DELTA: " ^ name ^ " delta_tuple: " ^ (Int.toString tuple_id) ^ " delta_arg: " ^ (Int.toString pos) ^ "\n"))

	fun findDelta (T.Delta (name, pos)) thms =
		let
			val count = ref 0
			val found = List.find (fn thm =>
					let
						val _ = count := !count + 1
					in
						case thm of
							  T.Delta (name2, pos2) => ((name2 = name) andalso (pos = pos2))
							| _ => false
					end) thms
		in
			case found of
				  SOME _ => (!count - 1, pos)
				| _ => raise (InternalError "this shouldn't happen")
		end
	| findDelta _ _ = raise (InternalError "this also shouldn't happen")

	fun printDeltas (deltas, name) =
		List.map (fn delta => printDelta (delta, name)) deltas

	fun doSort (prog : VMst.program) =
		let
			val prog = sort prog

			val thms = List.map (#1 o #1) prog
			val () = idents := ListPair.zip (List.map T.thmName thms, List.tabulate (List.length prog, w))

			val prog' = List.map #2 prog

			val misc = List.map (fn ((_, agg, _, _, persists, stratNum, hasProved), _) => (agg, persists, stratNum, hasProved)) prog

			val args = List.map (#3 o #1) prog

			val deltas = List.map (fn deltas => List.map (fn delta => findDelta delta thms) deltas) (List.map (#4 o #1) prog)
			(*val _ = List.map printDeltas (ListPair.zip (deltas, (List.map (T.thmName o #1 o #1) prog)))*)
		in
			(prog', thms, misc, args, deltas)
		end

	fun write prog =
		let
			val (prog', thms, misc, args, deltas) = doSort prog

			val code = map (Word8Vector.concat o (map (Word8Vector.fromList o writeInstruction))) prog'

			val lengths = map Word8Vector.length code
			val numEntries = List.length lengths

			val numDeltas = map List.length deltas
			val totalDeltas = foldl Int.+ 0 numDeltas
			val numArguments = map List.length args
			val totalArguments = foldl Int.+ 0 numArguments

			val baseDescriptorSize = 7 (* TODO: THIS IS A MISTAKE. THIS EITHER NEEDS TO BE CALCULATED OR THERE NEEDS TO BE A CLEAR AND OBVIOUS COMMENT ADJACENT TO THE CODE THE PRODUCES THE BASE DESCRIPTOR *)

			val descriptorLengths = map (fn (nargs, ndeltas) => baseDescriptorSize + nargs + ndeltas * 2)
																	(ListPair.zip (numArguments, numDeltas))
			val descriptorOffsets = ListUtil.addify (1 + numEntries) descriptorLengths

			val codeOffsets = ListUtil.addify (1 + numEntries + baseDescriptorSize * numEntries + totalArguments + 2 * totalDeltas) lengths 

			val thmHeaders' = ListUtil.zip5 (thms, codeOffsets, misc, args, deltas)

			val thmHeaders = List.map writeHeader thmHeaders'

			val descriptorHeader = Word8Vector.fromList (List.map (fn offset => w offset) descriptorOffsets)
	in
		(Word8Vector.concat ((Word8Vector.fromList [w numEntries])::(descriptorHeader::thmHeaders)@code),
		List.map #1 (!idents))
	end

	(*TODO: sort *)
end
