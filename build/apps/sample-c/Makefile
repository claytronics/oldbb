##################################################
# below here should be in an include file
##################################################

#SOURCE := ct.bb
#SOURCE := color_accel_tester.bb
#SOURCE := setID.bb
#SOURCE := spanningTree.bb
#SOURCE := net_test_with_timeout.bb
#SOURCE := timeout_readme.bb
SOURCE := neighbor_count.bb

# add any extra of yours here
#ALLBBSRC  += color.bb
#ALLCSRC   +=
#MELDBBSRC  += color.bb
#MELDCSRC   +=
#BBINCLUDE += 
#ALLO +=
#MELDO +=

#CFLAGS_OPTIONS = -DCLOCK_SYNC
CFLAGS_OPTIONS += -DLOG_DEBUG  -Wl,-u,vfprintf -lprintf_flt -lm
CFLAGS_OPTIONS += -DTESTING

include Makefile.header
