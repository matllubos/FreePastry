#!/usr/bin/perl

# prints out data for interval vizualization
# run as "perl vizdata.pl extracted > vizdata"
# where extracted is the output of extract.pl

use warnings;
use strict;

our $size = 0x1000000;

# finds halfway point bewteen two keys
sub half {
  my ($a,$b) = (hex($_[0]),hex($_[1]));

  if ($a > $b) {
    # in this case we're wrapping over
    my $mean = ($a+$b+$size)/2;
    $mean -= $size if ($mean > $size);
    return sprintf "0x%06X",$mean;
  } else {
    return sprintf "0x%06X",($a+$b)/2;
  }
}

our @start = ("","",0,0,0,0,0);

# read data
while (<>) {
  chomp;
  my ($file,$line,$type,$timestamp,$l,$us,$r) = split;

  # find interval
  my ($l_int,$r_int) = (half($l,$us),half($us,$r));

  if ($type eq "SHUTDOWN") {
    ($l_int,$r_int) = (0,0);
    $type = 3; # java vizualizer expects an int
  }

  if (($type ne $start[2]) or ($us ne $start[5]) or ($l_int ne $start[4]) or ($r_int ne $start[6])) {
    print join(' ',$file,$line,$type,$timestamp,hex($l_int),$us,hex($r_int)) . "\n";
    @start = ($file,$line,$type,$timestamp,$l_int,$us,$r_int);
  }
}

