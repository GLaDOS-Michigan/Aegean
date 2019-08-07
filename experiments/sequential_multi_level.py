#!/usr/bin/env python

import os
import sys
import time
import argparse
import fcntl

firstServiceNumThreads = 128
secondServiceNumThreads = 32
thirdServiceNumThreads = 8
firstServiceBatchWaitTime = 20
secondServiceBatchWaitTime = 1
thirdServiceBatchWaitTime = 1
forthServiceBatchWaitTime = 1
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
        execute(args['start_clients'], args['end_clients'], args['run_name'], args['loop_time'],
                args['num_threads'])
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
    parser.add_argument('--threads', dest='num_threads', type=int, default=16,
                        help='Number of threads that executed client requests concurrently. There are this many threads working during each batch if the run is pipelined')
    parser.add_argument('--spin', dest='loop_time', type=int, default=600,
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
    parser.add_argument('--mode', dest='mode', choices=['s', 'ps'], required=True,
                        help='Configures the system for (s) sequential, or (ps) parallel pipelined runs.')
    parser.add_argument('start_clients', type=int, help='The minimum number of clients sending requests to the system')
    parser.add_argument('end_clients', type=int, help='The maximum number of clients sending requests to the system')
    parser.add_argument('run_name', type=str, help='The directory name the runs will be saved under in results')


# TODO write asserts/check process scripts
def validate_args(args):
    pass
    # with open('start_clients.py') as start_clients:
    #  for line in start_clients

    # with open('final_process.sh') as final_process


# Goes from start clients to end clients by powers of 2, executing a run for each value
def execute(start_clients, end_clients, run_name, loop_time, num_threads):
    clients = []
    c = start_clients
    os.system('rm -rf results/%s' % run_name)
    while c <= end_clients:
        clients.append(c)
        c *= 2 #it was *=2

    for num_clients in clients:
        # Why is this important? -Michael
        clients_per_process = 1 #max(1, num_clients / 16)  # TODO
        changeParam('0seq.properties', "numberOfClients", num_clients)

        # TODO handle script dependencies more effectively
        # Moves all jars and dependencies to nodes
        print 'COPYING ALL...'
        print 'STARTING REPLICAS OF 4th SERVICE'
        os.system('./copy_all.py')
        os.system('./start_sequential_multi_backend.py %s' % ('2seq.properties.backend'))
        time.sleep(2)
        print 'STARTING REPLICAS OF 3rd SERVICE...'
        os.system('./start_sequential_multi_middle2.py %s %s %s' % ('1seq.properties.backend', '2seq.properties.backend', 'service3'))
        time.sleep(2)
        print 'STARTING REPLICAS OF 2nd SERVICE...'
        os.system('./start_sequential_multi_middle.py %s %s %s' % ('0seq.properties.backend', '1seq.properties.backend', 'service2'))
        time.sleep(2)
        print 'STARTING REPLICAS OF 1st SERVICE...'
        os.system('./start_sequential_middle.py %s %s %s' % ('0seq.properties', '0seq.properties.backend', 'service1'))
        time.sleep(2)
        print 'STARTING CLIENTS'
        os.system('./start_client.py %s %s %s %s' % (num_clients, clients_per_process, loop_time, '0seq.properties'))
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

    os.system('cat results/{}/*/result_all.txt'.format(run_name))


def configure_properties(args):#TODO This configuration is for sequential mode only, it needs to be rethought for other modes
    changeParam('0seq.properties', 'noOfThreads', firstServiceNumThreads)
    changeParam('0seq.properties.backend', 'noOfThreads', secondServiceNumThreads)
    changeParam('1seq.properties.backend', 'noOfThreads', thirdServiceNumThreads)
    changeParam('2seq.properties.backend', 'noOfThreads', 1)

    changeParam('0seq.properties', 'execBatchSize', firstServiceNumThreads)
    changeParam('0seq.properties.backend', 'execBatchSize', secondServiceNumThreads)
    changeParam('1seq.properties.backend', 'execBatchSize', thirdServiceNumThreads)
    changeParam('2seq.properties.backend', 'execBatchSize', 1)

    changeParam('0seq.properties', 'execBatchWaitTime', firstServiceBatchWaitTime)
    changeParam('0seq.properties.backend', 'execBatchWaitTime', secondServiceBatchWaitTime)
    changeParam('1seq.properties.backend', 'execBatchWaitTime', thirdServiceBatchWaitTime)
    changeParam('2seq.properties.backend', 'execBatchWaitTime', forthServiceBatchWaitTime)

    changeParam('0seq.properties', 'dynamicBatchFillTime', True)
    changeParam('0seq.properties.backend', 'dynamicBatchFillTime', True)
    changeParam('1seq.properties.backend', 'dynamicBatchFillTime', True)
    changeParam('2seq.properties.backend', 'dynamicBatchFillTime', True)

    changeParam('0seq.properties', 'pipelinedBatchExecution', False)
    changeParam('0seq.properties.backend', 'pipelinedBatchExecution', False)
    changeParam('1seq.properties.backend', 'pipelinedBatchExecution', False)
    changeParam('2seq.properties.backend', 'pipelinedBatchExecution', False)

    changeParam('0seq.properties', 'pipelinedSequentialExecution', False)
    changeParam('0seq.properties.backend', 'pipelinedSequentialExecution', False)
    changeParam('1seq.properties.backend', 'pipelinedSequentialExecution', False)
    changeParam('2seq.properties.backend', 'pipelinedSequentialExecution', False)

    changeParam('0seq.properties.backend', "numberOfClients", firstServiceNumThreads)
    changeParam('1seq.properties.backend', "numberOfClients", secondServiceNumThreads)
    changeParam('2seq.properties.backend', "numberOfClients", thirdServiceNumThreads)

    changeParam('0seq.properties', 'parallelExecution', False)
    changeParam('0seq.properties.backend', 'parallelExecution', False)
    changeParam('1seq.properties.backend', 'parallelExecution', False)
    changeParam('2seq.properties.backend', 'parallelExecution', False)

    if args['mode'] == 'ps':
        print 'pipelined sequential'
        changeParam('0seq.properties', 'pipelinedSequentialExecution', True)
        changeParam('0seq.properties.backend', 'pipelinedSequentialExecution', True)
        changeParam('1seq.properties.backend', 'pipelinedSequentialExecution', True)

        # TODO add errors if params are passed that don't matter for the mode chosen. This is harder than it sounds because of the defaults.


if __name__ == '__main__':
    main()
