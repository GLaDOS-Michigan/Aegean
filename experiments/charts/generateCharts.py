#!/usr/bin/env python
import os

def findMaxTrouhgoutFromFile(file):
    maxTrouhgput = 0
    with open(file, "r") as myfile:
        data = myfile.readlines()
        for line in data:
            values = line.split()
            throuhgput = int(values[1])
            maxTrouhgput = max(throuhgput, maxTrouhgput)
    return maxTrouhgput


def downloadResults(folder, experimentName):
    serverName = 'remzican@skynet.eecs.umich.edu'
    print "ssh %s \'cd ~/git/adam/experiments; mkdir %s_TMP; cd %s; cp --parents */results.txt ../%s_TMP/\'" % (serverName, experimentName, folder, experimentName)
    os.system("ssh %s \'cd ~/git/adam/experiments; mkdir %s_TMP; cd %s; cp --parents */results.txt ../%s_TMP/\'" % (serverName, experimentName, folder, experimentName))
    os.system("scp -r %s:~/git/adam/experiments/%s_TMP ." % (serverName, experimentName))
    os.system("ssh %s \'rm -rf ~/git/adam/experiments/%s_TMP\'" % (serverName, experimentName))
    os.system("mv %s_TMP %s" % (experimentName, experimentName))


def main():
    experimentName = raw_input("The name for the experiment(default is \'results\'):")
    if not experimentName:
        experimentName = "results"

    download = raw_input("Do you want to download the results from the server. Default is yes, type anything for no:")
    if not download:
        folder = raw_input("The name of folder on the server, which has the experiment results (default is experimentName):")
        if not folder:  # false if empty string
            folder = experimentName
        os.system("rm -rf %s" % experimentName)
        downloadResults(folder, experimentName)

    subexperiments = [x[0] for x in os.walk(experimentName)]
    subexperiments = subexperiments[1:]
    print subexperiments
    throughputResults = {}
    for subexperiment in subexperiments:
        # I may need to trim lines with "cannot find results" as well
        numThreads = int(subexperiment.split('_')[-1])
        print "sed \'/throuhgput/d\' %s/results.txt | sed \'/find/d\' > %s/modified_results.data" % (subexperiment, subexperiment)
        os.system("sed \'/throuhgput/d\' %s/results.txt | sed \'/find/d\' > %s/modified_results.data" % (subexperiment, subexperiment))
        #os.system("sed \'/find/d\' %s/modified_results_tmp.data > %s/modified_results.data" % (subexperiment, subexperiment))
        print "cd %s; gnuplot -c ../../first.p modified_results %s %s" % (subexperiment, experimentName, numThreads)
        os.system("cd %s; gnuplot -c ../../first.p modified_results %s %s" % (subexperiment, experimentName, numThreads))
        maxThrouhgput = findMaxTrouhgoutFromFile("%s/modified_results.data" % subexperiment)
        throughputResults[numThreads] = maxThrouhgput
        #os.system("echo \'%s %s\' >> throughput_results.data" % (numThreads, maxThrouhgput))

    os.system("rm -rf %s/throughput_results.data" % experimentName)
    for i in range(1,17):
        if i in throughputResults:
            os.system("echo \'%s %s\' >> %s/throughput_results.data" % (i, throughputResults[i], experimentName))
    os.system("gnuplot -c throughput.p %s/throughput_results %s" % (experimentName, experimentName))

if __name__ == '__main__':
    main()
