#!/usr/bin/env python

import os
import sys
import time
import argparse
import fcntl

numBatches = 16
numBackendBatches = 8
numThreads = 8
numBackendThreads = 4
numMultiThreads =2
waitTime = 70
backendWaitTime = 1
multiWaitTime = 1
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
        ##execute(args['run_name'] + '_' + str(8), args['loop_time'], 8, args['start_clients'], args['end_clients'] )
        ##execute(args['run_name'] + '_' + str(12), args['loop_time'], 12, args['start_clients'], args['end_clients'] )
        execute(args['run_name'] + '_' + str(16), args['loop_time'], 16, args['start_clients'], args['end_clients'] )
        #start = max(8, args['start_num_threads'])
        #for i in range(start, args['end_num_threads']+1, 4):
        #    execute(args['run_name'] + '_' + str(i), args['loop_time'], i, args['start_clients'], args['end_clients'] )
        fcntl.flock(running_file, fcntl.LOCK_UN)
    except IOError:
        print('Run Already in Progress!!!')
        sys.exit()


def changeParam(property_file, param, value):
    os.system("cat %s | sed -e 's/%s = .*/%s = %s/' > test && cp test %s" % (property_file, param, param, value, property_file))


# Currently not used, but may strongly influence run times TODO what is this about?
def findBatchSize(clients, threads, groups):
    if (clients / 2) < (threads * groups):
        return max(clients / 2, 1)
    maxSize = threads * groups
    while 120 > maxSize:
        maxSize += (threads * groups)
    return maxSize


def setup_parser(parser):
    parser.add_argument('--num-batches', dest='num_batches', type=int, default=4,
                        help='Number of groups executed concurrently for parallel or sequential pipelined executions')
    parser.add_argument('--threads', dest='num_threads', type=int, default=4,
                        help='Number of threads that executed client requests concurrently. There are this many threads working during each batch if the run is pipelined')
    parser.add_argument('--spin', dest='loop_time', type=int, default=1000,
                        help='Determines how long the client request takes by "spinning" or waiting the specified number of miliseconds')
    parser.add_argument('--no-verify', action='store_false', help='Disables the verification nodes for the run')
    parser.add_argument('--node-rollback', action='store_false',
                        help='Executes an arbitrary rollback, not based on any specific divergence')
    parser.add_argument('--verifier-rollback', action='store_true',
                        help='Causes a view change in the verifier nodes, which should result in a rollback for the execution nodes')
    parser.add_argument('--force-sequential', action='store_true',
                        help='Forces the system to begin sequential rollback after the 500th request')
    parser.add_argument('--debug', action='store_true',
                        help='Enables debug print statements throughout. Will massively increase log file size')
    parser.add_argument('--diverge', action='store_true',
                        help='This flag deletes the digests of certain execution nodes, at the next checkpoint it will cause a divergence for those nodes and triggera  rollback.')
    parser.add_argument('--exec-rollback', action='store_true',
                        help='When the exec node process a verifier response, it ignores the result and instead rolls back to the last seq no verified other than that one. Only produces correct behavior for parallel executions.')
    parser.add_argument('--sanity-check', action='store_true',
                        help='Runs a series of experiments to check that the system is behaving as expected.')
    parser.add_argument('--mode', dest='mode', choices=['p', 'pp'], required=True,
                        help='Configures the system for (p) parallel, or (pp) parallel pipelined runs.')
    parser.add_argument('start_clients', type=int, help='The minimum number of clients sending requests to the system')
    parser.add_argument('end_clients', type=int, help='The maximum number of clients sending requests to the system')
    parser.add_argument('run_name', type=str, help='The directory name the runs will be saved under in results')


# TODO write asserts/check process scripts
def validate_args(args):
    pass
# with open('start_clients.py') as start_clients::
    #  for line in start_clients

    # with open('final_process.sh') as final_process


