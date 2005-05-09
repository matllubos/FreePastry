#!/usr/bin/perl

use warnings;
use strict;

my @data;

while (<>) {
    chomp;
    my $n = (split)[0];
    $data[$n/10000]++;
}


for my $i (0..$#data) {
    print $i*10 . " " . $data[$i] . "\n" if defined($data[$i]);
}
