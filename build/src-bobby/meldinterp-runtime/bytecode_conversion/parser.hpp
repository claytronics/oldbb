
#ifndef PARSER_HPP
#define PARSER_HPP

#include "reader.hpp"

#include <string>
#include <vector>
#include <stdexcept>
#include <ostream>

#define VERSION_AT_LEAST(MAJ, MIN)					\
  (major_version > (MAJ) || (major_version == (MAJ) && minor_version >= (MIN)))

class predicate {
public:
  // offset to descriptor
  size_t desc_offset;
  
  // descriptor
  size_t bytecode_offset;
  size_t properties;
  size_t agg_type;
  size_t strat_round;
  size_t num_args;
  size_t num_deltas;

  // id
  predicate_id id;

  // List of argument types
  std::vector<byte> args;

  // Bytecode descriptor
  std::vector<byte_code_el> code;

  inline byte get_field_type(size_t i) { return args[i]; }

  predicate(const size_t _properties, const size_t _agg_type, const 
	    size_t _strat_round, const size_t _num_args, const predicate_id _id,
	    std::vector<byte> _args, std::vector<byte_code_el> _code):
    properties(_properties),
    agg_type(_agg_type),
    strat_round(_strat_round),
    num_args(_num_args),
    num_deltas(0),
    id(_id),
    args(_args),
    code(_code)
  {
  }
};


extern std::vector<predicate*> predicates;
extern std::vector<std::string> tuple_names;

void print_program(const std::string& filename);
byte read_type_from_reader(code_reader& read, bool id);
void read_node_references(byte_code code, code_reader& read);

void generate_tuple_names_list(void);
void generate_bytecode_header(void);
size_t convert_and_generate_bytecode(pcounter pc, predicate_id i);
void print_predicate_descriptor(const predicate_id id);
byte get_type(code_reader& read);

#endif
