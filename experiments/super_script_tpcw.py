#!/usr/bin/env python

import os
import time
import fcntl
import argparse
import sys

property_file = 'test.properties'
numBatches = 1
def main():
    try:
        running_file = open('is_running', 'r')
        print 'Trying to acquire file lock...'
        fcntl.flock(running_file, fcntl.LOCK_EX)
        os.system("cp ../dist/lib/bft.jar .")
        os.system("cp bft.jar lib/")
        parser = argparse.ArgumentParser(description='Setup and run Adam codebase.')
        setup_parser(parser)
        args = vars(parser.parse_args())
        configure_properties(args)
        # This gives you time to cancel the run if you have the last minute "I forgot..."
        print "Starting..."
        execute(args['start_clients'], args['end_clients'], args['run_name'], args['num_threads'])
        fcntl.flock(running_file, fcntl.LOCK_UN)
    except IOError:
        print('Run Already in Progress!!!')
        sys.exit()

def changeParam(param, value, backend=False):
    f = property_file
    f += '.backend' if backend else ''
    os.system("cat %s | sed -e 's/%s = .*/%s = %s/' > test && cp test %s" % (f, param, param, value, f))

def setup_parser(parser):
    parser.add_argument('--num-batches', dest='num_batches', type=int, default=16,
                        help='Number of groups executed concurrently for parallel or sequential pipelined executions')
    parser.add_argument('--threads', dest='num_threads', type=int, default=1,
                        help='Number of threads that executed client requests concurrently. There are this many threads working during each batch if the run is pipelined')
    parser.add_argument('--mode', dest='mode', choices=['p', 'pp'], default='p',
                        help='Configures the system for (p) parallel, or (pp) parallel pipelined runs.')
    parser.add_argument('start_clients', type=int, help='The minimum number of clients sending requests to the system')
    parser.add_argument('end_clients', type=int, help='The maximum number of clients sending requests to the system')
    parser.add_argument('run_name', type=str, help='The directory name the runs will be saved under in results')
    parser.add_argument('--debug', action='store_true',
                        help='Enables debug print statements throughout. Will massively increase log file size')

def configure_properties(args):
    # Changes all params to sane defaults or overrides them based on optional command line flags
    changeParam('primaryBackup', False)
    changeParam('useVerifier', True) #TODO not good to set it to default
    changeParam('filtered', True) #TODO no  to set it to default
    changeParam('numberOfClients', 2) #TODO not good to set it to default
    changeParam('filterCaching', False)
    changeParam('threadPoolSize', 1)
    changeParam('doLogging', False)
    # Don't change this value? the '/*' causes sed to crash
    # changeParam('execLoggingDirectory', '/tmp')
    changeParam('execSnapshotInterval', 1000)
    # changeParam('verifierLoggingDirectory', '/test/data')
    changeParam('insecure', False)
    changeParam('digestType', 'SHA-256')
    changeParam('executionPipelineDepth', 10)
    changeParam('execBatchWaitTime', 70)
    changeParam('execBatchWaitFillTime', 0)
    changeParam('dynamicBatchFillTime', True)
    changeParam('loadBalanceDepth', 2)
    
    changeParam('useDummyTree', False) #TODO not good to set it to default
    changeParam("useDummyTree", False, True)
    changeParam('sendVerify', True)
    changeParam('sendVerify', True, True)

    changeParam('uprightInMemory', True)
    changeParam('sliceExecutionPipelineDepth', 2)
    changeParam('replyCacheSize', 1)
    changeParam('level_debug', args['debug'])
    changeParam('level_fine', args['debug'])
    changeParam('level_info', args['debug'])
    changeParam('level_warning', True)
    changeParam('level_error', True)
    changeParam('level_trace', True)
    changeParam('pipelinedBatchExecution', False)
    changeParam('pipelinedSequentialExecution', False)
    # Two batches in a 1 step system should be optimal
    changeParam('numPipelinedBatches', args['num_batches'])
    changeParam('numWorkersPerBatch', args['num_threads'])
    changeParam('parallelExecution', False)
    changeParam('concurrentRequests', 192)
    changeParam('clientReadOnly', False)
    changeParam('clientNoConflict', False)
    changeParam('execBatchNoChangeWindow', 2)
    # Backend Vals
    changeParam("execBatchWaitTime", 10, True)
    # this doesn't have to match threads, but it's convenient because it means work should be done at the same rate on the middle and backend execution nodes
    changeParam('level_debug', args['debug'], True)
    changeParam('level_fine', args['debug'], True)
    changeParam('level_info', args['debug'], True)
    changeParam('level_trace', True, True)

    # Changes specified params to user input depending on mode
    if args['mode'] == 'p':
        print 'parallel'
        changeParam('parallelExecution', True)
    elif args['mode'] == 'pp':
        global numBatches
        numBatches = args['num_batches']
        print 'pp'
        changeParam('parallelExecution', True)
        changeParam('pipelinedBatchExecution', True)
        # TODO add errors if params are passed that don't matter for the mode chosen. This is harder than it sounds because of the defaults.

def execute(start_clients, end_clients, run_name, num_threads):
    clients = []
    c = start_clients
    os.system('rm -rf results/%s' % run_name)
    changeParam('batchSuggestion', True, True)
    changeParam('batchSuggestion', True)
    
    while c <= end_clients:
        clients.append(c)
        c *= 2#it was *=2

    for num_clients in clients:
        clients_per_process = 1
        print "numThreads: " + str(num_threads)
        numBackendClients = numBatches * num_threads
        print "numBackendClients: " + str(numBackendClients)
        changeParam("numberOfClients", numBackendClients, True)
        changeParam("numberOfClients", num_clients)
        backendBatch = min(num_clients, num_threads)
        changeParam("execBatchSize", num_threads, True)
        changeParam("execBatchSize", max(num_clients/10, 1))
        changeParam('noOfThreads', num_threads)
        changeParam('noOfThreads', num_threads, True)
        print 'COPYING ALL...'
        os.system('./copy_all.py')
        time.sleep(2)
        print 'STARTING DB REPLICAS...'
        print './start_tpcw_db.py %s' % (property_file + '.backend')
        os.system('./start_tpcw_db.py %s' % (property_file + '.backend'))
        time.sleep(2)
        print 'STARTING SERVER REPLICAS...'
        print './start_tpcw_server.py %s' % property_file
        os.system('./start_tpcw_server.py %s' % property_file)
        time.sleep(2)
        print 'STARTING CLIENTS'
        print './start_tpcw_client.py %s %s %s' % (num_clients, clients_per_process, property_file)
        os.system('./start_tpcw_client.py %s %s %s' % (num_clients, clients_per_process, property_file))
        print 'STOPPING ALL'
        print './stop_all.py'
        os.system('./stop_all.py')
        time.sleep(2)
        dirName = "{}/{}".format(run_name, num_clients)
        dir_path = os.path.dirname(os.path.realpath(__file__))
        print dir_path
        os.system('./calculate.sh %s' % dirName)
        os.system('cp -r ./jh_log ./results/{}/'.format(dirName))
        os.system('cp -r ./exp_log ./results/{}/'.format(dirName))

if __name__ == '__main__':
    main()
# import os
# import time
#
# os.system('./copy_all.py')
# print "copied all"
# time.sleep(2)
# os.system('./start_tpcw_db.py test.properties.backend')
# print "db is started"
# time.sleep(2)
# os.system('./start_tpcw_server.py test.properties')
# print "server is started"
# time.sleep(2)
# os.system('./start_tpcw_client.py test.properties')
# print "client is started"
# os.system('./stop_all.py')
