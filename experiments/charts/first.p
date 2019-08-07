filepath = ARG1
experimentName = ARG2
numThread = ARG3
set terminal postscript monochrome font "Helvetica,18"
outputString = sprintf("| pstopdf -o %s.pdf -i", filepath) #mac version
#outputString = sprintf("| ps2pdf - %s.pdf", filepath) #ubuntu version
set output outputString
set pointsize 1
set style line 1 lt 1 lw 1
set xlabel "Throughput (requests/second)" font "Helvetica,23"
set ylabel "Latency (ms)" font "Helvetica,23"
chartTitle = sprintf("Latency-Throughput for %s with %s threads", experimentName, numThread)
set title chartTitle font "Helvetica,24"
set yr[0:]
set xr[0:]
#set key top left
#set xtics 1,1,16
data = ".data"
dataString = filepath.data
plot dataString using 2:3 with lines ti "sequential replicated"

