#!/usr/bin/perl

use warnings;
use strict;

our %times;

while (<>) {
  chomp;
  my ($file,$line,$type,$timestamp,$l,$us,$r) = split;

  # find interval
  if ($type eq "SHUTDOWN") {
    if (exists $times{$us}) {
      if ($times{$us} ne "LIVE") {
	print (($timestamp - $times{$us}) . " $us never_went_live\n");
      }
    } else {
      print STDERR "shutdown of node $us we didn't know about at $timestamp\n";
    }
  } elsif ($type eq "1" or $type eq "2") {
    if (exists $times{$us}) {
      if ($times{$us} ne "LIVE") {
	print (($timestamp - $times{$us}) . " $us\n");
	$times{$us} = "LIVE";
      }
    } else {
      print STDERR "node $us went live and we didn't know about it at $timestamp\n";
    }
  } elsif ($type eq "4" or $type eq "5") {
    # LEAFSET 4 and 5 messages are from before the node is up
    if (!exists($times{$us})) {
      $times{$us} = $timestamp;
    } elsif ($times{$us} eq "LIVE") {
      print STDERR "node $us was live, but now waiting again at $timestamp\n";
    }
  }
}