# Goes from start clients to end clients by powers of 2, executing a run for each value
def execute(run_name, loop_time, num_threads, start_clients, end_clients):
    print 'RUN_NAME: %s, NUM_THREADS: %s' % (run_name, num_threads)
    num_clients = start_clients
    os.system('rm -rf results/%s' % run_name)
    
    changeParam('test.properties', "noOfThreads", numThreads)
    changeParam('test.properties.backend', "noOfThreads", numBackendThreads)
    changeParam('multiLevel.backend', "noOfThreads", numMultiThreads)

    changeParam('test.properties', 'numWorkersPerBatch', numThreads)
    changeParam('test.properties.backend', 'numWorkersPerBatch', numBackendThreads)
    changeParam('multiLevel.backend', 'numWorkersPerBatch', numMultiThreads)
    oldTroughput = 0.0
    while num_clients <= end_clients:
        # Why is this important? -Michael
        clients_per_process = 1 #max(1, num_clients / 16)  # TODO
        numBackendClients = numBatches * numThreads
        numMultiClients = numBackendBatches * numBackendThreads
        backendBatchSize = numBackendBatches * numBackendThreads
        middleBatchSize = max(num_clients/10, 1)
        print "numBatches: " + str(numBatches)
        print "numThreads: " + str(num_threads)
        print "numBackendBatchSize and numBackendClients: " + str(numBackendClients)
        print "middleBatchSize: " + str(middleBatchSize)
        print "numMultiClients: " + str(numMultiClients)

        changeParam('test.properties', "numberOfClients", num_clients)
        changeParam('test.properties.backend', "numberOfClients", numBackendClients)
        changeParam('multiLevel.backend', "numberOfClients", numMultiClients)
        
        changeParam('test.properties', 'execBatchSize', middleBatchSize)
        changeParam('test.properties.backend', 'execBatchSize', backendBatchSize)
        changeParam('multiLevel.backend', 'execBatchSize', numMultiThreads)

        changeParam('test.properties', 'numPipelinedBatches', numBatches)
        changeParam('test.properties.backend','numPipelinedBatches', numBackendBatches)
        changeParam('multiLevel.backend', 'numPipelinedBatches', 1)
        # TODO handle script dependencies more effectively
        # Moves all jars and dependencies to nodes
        print 'COPYING ALL...'
        os.system('./copy_all.py')
        time.sleep(2)
        print 'STARTING REPLICAS OF 3rd SERVICE...'
        os.system('./start_backend_multi.py %s' % ('multiLevel.backend'))
        time.sleep(2)
        print 'STARTING REPLICAS OF 2nd SERVICE...'
        os.system('./start_middle_multi.py %s %s %s' % ('test.properties.backend', 'multiLevel.backend', 'service2'))
        time.sleep(2)
        print 'STARTING REPLICAS OF 1st SERVICE...'
        os.system('./start_middle.py %s %s %s' % ('test.properties', 'test.properties.backend', 'service1'))
        time.sleep(2)
        print 'STARTING CLIENTS'
        os.system('./start_client.py %s %s %s %s' % (num_clients, clients_per_process, loop_time, 'test.properties'))
        time.sleep(2)
        print 'STOPPING ALL'
        os.system('./stop_all.py')  ##This is already called at the end of start_client
        time.sleep(2)
        dirName = "{}/{}".format(run_name, num_clients)
        dir_path = os.path.dirname(os.path.realpath(__file__))
        print dir_path
        os.system('./calculate.sh %s' % dirName)
        os.system('cp -r ./jh_log ./results/{}/'.format(dirName))
        os.system('cp -r ./exp_log ./results/{}/'.format(dirName))
        os.system('cp ./super_script_am.py ./results/{}/'.format(dirName)) #TODO what is this script and why copying?
        os.system('cp ./start_backend.py ./results/{}/'.format(dirName)) #TODO why copying? It seems an old version of modified_super.py
        print 'results'
        os.system("sed \'/throuhgput/d\' results/%s/%s/result_all.txt > results/%s/%s/tmp.txt" % (run_name, num_clients, run_name, num_clients))
        f = open('results/%s/%s/tmp.txt' % (run_name, num_clients))
        line = f.readline()
        os.system("rm results/%s/%s/tmp.txt" % (run_name, num_clients))
        values = line.split()
        os.system('cat results/%s/%s/result_all.txt >> results/%s/results.txt' % (run_name, num_clients, run_name))
        print 'cat results/%s/%s/result_all.txt >> results/%s/results.txt' % (run_name, num_clients, run_name)
        new_latency = 0.0
        try:
            print 'num_clients: ' + values[0]
            print 'throughput: ' + values[1]
            print 'latency: ' + values[2]
            new_num_clients = float(values[0])
            new_throughput = float(values[1])
            new_latency = float(values[2])
            if oldTroughput*1.025 >= new_throughput:
                print "throughput decreasing"
                break
            oldTroughput = new_throughput
        except:
            print "not a float"
        num_clients *= 2
    os.system('cat results/{}/*/result_all.txt'.format(run_name))


def configure_properties(args):#TODO This configuration is for parallel modes only, it needs to be rethought for other modes
    changeParam('test.properties', 'noOfThreads', numThreads)
    changeParam('test.properties.backend', 'noOfThreads', numBackendThreads)
    changeParam('multiLevel.backend', 'noOfThreads', numMultiThreads)

    changeParam('test.properties', 'execBatchWaitTime', waitTime)
    changeParam('test.properties.backend', 'execBatchWaitTime', backendWaitTime)
    changeParam('multiLevel.backend', 'execBatchWaitTime', backendWaitTime)

    changeParam('test.properties', 'dynamicBatchFillTime', True)
    changeParam('test.properties.backend', 'dynamicBatchFillTime', True)
    changeParam('multiLevel.backend', 'dynamicBatchFillTime', True)

    changeParam('test.properties', 'pipelinedBatchExecution', False)
    changeParam('test.properties.backend', 'pipelinedBatchExecution', False)
    changeParam('multiLevel.backend', 'pipelinedBatchExecution', False)

    changeParam('test.properties', 'pipelinedSequentialExecution', False)
    changeParam('test.properties.backend', 'pipelinedSequentialExecution', False)
    changeParam('multiLevel.backend', 'pipelinedSequentialExecution', False)

    changeParam('test.properties', 'parallelExecution', True)
    changeParam('test.properties.backend', 'parallelExecution', True)
    changeParam('multiLevel.backend', 'parallelExecution', True)
    
    changeParam('test.properties', 'batchSuggestion', False)
    changeParam('test.properties.backend', 'batchSuggestion', False)
    changeParam('multiLevel.backend', 'batchSuggestion', False)
    
    if args['mode'] == 'pp':
        print 'parallel pipelined'
        changeParam('test.properties', 'pipelinedBatchExecution', True)
        changeParam('test.properties.backend', 'pipelinedBatchExecution', True)
        

        # TODO add errors if params are passed that don't matter for the mode chosen. This is harder than it sounds because of the defaults.


if __name__ == '__main__':
    main()
