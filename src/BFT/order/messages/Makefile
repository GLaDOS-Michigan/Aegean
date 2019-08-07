######################################################################
# Generic C++ Project Makefile v2.1
# Copyright (c) 1998-2001 Vokimon/KKEPerian UNLTD
# Copyright (c) 1998-2001 Helly/Patata Productions
######################################################################
# $Id: Makefile 2197 2009-01-08 18:16:55Z riche $
#

# Change the following options as suited to your project

# The name of the executable file
EXEC=test

# Library flags for the linker
LDLIBS=
# Flags for the C compiler
CFLAGS=-Wall -O2 -I. -I/usr/local/include -I../../.. -g
LDFLAGS= 

# Change the following options as suited to your system

# The C compiler
CC=gcc
# The C++ compiler
CPPC=g++
# Command used to delete files
RM=rm -rf

CPPSOURCES:= cppTest.cc parameters.cc Commit.cc
OBJSCPP:=$(CPPSOURCES:.cc=.o)

default: depend ${EXEC}

depend: .depend
	@echo --- Generating dependencies
	$(CPPC) $(CFLAGS) -MM $(CPPSOURCES) -D_KKEP_BUSCANT_DEPENDENCIES > .depend

.depend:
	touch .depend

clean: 
	-$(RM) *.o
	-$(RM) ${EXEC}

include .depend

${EXEC}:  $(OBJSCPP)
	@echo --- Linking $@
	${CPPC} $(LDFLAGS) $(OBJSCPP) ${LDLIBS} -o ${EXEC}

$(OBJSCPP) : %.o: %.cc
	@echo --- Compiling $<
	${CPPC} ${CFLAGS} -c $< -o $@




