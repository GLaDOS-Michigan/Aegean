#!/usr/bin/perl

# There is only 1 logging file.
# We need to separate it according to the different values of ebid
# $1: name of the logging file

if ($#ARGV != 0) {
	print "Wrong number of arguments.\n";
	print "Usage: ./extract_ebids.pl <logging_file>\n";
	exit;
}

$log_file = $ARGV[0];

open(LOGFILE, "< $log_file") or die "Unable to open the file in read mode: $!\n";

# for each line
while (<LOGFILE>) {
   # get ebid
   /(\d+:\d+:\d+,\d+\t\d+\t\w+)\t(\d+)\t(.*)/;
   my $ebid = $2;
   #print "ebid is $ebid\n";

   # put line (minus ebid) in request${ebid}.log
   my $new_line = "$1\t$3";
   #print "New line is $new_line";
   
   open(NEWFILE, ">> $log_file$ebid") or die "Unable to open the file in write mode: $!\n";
   print NEWFILE $new_line."\n";
   close(NEWFILE) or die "Unable to close the file: $!\n";
   
}

close(LOGFILE) or die "Unable to close the file: $!\n";
