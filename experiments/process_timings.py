#!/usr/bin/python

import subprocess
import sys
import os
import re


class Accumulator:
    def __init__(self):
        self.nums = []

    def addValue(self, num):
        self.nums.append(num)

    def findAvg(self):
        self.nums.sort()
        stuff = self.nums[10:-10]
        if len(stuff) == 0.0:
            return 0.0
        return float(sum(stuff)) / float(len(stuff))


class Distribution:
    def __init__(self):
        self.dist = {}

    def addCount(self, value):
        if value in self.dist.keys():
            self.dist[value] += 1
        else:
            self.dist[value] = 1

    def showDist(self):
        count = 0
        for k in sorted(self.dist.keys()):
            v = self.dist[k]
            count += 1
            end = "\t"
            if count == 5:
                end = "\n"
                count = 0
            sys.stdout.write('{0}: {1}'.format(k, v))
            sys.stdout.write(end)
        print ''


def executeCommandWithOutput(cmdStr):
    return [line.rstrip('\n') for line in
            iter(subprocess.Popen(cmdStr, shell=True, stdout=subprocess.PIPE).stdout.readline, '')]


def executeCommand(cmdStr):
    print cmdStr
    # subprocess.Popen(cmdStr, shell=True, stdout=subprocess.PIPE)


def readFileStripped(fileName):
    with open(fileName, 'r') as file:
        fileList = [line.strip() for line in file.readlines()]
        return fileList


def computeBatches(lines):
    sdist = Distribution()
    tdist = Distribution()
    filtered = filter(lambda x: re.match('^\[Batching Thread\].*', x), lines)
    for line in filtered:
        if line.split(' ')[2] == 'Increasing' or line.split(' ')[2] == 'Decreasing' or line.split(' ')[2] == 'Now':
            continue
        size = int(line.split(' ')[7])
        value = line.split(' ')[15]
        time = int(value) # TODO I am not completely sure about that. Also, what is this line and what are these exceptions
        sdist.addCount(size)
        tdist.addCount(time)
    print "Batch Size"
    sdist.showDist()
    print "Batch Time"
    tdist.showDist()


def splitIntoGroups(logFile):
    execution = []
    current = []
    for line in logFile:
        if line == 'TIMINGS: ---------------BEGIN-----------------':
            '''
            execution.append(current)
            current = []
            '''
            continue
        current.append(line)
    execution.append(current)
    return execution


def splitByThread(eList):
    threadMap = {}
    for line in eList:
        temp = line.split(' ')
        tID = temp[2]
        if not tID in threadMap.keys():
            threadMap[tID] = []
        threadMap[tID].append((temp[3], temp[4]))
    ret = []
    for key in threadMap.keys():
        ret.append(threadMap[key])
    return ret


def findTime(splitLists, start, end, name):
    accum = Accumulator()
    for batch in splitLists:
        for thread in batch:
            startTime = None
            for operation in thread:
                '''
                if operation  == 'TIMINGS: ---------------BEGIN-----------------':
                  continue
                operation = (operation.split(' ')[3], operation.split(' ')[4])
                '''
                if operation[0] == start:
                    startTime = int(operation[1])
                    continue
                if operation[0] == end:
                    if not startTime == None:
                        accum.addValue(int(operation[1]) - startTime)
                        startTime = None

    print "{0}: {1}ms".format(name, accum.findAvg() / 1000000.0)


def main():
    f = readFileStripped(sys.argv[1])
    computeBatches(f)
    filtered = filter(lambda x: re.match('^TIMINGS.*', x), f)

    splitList = []
    for group in splitIntoGroups(filtered):
        splitList.append(splitByThread(group))

    # findTime(splitList, 'reqRec', 'reqEnd', 'Batching time')
    findTime(splitList, 'StartDoAppWork', 'FinishedWaitForMyTurnInDoAppWork', 'Wait for first turn')
    findTime(splitList, 'StartBenchLocalRequest', 'EndBenchLocalRequest', 'Local Computation')
    findTime(splitList, 'StartBenchNestedRequest', 'EndBenchExecRequest', 'Nested Request')
    findTime(splitList, 'startFinishThisVersion', 'endFinishThisVersion', 'Finish This Version')
    findTime(splitList, 'StartHitWall', 'EndHitWall', 'Hit Wall')
    findTime(splitList, 'StartCheckpoint', 'EndCheckpoint', 'Checkpoint')
    findTime(splitList, 'StartSuspend', 'EndSuspend', 'Suspend')
    findTime(splitList, 'StartYieldPipeline', 'EndYieldPipeline', 'Yield Pipeline')
    findTime(splitList, 'StartExecRequest', 'EndExecRequest', 'Total Request')
    findTime(splitList, 'gcStart', 'gcEnd', 'GC')
    findTime(splitList, 'StartHitWallNested', 'EndHitWallNested', 'Nested Hit Wall')
    findTime(splitList, 'StartWaitTurnAfterNested', 'EndWaitTurnAfterNested', 'Nested waitForMyTurn')
    findTime(splitList, 'StartCSBNSExecute', 'EndCSBNSExecute', 'Backend Computation')
    findTime(splitList, 'StartCSBNSExecute', 'CSBNSStartSend', 'CSBNS setup Computation')
    findTime(splitList, 'CSBNSStartSend', 'CSBNSEndSend', 'CSBNS Send')
    findTime(splitList, 'CSBNSStartSend', 'StartNettySend', 'CSBNS Send -> Netty Send')
    findTime(splitList, 'CSBNSEndSend', 'CSBNSStartRecv', 'CSBNS waiting...')
    findTime(splitList, 'CSBNSEndSend', 'CSBNSAboutWait', 'CSBNS between sending and waiting...')
    findTime(splitList, 'CSBNSAboutWait', 'CSBNSStartRecv', 'CSBNS waiting...')
    findTime(splitList, 'CSBNSStartRecv', 'CSBNSEndRecv', 'CSBNS Recv')
    findTime(splitList, 'StartBackendReq', 'EndBackendReq', 'Total Request Backend')
    findTime(splitList, 'StartBackendReq', 'reqRec', 'Between receive and processing')

    findTime(splitList, 'CSBNSStartSend', 'reqRec', 'CSBNS Send -> Backend')


if __name__ == '__main__':
    main()
