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
our @has_tab;
our @has_cr;

our $msg_license = "";
our $msg_tab = "";
our $msg_cr = "";
my $numFiles = 0;

open FILEHANDLE, "find $src_dir \\\( -name '.svn' -prune \\\) -o \\\( -name '*.java' -print \\\) |";
while (<FILEHANDLE>) {
	 chomp $_; 
	 my $java_file = $_;
#	 print "$java_file\n"; 
	 $numFiles++;
	 
	 
	 open my $fh, "<", $java_file;
	 local $/;  # so we read whole file in one go
	 my $java_contents = <$fh>;
	 close $fh;
#	 my $java_contents = `head -50 ./$java_file`;
	 if ($java_contents =~ /^$license_text/) {
	 } else {
		 $msg_license .= "\t$java_file\n";
         push @needs_license, $java_file;
	 }
	 
	 if ($java_contents =~ /\t/) {
		 $msg_tab .= "\t$java_file\n";
         push @has_tab, $java_file;	 
	 }
	 
	 if ($java_contents =~ /\r/) {
		 $msg_cr .= "\t$java_file\n";
         push @has_cr, $java_file;	 
	 }
}

my $num_needs_license = scalar(@needs_license);
my $num_has_tab = scalar(@has_tab);
my $num_has_cr = scalar(@has_cr);

if ($num_needs_license || $num_has_tab || $num_has_cr) {
  my $TODAY = `date +%Y%m%d`;
  my $date = `date`;

  chomp $TODAY;
  chomp $date;

  print "$num_needs_license files need a license.\n";
  print "$msg_license\n";
  print "$num_has_tab files have tabs.\n";
  print "$msg_tab\n";   
  print "$num_has_cr files are dos format.\n";
  print "$msg_cr\n";   
  
  # compute date, today
  my $recipient = "jeffh\@mpi-sws.mpg.de,jstewart\@mpi-sws.mpg.de"; # "Pastry Team <freepastry\@cs.rice.edu>";
  my $whole_msg = <<EOM;
From: Automatic Build Server <no-reply\@mpi-sws.mpg.de>
To: $recipient
Subject: [pastry-build] need license:$num_needs_license has tabs:$num_has_tab dos files:$num_has_cr $TODAY
Date: $date

$num_needs_license files need a license.
$msg_license

$num_has_tab files have tabs.
$msg_tab   

$num_has_cr files are dos format.
$msg_cr   
  

EOM
   open my $fh, "|sendmail $recipient";
        print $fh $whole_msg;
} else {
  print "All $numFiles java files are fine. \n";	
}

