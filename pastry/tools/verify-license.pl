#!/usr/bin/perl -w

#verifyLicense.pl
#usage ./verifyLicense.pl licensefile src-dir

use warnings;
use strict;

our $licensefile = shift;
our $src_dir = shift;

our $license_text = `cat $licensefile`;

$license_text = quotemeta($license_text); #escape the license

#print "$license_text\n";

our @needs_license;
our $msg;
my $numFiles = 0;

open FILEHANDLE, "find $src_dir \\\( -name '.svn' -prune \\\) -o \\\( -name '*.java' -print \\\) |";
while (<FILEHANDLE>) {
	 chomp $_; 
	 my $java_file = $_;
#	 print "$java_file\n"; 
	 $numFiles++;
	 
	 my $java_contents = `head -50 ./$java_file`;
	 if ($java_contents =~ /^$license_text/) {
#		 print "$java_file TRUE\n";
	 } else {
#		 print "$java_file FALSE\n";		 
		 $msg .= "$java_file\n";
     push @needs_license, $java_file;
	 }
}

my $num_needs_license = scalar(@needs_license);

if ($num_needs_license) {
  my $TODAY = `date +%Y%m%d`;
  my $date = `date`;

  chomp $TODAY;
  chomp $date;

  print "$num_needs_license files need a license.\n";
  print "$msg\n";
        # compute date, today
        my $recipient = "jeffh\@cs.rice.edu,jstewart\@mpi-sws.mpg.de"; # "Pastry Team <freepastry\@cs.rice.edu>";
        my $whole_msg = <<EOM;
From: Automatic Build Server <no-reply\@mpi-sws.mpg.de>
To: $recipient
Subject: [pastry-build] $num_needs_license files are missing the license $TODAY
Date: $date

$num_needs_license files need a license.

$msg

EOM
   open my $fh, "|sendmail $recipient";
        print $fh $whole_msg;
} else {
  print "All $numFiles java files have the license. \n";	
}

