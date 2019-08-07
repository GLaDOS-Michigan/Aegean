#!/usr/bin/env python

import os, sys
import commands
import time
import utils

FILEPATH = os.path.dirname(os.path.realpath(__file__))
SERVER_NAME = utils.get_machines_from_file('execs.backend')
VERIFIER_NAME = utils.get_machines_from_file('verifiers.backend')
FILTER_NAME = utils.get_machines_from_file('filters.backend')

all_machines = utils.get_machines()

print 'Killing Java processes on needed machines...'

for m in all_machines:
    #print 'Killing processes on: ' + m
    os.system('ssh %s \'killall java \'' % m)
