#!/usr/bin/env python

import os
import sys
import time
import argparse


def main(): 
  if len(sys.argv) != 4:
    print "Parameters should be: start_client_num, end_client_num, run_name"
    sys.exit(1)

  c = int(sys.argv[1])
  c_start = c
  end = int(sys.argv[2])
  name = sys.argv[3]
  threads = [1]
  groups = [3]
  loops = [100] # , 66242, 7547] #, 767, 77, 7]

  r_orig = 8

  time.sleep(2)
  a = ['false']
  tree = 'true'

  clients = []
  c = c_start
  while c <= end:
      clients.append(c)
      c *= 2

  for mix in a:

    for g in groups:
      #why is this changing? 
      changeParam("numPipelinedBatches", g)

      for t in threads:
        for c in clients:
          clients_per_process = max(1, c / 16)
          fracs = [(1, 2)]
          for num, denom in fracs:
            size = findBatchSize(c, t, g)
            size = 48
            batchTime = 1000

            # Middle values
            #changeParam("execBatchSize", size)
            #changeParam("execBatchWaitTime", batchTime)
            changeParam("numWorkersPerBatch", t)
            #changeParam("useDummyTree", 'false')
            changeParam("numberOfClients", c)

            backendBatch = min(c,t)
            # Backend values
            changeParam("execBatchSize", backendBatch, True)
            changeParam("execBatchMinSize", backendBatch, True)
            changeParam("execBatchWaitTime", 50, True)
            changeParam("noOfThreads", min(t,16), True)
            changeParam("useDummyTree", 'false', True)

            os.system('./copy_all.py')

            for l in loops:
              os.system('./start_backend.py')
              time.sleep(2)
              os.system('./start_middle.py')
              time.sleep(2)
              os.system('./start_client.py %s %s %s' % (c, clients_per_process, l))
              time.sleep(2)
              os.system('./stop_all.py')
              time.sleep(2)
              dirName = "{}/{}".format(name, c) 
              os.system('./calculate.sh %s' % dirName)
              os.system('cp -r ~/adam/jh_log ~/adam/results/{}/'.format(dirName))
              os.system('cp -r ~/adam/exp_log ~/adam/results/{}/'.format(dirName))
              os.system('cp ~/adam/super_script_am.py ~/adam/results/{}/'.format(dirName))
              os.system('cp ~/adam/start_backend.py ~/adam/results/{}/'.format(dirName))
      print 'Done with {0} group(s)'.format(g)

    os.system('cat results/{}/*/result_all.txt'.format(name))
def changeParam(param, value, backend=False):
  f = 'test.properties'
  f += '.backend' if backend else ''
  os.system("cat %s | sed -e 's/%s = .*/%s = %s/' > test && cp test %s" % (f, param, param, value, f))

def findBatchSize(clients, threads, groups):
 if (clients / 2) < (threads * groups):
     return max(clients / 2, 1)
 maxSize = threads * groups
 while 120 > maxSize:
     maxSize += (threads * groups)
 return maxSize

if __name__=='__main__':
  main()
