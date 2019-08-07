#!/usr/bin/env python

import os, sys, commands, threading, time
import utils

FILEPATH = os.path.dirname(os.path.realpath(__file__))
HOSTNAME = commands.getoutput('hostname')

dirs = ['keys', 'lib']
dstuff = '{keys,lib}'

files = ['*.properties*', '*.backend', "execs", "verifiers", "filters", "clients"]
fstuff = '{*.properties*,*.backend,execs,verifiers,filters}'

ml = utils.get_machines()

print ("Copying from", str(ml))

os.system("cp ../dist/lib/bft.jar .")
os.system('rm lib/bft.jar')
os.system('cp bft.jar lib/')


def copy_to_machine(machine):
    cmd_template = 'ssh %s \' mkdir -p %s/%%s \' ' % (machine, FILEPATH)
    print "Copying to " + machine	
    #print 'ssh %s \' mkdir -p %s\' ' % (machine, FILEPATH)
    os.system('ssh %s \' mkdir -p %s\' ' % (machine, FILEPATH))
    #print 'ssh %s \' mkdir -p %s%s\' ' % (machine, FILEPATH, '/jh_log')
    os.system('ssh %s \' mkdir -p %s%s\' ' % (machine, FILEPATH, '/jh_log'))
    #print 'ssh %s \' rm -rf %s%s/*\' ' % (machine, FILEPATH, '/jh_log')
    os.system('ssh %s \' rm -rf %s%s/*\' ' % (machine, FILEPATH, '/jh_log'))

    for d in dirs:
        cmd = cmd_template % d
        #print cmd
        os.system(cmd)

    cmd_template = 'rsync -r %%s/* %s:%s/%%s/ ' % (machine, FILEPATH)

    for d in dirs:
        cmd = cmd_template % (d, d)
        #print cmd
        os.system(cmd)

    cmd_template = 'rsync -r %%s %s:%s/' % (machine, FILEPATH)

    for d in files:
        cmd = cmd_template % d
        #print cmd
        os.system(cmd)

    cmd_template = 'rsync lib/bft.jar %s:%s/lib/bft.jar' % (machine, FILEPATH)
    cmd = cmd_template
    #print cmd
    os.system(cmd)


threads = []
for machine in ml:
    if machine.startswith(HOSTNAME):
        continue
    t = threading.Thread(target=copy_to_machine, args=(machine,))
    threads.append(t)
    t.start()

for t in threads:
    t.join()

print 'Done copy files.'
