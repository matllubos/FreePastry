#!/usr/bin/perl

use warnings;
use strict;

our @data;
our $total;

while (<>) {
  chomp;
  my $d = (split)[0];
  push @data, $d;
  $total += $d;
}

our $n = scalar(@data);
our $mean = $total / $n;

@data = sort {$a <=> $b} @data;

printf "mean: %.3g seconds\n",$mean / 1000;
if ($n % 2 == 1) {
  printf "median: %.3g seconds\n",($data[$n / 2]) / 1000;
} else {
  printf "median: %.3g seconds\n",($data[$n / 2 - 1]+$data[$n / 2]) / (2 * 1000);
}

our $stdev = 0;

for (@data) {
  $stdev += ($_ - ($mean))**2;
}

printf "stdev: %.3g seconds\n",(sqrt($stdev/($n-1))) / 1000;

