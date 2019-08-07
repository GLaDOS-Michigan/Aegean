#!/usr/bin/env python

import os
import sys
import time
import argparse
import fcntl

property_file = 'testPB.properties'
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
        if args['sanity_check']:
            sanity_check()
        else:
            print 'numBathces ' + str(numBatches)
            execute(args['start_clients'], args['end_clients'], args['run_name'], args['loop_time'],
                    args['num_threads'])
        fcntl.flock(running_file, fcntl.LOCK_UN)
    except IOError:
        print('Run Already in Progress!!!')
        sys.exit()


def changeParam(param, value, backend=False):
    f = property_file
    f += '.backend' if backend else ''
    os.system("cat %s | sed -e 's/%s = .*/%s = %s/' > test && cp test %s" % (f, param, param, value, f))


# Currently not used, but may strongly influence run times TODO what is this about?
def findBatchSize(clients, threads, groups):
    if (clients / 2) < (threads * groups):
        return max(clients / 2, 1)
    maxSize = threads * groups
    while 120 > maxSize:
        maxSize += (threads * groups)
    return maxSize


def sanity_check():
    # Test Sequential Execution
    num_clients = 3
    execute(num_clients, num_clients, 'sanity_check_sequential', '1000', '8')

    # Test Pipelined Sequential Execution
    changeParam('pipelinedSequentialExecution', True)
    execute(num_clients, num_clients, 'sanity_check_sequential_pipelined', '1000', '8')

    # Test Parallel Execution
    changeParam('parallelExecution', True)
    changeParam('pipelinedSequentialExecution', False)
    execute(num_clients, num_clients, 'sanity_check_parallel', '1000', '8')

    # Test Pipelined Parallel Execution
    changeParam('pipelinedBatchExecution', True)
    execute(num_clients, num_clients, 'sanity_check_parallel_pipelined', '1000', '8')


def setup_parser(parser):
    parser.add_argument('--num-batches', dest='num_batches', type=int, default=16,
                        help='Number of groups executed concurrently for parallel or sequential pipelined executions')
    parser.add_argument('--threads', dest='num_threads', type=int, default=1,
                        help='Number of threads that executed client requests concurrently. There are this many threads working during each batch if the run is pipelined')
    parser.add_argument('--spin', dest='loop_time', type=int, default=1000,
                        help='Determines how long the client request takes by "spinning" or waiting the specified number of miliseconds')
    parser.add_argument('--no-verify', action='store_true', help='Disables the verification nodes for the run')
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
        clients_per_process = 1  # max(1, num_clients / 16)  # TODO
        changeParam("numberOfClients", num_clients)
        middleBatchSize = max(1, num_clients / 5)
        changeParam('execBatchSize', middleBatchSize)
        # Should the batch values match? TODO why???
        backendBatch = min(num_clients, num_threads)
        changeParam("execBatchSize", backendBatch, True)
        changeParam("execBatchMinSize", backendBatch, True)
        numberOfBackiendClients = int(num_threads) * numBatches
        print "backendClient: " + str(numberOfBackiendClients) + ", numBatches: " + str(numBatches)
        changeParam("numberOfClients", numberOfBackiendClients, True)

        # TODO handle script dependencies more effectively
        # Moves all jars and dependencies to nodes
        print 'COPYING ALL...'
        os.system('./copy_all.py')
        time.sleep(2)
        print 'STARTING BACKEND REPLICAS...'
        os.system('./start_PB_backend.py %s' % (property_file + '.backend'))
        time.sleep(2)
        print 'STARTING MIDDLE REPLICAS...'
        os.system('./start_PB_middle.py %s %s' % (property_file, property_file + '.backend'))
        time.sleep(2)
        print 'STARTING CLIENTS'
        os.system('./start_client.py %s %s %s %s' % (num_clients, clients_per_process, loop_time, property_file))
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


def configure_properties(args):
    changeParam('numberOfClients', 2) #TODO not good to set it to default
    changeParam('noOfThreads', args['num_threads'])
    changeParam('executionPipelineDepth', 10)
    changeParam('execBatchWaitTime', 70)
    changeParam('execBatchWaitFillTime', 0)
    changeParam('dynamicBatchFillTime', True)
    changeParam('level_debug', args['debug'])
    changeParam('level_fine', args['debug'])
    changeParam('level_info', args['debug'])
    changeParam('level_warning', True)
    changeParam('level_error', True)
    changeParam('level_trace', True)
    changeParam('pipelinedBatchExecution', False)
    changeParam('pipelinedSequentialExecution', False)
    # Two batches in a 1 step system should be optimal
    changeParam('batchSuggestion', True)
    changeParam('normalMode', False)
    changeParam('backendAidedVerification', True)
#    changeParam('batchSuggestion', False)
#    changeParam('normalMode', True)
#    changeParam('backendAidedVerification', False)

    changeParam('batchSuggestion', True, True)
    changeParam('normalMode', False, True)
    changeParam('unreplicated', True, True)
    changeParam('pipelinedBatchExecution', False, True) 
    changeParam('noOfThreads', 16, True) #     changeParam("noOfThreads", min(args['num_threads'], 16), True)

#    changeParam('batchSuggestion', False, True)
#    changeParam('normalMode', True, True)
#    changeParam('unreplicated', True, True)
#    changeParam('pipelinedBatchExecution', False, True)
    

    changeParam('numPipelinedBatches', args['num_batches'])
    changeParam('numWorkersPerBatch', args['num_threads'])
    changeParam('rollbackDisabled', args['node_rollback'])
    changeParam('forceVerifierRollback', args['verifier_rollback'])
    changeParam('forceExecutionRollback', args['exec_rollback'])
    changeParam('forceSequential', args['force_sequential'])
    changeParam('parallelExecution', False)
    changeParam('fillBatchesFirst', True)
    changeParam('sendVerify', True)
    changeParam('sendVerify', True, True)
    changeParam('sendNested', True)
    changeParam('useDummyTree', False)
    changeParam("useDummyTree", False, True)
    changeParam('causeDivergence', args['diverge'])
    changeParam('noOfObjects', 10000000)#TODO not good to set it to default
    # Backend Vals
    changeParam("execBatchWaitTime", 50, True)
    changeParam('noOfObjects', 10000000, True)#TODO not good to set it to default
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
        print 'parallel pipelined, numBatches: ' + str(numBatches)
        changeParam('parallelExecution', True)
        changeParam('pipelinedBatchExecution', True)

        # TODO add errors if params are passed that don't matter for the mode chosen. This is harder than it sounds because of the defaults.


if __name__ == '__main__':
    main()
