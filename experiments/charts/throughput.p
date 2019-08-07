filepath = ARG1
experiment = ARG2
set terminal postscript monochrome font "Helvetica,18"
outputString = sprintf("| pstopdf -o %s.pdf -i", filepath) #mac version
#outputString = sprintf("| ps2pdf - %s.pdf", filepath) #ubuntu version
set output outputString
set pointsize 1
set style line 1 lt 1 lw 1
set xlabel "Num threads (requests/second)" font "Helvetica,23"
set ylabel "Throughput (ms)" font "Helvetica,23"
experimentTitle = sprintf("Throughput of Adam for %s", experiment)
set title experimentTitle font "Helvetica,24"
set yr[0:]
set xr[0:16]
#set key top left
set xtics 1,1,16
data = ".data"
dataString = filepath.data
plot dataString using 1:2 with lines ti experiment

