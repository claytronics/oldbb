(* L1 Compiler
 * Parse State System
 *)
signature PARSE_STATE =
  sig
    (* resets the parse tracker *)
    val reset : unit -> unit

    (* sets the cucurrent file -- for purposes of preprocessor *) 
    val setfile : string -> unit

    (* increment a line count *)
    val newline : int -> unit

    (* returns the current line *) 
    val curline : unit -> int

    (* returns the current position information based on two integer offsets *)
    val ext : int * int -> Mark.ext option
  end

structure ParseState :> PARSE_STATE =
  struct
    structure M = BinaryMapFn (struct
                                 type ord_key = string
                                 val compare = String.compare
                               end)

    val files : {name : string,
                 curline : int ref,
                 starts : int list ref} M.map ref = ref M.empty

    val curfile : string list ref = ref []

    fun reset () = (files := M.empty; curfile := [])

    val bug = ErrorMsg.impossible

    fun unquote s = 
        (case String.sub (s, 0) of
           #"\"" => (String.implode o List.rev o List.tl o List.rev o List.tl o String.explode) s
         | _ => s)
      
    fun setfile cppline =
        (case String.tokens Char.isSpace cppline of
           ("#" :: linenum :: file :: flags) =>
             let
               val linenum = Option.valOf (Int.fromString linenum) 
                 handle e => bug ("badline:" ^ linenum)
               val file = unquote file
             in
               (case M.find (!files, file) of
                  SOME {name, curline, ...} => (curfile := List.tl (!curfile); curline := linenum)
                | NONE => 
                    let
                      val fstate = {name = file, starts = ref [1], curline = ref linenum}
                    in
                      files := M.insert (!files, file, fstate);
                      curfile := file :: (!curfile)
                    end)
             end
         | _ => raise bug ("badcppdir:\"" ^ cppline ^ "\""))


    fun curf () =
        (case !curfile of
             (f :: _) => f
           | _ => bug "badcur")
	
    fun newline pos =
        (case M.find (!files, curf ()) of
           SOME {curline, starts, ...} => (starts := pos :: (!starts); curline := (!curline + 1))
         | NONE => bug "nocurfile")

    fun curline () =
        (case M.find (!files, curf ()) of
           SOME {curline, ...} => !curline
         | NONE => bug "nocurline")

    fun look (pos, a :: rest, n) =
         if a < pos then (n, pos - a)
         else look (pos, rest, n - 1) 
       | look _ = (0, 0)

    fun history [] = ""
      | history [f] = f
      | history (f :: fs) = f ^ " (from " ^ history fs ^ ")"

    fun ext (left, right) =
        (case M.find (!files, curf ()) of
           SOME {starts, curline, ...} => SOME (look (left, !starts, !curline),
                                                look (right, !starts, !curline),
                                                history (!curfile))
         | NONE => NONE)
             
  end
