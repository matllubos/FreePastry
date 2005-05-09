#!/usr/bin/perl

use warnings;
use strict;

sub usage($) {
  my $die = defined($_[0]) ? $_[0] : 0;
  print STDERR <<EOT;
usage: perl bandwidth.pl [-p|--plot] logfile [outfile]
 
-p will auto-run gnuplot with the result
if no outfile is specified, and -p is specified, a temp file is used
if no outfile is specified and -p is not specified, stdout is used
EOT
  exit(1) if ($die);
}

usage(1) unless @ARGV;

our $doplot = 0;

if ($ARGV[0] =~ /^-p/ or $ARGV[0] =~ /^--plot/) {
  shift @ARGV;
  $doplot = 1;
}

usage(1) unless @ARGV;

our $logfile = shift @ARGV;
our $outfile;
our $istmp = 0;
our $title;

if (@ARGV == 1) {
  $outfile = shift @ARGV;
} elsif (@ARGV == 0) {
  $outfile = $logfile;
  $outfile =~ s|^.*/||;
  $outfile =~ s|^log4\.txt\.|| or die "filename funniness: $outfile";
  if ($doplot) { 
    $title = $outfile;
    $outfile = "/tmp/bandwidth.".$outfile;
    $istmp = 1;
  } else {
    $outfile = "bandwidth.".$outfile;
  }
} else {
  usage(1);
}

open IN, "<", $logfile or die "can't open $logfile for input: $!";
open OUT, ">", $outfile or die "can't open $outfile for output: $!";

our $start_time = 1114000000;
our $time = 0;
our $read = 0;
our $sent = 0;

while (<IN>) {
  if (/^COUNT: (\d+)/) {
    my $t = int($1 / 1000);
    next unless m/size (\d+)/;
    my $b = $1;
    my $dir;
    if (m/Read/) {
      $dir = "read";
    } elsif (m/Sent/) {
      $dir = "sent";
    } else {
      next;
    }
    if ($time != $t) {
      if ($time != 0) {
#	print OUT "$time $read $sent\n"; #
	print OUT (($time - $start_time)." $read $sent\n");
      } else {
	$start_time = $t;
      }
      $time = $t;
      if ($dir eq "read") {
	$read = $b;
#	$read = 1;
	$sent = 0;
      } else {
	$read = 0;
	$sent = $b;
#	$sent =1;
      }
    } else {
      if ($dir eq "read") {
	$read += $b;
#	$read++;
      } else {
	$sent += $b;
#	$sent++;
      }
    }
  }
}

close IN;
close OUT;

if ($doplot) {
  my $plotfile = "/tmp/plot.$$";
  open GNUPLOT, ">", $plotfile or die "can't open $plotfile: $!";

  print GNUPLOT "set title \"$title\"\n" if defined $title;
  print GNUPLOT <<EOT;
set xlabel "seconds"
set ylabel "bytes/sec"
plot "$outfile" using 1:2 title "read", "$outfile" using 1:3 title "sent"
pause -1
EOT
  system("gnuplot $plotfile");
}

unlink $outfile if $istmp;

# planet3.pittsburgh.intel-research.net
# planet2.halifax.canet4.nodes.planet-lab.org
# planet1.att.nodes.planet-lab.org


# TODO
# better time-base management
# count packets mode
# 
