##################################################
# below here should be in an include file
##################################################

#SOURCE := color_accel_tester.bb
SOURCE := ct.bb
#SOURCE := setID.bb
#SOURCE := spanningTree.bb
#SOURCE := network_test_ST.bb
#SOURCE := network_test_ST_host_enabled.bb



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
