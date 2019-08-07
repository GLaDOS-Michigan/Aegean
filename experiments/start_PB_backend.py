#!/usr/bin/env python

import os, sys
import commands
import time
import utils

FILEPATH = os.path.dirname(os.path.realpath(__file__))
SERVER_NAME = utils.get_machines_from_file('execs.backend')
PROPERTY_FILE = sys.argv[1]

all_machines = utils.get_machines()
agent_path = '-agentlib:hprof=cpu=samples,interval=100,depth=10'

# TODO why is this commented out?
num_servers = int(commands.getstatusoutput('grep \"EXEC.* =\" %s | wc -l' % PROPERTY_FILE)[1])
print 'num_servers: ', num_servers

print 'start exec nodes'
no_server_machines = len(SERVER_NAME)
for i in range(0, num_servers):
    current_machine = SERVER_NAME[i % no_server_machines]
    print 'start exec at ' + current_machine
    # The number after test.properties.backend represents the number of objects stored on the backend server for modification
    # ... %s test.properties.backend 1 <--- 10
    debugPort = 5000 + i
    #-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=%s
    cmd_template = 'java -ea %s -Xms2G -Xmx4G -Djava.library.path=. -cp lib/zookeeper-3.2.2.jar:lib/log4j-1.2.15.jar:lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:lib/CoDec-build17-jdk13.jar:lib/netty-3.2.1.Final.jar:lib/h2.jar:lib/zookeeper-3.2.2.jar:lib/commons-javaflow-2.0-SNAPSHOT.jar:lib/commons-logging-1.1.3.jar Applications.benchmark.BenchServer %s %s 1000 10 2> jh_log/backend-exec_%s_err >> jh_log/backend-exec_%s_out &' % (##str(debugPort),
        agent_path, str(i), PROPERTY_FILE, str(i), str(i))
    cmd = 'ssh %s \'cd %s; %s \' ' % (current_machine, FILEPATH, cmd_template)
    print 'Executing command: ' + cmd
    os.system(cmd)
    time.sleep(1)