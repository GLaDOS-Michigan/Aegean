#!/usr/bin/env python

import os, sys, threading
import commands
import utils
import time


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
    
    startI = 0
    totalClients = num_clients * num_clients_per
    while done < num_clients:#/8:
        print 'Finished clients: %s total clients: %s' % (done, num_clients)
        i = startI
        j = 0
        while i < totalClients:
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
		    if done > 50:
			if done_map[totalClients-1] == True:
			    print 'We are done with a high probability becaise more than 30 clients finished including the last one, no need to check others'
			    done = num_clients
			    break
			else:
                            startI = max(0, totalClients-64)
                            i = max(startI, i)

            j += 1
            i += num_clients_per# *8  # int(sys.argv[2]) -> clients_per_process in the modified_super.py, which is max(1, num_clients/16)
            # which is max(1, num_clients /16)

        if last_iter == sum(compl_map.values()):
            num_times_no_progress += 1
            if num_times_no_progress >= NO_PROGRESS_WINDOW:
                raise Exception
        else:
            num_times_no_progress = 0
            last_iter = sum(compl_map.values())
        if(done < num_clients):#/8):
        	time.sleep(10)
	else:
		time.sleep(5)

    print (len(filter(lambda x: x is False, done_map.values())) == 0)
    print 'All clients finished.'


def start_a_client(i):
    current_machine = CLIENT_NAME[i % len(CLIENT_NAME)]  # TODO
    # <idStart> <idGap> <idEnd> 1,2 and 3. arguments to BenchClientMulti. BenchCLientMulti creates a thread for each id
    if i <= 8 or i % 16 == 0:
        print 'start client ' + str(i) + ' at ' + current_machine
    #    cmd_template = 'java -ea -Xmx32768m -cp lib/log4j-1.2.15.jar:lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:lib/CoDec-build17-jdk13.jar:lib/netty-3.2.1.Final.jar:lib/h2.jar:lib/zookeeper-3.2.2.jar:lib/commons-javaflow-2.0-SNAPSHOT.jar:lib/commons-logging-1.1.3.jar -Djava.library.path=. Applications.benchmark.BenchClientMulti %s %s %s %s %s %s %s %s %s 1 1 0 %s/%s/client_%s > %s/%s/client_%s/out 2> %s/%s/client_%s/err & ' % (
    cmd_template = 'java -ea -Xmx32768m -cp lib/log4j-1.2.15.jar:lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:' \
                   'lib/CoDec-build17-jdk13.jar:lib/netty-3.2.1.Final.jar:lib/h2.jar:lib/zookeeper-3.2.2.jar:' \
                   'lib/commons-javaflow-2.0-SNAPSHOT.jar:lib/commons-logging-1.1.3.jar -Djava.library.path=. ' \
                   'Applications.benchmark.BenchClientMulti %s %s %s %s %s %s %s %s %s 1 1 0 %s/%s/client_%s > ' \
                   '%s/%s/client_%s/out 2> %s/%s/client_%s/err & ' % (
                       PROPERTY_FILE, str(i), str(1), str(i + num_clients_per), NUM_OBJECTS, LOCALITY, REQUEST_SIZE,
                       TOTAL_OPS,
                       LOOP_NUM, FILEPATH, LOG_DIR, str(i), FILEPATH, LOG_DIR, str(i), FILEPATH, LOG_DIR, str(i))
    cmd = 'ssh %s \'cd %s; mkdir -p %s/%s/client_%s; %s %s %s\'' % (
        current_machine, FILEPATH, FILEPATH, LOG_DIR, str(i), record_start, cmd_template, record_end)
    #print cmd
    os.system(cmd)


if len(sys.argv) != 5:
    print "Must provide number of clients, number of clients per process, number of spins, and property file"
    sys.exit(1)

FILEPATH = os.path.dirname(os.path.realpath(__file__))

LOG_DIR = 'jh_log'
HOSTNAME = commands.getoutput('hostname')
# num_clients = int(commands.getstatusoutput('grep \"CLIENT.*.* =\" %s | wc -l' % PROPERTY_FILE)[1]) #this thing is useless

# sys.argv[0] is the name of the script.
o = int(sys.argv[1])  # numbers between start_client and end_client in modified_super.py inclusive
num_clients_per = int(sys.argv[2])  # max(1, num_clients / 16) in modified_super.py
if num_clients_per > 4000:
   num_clients_per = 4000
num_clients = (o / num_clients_per)

# TODO Does this need to match the backend? Trace these parameters to understand
NUM_OBJECTS = 1000
LOCALITY = 1000
# Does this need to match the loops? What does this change?
REQUEST_SIZE = 1000
LOOP_NUM = int(sys.argv[3])
# If you change this, you need to make sure it matches the processing scripts TODO ???
TOTAL_OPS = 1000
PROPERTY_FILE = sys.argv[4]

NO_PROGRESS_WINDOW = 1  # 10

CLIENT_NAME = utils.get_machines_from_file('clients')

num_client_machines = len(CLIENT_NAME)  # TODO it is not used

machine_txt_file = '%s/jh_log/machine.txt' % FILEPATH
# TODO why there is %N in two lines below and why 13?
# TODO we should update linux server machines
record_start = 'startTime=`date +%%s%%N`; echo startTime ${startTime:0:13} >> %s;' % machine_txt_file
#record_start = 'startTime=`date +%%s000`; echo startTime ${startTime:0:13} >> %s;' % machine_txt_file
record_end = 'endTime=`date +%%s%%N`; echo endTime ${endTime:0:13} >> %s;' % machine_txt_file
#record_end = 'endTime=`date +%%s000`; echo endTime ${endTime:0:13} >> %s;' % machine_txt_file

i = 0
threads = []
time.sleep(30) #wait everything to be set up
while i < num_clients * num_clients_per:
    # start_a_client(i)
    t = threading.Thread(target=start_a_client, args=(i,))
    threads.append(t)
    i += num_clients_per
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
    copy_log()
except:
    print "EXCEPT"
    copy_log()
