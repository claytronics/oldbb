
#include "instr.hpp"
#include "parser.hpp"

using namespace std;

static inline string
argument_string(const argument_id id)
{
   return string("@arg ") + to_string((int)id);
}

static inline string
operation_string(pcounter& pc, const string& op)
{
   return reg_string(pcounter_reg(pc + instr_size)) + " " + op + " " + reg_string(pcounter_reg(pc + instr_size + reg_val_size)) + " TO "
      + reg_string(pcounter_reg(pc + instr_size + 2 * reg_val_size));
}

static inline string
call_string(const string& extra, pcounter& pc, const size_t args)
{
  (void)extra;
  (void)args;
  (void)pc;
   // external_function* f(lookup_external_function(call_extern_id(pc)));
   // return string("CALL") + extra + " " + f->get_name() + "/" + to_string(args) + " TO " + reg_string(call_dest(pc)) + " = (";
  return "CALL function";
}

static string val_string(const instr_val, pcounter *);

static string
list_string(pcounter *pm)
{
   const instr_val head_val(val_get(*pm, 0));

   pcounter_move_byte(pm);
   // const string head_string(val_string(head_val, pm));
   (void)head_val;
   const instr_val tail_val(val_get(*pm, 0));
   pcounter_move_byte(pm);
   // const string tail_string(val_string(tail_val, pm));
   (void)tail_val;

   // return string("[") + head_string + " | " + tail_string + string("]");
   return "List string";
}

static inline string
int_string(const int_val i)
{
   return string("INT ") + to_string(i);
}

static inline string
ptr_string(const ptr_val p)
{
   return string("PTR ") + to_string(p);
}

static inline string
const_id_string(const const_id id)
{
   return string("CONST ") + to_string(id);
}

static inline string
node_string(const node_val n)
{
   return string("@") + to_string(n);
}

static inline string
stack_string(const offset_num s)
{
   return string("STACK ") + to_string((int)s);
}

static inline string
float_string(const float_val f)
{
   return string("FLOAT ") + to_string(f);
}

static inline string
field_string(pcounter *pm)
{
   const string ret(field_string(*pm));
   pcounter_move_field(pm);
   return ret;
}

static string
val_string(const instr_val v, pcounter *pm)
{
   if(val_is_reg(v))
      return reg_string(val_reg(v));
   else if(val_is_host(v))
      return string("host");
   else if(val_is_nil(v))
      return string("nil");
   else if(val_is_non_nil(v))
      return string("non nil");
   else if(val_is_any(v))
      return string("...");
   else if(val_is_list(v))
      return list_string(pm);
   else if(val_is_field(v))
      return field_string(pm);
   else if(val_is_int(v)) {
      const string ret(int_string(pcounter_int(*pm)));
      pcounter_move_int(pm);
      return ret;
   } else if(val_is_bool(v)) {
      const bool_val v = pcounter_bool(*pm);
      pcounter_move_bool(pm);
      return string("BOOL ") + (v ? "true" : "false");
   } else if(val_is_float(v)) {
      const string ret(float_string(pcounter_float(*pm)));
      pcounter_move_float(pm);
      return ret;
   } else if(val_is_node(v)) {
      const string ret(node_string(pcounter_node(*pm)));
      pcounter_move_node(pm);
      return ret;
	} else if(val_is_string(v)) {
      const uint_val id(pcounter_uint(*pm));
      (void)id;
      const string ret(string("\"") + "\"");
      pcounter_move_uint(pm);
      return ret;
   } else if(val_is_arg(v)) {
     const string ret(argument_string(pcounter_argument_id(*pm)));
     
     pcounter_move_argument_id(pm);
     
     return ret;
   } else if(val_is_stack(v)) {
      const offset_num offset(pcounter_stack(*pm));
      
      pcounter_move_offset_num(pm);
      
      return stack_string(offset);
   } else if(val_is_pcounter(v)) {
      return string("PCOUNTER");
	} else if(val_is_const(v)) {
		const string ret(const_id_string(pcounter_const_id(*pm)));
		
		pcounter_move_const_id(pm);
		
		return ret;
   } else if(val_is_ptr(v)) {
      const string ret(ptr_string(pcounter_ptr(*pm)));

      pcounter_move_ptr(pm);

      return ret;
   } else
     cerr << "Unrecognized val type " + to_string(v) + " (val_string)" << endl;
   
   return string("");
}

static inline void
instrs_print_until_return_select(byte_code code, const int tabcount, ostream& cout)
{
   pcounter pc = code;
   pcounter new_pc;
   
	while (true) {
		new_pc = instr_print(pc, true, tabcount, cout);
		if(fetch(pc) == RETURN_SELECT_INSTR)
         return;
      pc = new_pc;
	}
}

