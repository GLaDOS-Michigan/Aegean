#!/usr/bin/env python

import os, sys, threading
import commands
import utils
import time

#print 'Number of clients: ' + str(num_clients)

#exit(0)
def changeParam(param, value, backend=False):
    f = property_file
    f += '.backend' if backend else ''
    os.system("cat %s | sed -e 's/%s = .*/%s = %s/' > test && cp test %s" % (f, param, param, value, f))

def copy_log():
    print ''
    ml = utils.get_machines()

    os.system('rm -rf exp_log')
    for m in ml:
        os.system('mkdir -p exp_log/%s 2> /dev/null > /dev/null' % m.strip())

    cmd_template = 'scp -r %%s:%s/%s/* exp_log/%%s/ 2> /dev/null > /dev/null' % (FILEPATH, LOG_DIR)
    cmd_template_for_local = 'cp -r %s/%s/* exp_log/%%s/ 2> /dev/null > /dev/null' % (FILEPATH, LOG_DIR)

    for m in ml:
        if m.startswith(HOSTNAME):
            cmd = cmd_template_for_local % m
        else:
            cmd = cmd_template % (m, m)

        print 'Copying logs from ' + m
        os.system(cmd)
        print cmd
    if m.startswith(HOSTNAME):
        os.system('ssh %s \'rm -rf %s/%s/*\'' % (m, FILEPATH, LOG_DIR))
    print 'Finished.'

def wait_for_exp_to_finish(num_clients, clients):
    done = 0
    done_map = {}
    compl_map = {}
    last_iter = 0
    num_times_no_progress = -1
    for i in range(0, num_clients):
        done_map[i] = False
        compl_map[i] = 0

    while done < num_clients/8:
        print 'Finished clients: %s total clients: %s' % (done, num_clients)
        i = 0
        j = 0
        while i < num_clients * clients_per_process:
            # Change me!!!!
            current_machine = clients[i % len(clients)]  # clients: utils.get_machines_from_file('clients'). TODO it seems it works
            # correct since start_client also divides by 256 but why 256? Dividing by number of client machines is better
            if done_map[j] == False:  # TODO can this code get all clients info from one machine?
                cmd = 'ssh %s \'grep "#req" %s/%s/client_%s/client%s.txt | wc -l\'' % (
                    current_machine, FILEPATH, LOG_DIR, str(i), str(i))
                out = commands.getstatusoutput(cmd)
                try:
                    finished = int(out[1])
                except:
                    print 'Something wrong with the output: %s' % out[1]
                    start_a_client(i)  # Sometimes it gives error: 'ssh_exchange_identification: Connection closed by remote host', needs to run corresponding command
                    finished = 0;
                compl_map[j] = finished
                print 'Client %s progress: %s%%' % (str(i), str(finished * 100 / TOTAL_OPS))
                if finished >= TOTAL_OPS and done_map[j] == False:
                    done += 1
                    done_map[j] = True
            j += 1
            i += clients_per_process * 8  # int(sys.argv[2]) -> clients_per_process in the modified_super.py, which is max(1, num_clients/16)
            # which is max(1, num_clients /16)

        if last_iter == sum(compl_map.values()):
            num_times_no_progress += 1
            if num_times_no_progress >= NO_PROGRESS_WINDOW:
                raise Exception
        else:
            num_times_no_progress = 0
            last_iter = sum(compl_map.values())

        time.sleep(5)

    print (len(filter(lambda x: x is False, done_map.values())) == 0)
    print 'All clients finished.'

