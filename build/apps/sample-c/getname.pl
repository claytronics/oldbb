#!/usr/bin/perl

$reduce = 0;

my %list = ();
my @input = ();
while (<>) {
    push(@input, $_);
    while (/0x[0-9a-f]+/) {
	my ($x) = /(0x[0-9a-f]+)/;
	s/$x/--/;
	$x =~ s/0x//;
	$list{$x} = 1;
    }
}
$str = "";
foreach $key (sort keys %list) {
    $str .= "nm arch-x86_64-Linux/newspan | grep $key; "
}
$result = `$str`;
@lines = split("\n", $result);
foreach $line (@lines) {
    ($adr, $name) = $line =~ /0+([0-9a-f]+) T ([^ \t\r\n]+)/;
#    print "0x$adr $name\n";
    $list{$adr} = $name;
}
$lastline = "";
%prev = ();
foreach $line (@input) {
    while ($line =~ /0x[0-9a-f]+/) {
	my ($x) = $line =~ /(0x[0-9a-f]+)/;
	$x =~ s/0x//;
	$y = $list{$x};
	$line =~ s/0x$x/$y/;
    }
    ($lastline eq $line) && next;
    if ($reduce && ($line =~ /^[0-9]+:(.*)/)) {
	($node, $rest) = $line =~ /^([0-9]+):(.*)/;
	if ($prev{$node} eq $rest) {
	    $line = "";
	} else {
	    $prev{$node} = $rest;
	}
    }
    print $line;
    $lastline = $line;
}
exit(0);