static inline pcounter
instrs_print_until_end_linear(byte_code code, const int tabcount, ostream& cout)
{
   pcounter pc = code;
   pcounter new_pc;

   while (true) {
      new_pc = instr_print(pc, true, tabcount, cout);
      if(fetch(pc) == END_LINEAR_INSTR)
         return pc;
      pc = new_pc;
   }
}

static inline pcounter
instrs_print_until(byte_code code, byte_code until, const int tabcount, ostream& cout)
{
   pcounter pc = code;
   
	for (; pc < until; ) {
		pc = instr_print(pc, true, tabcount, cout);
	}
	
   return until;
}

static inline void
print_tab(const int tabcount)
{
   for(int i = 0; i < tabcount; ++i)
      cout << "  ";
}

static inline void
print_axiom_data(pcounter& p, size_t t, bool in_list = false)
{
   switch(t) {
      case FIELD_INT:
         cout << pcounter_int(p);
         pcounter_move_int(&p);
         break;
      case FIELD_FLOAT:
         cout << pcounter_float(p);
         pcounter_move_float(&p);
         break;
      case FIELD_NODE:
         cout << "@" << pcounter_node(p);
         pcounter_move_node(&p);
         break;
      case FIELD_LIST: {
         if(!in_list) {
            cout << "[";
         }
         if(*p++ == 0) {
            cout << "]";
            break;
         }
         // list_type *lt((list_type*)t);
         // print_axiom_data(p, lt->get_subtype());
         if(*p == 1)
            cout << ", ";
         // print_axiom_data(p, lt, true);
        }
         break;
      default: ;
   }
}

static inline void
print_iter_matches(const pcounter pc, const size_t base, const size_t tabcount)
{
   pcounter m = pc + base;
   const size_t iter_matches(iter_matches_size(pc, base));

   for(size_t i(0); i < iter_matches; ++i) {
      print_tab(tabcount+1);
      const instr_val mval(iter_match_val(m));
      const field_num field(iter_match_field(m));
      m += iter_match_size;
      cout << "  (match)." << field << "=" << val_string(mval, &m) << endl;
   }
}