def start_a_client(i):
    current_machine = CLIENT_NAME[i % len(CLIENT_NAME)]  # TODO
    # <idStart> <idGap> <idEnd> 1,2 and 3. arguments to BenchClientMulti. BenchCLientMulti creates a thread for each id
    print 'start client ' + str(i) + ' at ' + current_machine
    cmd_template = 'java -ea -Xmx32768m -cp lib/log4j-1.2.15.jar:lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:' \
                   'lib/CoDec-build17-jdk13.jar:lib/netty-3.2.1.Final.jar:lib/h2.jar:lib/zookeeper-3.2.2.jar:' \
                   'lib/commons-javaflow-2.0-SNAPSHOT.jar:lib/commons-logging-1.1.3.jar -Djava.library.path=. ' \
                   'Applications.tpcw_webserver.rbe.RBE -EB Applications.tpcw_webserver.rbe.EBTPCW1Factory %s %s %s %s %s/%s/client_%s ' \
                   '-OUT run1.m -MAXERROR 100000 -RU 1 -MI 1000 -RD 1 -WWW http://128.83.122.190:8389/ -CUST 144000 ' \
                   '-ITEM 10000 -GETIM false -TT 0.0 > %s/%s/client_%s/out 2> %s/%s/client_%s/err &' % (
                    property_file, str(i), str(i+clients_per_process), TOTAL_OPS, FILEPATH, LOG_DIR, str(i), FILEPATH, LOG_DIR,
                    str(i), FILEPATH, LOG_DIR, str(i))
    cmd = 'ssh %s \'cd %s; mkdir -p %s/%s/client_%s; %s %s %s\'' % (
        current_machine, FILEPATH, FILEPATH, LOG_DIR, str(i), record_start, cmd_template, record_end)
    print cmd
    os.system(cmd)

if len(sys.argv) != 4:
    print "Must Provide number of clients, number of clients per process, and property file"
    sys.exit(1)

FILEPATH = os.path.dirname(os.path.realpath(__file__))
num_clients = int(sys.argv[1])
clients_per_process = int(sys.argv[2])
property_file = sys.argv[3]
num_client_process = (num_clients / clients_per_process)
# USERNAME = 'manos'
# EXP_DIR = 'echoMT_test'
LOG_DIR = 'jh_log'
HOSTNAME = commands.getoutput('hostname')
NO_PROGRESS_WINDOW = 2  # 10
CLIENT_NAME = utils.get_machines_from_file('clients')
num_client_machines = len(CLIENT_NAME)
TOTAL_OPS = 250

machine_txt_file = '%s/jh_log/machine.txt' % FILEPATH
# TODO why there is %N in two lines below and why 13?
# TODO we should update linux server machines
print 'num_clients = ' + str(num_clients)
changeParam("numberOfClients", num_clients)
record_start = 'startTime=`date +%%s%%N`; echo startTime ${startTime:0:13} >> %s;' % machine_txt_file
record_end = 'endTime=`date +%%s%%N`; echo endTime ${endTime:0:13} >> %s;' % machine_txt_file

i = 0
threads = []
while i < num_client_process * clients_per_process:
    # start_a_client(i)
    t = threading.Thread(target=start_a_client, args=(i,))
    threads.append(t)
    i += clients_per_process
    t.start()
    if len(threads) >= 10:
        for tt in threads:
            tt.join()
        threads = []

for t in threads:
    t.join()

try:
    time.sleep(5)
    s_ts = time.time()
    wait_for_exp_to_finish(num_clients, CLIENT_NAME)
    print 'test'
    e_ts = time.time()
    print "Total time: ", e_ts - s_ts
    os.system('python stop_all.py')
    copy_log()
except:
    print "EXCEPT"
    os.system('python stop_all.py')
    copy_log()

#
# try:
#     #TODO change the hardcode
#     # -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5000
#     os.system('ssh localhost \"cd %s; java -ea -Xmx32768m -cp lib/log4j-1.2.15.jar:lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:lib/CoDec-build17-jdk13.jar:lib/netty-3.2.1.Final.jar:lib/h2.jar:lib/zookeeper-3.2.2.jar:lib/commons-javaflow-2.0-SNAPSHOT.jar:lib/commons-logging-1.1.3.jar -Djava.library.path=. Applications.tpcw_webserver.rbe.RBE -EB Applications.tpcw_webserver.rbe.EBTPCW1Factory %s %s -OUT run1.m -MAXERROR 100000 -RU 1 -MI 1000 -RD 1 -WWW http://128.83.122.190:8389/ -CUST 144000 -ITEM 10000 -GETIM false -TT 0.0 > jh_log/client_out 2> jh_log/client_err\" &' % (
#         FILEPATH, property_file, str(num_clients)))
#
#     time.sleep(30)#295)#TODO why 295?
#     copy_log()
#     os.system('grep \"Done 1\" exp_log/localhost/client_err | wc -l') #TODO update for the server
# except:
#     copy_log()
