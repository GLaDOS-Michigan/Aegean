# Copyright (c) 2010 Gianluca Ciccarelli
# Module: echo_server
# $Id: Makefile 609 2010-10-07 15:36:16Z porterde $

include ../../../BFT-c99/Makefile.inc
APP := testEchoServer

#SRC := $(shell find ../../../BFT-c99/messages -name '*c' -a \! -name 'main.c' -print ) \
#       $(shell find ../../../BFT-c99/servershim -name '*c' -a \! -name 'main.c' -print) \
#       $(shell find ../../../BFT-c99/clientshim -name '*c' -a \! -name 'main.c' -print) \
#       $(shell find ../../../BFT-c99/util -name '*c' -a \! -name 'main.c' -print) \
#       $(shell find ../../../BFT-c99/membership -name '*c' -a \! -name 'main.c' -print) \
#       $(shell find . -name '*.c' -print) 
OBJ := ${SRC:.c=.o} 


INCLUDE := ${SRC:.c=.h}
SRCDIR = ../../../BFT-c99

DEP := \
	$(SRCDIR)/base_node.o \
	$(SRCDIR)/parameters.o \
	$(SRCDIR)/network/tcp_network.o \
        $(SRCDIR)/configuration/configuration.o \
	$(SRCDIR)/membership/membership.o \
	$(SRCDIR)/membership/principal.o \
	$(SRCDIR)/messages/batch_complete.o \
	$(SRCDIR)/messages/cache_command.o \
	$(SRCDIR)/messages/certificate_entry.o \
	$(SRCDIR)/messages/client_request.o \
	$(SRCDIR)/messages/cp_update.o \
	$(SRCDIR)/messages/cp_token.o \
	$(SRCDIR)/messages/entry.o \
	$(SRCDIR)/messages/fetch_command.o \
	$(SRCDIR)/messages/forward_command.o \
	$(SRCDIR)/messages/last_executed.o \
	$(SRCDIR)/messages/mac_message.o \
	$(SRCDIR)/messages/mac_array_message.o \
	$(SRCDIR)/messages/msg_types.o \
	$(SRCDIR)/messages/next_batch.o \
	$(SRCDIR)/messages/non_determ.o \
	$(SRCDIR)/messages/quorum.o \
	$(SRCDIR)/messages/reply.o \
	$(SRCDIR)/messages/request_cp.o \
	$(SRCDIR)/messages/retransmit.o \
	$(SRCDIR)/messages/verified_message_base.o \
        $(SRCDIR)/servershim/glue_shim_interface.o \
        $(SRCDIR)/servershim/server_shim_interface.o \
        $(SRCDIR)/servershim/shim_base_node.o \
	$(SRCDIR)/servershim/messages/app_state.o \
	$(SRCDIR)/servershim/messages/cp_state.o \
	$(SRCDIR)/servershim/messages/fetch_state.o \
	$(SRCDIR)/servershim/statemgmt/checkpoint_state.o \
	$(SRCDIR)/servershim/statemgmt/next_batch_certificate.o \
	$(SRCDIR)/util/bft_types.o \
	$(SRCDIR)/util/integer_byte_conv.o \
	$(SRCDIR)/util/keygen.o \
	$(SRCDIR)/util/log.o \
	$(SRCDIR)/util/roles.o \
	$(SRCDIR)/util/rngs.o \
	$(SRCDIR)/util/tokenutil.o \

# Linking
$(APP): $(OBJ) *.c $(DEP)
	$(CC) $(VERBOSE) $(GFLAGS) $(GLIBS) $(LIB) $(OBJ) $(DEP) -o $@ -lcrypto -lssl -L/usr/lib -lglib-2.0 -levent

%.o: %.c 
	$(CC) -c $(GFLAGS) $(DEBUG) $(STD) $(CFLAGS) -o $@ $<

		 
include depend

depend:
	$(MAKE) -C $(SRCDIR)
	$(CC) $(CFLAGS) $(GFLAGS) -MM $(SRC) > depend

clean:
	rm -rf $(OBJ) $(APP).dSYM

cleanall: clean
	rm -rf $(APP) 

# No comment
love:
	@echo ...not war!

