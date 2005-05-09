#!/usr/bin/perl

# find overlaps between spaces nodes are responsible for.
# run as "perl interval.pl extracted > output"
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


sub overlap {
  my ($r1,$r2) = @_;  # each is ($left extent,$right extent)

  if (!defined($r1->[0]) || !defined($r2->[0])) {
    # one of them isn't up right now
#    warn "wassup? @$r1 @$r2 $_[2] $_[3]\n";
    return 0;
  }

  return 0 if (($r1->[0] eq "SHUTDOWN") or ($r2->[0] eq "SHUTDOWN"));

  # $r1's right edge is to the right of $r2's left edge
  # AND
  # $r1's left edge is to the left of $r2's right edge
  if ((hex($r1->[1]) > hex($r2->[0])) and (hex($r1->[0]) < hex($r2->[1]))) {
    return 1;
  }

  # converse of above
  if ((hex($r2->[1]) > hex($r1->[0])) and (hex($r2->[0]) < hex($r1->[1]))) {
    return 1;
  }

  return 0;
}

# we make the assumption (for backtracking through output purposes)
# that every id maps to exactly one file.

our %overlaps; # {id}{id} -> [[start_time, end_time], ...]

our %times;

# read data
while (<>) {
  chomp;
  my ($file,$line,$type,$timestamp,$l,$us,$r) = split;

  # find interval
  if ($type eq "SHUTDOWN") {
    push @{$times{$timestamp}},[$file,$line,$us,"SHUTDOWN","SHUTDOWN"];
  } elsif ($type eq "1" or $type eq "2") {
    # LEAFSET 4 and 5 messages are from before the node is up

    my ($l_int,$r_int) = (half($l,$us),half($us,$r));

    #  print "> $type $timestamp $l $us $r : $l_int $r_int\n";

    push @{$times{$timestamp}},[$file,$line,$us,$l_int,$r_int];
  }
}

our %world; # {id} -> [$l_int,$r_int,$time,$file,$line]

my $ntimes = scalar(keys %times);
my $n = 0;
my $onepercent = int($ntimes / 100.0 + 0.5);

# walk through timeline
for my $t (sort { $a <=> $b } keys %times) {
  if ($n++ % $onepercent == 0) {
    printf STDERR "processing time $t (%d percent) [%s]\n", int($n / $ntimes * 100.0), join(' ',(times)[0,1]);
  }
  # check for overlaps
  for my $e (@{$times{$t}}) {
    my $foo = [$e->[3],$e->[4],$t,$e->[0],$e->[1]];
    my $k2 = $e->[2];
    if ($foo->[0] eq "SHUTDOWN") {
      delete $world{$k2};
    } else {
      $world{$k2} = $foo;
    }
    warn "empty world" if scalar(keys %world)==0;
    for my $k1 (keys %world){ 
      my ($rk1,$rk2) = (hex($k1) < hex($k2) ? ($k1,$k2) : ($k2,$k1));
      if ($rk1 ne $rk2) {
	# now SHUTDOWN messages are bunged up
	if (overlap($world{$rk1},$world{$rk2})) {
	  print STDERR "$rk1 $rk2 found an overlap\n";
	  if (!exists($overlaps{$rk1}{$rk2})) {
	    print STDERR "$rk1 $rk2 started an overlap\n";
	    $overlaps{$rk1}{$rk2} = $t;
	  }
	} elsif (exists($overlaps{$rk1}{$rk2})) {
	  print STDERR "$rk1 $rk2 ended an overlap\n";
	  print "$rk1 $rk2 ".$overlaps{$rk1}{$rk2}." $t\n";
	  delete $overlaps{$rk1}{$rk2};
	}
      }
    }
  }
}

