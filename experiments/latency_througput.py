#!/usr/bin/env python

import os
import sys
import time
import argparse
import fcntl

property_file = 'test.properties'
numBathces = 1
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
	os.system('rm -rf results/%s' % args['run_name'] + '_*')
	#end = min(args['end_num_threads'], 3)
        #for i in range(args['start_num_threads'], end+1, 7):
        #    execute(args['run_name'] + '_' + str(i), args['loop_time'], i, args['start_clients'], args['end_clients'] )
#        execute(args['run_name'] + '_' + str(1), args['loop_time'], 1, args['start_clients'], args['end_clients'] )
#        execute(args['run_name'] + '_' + str(2), args['loop_time'], 2, args['start_clients'], args['end_clients'] )
#        execute(args['run_name'] + '_' + str(4), args['loop_time'], 4, args['start_clients'], args['end_clients'] )
	start = max(6, args['start_num_threads'])
	for i in range(start, args['end_num_threads']+1, 2):
            execute(args['run_name'] + '_' + str(i), args['loop_time'], i, args['start_clients'], args['end_clients'] )
        fcntl.flock(running_file, fcntl.LOCK_UN)
    except IOError:
        print('Run Already in Progress!!!')
        sys.exit()


def changeParam(param, value, backend=False):
    f = 'test.properties'
    f += '.backend' if backend else ''
    os.system("cat %s | sed -e 's/%s = .*/%s = %s/' > test && cp test %s" % (f, param, param, value, f))


def setup_parser(parser):
    parser.add_argument('--mode', dest='mode', choices=['s', 'ps', 'p', 'pp'], required=True,
                        help='Configures the system for (s) sequential, (sp) pipelined sequential, (p) parallel, or (pp) parallel pipelined runs.')
    parser.add_argument('run_name', type=str, help='The directory name the runs will be saved under in results')
    parser.add_argument('--spin', dest='loop_time', type=int, default=1000,
                        help='Determines how long the client request takes by "spinning" or waiting the specified number of miliseconds')
    parser.add_argument('start_num_threads', type=int, default=1,
                        help='The minimum number of threads sending requests to the system')
    parser.add_argument('end_num_threads', type=int, default=16,
                        help='The maximum number of threads sending requests to the system')
    parser.add_argument('start_clients', type=int, default = 4, help='The minimum number of clients sending requests to the system')
    parser.add_argument('end_clients', type=int, default = 64, help='The maximum number of clients sending requests to the system')
    # TODO irrelevant params from modified_super
    parser.add_argument('--num-batches', dest='num_batches', type=int, default=8,
                        help='Number of groups executed concurrently for parallel or sequential pipelined executions')
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


# execute(args['run_name'], args['loop_time'], args['start_num_threads'], args['end_num_threads'])
def execute(run_name, loop_time, num_threads, start_clients, end_clients):
    print 'RUN_NAME: %s, NUM_THREADS: %s' % (run_name, num_threads)
    num_clients = start_clients
    #increase = 2.0
    #old_throughput = 0.0
    #new_throughput = 0.0
    os.system('rm -rf results/%s' % run_name)
    changeParam('noOfThreads', num_threads)
    changeParam('numWorkersPerBatch', num_threads)  # TODO check again
    changeParam("noOfThreads", min(num_threads, 16), True)  # TODO check again
    changeParam('batchSuggestion', True, True)
    changeParam('batchSuggestion', True)
    oldTroughput = 0.0
    while num_clients <= end_clients:
        # Why is this important? -Michael
        clients_per_process = 1  # max(1, num_clients / 16)  # TODO
        changeParam("numberOfClients", num_clients)
        backendBatch = min(num_clients, num_threads)
        changeParam("execBatchSize", backendBatch, True)
        changeParam("execBatchMinSize", backendBatch, True)
        middleBatchSize = max(num_clients/10, 1)
        changeParam('execBatchSize', middleBatchSize)
	numBackendClients = numBathces * num_threads
	print 'numBackendClients: ' + str(numBackendClients) + ', numClients: ' + str(num_clients)
	if numBackendClients * 3 >= num_clients:
	    print 'not enough client(' + str(num_clients) + ') for all threads ( ' + str(numBackendClients) + ')'
            num_clients *= 2
	    continue
	print "NumBackendClients: " + str(numBackendClients)
        changeParam('numberOfClients', numBackendClients, True)
        # TODO handle script dependencies more effectively
        # Moves all jars and dependencies to nodes
        print 'COPYING ALL...'
        os.system('./copy_all.py')
        time.sleep(2)
        print 'STARTING BACKEND REPLICAS...'
        os.system('./start_backend.py %s' % (property_file + '.backend'))
	time.sleep(2)
        print 'STARTING MIDDLE REPLICAS...'
        os.system('./start_middle.py %s %s' % (property_file, property_file + '.backend'))
        time.sleep(2)
        print 'STARTIN CLIENTS'
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
        os.system('cp ./super_script_am.py ./results/{}/'.format(dirName))  # TODO what is this script and why copying?
        os.system('cp ./start_backend.py ./results/{}/'.format(
            dirName))  # TODO why copying? It seems an old version of modified_super.py
        print 'results'
        # read one the line from run_name/client_name/result_all.txt, calculate increase and write into file
        #print 'results/%s/%s/result_all.txt' % (run_name, num_clients)
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
        	if oldTroughput >= new_throughput:
			print "throughput decreasing"
			break
		oldTroughput = new_throughput
	except:
                print "not a float"
        ##if new_latency >= 150:
        ##        print "saturated so break"
        ##        break
	num_clients *= 2
    os.system('cat results/{}/*/result_all.txt'.format(run_name))


