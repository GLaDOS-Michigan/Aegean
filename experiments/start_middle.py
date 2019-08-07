#!/usr/bin/env python

import os, sys
import commands
import time
import utils

print "Starting middle"

# These directories don't exist anymore
# os.system('cp *.sh /home/ren/script_backup/')
# os.system('cp *.py /home/ren/script_backup/')
# os.system('cp *.properties* /home/ren/script_backup/')

FILEPATH = os.path.dirname(os.path.realpath(__file__))
SERVER_NAME = utils.get_machines_from_file('execs')
VERIFIER_NAME = utils.get_machines_from_file('verifiers')
FILTER_NAME = utils.get_machines_from_file('filters')

print "servers", SERVER_NAME
print "verifiers", VERIFIER_NAME
print "filters", FILTER_NAME

property_file = sys.argv[1]
backend_property_file = sys.argv[2]
log_prefix = ''
if len(sys.argv) == 4:
    log_prefix = sys.argv[3]
else:
    log_prefix = 'middle'
agent_path = '-agentlib:hprof=cpu=samples,interval=100,depth=10'
agent_path_execs = '-agentlib:hprof=cpu=samples,interval=100,depth=10 -agentpath:~/yjp-9.5.6/bin/linux-x86-64/libyjpagent.so=,port=8498'
num_servers = int(commands.getstatusoutput('grep \"EXEC.* =\" %s | wc -l' % property_file)[1])
print 'num_servers: ', num_servers
num_verifiers = int(commands.getstatusoutput('grep \"VERIFIER.* =\" %s | wc -l' % property_file)[1])
num_filters = int(commands.getstatusoutput('grep \"FILTER.* =\" %s | wc -l' % property_file)[1])

num_dbclients_on_webserver = int(
    commands.getstatusoutput('grep \"toleratedCrashes =\" %s | wc -l' % backend_property_file)[1])

if num_dbclients_on_webserver < 1:
    print 'Invalid property file.'
    exit(0)

print 'Number of DB clients on Web Server is: ' + str(num_dbclients_on_webserver)
backendLoopRatio = 2
print 'Start exec nodes with ' + str(backendLoopRatio)
no_server_machines = len(SERVER_NAME)
for i in range(0, num_servers):
    current_machine = SERVER_NAME[i % no_server_machines]
    debugPort = 5000 + i
    #-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=%s
    cmd_template = 'java -ea %s -Djava.library.path=. -Xmx10240m -cp lib/zookeeper-3.2.2.jar:lib/log4j-1.2.15.jar:lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:lib/CoDec-build17-jdk13.jar:lib/netty-3.2.1.Final.jar:lib/javax.annotation-api-1.2.jar:lib/javax.mail.glassfish-1.4.1.v201005082020.jar:lib/javax.security.auth.message-1.0.0.v201108011116.jar:lib/javax.servlet-api-3.1.0.jar:lib/javax.transaction-api-1.2.jar:lib/javax.websocket-api-1.0.jar:lib/jcl-over-slf4j-1.6.1.jar:lib/npn-api-1.1.0.v20120525.jar:lib/slf4j-api-1.6.1.jar:lib/commons-io-1.4.jar:lib/commons-javaflow-2.0-SNAPSHOT.jar:lib/commons-lang3-3.3.jar:lib/commons-logging-1.1.3.jar Applications.benchmark.BenchServer %%s %s 1000 10 %s 0 %%s %s > jh_log/%s-exec_%%s_out 2> jh_log/%s-exec_%%s_err &' % ( #str(debugPort),
    agent_path, property_file, backend_property_file, str(backendLoopRatio), log_prefix, log_prefix)
    cmd = cmd_template % (str(i), str(i), str(i), str(i))

    remote_cmd = 'ssh %s \' cd %s; %s \' ' % (current_machine, FILEPATH, cmd)
    print 'Executing command: ' + remote_cmd
    os.system(remote_cmd)

    time.sleep(1)

time.sleep(1)

print 'Start verifier node'
no_verifier_machines = len(VERIFIER_NAME)
for i in range(0, num_verifiers):
    current_machine = VERIFIER_NAME[i % no_verifier_machines]
    cmd_template = 'java %s -Djava.library.path=. -Xmx1024m -cp lib/log4j-1.2.15.jar:lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:lib/CoDec-build17-jdk13.jar:lib/netty-3.2.1.Final.jar BFT.verifier.VerifierBaseNode %%s %s > jh_log/%s-verifier_%%s_out 2> jh_log/%s-verifier_%%s_err &' % (
    agent_path, property_file, log_prefix, log_prefix)
    cmd = cmd_template % (str(i), str(i), str(i))
    remote_cmd = 'ssh %s \' cd %s; %s \' ' % (current_machine, FILEPATH, cmd)
    print 'Executing command: ' + remote_cmd
    os.system(remote_cmd)

time.sleep(1)

print 'Start filter node'
no_filter_machines = len(FILTER_NAME)
for i in range(0, num_filters):
    debugPort = 5003 + i
    current_machine = FILTER_NAME[i % no_filter_machines]
    cmd_template = 'java %s -Xmx10240m -Djava.library.path=. -cp conf:lib/log4j-1.2.15.jar:lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:lib/CoDec-build17-jdk13.jar:lib/netty-3.2.1.Final.jar BFT.filter.FilterBaseNode %%s %s > jh_log/%s-filter_%%s_out 2> jh_log/%s-filter_%%s_err &' % (#str(debugPort),
    agent_path, property_file, log_prefix, log_prefix)
    cmd = cmd_template % (str(i), str(i), str(i))
    remote_cmd = 'ssh %s \' cd %s; %s \' ' % (current_machine, FILEPATH, cmd)
    print 'Executing command: ' + remote_cmd
    os.system(remote_cmd)
