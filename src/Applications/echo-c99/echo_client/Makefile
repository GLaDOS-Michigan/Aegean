# Copyright (c) 2010 Gianluca Ciccarelli
# Module: echo_server
# $Id: Makefile 603 2010-10-01 07:43:21Z glc $

CC := gcc
CFLAGS := -Wall -Werror
GFLAGS := `pkg-config --cflags glib-2.0`
LIB := -lcrypto -lssl `pkg-config --libs glib-2.0` -levent
APP := testEchoClient

SRC := $(shell find ../../../BFT-c99/messages -name '*c' -a \! -name 'main.c' -print ) \
       $(shell find ../../../BFT-c99/util -name '*c' -a \! -name 'main.c' -print) \
       $(shell find ../../../BFT-c99/membership -name '*c' -a \! -name 'main.c' -print) \
       $(shell find ../../../BFT-c99/clientshim -name '*c' -a \! -name 'main.c' -print) \
       $(shell find . -name '*.c' -print) 
OBJ := ${SRC:.c=.o} \
      parameters.o \
      configuration.o base_node.o tcp_network.o

INCLUDE := ${SRC:.c=.h}
STD := -std=gnu99
DEBUG := -g 
SRCDIR = ../../../BFT-c99

# Linking
.PHONY: $(APP)
$(APP): $(OBJ)
	$(CC) $(LIB) $^ -o $@

%.o: %.c
	$(CC) -c $(GFLAGS) $(DEBUG) $(STD) $(CFLAGS) -o $@ $<

		 
include depend

.PHONY: depend
depend:
	$(CC) -MM $(SRC) > depend


tcp_network.o: $(SRCDIR)/network/tcp_network.c $(SRCDIR)/network/tcp_network.h
	$(CC) -c $(GFLAGS) $(DEBUG) $(STD) $(CFLAGS) $<

base_node.o: $(SRCDIR)/base_node.c $(SRCDIR)/base_node.h
	$(CC) -c $(DEBUG) $(GFLAGS) $(STD) $(CFLAGS) $<

configuration.o: $(SRCDIR)/configuration/configuration.c \
		$(SRCDIR)/configuration/configuration.h
	$(CC) -c $(DEBUG) $(STD) $(CFLAGS) $(GFLAGS) $<

parameters.o: $(SRCDIR)/parameters.c $(SRCDIR)/parameters.h
	$(CC) -c $(GFLAGS) $(DEBUG) $(STD) $(CFLAGS) $<

clean:
	rm -rf $(OBJ) $(APP).dSYM

cleanall: clean
	rm -rf $(APP) 

# No comment
love:
	@echo ...not war!