def configure_properties(args):
    # Changes all params to sane defaults or overrides them based on optional command line flags
    changeParam('primaryBackup', False)
    changeParam('useVerifier', True)
    changeParam('filtered', True)
    changeParam('numberOfClients',
                2)  ##TODO why???? It is not effective anyway since execs overrides according to arguments
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
    changeParam('uprightInMemory', True)
    changeParam('sliceExecutionPipelineDepth', 2)
    changeParam('replyCacheSize', 1)
    changeParam('level_debug', args['debug'])
    changeParam('level_fine', args['debug'])
    changeParam('level_info', args['debug'])
    changeParam('level_warning', True)
    changeParam('level_error', True)
    changeParam('pipelinedBatchExecution', False)
    changeParam('pipelinedSequentialExecution', False)
    # Two batches in a 1 step system should be optimal
    changeParam('numPipelinedBatches', args['num_batches'])
    changeParam('rollbackDisabled', args['node_rollback'])
    changeParam('forceVerifierRollback', args['verifier_rollback'])
    changeParam('forceExecutionRollback', args['exec_rollback'])
    changeParam('forceSequential', args['force_sequential'])
    changeParam('parallelExecution', False)
    changeParam('backendLoopRatio', 1.0)
    changeParam('fillBatchesFirst', True)
    changeParam('sendVerify', True)
    changeParam('sendVerify', True, True)
    changeParam('sendNested', True)
    changeParam('useDummyTree', False)
    changeParam("useDummyTree", False, True)
    changeParam('concurrentRequests', 192)
    changeParam('causeDivergence', args['diverge'])
    changeParam('clientReadOnly', False)
    changeParam('clientNoConflict', False)
    changeParam('execBatchNoChangeWindow', 2)
    changeParam('noOfObjects', 1000000)
    # Backend Vals
    changeParam("execBatchWaitTime", 50, True)
    changeParam('noOfObjects', 1000000, True)
    # this doesn't have to match threads, but it's convenient because it means work should be done at the same rate on the middle and backend execution nodes
    changeParam('level_debug', args['debug'], True)
    changeParam('level_fine', args['debug'], True)
    changeParam('level_info', args['debug'], True)

    # Changes specified params to user input depending on mode
    if args['mode'] == 'sp':
        changeParam('pipelinedSequentialExecution', True)
    elif args['mode'] == 'p':
        changeParam('parallelExecution', True)
    elif args['mode'] == 'pp':
	global numBathces
	numBathces = args['num_batches']
        changeParam('parallelExecution', True)
        changeParam('pipelinedBatchExecution', True)

        # TODO add errors if params are passed that don't matter for the mode chosen. This is harder than it sounds because of the defaults.


if __name__ == '__main__':
    main()
