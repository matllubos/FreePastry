#!/usr/bin/perl

use warnings;
use strict;

our $earliest;
our $latest;

our $extractfile = $ARGV[0];

open IN, "<",$extractfile or die "opening $extractfile: $!";

while (<IN>) {
  chomp;
  my ($file,$line,$type,$timestamp,$l,$us,$r) = split;

  if ($file =~ /ricepl-1.cs.rice.edu/) {
      $earliest = $timestamp unless defined($earliest);
      $latest = $timestamp;
  }
}

#                123456789012345678901234567
print STDERR "   ___________________________\n";
print STDERR "  /                           \\\n";
print STDERR " /                             \\\n";
print STDERR "|            ricepl-1           |\n";
print STDERR "|                               |\n";
print STDERR "|             R.I.P.            |\n";
print STDERR "|                               |\n";
print STDERR "|  $earliest-$latest  |\n";
print STDERR "|                               |\n";
print STDERR "|     devoted bootstrap node    |\n";
print STDERR "|                               |\n";
print STDERR "|                               |\n";
print STDERR "---------------------------------\n";

close(IN);
open IN, "<",$extractfile or die "opening $extractfile: $!";

while (<IN>) {
  chomp;
  my ($file,$line,$type,$timestamp,$l,$us,$r) = split;

  print "$_\n" if ($timestamp >= $earliest and $timestamp <= $latest);
}
