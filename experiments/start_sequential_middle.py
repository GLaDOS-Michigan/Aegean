#!/usr/bin/env python

import os, sys
import commands
import time
import utils

print "Starting sequential middle"

FILEPATH = os.path.dirname(os.path.realpath(__file__))
SERVER_NAME = utils.get_machines_from_file('execs')
ORDER_NAME = utils.get_machines_from_file('orders')
FILTER_NAME = utils.get_machines_from_file('filters')

print "servers", SERVER_NAME
print "orders", ORDER_NAME
print "filters", FILTER_NAME

property_file = sys.argv[1]
backend_property_file = sys.argv[2]
if len(sys.argv) == 4:
    log_prefix = sys.argv[3]
else:
    log_prefix = 'middle'

agent_path = '-agentlib:hprof=cpu=samples,interval=100,depth=10'
agent_path_execs = '-agentlib:hprof=cpu=samples,interval=100,depth=10 -agentpath:~/yjp-9.5.6/bin/linux-x86-64/libyjpagent.so=,port=8498'
num_servers = int(commands.getstatusoutput('grep \"EXEC.* =\" %s | wc -l' % property_file)[1])
print 'num_servers: ', num_servers
num_orders = int(commands.getstatusoutput('grep \"ORDER.* =\" %s | wc -l' % property_file)[1])
num_filters = int(commands.getstatusoutput('grep \"FILTER.* =\" %s | wc -l' % property_file)[1])

#TODO below is not necessary for now
# num_dbclients_on_webserver = int(
#     commands.getstatusoutput('grep \"toleratedCrashes =\" %s.backend| wc -l' % property_file)[1])
#
# if num_dbclients_on_webserver < 1:
#     print 'Invalid property file.'
#     exit(0)
#
# print 'Number of DB clients on Web Server is: ' + str(num_dbclients_on_webserver)

print 'Start exec(server) nodes'
no_server_machines = len(SERVER_NAME)
backendLoopRatio = 10
for i in range(0, num_servers):
    current_machine = SERVER_NAME[i % no_server_machines]
    debugPort = 5000 + i
    #-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=%s
    cmd_template = 'java -ea %s -Djava.library.path=. -Xmx4096m -cp lib/zookeeper-3.2.2.jar:lib/log4j-1.2.15.jar:lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:lib/CoDec-build17-jdk13.jar:lib/netty-3.2.1.Final.jar:lib/javax.annotation-api-1.2.jar:lib/javax.mail.glassfish-1.4.1.v201005082020.jar:lib/javax.security.auth.message-1.0.0.v201108011116.jar:lib/javax.servlet-api-3.1.0.jar:lib/javax.transaction-api-1.2.jar:lib/javax.websocket-api-1.0.jar:lib/jcl-over-slf4j-1.6.1.jar:lib/npn-api-1.1.0.v20120525.jar:lib/slf4j-api-1.6.1.jar:lib/commons-io-1.4.jar:lib/commons-javaflow-2.0-SNAPSHOT.jar:lib/commons-lang3-3.3.jar:lib/commons-logging-1.1.3.jar Applications.benchmark.BenchServerUpRight %s %%s 1000 10 %s %s %s 0 %%s %s > jh_log/%s-exec_%%s_out 2> jh_log/%s-exec_%%s_err &' % ( #str(debugPort),
    agent_path, property_file, 'jh_log', 'jh_log', backend_property_file, str(backendLoopRatio), log_prefix, log_prefix)# probably server nodes will also need this when we implement nested request: property_file + '.backend')
    cmd = cmd_template % (str(i), str(i), str(i), str(i))

    remote_cmd = 'ssh %s \' cd %s; %s \' ' % (current_machine, FILEPATH, cmd)
    print 'Executing command: ' + remote_cmd
    os.system(remote_cmd)

    time.sleep(1)

time.sleep(1)

print 'Start order nodes'
no_order_machines = len(ORDER_NAME)
for i in range(0, num_orders):
    current_machine = ORDER_NAME[i % no_order_machines]
    debugPort = 5003 + i
    #-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=%s
    cmd_template = 'java %s -Djava.library.path=. -Xmx1024m -cp lib/log4j-1.2.15.jar:lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:lib/CoDec-build17-jdk13.jar:lib/netty-3.2.1.Final.jar BFT.order.OrderBaseNode %%s %s > jh_log/%s-order_%%s_out 2> jh_log/%s-order_%%s_err &' % (#str(debugPort),
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
