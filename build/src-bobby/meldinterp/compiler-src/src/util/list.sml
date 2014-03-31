signature LISTUTIL =
  sig
	  val sort : ('a * 'a -> order) -> 'a list -> 'a list
	  val addify : int -> int list -> int list
		val zip3 : ('a list * 'b list * 'c list) -> ('a * 'b * 'c) list
		val zip4 : ('a list * 'b list * 'c list * 'd list) -> ('a * 'b * 'c * 'd) list
		val zip5 : ('a list * 'b list * 'c list * 'd list * 'e list) -> ('a * 'b * 'c * 'd * 'e) list
  end


structure ListUtil :> LISTUTIL =
  struct
    fun insert _ x [] = [x]
	  | insert compare x (x'::l) =
		case compare(x, x') of
			LESS => x::x'::l
		  | EQUAL => x::x'::l
		  | GREATER => x'::(insert compare x l)

    fun sort _ [] = []
	  | sort compare (x::l) = insert compare x (sort compare l)

    fun addify n (x::l) = n::(addify (x+n) l)
      | addify _ [] = []

		fun zip3 (list1, list2, list3) =
			let
				val firstZip = ListPair.zip (list1, list2)
				val secondZip = ListPair.zip (firstZip, list3)
			in
				map (fn ((el1, el2), el3) => (el1, el2, el3)) secondZip
			end

		fun zip4 (list1, list2, list3, list4) =
			let
				val firstZip = zip3 (list1, list2, list3)
				val secondZip = ListPair.zip (firstZip, list4)
			in
				map (fn ((el1, el2, el3), el4) => (el1, el2, el3, el4)) secondZip
			end
		
		fun zip5 (list1, list2, list3, list4, list5) =
		   let
		      val firstZip = zip4 (list1, list2, list3, list4)
		      val secondZip = ListPair.zip (firstZip, list5)
		   in
		      map (fn ((el1, el2, el3, el4), el5) => (el1, el2, el3, el4, el5)) secondZip
	      end
  end
