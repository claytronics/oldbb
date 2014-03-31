AUTOSRCS=parse/simple.grm.sig parse/simple.grm.sml parse/simple.lex.sml

MLYACC = $(shell which mlyacc)
MLLEX = $(shell which mllex)

ifeq ($(MLYACC), "")
	MLYACC = $(shell which ml-yacc)
endif

ifeq ($(MLLEX), "")
	MLLEX = $(shell which ml-lex)
endif

.phony: mlton smlnj clean

mlton: $(AUTOSRCS) 
	mlton -output meldCompiler -link-opt -ldl sources.mlb

smlnj: $(AUTOSRCS)
	echo 'CM.make "sources.cm"; Top.export();' | sml

parse/simple.grm.sig: parse/simple.grm
	$(MLYACC) parse/simple.grm

parse/simple.grm.sml: parse/simple.grm
	$(MLYACC) parse/simple.grm

parse/simple.lex.sml: parse/simple.lex
	$(MLLEX) parse/simple.lex

clean:
	rm -f meldCompiler* $(AUTOSRCS)
	find . -name ".cm" | xargs rm -rf