pcounter
instr_print(pcounter pc, const bool recurse, const int tabcount, ostream& cout)
{
   print_tab(tabcount);

   switch(fetch(pc)) {
      case RETURN_INSTR:
         cout << "RETURN" << endl;
			break;
      case RETURN_LINEAR_INSTR:
         cout << "RETURN LINEAR" << endl;
         break;
      case RETURN_DERIVED_INSTR:
         cout << "RETURN DERIVED" << endl;
         break;
      case END_LINEAR_INSTR:
         cout << "END LINEAR" << endl;
         break;
      case RESET_LINEAR_INSTR:
         cout << "RESET LINEAR" << endl;
         if(recurse)
            pc = instrs_print_until_end_linear(advance(pc), tabcount + 1, cout);
         break;
	   case IF_INSTR: {
            cout << "IF (" << reg_string(if_reg(pc)) << ") THEN" << endl;
				if(recurse) {
               pcounter cont = instrs_print(advance(pc), if_jump(pc) - (advance(pc) - pc),
                                       tabcount + 1, cout);
               print_tab(tabcount);
               cout << "ENDIF" << endl;
					return cont;
				}
			}
			break;
      case IF_ELSE_INSTR: {
            cout << "IF (" << reg_string(if_reg(pc)) << ") THEN" << endl;
            if(recurse) {
               pcounter cont_then = instrs_print(advance(pc), if_else_jump_else(pc) - (advance(pc) - pc), tabcount + 1, cout);
               (void)cont_then;
               print_tab(tabcount);
               cout << "ELSE" << endl;
               instrs_print(pc + if_else_jump_else(pc), if_else_jump(pc) - if_else_jump_else(pc), tabcount + 1, cout);
               print_tab(tabcount);
               cout << "ENDIF" << endl;
               return pc + if_else_jump(pc);
            }
         }
         break;
		case TESTNIL_INSTR:
         cout << "TESTNIL " << reg_string(test_nil_op(pc)) << " TO " << reg_string(test_nil_dest(pc)) << endl;
         break;
   	case ALLOC_INSTR:
	  cout << "ALLOC " << "predicate " << (int)(*pc)
              << " TO " << reg_string(alloc_reg(pc))
              << endl;
         break;
   	case FLOAT_INSTR:
         cout << "FLOAT " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + reg_val_size)) << endl;
         break;
      case SEND_INSTR:
         cout << "SEND " << reg_string(send_msg(pc))
              << " TO " << reg_string(send_dest(pc))
              << endl;
   		break;
      case ADDLINEAR_INSTR:
         cout << "ADDLINEAR " << reg_string(pcounter_reg(pc + instr_size)) << endl;
         break;
      case ADDPERS_INSTR:
         cout << "ADDPERS " << reg_string(pcounter_reg(pc + instr_size)) << endl;
         break;
      case RUNACTION_INSTR:
         cout << "RUNACTION " << reg_string(pcounter_reg(pc + instr_size)) << endl;
         break;
      case ENQUEUE_LINEAR_INSTR:
         cout << "ENQUEUE LINEAR " << reg_string(pcounter_reg(pc + instr_size)) << endl;
         break;
      case PERS_ITER_INSTR:
         cout << "PERSISTENT ITERATE OVER " << "Predicate name" << " MATCHING TO " << reg_string(iter_reg(pc)) << endl;
         print_iter_matches(pc, PERS_ITER_BASE, tabcount);
         if(recurse) {
            instrs_print_until(pc + iter_inner_jump(pc), pc + iter_outer_jump(pc), tabcount + 1, cout);
            return pc + iter_outer_jump(pc);
         }
         break;
      case LINEAR_ITER_INSTR:
         cout << "LINEAR ITERATE OVER " << "Predicate name" << " MATCHING TO " << reg_string(iter_reg(pc)) << endl;
         print_iter_matches(pc, LINEAR_ITER_BASE, tabcount);
         if(recurse) {
            instrs_print_until(pc + iter_inner_jump(pc), pc + iter_outer_jump(pc), tabcount + 1, cout);
            return pc + iter_outer_jump(pc);
         }
         break;
      case RLINEAR_ITER_INSTR:
         cout << "LINEAR(R) ITERATE OVER " << "Predicate name" << " MATCHING TO " << reg_string(iter_reg(pc)) << endl;
         print_iter_matches(pc, RLINEAR_ITER_BASE, tabcount);
         if(recurse) {
            instrs_print_until(pc + iter_inner_jump(pc), pc + iter_outer_jump(pc), tabcount + 1, cout);
            return pc + iter_outer_jump(pc);
         }
         break;
      case OPERS_ITER_INSTR:
         cout << "ORDER PERSISTENT ITERATE OVER " << "Predicate name" << " (";
         {
            const byte opts(iter_options(pc));
            if(iter_options_random(opts))
               cout << "random";
            else if(iter_options_min(opts))
               cout << "min " << iter_options_min_arg(iter_options_argument(pc));
         }
         cout << ") MATCHING TO " << reg_string(iter_reg(pc)) << endl;
         print_iter_matches(pc, OPERS_ITER_BASE, tabcount);
         if(recurse) {
            instrs_print_until(pc + iter_inner_jump(pc), pc + iter_outer_jump(pc), tabcount + 1, cout);
            return pc + iter_outer_jump(pc);
         }
         break;
      case OLINEAR_ITER_INSTR:
         cout << "ORDER LINEAR ITERATE OVER " << "Predicate name" << " (";
         {
            const byte opts(iter_options(pc));
            if(iter_options_random(opts))
               cout << "random";
            else if(iter_options_min(opts))
               cout << "min " << iter_options_min_arg(iter_options_argument(pc));
         }
         cout << ") MATCHING TO " << reg_string(iter_reg(pc)) << endl;
         print_iter_matches(pc, OLINEAR_ITER_BASE, tabcount);
         if(recurse) {
            instrs_print_until(pc + iter_inner_jump(pc), pc + iter_outer_jump(pc), tabcount + 1, cout);
            return pc + iter_outer_jump(pc);
         }
         break;
      case ORLINEAR_ITER_INSTR:
         cout << "ORDER LINEAR(R) ITERATE OVER " << "Predicate name" << " (";
         {
            const byte opts(iter_options(pc));
            if(iter_options_random(opts))
               cout << "random";
            else if(iter_options_min(opts))
               cout << "min " << iter_options_min_arg(iter_options_argument(pc));
         }
         cout << ") MATCHING TO " << reg_string(iter_reg(pc)) << endl;
         print_iter_matches(pc, ORLINEAR_ITER_BASE, tabcount);
         if(recurse) {
            instrs_print_until(pc + iter_inner_jump(pc), pc + iter_outer_jump(pc), tabcount + 1, cout);
            return pc + iter_outer_jump(pc);
         }
         break;
   	case NEXT_INSTR:
         cout << "NEXT" << endl;
         break;
      case CALL0_INSTR:
         cout << call_string("0", pc, 0) << ")" << endl;
         break;
      case CALL1_INSTR:
         cout << call_string("1", pc, 1) << reg_string(pcounter_reg(pc + call_size)) << ")" << endl;
         break;
      case CALL2_INSTR:
         cout << call_string("2", pc, 2) << reg_string(pcounter_reg(pc + call_size)) << ", " <<
            reg_string(pcounter_reg(pc + call_size + reg_val_size)) << ")" << endl;
         break;
      case CALL3_INSTR:
         cout << call_string("3", pc, 3) << reg_string(pcounter_reg(pc + call_size)) << ", " <<
            reg_string(pcounter_reg(pc + call_size + reg_val_size)) << ", " <<
            reg_string(pcounter_reg(pc + call_size + 2 * reg_val_size)) << ")" << endl;
         break;
   	case CALL_INSTR: {
            pcounter m = pc + CALL_BASE;

            cout << call_string("", pc, call_num_args(pc));
            
            for(size_t i = 0; i < call_num_args(pc); ++i) {
               if(i != 0)
                  cout << ", ";
               
               cout << reg_string(pcounter_reg(m));
               m += reg_val_size;
            }
            cout << ")" << endl;
   		}
   		break;
   	case CALLE_INSTR: {
            pcounter m = pc + CALL_BASE;

            cout << call_string("E", pc, calle_num_args(pc));
            
            for(size_t i = 0; i < calle_num_args(pc); ++i) {
               if(i != 0)
                  cout << ", ";
               
               cout << reg_string(pcounter_reg(m));
               m += reg_val_size;
            }
            cout << ")" << endl;
   		}
   		break;
      case DELETE_INSTR: {
            pcounter m = pc + DELETE_BASE;
            const predicate_id pred_id(delete_predicate(pc));
            const size_t num_args(delete_num_args(pc));
            
            cout << "DELETE " << tuple_names[pred_id]
                 << " USING ";
                 
            for(size_t i(0); i < num_args; ++i) {
               if(i != 0)
                  cout << ", ";
               
               pcounter val_ptr(m);
               cout << (int)delete_index(val_ptr) << ":";
               
               m += index_size + val_size; // skip index and value bytes of this arg
               
               cout << val_string(delete_val(val_ptr), &m);
            }
            cout << endl; 
         }
         break;
      case REMOVE_INSTR:
         cout << "REMOVE " << reg_string(pcounter_reg(pc + instr_size)) << endl;
         break;
      case UPDATE_INSTR:
         cout << "UPDATE " << reg_string(pcounter_reg(pc + instr_size)) << endl;
         break;
    	case NOT_INSTR:
         cout << "NOT " << reg_string(not_op(pc)) << " TO " << reg_string(not_dest(pc)) << endl;
         break;
    	case RETURN_SELECT_INSTR:
         cout << "RETURN SELECT " << return_select_jump(pc) << endl;
         break;
      case SELECT_INSTR:
         cout << "SELECT BY NODE" << endl;
         if(recurse) {
            const code_size_t elems(select_hash_size(pc));
            const pcounter hash_start(select_hash_start(pc));
            
            for(size_t i(0); i < elems; ++i) {
               print_tab(tabcount);
               cout << i << endl;
               
               const code_size_t hashed(select_hash(hash_start, i));
               
               if(hashed != 0)
                  instrs_print_until_return_select(select_hash_code(hash_start, elems, hashed), tabcount + 1, cout);
            }
            
            return pc + select_size(pc);
         }
         break;
      case RULE_INSTR: {
            const size_t rule_id(rule_get_id(pc));

            cout << "RULE " << rule_id << endl;
         }
         break;
      case RULE_DONE_INSTR:
         cout << "RULE DONE" << endl;
         break;
      case NEW_NODE_INSTR:
         cout << "NEW NODE TO " << reg_string(new_node_reg(pc)) << endl;
         break;
      case NEW_AXIOMS_INSTR: {
         cout << "NEW AXIOMS" << endl;
         const pcounter end(pc + new_axioms_jump(pc));
         pcounter p(pc);
         p += NEW_AXIOMS_BASE;

         while(p < end) {
            // read axions until the end!
            predicate_id pid(predicate_get(p, 0));
            predicate *pred(predicates[pid]);
            print_tab(tabcount+1);
            cout << tuple_names[pid] << "(";

            p++;

            for(size_t i(0), num_fields(pred->num_args);
                  i != num_fields;
                  ++i)
            {
               size_t t(pred->get_field_type(i));
               print_axiom_data(p, t);

               if(i != num_fields-1)
                  cout << ", ";
            }
            cout << ")" << endl;
         }
         }
         break;
      case SEND_DELAY_INSTR:
         cout << "SEND " << reg_string(send_delay_msg(pc))
              << " TO " << reg_string(send_delay_dest(pc))
              << " WITH DELAY " << send_delay_time(pc) << "ms"
              << endl;

         break;
      case PUSH_INSTR:
         cout << "PUSH" << endl;
         break;
      case PUSHN_INSTR:
         cout << "PUSHN " << push_n(pc) << endl;
         break;
      case POP_INSTR:
         cout << "POP" << endl;
         break;
      case PUSH_REGS_INSTR:
         cout << "PUSH REGS" << endl;
         break;
      case POP_REGS_INSTR:
         cout << "POP REGS" << endl;
         break;
      case CALLF_INSTR: {
            const callf_id id(callf_get_id(pc));

            cout << "CALLF " << to_string((int)id) << endl;
         }
         break;
      case MAKE_STRUCTR_INSTR:
         cout << "MAKE STRUCTR TO " << reg_string(pcounter_reg(pc + instr_size + type_size)) << endl;
         break;
      case MAKE_STRUCTF_INSTR:
         cout << "MAKE STRUCTF TO " << field_string(pc + instr_size) << endl;
         break;
      case STRUCT_VALRR_INSTR:
         cout << "STRUCT VALRR " << struct_val_idx(pc) << " FROM " << reg_string(pcounter_reg(pc + instr_size + count_size)) <<
            " TO " << reg_string(pcounter_reg(pc + instr_size + count_size + reg_val_size)) << endl;
         break;
      case STRUCT_VALFR_INSTR:
         cout << "STRUCT VALFR " << struct_val_idx(pc) << " FROM " << field_string(pc + instr_size + count_size) <<
            " TO " << reg_string(pcounter_reg(pc + instr_size + count_size + field_size)) << endl;
         break;
      case STRUCT_VALRF_INSTR:
         cout << "STRUCT VALRF " << struct_val_idx(pc) << " FROM " << reg_string(pcounter_reg(pc + instr_size + count_size)) <<
            " TO " << field_string(pc + instr_size + count_size + reg_val_size) << endl;
         break;
      case STRUCT_VALRFR_INSTR:
         cout << "STRUCT VALRF " << struct_val_idx(pc) << " FROM " << reg_string(pcounter_reg(pc + instr_size + count_size)) << " (REFS)" << endl;
         break;
      case STRUCT_VALFF_INSTR:
         cout << "STRUCT VALFF " << struct_val_idx(pc) << " FROM " << field_string(pc + instr_size + count_size) <<
            " TO " << field_string(pc + instr_size + count_size + field_size) << endl;
         break;
      case STRUCT_VALFFR_INSTR:
         cout << "STRUCT VALFF " << struct_val_idx(pc) << " FROM " << field_string(pc + instr_size + count_size) <<
            " TO " << field_string(pc + instr_size + count_size + field_size) << " (REFS)" << endl;
         break;
      case MVINTFIELD_INSTR: {
         const int_val i(pcounter_int(pc + instr_size));
         const string field(field_string(pc + instr_size + int_size));
         cout << "MVINTFIELD " << int_string(i) << " TO " << field << endl;
      }
      break;
      case MVFIELDFIELD_INSTR: {
         const string field1(field_string(pc + instr_size));
         const string field2(field_string(pc + instr_size + field_size));
         cout << "MVFIELDFIELD " << field1 << " TO " << field2 << endl;
      }
      break;
      case MVFIELDFIELDR_INSTR: {
         const string field1(field_string(pc + instr_size));
         const string field2(field_string(pc + instr_size + field_size));
         cout << "MVFIELDFIELD " << field1 << " TO " << field2 << " (REFS)" << endl;
      }
      break;
      case MVINTREG_INSTR: {
         const int_val i(pcounter_int(pc + instr_size));
         const reg_num reg(pcounter_reg(pc + instr_size + int_size));

         cout << "MVINTREG " << int_string(i) << " TO " << reg_string(reg) << endl;
      }
      break;
      case MVFIELDREG_INSTR: {
         const string field(field_string(pc + instr_size));
         const reg_num reg(pcounter_reg(pc + instr_size + field_size));

         cout << "MVFIELDREG " << field << " TO " << reg_string(reg) << endl;
      }
      break;
      case MVPTRREG_INSTR: {
         const ptr_val p(pcounter_ptr(pc + instr_size));
         const reg_num reg(pcounter_reg(pc + instr_size + ptr_size));

         cout << "MVPTRREG " << ptr_string(p) << " TO " << reg_string(reg) << endl;
      }
      break;
   	case MVNILFIELD_INSTR:
         cout << "MVNILFIELD TO " << field_string(pc + instr_size) << endl;
      break;
      case MVNILREG_INSTR:
         cout << "MVNILREG TO " << reg_string(pcounter_reg(pc + instr_size)) << endl;
      break;
      case MVREGFIELD_INSTR:
         cout << "MVREGFIELD " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << field_string(pc + instr_size + reg_val_size) << endl;
      break;
      case MVREGFIELDR_INSTR:
         cout << "MVREGFIELD " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << field_string(pc + instr_size + reg_val_size) << " (REFS)" << endl;
      break;
      case MVHOSTFIELD_INSTR:
         cout << "MVHOSTFIELD TO " << field_string(pc + instr_size) << endl;
      break;
      case MVREGCONST_INSTR:
         cout << "MVREGCONST " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << const_id_string(pcounter_const_id(pc + instr_size + reg_val_size)) << endl;
      break;
      case MVCONSTFIELD_INSTR:
         cout << "MVCONSTFIELD " << const_id_string(pcounter_const_id(pc + instr_size)) << " TO " << field_string(pc + instr_size + const_id_size) << endl;
      break;
      case MVCONSTFIELDR_INSTR:
         cout << "MVCONSTFIELD " << const_id_string(pcounter_const_id(pc + instr_size)) << " TO " << field_string(pc + instr_size + const_id_size) << " (REFS)" << endl;
      break;
      case MVADDRFIELD_INSTR:
         cout << "MVADDRFIELD " << node_string(pcounter_node(pc + instr_size)) << " TO " << field_string(pc + instr_size + node_size) << endl;
      break;
      case MVFLOATFIELD_INSTR:
         cout << "MVFLOATFIELD " << pcounter_float(pc + instr_size) << " TO " << field_string(pc + instr_size + float_size) << endl;
      break;
      case MVFLOATREG_INSTR:
         cout << "MVFLOATREG " << float_string(pcounter_float(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + float_size)) << endl;
      break;
      case MVINTCONST_INSTR:
         cout << "MVINTCONST " << int_string(pcounter_int(pc + instr_size)) << " TO " << const_id_string(pcounter_const_id(pc + instr_size + int_size)) << endl;
         break;
      case MVWORLDFIELD_INSTR:
         cout << "MVWORLDFIELD TO " << field_string(pc + instr_size) << endl;
         break;
      case MVSTACKPCOUNTER_INSTR:
         cout << "MVSTACKPCOUNTER TO " << stack_string(pcounter_stack(pc + instr_size)) << endl;
         break;
      case MVPCOUNTERSTACK_INSTR:
         cout << "MVPCOUNTERSTACK TO " << stack_string(pcounter_stack(pc + instr_size)) << endl;
         break;
      case MVSTACKREG_INSTR:
         cout << "MVSTACKREG " << stack_string(pcounter_stack(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + stack_val_size)) << endl;
         break;
      case MVREGSTACK_INSTR:
         cout << "MVREGSTACK " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << stack_string(pcounter_stack(pc + instr_size + reg_val_size)) << endl;
         break;
      case MVADDRREG_INSTR:
         cout << "MVADDRREG " << node_string(pcounter_node(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + node_size)) << endl;
         break;
      case MVHOSTREG_INSTR:
         cout << "MVHOSTREG TO " << reg_string(pcounter_reg(pc + instr_size)) << endl;
         break;
      case MVINTSTACK_INSTR:
         cout << "MVINTSTACK " << int_string(pcounter_int(pc + instr_size)) << " TO " << stack_string(pcounter_stack(pc + instr_size + int_size)) << endl;
         break;
      case MVARGREG_INSTR:
         cout << "MVARGREG " << argument_string(pcounter_argument_id(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + argument_size)) << endl;
         break;
      case ADDRNOTEQUAL_INSTR:
         cout << operation_string(pc, "ADDR NOT EQUAL") << endl;
         break;
      case ADDREQUAL_INSTR:
         cout << operation_string(pc, "ADDR EQUAL") << endl;
         break;
      case INTMINUS_INSTR:
         cout << operation_string(pc, "INT MINUS") << endl;
         break;
      case INTEQUAL_INSTR:
         cout << operation_string(pc, "INT EQUAL") << endl;
         break;
      case INTNOTEQUAL_INSTR:
         cout << operation_string(pc, "INT NOT EQUAL") << endl;
         break;
      case INTPLUS_INSTR:
         cout << operation_string(pc, "INT PLUS") << endl;
         break;
      case INTLESSER_INSTR:
         cout << operation_string(pc, "INT LESSER") << endl;
         break;
      case INTGREATEREQUAL_INSTR:
         cout << operation_string(pc, "INT GREATER EQUAL") << endl;
         break;
      case BOOLOR_INSTR:
         cout << operation_string(pc, "BOOL OR") << endl;
         break;
      case INTLESSEREQUAL_INSTR:
         cout << operation_string(pc, "INT LESSER EQUAL") << endl;
         break;
      case INTGREATER_INSTR:
         cout << operation_string(pc, "INT GREATER") << endl;
         break;
      case INTMUL_INSTR:
         cout << operation_string(pc, "INT MUL") << endl;
         break;
      case INTDIV_INSTR:
         cout << operation_string(pc, "INT DIV") << endl;
         break;
      case INTMOD_INSTR:
         cout << operation_string(pc, "INT MOD") << endl;
         break;
      case FLOATPLUS_INSTR:
         cout << operation_string(pc, "FLOAT PLUS") << endl;
         break;
      case FLOATMINUS_INSTR:
         cout << operation_string(pc, "FLOAT MINUS") << endl;
         break;
      case FLOATMUL_INSTR:
         cout << operation_string(pc, "FLOAT MUL") << endl;
         break;
      case FLOATDIV_INSTR:
         cout << operation_string(pc, "FLOAT DIV") << endl;
         break;
      case FLOATEQUAL_INSTR:
         cout << operation_string(pc, "FLOAT EQUAL") << endl;
         break;
      case FLOATNOTEQUAL_INSTR:
         cout << operation_string(pc, "FLOAT NOT EQUAL") << endl;
         break;
      case FLOATLESSER_INSTR:
         cout << operation_string(pc, "FLOAT LESSER") << endl;
         break;
      case FLOATLESSEREQUAL_INSTR:
         cout << operation_string(pc, "FLOAT LESSER EQUAL") << endl;
         break;
      case FLOATGREATER_INSTR:
         cout << operation_string(pc, "FLOAT GREATER") << endl;
         break;
      case FLOATGREATEREQUAL_INSTR:
         cout << operation_string(pc, "FLOAT GREATER EQUAL") << endl;
         break;
      case MVREGREG_INSTR:
         cout << "MVREGREG " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + reg_val_size)) << endl;
         break;
      case BOOLEQUAL_INSTR:
         cout << operation_string(pc, "BOOL EQUAL") << endl;
         break;
      case BOOLNOTEQUAL_INSTR:
         cout << operation_string(pc, "BOOL NOT EQUAL") << endl;
         break;
      case HEADRR_INSTR:
         cout << "HEADRR " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + reg_val_size)) << endl;
         break;
      case HEADFR_INSTR:
         cout << "HEADFR " << field_string(pc + instr_size) + " TO " << reg_string(pcounter_reg(pc + instr_size + field_size)) << endl;
         break;
      case HEADFF_INSTR:
         cout << "HEADFF " << field_string(pc + instr_size) << " TO " << field_string(pc + instr_size + field_size) << endl;
         break;
      case HEADRF_INSTR:
         cout << "HEADRF " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << field_string(pc + instr_size + reg_val_size) << endl;
         break;
      case HEADFFR_INSTR:
         cout << "HEADFF " << field_string(pc + instr_size) << " TO " << field_string(pc + instr_size + field_size) << " (REFS)" << endl;
         break;
      case HEADRFR_INSTR:
         cout << "HEADRF " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << field_string(pc + instr_size + reg_val_size) << " (REFS)" << endl;
         break;
      case TAILRR_INSTR:
         cout << "TAILRR " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + reg_val_size)) << endl;
         break;
      case TAILFR_INSTR:
         cout << "TAILFR " << field_string(pc + instr_size) << " TO " << reg_string(pcounter_reg(pc + instr_size + field_size)) << endl;
         break;
      case TAILFF_INSTR:
         cout << "TAILFF " << field_string(pc + instr_size) << " TO " << field_string(pc + instr_size + field_size) << endl;
         break;
      case TAILRF_INSTR:
         cout << "TAILRF " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << field_string(pc + instr_size + reg_val_size) << endl;
         break;
      case MVWORLDREG_INSTR:
         cout << "MVWORLDREG TO " << reg_string(pcounter_reg(pc + instr_size)) << endl;
         break;
      case MVCONSTREG_INSTR:
         cout << "MVCONSTREG " << const_id_string(pcounter_const_id(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + const_id_size)) << endl;
         break;
      case CONSRRR_INSTR:
         cout << "CONSRRR (" << reg_string(pcounter_reg(pc + instr_size + type_size)) << "::" << reg_string(pcounter_reg(pc + instr_size + type_size + reg_val_size)) << ") TO " << reg_string(pcounter_reg(pc + instr_size + type_size + 2 * reg_val_size)) << endl;
         break;
      case CONSRFF_INSTR:
         cout << "CONSRFF (" << reg_string(pcounter_reg(pc + instr_size)) << "::" << field_string(pc + instr_size + reg_val_size) << ") TO " <<
            field_string(pc + instr_size + reg_val_size + field_size) << endl;
         break;
      case CONSFRF_INSTR:
         cout << "CONSFRF (" << field_string(pc + instr_size) << "::" << reg_string(pcounter_reg(pc + instr_size + field_size)) << ") TO " <<
            field_string(pc + instr_size + field_size + reg_val_size) << endl;
         break;
      case CONSFFR_INSTR:
         cout << "CONSFFR (" << field_string(pc + instr_size) << "::" << field_string(pc + instr_size + field_size) << ") TO " <<
            reg_string(pcounter_reg(pc + instr_size + 2 * field_size)) << endl;
         break;
      case CONSRRF_INSTR:
         cout << "CONSRRF (" << reg_string(pcounter_reg(pc + instr_size)) << "::" << reg_string(pcounter_reg(pc + instr_size + reg_val_size)) << ") TO " <<
            field_string(pc + instr_size + 2 * reg_val_size) << endl;
         break;
      case CONSRFR_INSTR:
         cout << "CONSRFR (" << reg_string(pcounter_reg(pc + instr_size)) << "::" << field_string(pc + instr_size + reg_val_size) << ") TO " <<
            reg_string(pcounter_reg(pc + instr_size + reg_val_size + field_size)) << endl;
         break;
      case CONSFRR_INSTR:
         cout << "CONSFRR (" << field_string(pc + instr_size + type_size) << "::" << reg_string(pcounter_reg(pc + instr_size + type_size + field_size)) << ") TO " <<
            reg_string(pcounter_reg(pc + instr_size + type_size + field_size + reg_val_size)) << endl;
         break;
      case CONSFFF_INSTR:
         cout << "CONSFFF (" << field_string(pc + instr_size) << "::" << field_string(pc + instr_size + field_size) << ") TO " <<
            field_string(pc + instr_size + 2 * field_size) << endl;
         break;
      case MVFLOATSTACK_INSTR:
         cout << "MVFLOATSTACK " << float_string(pcounter_float(pc + instr_size)) << " TO " << stack_string(pcounter_stack(pc + instr_size + float_size)) << endl;
         break;
      case SET_PRIORITY_INSTR:
         cout << "SET PRIORITY " << reg_string(pcounter_reg(pc + instr_size)) << " ON " << reg_string(pcounter_reg(pc + instr_size + reg_val_size)) << endl;
         break;
      case SET_PRIORITYH_INSTR:
         cout << "SET PRIORITY " << reg_string(pcounter_reg(pc + instr_size)) << endl;
         break;
      case ADD_PRIORITY_INSTR:
         cout << "ADD PRIORITY " << reg_string(pcounter_reg(pc + instr_size)) << " ON " << reg_string(pcounter_reg(pc + instr_size + reg_val_size)) << endl;
         break;
      case ADD_PRIORITYH_INSTR:
         cout << "ADD PRIORITY " << reg_string(pcounter_reg(pc + instr_size)) << endl;
         break;
      case STOP_PROG_INSTR:
         cout << "STOP PROGRAM" << endl;
         break;
      case CPU_ID_INSTR:
         cout << "CPU ID OF " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + reg_val_size)) << endl;
         break;
      case NODE_PRIORITY_INSTR:
         cout << "NODE PRIORITY OF " << reg_string(pcounter_reg(pc + instr_size)) << " TO " << reg_string(pcounter_reg(pc + instr_size + reg_val_size)) << endl;
         break;
      case JUMP_INSTR:
         cout << "JUMP TO " << jump_get(pc, instr_size) << endl;
         break;
		default:
		  cerr << "unknown instruction code" << endl;
	}
	
   return advance(pc);
}

pcounter
instr_print_simple(pcounter pc, const int tabcount, ostream& cout)
{
   return instr_print(pc, false, tabcount, cout);
}
 
byte_code
instrs_print(byte_code code, const code_size_t len, const int tabcount, ostream& cout)
{
   pcounter pc = code;
   pcounter until = code + len;
   
	for (; pc < until; ) {
		pc = instr_print(pc, true, tabcount, cout);
	}
	
   return until;
}
