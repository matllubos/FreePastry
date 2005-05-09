#!/usr/bin/perl

use warnings;
use strict;

our @data;

while (<>) {
  chomp;
  my @line = split;

  if (@line == 3) {
    unshift @line,0;
  } elsif (@line == 4) {
    unshift @line, $line[3]-$line[2];
  } else {
    warn "bogus line: @line";
  }

  push @data, \@line;
}

@data = sort { $a->[0] <=> $b->[0] } @data;

for my $d (@data) {
#  shift @$d;
  print join(' ',@$d) . "\n";
}
