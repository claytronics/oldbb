#!/usr/bin/perl
#
($prog) = ($0 =~ m!([^/]+)$!);
$versionstring = "0.9 9/25/10";
$verbose = 0;
$nolines = 0;
$showvars = 0;
$output = "bb.o";
$cflags = "-g -Wall -O1";
$outputDirectory = "";
$srcDirectory = "";

#used with --fullPath flag for experimental fix
$useFullOutputPath=0;

#used with --sethPath flag for experimental fix
$useSethOutputPath=0;

#used to generate block code
$outputForBlocks=0;

%sendfunc = ( "send" => 2,
	      "reply" => 1,
	      "send2list" => 3,
	      "broadcast" => 1
    );

# process options
#

while ($ARGV[0] =~ /^-/) {
    $_ = shift @ARGV;
  option: 
    {
	if (/^-[hH?]/) {
	    &usage;
	    last option;
	}
	if (/^-V/) {
	    print "$prog: Version $versionstring\n";
	    last option;
	}
	if (/^-v/) {
	    $verbose = 1;
	    last option;
	}
	if (/^-s/) {
	    $showvars = 1;
	    last option;
	}
	if (/^-l/) {
	    $nolines = 1;
	    last option;
	}
	if (/^-d/) {
		$outputDirectory = shift @ARGV;
		$outputDirectory .= "/";
		$outputDirectory =~ s+/*$+/+;
		last option;
	}
	if( /^--basePath/){
	    $srcDirectory = shift @ARGV;
	    $srcDirectory .= "/";
	    $srcDirectory =~ s+/*$+/+;
	    last option;
	}
	if( /^--fullPath/){
	    $useFullOutputPath=1;
	    last option;
	}
	if( /^--sethPath/){
	    $useSethOutputPath=1;
	    last option;
	}
	if( /^--blocks/){
		$outputForBlocks=1;
		last option;
	}
	if (/^-c/) {
		$cflags = shift @ARGV;
		last option;
	}
	
	print "Unknown option: ", $_, "\n";
	&usage;
    }
}

print STDERR "$outputDirectory\n";
$varincludefile = $outputDirectory."system/localvars.h";
$typeincludefile = $outputDirectory."system/localtypes.h";
$basemsgdeffile = "msghandlers.c";
$msgdeffile = $outputDirectory."system/".$basemsgdeffile;
$basemsgincludefile = "msghandlers.h";
$msgincludefile = $outputDirectory."system/".$basemsgincludefile;
$defincludefile = $outputDirectory."system/"."localdefs.h";

open(H, ">$varincludefile") || die("$prog: Can't open $varincludefile\n");
open(T, ">$typeincludefile") || die("$prog: Can't open $typeincludefile\n");
open(C, ">$msgdeffile") || die("$prog: Can't open $msgdeffile\n");
open(M, ">$msgincludefile") || die("$prog: Can't open $msgincludefile\n");
open(D, ">$defincludefile") || die("$prog: Can't open $defincludefile\n");

#print M "#ifndef _MSGHANDLERS_H_\n#define _MSGHANDLERS_H_\n\ntypedef void (*MsgHandler)();\n\n";
print M "#ifndef _MSGHANDLERS_H_\n#define _MSGHANDLERS_H_\n\n";
print C "#include \"$basemsgincludefile\"\n#include \"../sim/block.h\"\n\n";

# first pass just finds threadvars & bbh file names
$pass = 1;

print STDERR "pass 1 ...\n";
while ($#ARGV >= 0) {
    $infile = shift @ARGV;
    push @files, $infile;
    ($basedir, $basename) = $infile =~ m-(.*/)([^/]+)\.[^\.\\]+$-;
    if ($basename eq "") {
	$basedir = "./";
	($basename) = $infile =~ m-(.*)\.[^\.\\]+$-;
    }

    $verbose && print STDERR "pass 1: $infile\n";
    if ($infile =~ /\.bb$/) {
	$gcc = $outputForBlocks ? "avr-gcc" : "gcc";
	$mcu = $outputForBlocks ? "-mmcu=atxmega256a3" : "";

	if ($srcDirectory ne "") {
	    ($origdirectory) = $infile =~ m-(.*)/[^/]+$-;
	    if ($origdirectory =~ /$outputDirectory/) {
		$origdirectory =~ s/$outputDirectory/$srcDirectory/;
		$origdirectory = " -I$origdirectory";
		
	    } else {
		$origdirectory = "";
	    }
	} else {
	    $origdirectory = "";
	}

	$verbose && print STDERR "$gcc $mcu -x c $origdirectory $cflags -DIGNORE_IN_PASS1_OFF_COMPILE_BB -E -c $infile -o /tmp/$$.ccc";
	system("$gcc $mcu -x c $origdirectory $cflags -DIGNORE_IN_PASS1_OFF_COMPILE_BB -E -c $infile -o /tmp/$$.ccc");
	open(F, "</tmp/$$.ccc") || die("$prog: Can't open gcc header list file /tmp/$$.ccc");
	$lasttype = "";
	while (<F>) {
	    if (/# [0-9]+ ".*\.bbh"/) {
		# see about including .bbh file
		($iname) = /# [0-9]+ "(.*\.bbh)"/;
		@dirs = split('/', $iname);
		@gdirs = ();
		foreach $d (@dirs) {
		    if ($d eq ".") {
		    } elsif ($d eq "..") {
			pop @gdirs;
		    } else {
			push @gdirs, $d;
		    }
		}
		$iname = "";
		$sep = "";
		foreach $d (@gdirs) {
		    $iname .= $sep.$d;
		    $sep = "/";
		}
		if (!exists($handled{$iname})) {
		    $handled{$iname} = 1;
		    push @ARGV, $iname;
		}
	    } elsif (/^threadtype/) {
		$typestring = &gettypestring(0);
		$typestring =~ s/\n+$//;
		if (!($typestring =~ /^typedef/)) {
		    ($sname) = $typestring =~ /([a-z]+[ \t]+[a-zA-Z_][a-zA-Z_0-9]+)/;
		    $oldname = $sname;
		    $sname =~ s/[ \t]+/___/;
		    $typestring =~ s/;[ \t]*$//;
		    $end = "";
		    while ($typestring =~ /((struct|union|enum)[ \t]+[a-zA-Z_][a-zA-Z_0-9]*[ \t*]*)/g) {
			$x = $1;
			($x =~ /$oldname/) && next;
			($x =~ /[*][ \t]*$/) && next;
			# we need to make sure this struct is defined before this type is defined
			$x =~ s/^[ \t]*//;
			$x =~ s/[ \t]*$//;
			$x =~ s/[ \t]+/___/;
			$end .= " $x ";
		    }
		    $typestring = "/* $end */ typedef $typestring $sname;";
		}
		($body, $name) = $typestring =~ /(.*[} \t])([a-zA-Z_][a-zA-Z_0-9]*)[ \t]*;$/;
#	print STDERR "[$name]:\t[$body]\t:[$typestring]\n";
		if ($name eq "") {
		    # this must be a function type
		    ($name) = $typestring =~ /[(][ \t]*[*][ \t]*([a-zA-Z_][a-zA-Z_0-9]*)[ \t]*[)]/;
		    $ftype{$name} = $typestring;
		    $body = $typestring;
		    $body =~ s/$name//;
		}
		if (!exists $types{$name}) {
		    $types{$name} = $body;
		    push @alltypes, $name;
		    if ($lasttype ne "") {
#		print STDERR "$name <- $lasttype\n";
			push @{$prereq{$name}}, $lasttype;
		    }
		}
		$lasttype = $name;
	    }
	}
	close(F);
	unlink("$$.ccc $$.xxx");
	if (0) {
	    print STDERR "-------- $infile\n";
	    foreach $t (@alltypes) {
		print STDERR "$t:";
		foreach $u (@{$prereq{$t}}) {
		    print STDERR "\t$u";
		}
		print STDERR "\n";
	    }
	}
    }
    #"s: %lu, r: %lu, c: %lu, sp: %f", estimatedGlobalTime, receiveTime, getClockForTime(receiveTime), speedAvg
    #open(F, "<$infile") || die("$prog: Can't open $infile\n");

    while (<F>) {
	s/[ \r\n\t]*$//;
	if (/threadvar/) {
	    s/threadvar//;
	    $buffer = &grabstmt();
	    if ($buffer =~ /=/) {
		# has initialization
		($decl, $init) = $buffer =~ /([^=]+)\s*=\s*([^;]+);.*$/;
	    } else {
		$init = "";
		($decl) = $buffer =~ /([^;]*)\s*;.*$/;
	    }
	    ($pre, $var, $post) = $decl =~ /([^(]*\s\(?\**)([a-zA-Z_][a-zA-Z_0-9]*)([\s[()]*.*)/;
	    $showvars && print STDERR "localvar: $var" . (($init ne "") ? " = $init" : "") . "\n";
	    $localvar{$var} = $infile;
	    $localinit{$var} = $init;
	}  else {
	    &procline;
	}
    }
    close(F);
}

# second pass do actual rewrite
$pass = 2;
print STDERR "pass 2 ...\n";

while ($#files >= 0) {
	$infile = shift @files;
	($basedir, $basename) = $infile =~ m-(.*/)([^/]+)\.[^\.\\]+$-;
	if ($basename eq "") {
	    $basedir = "./";
	    ($basename) = $infile =~ m-(.*)\.[^\.\\]+$-;
	}
	$outname = $basename;
	if ( $useFullOutputPath == 1) {
	    $outfile = $basedir.$basename;
	} elsif ( $useSethOutputPath == 1) {
	    ($prefix) = $infile =~ m-.*/([^/]+/)[^/]+$-;
	    $outfile = $outputDirectory.$prefix.$basename;
	} else {
	    $outfile = $outputDirectory.$basename;
	}
		
	$verbose && print STDERR "pass 2 $infile -> $outfile\n";

	if ($infile =~ m/\.bb$/) {
		$outfile = $outfile . ".c";
		$sources = $sources . " " . $outname . ".c";
	}
	elsif ($infile =~ m/\.bbh$/) {
		$outfile = $outfile . ".h";
	}
	else {
		die("$prog: Invalid file input type - '$infile' must end with .bb or .bbh");
	}

	open(F, "<$infile") || die("$prog: Can't open $infile\n");
	open(G, ">$outfile") || die("$prog: Can't open $outfile\n");
	$nolines || print G "# 1 \"$infile\"\n";
	
	if($outputForBlocks ==0 ){
		print G "#include \"../sim/block.h\"\n";
	}
	while (<F>) {
	    s/[ \r\n\t]*$//;
	    if (/threadvar/) {
		s/threadvar//;
		$buffer = &grabstmt();
		if ($buffer =~ /=/) {
		    # has initialization
		    ($decl, $init) = $buffer =~ /([^=]+)\s*=\s*([^;]+);.*$/;
		} else {
		    $init = "";
		    ($decl) = $buffer =~ /([^;]*)\s*;.*$/;
		}
		($pre, $var, $post) = $decl =~ /([^(]*\s\(?\**)([a-zA-Z_][a-zA-Z_0-9]*)([\s[()]*.*)/;
		$showvars && print STDERR "localvar: $var" . (($init ne "") ? " = $init" : "") . "\n";
		$localvar{$var} = $infile;
		$localinit{$var} = $init;
		if($outputForBlocks == 0){
			print H "$decl;\n";
			print G "//$buffer\n";
		}else{
			print G "$buffer\n";
		}

	    } elsif (/^threadtype/) {
		$typestring = &gettypestring(1);
	    } elsif (/msgfunction/) {
		s/msgfunction//;
		if (/;/) {
		    ($name) = /([a-zA-Z_][a-zA-Z0-9_]*)\(/;
		    $msgfuncs{$name} = 1;
		    
		    print C "extern $_\n";
		}
	    	print G "$_\n";
	    } elsif (/threaddef/) {
			s/threaddef//;
			print D "$_\n";
			if($outputForBlocks==0){
				print G "//$_\n";
			}else{
				print G "$_\n";
			}
	    } elsif (/threadextern (.*)$/){
			if($outputForBlocks==0){
				print G "//$1\n";
			}else{
				print G "extern $1\n";
			}
   		} elsif (/#define/) {
				&procline;
	    } elsif (/#include ".*\.bbh"/) {
		s/.bbh"/.h"/;
	    	print G "$_\n";
	    } elsif (/#/) {
	    	print G "$_\n";
	    } else {
				&procline;
	    }
	}
	close(F);
	close(G);
}

# print types

if (0) {
    while ($#alltypes >= 0) {
	for ($i=0; $i<= $#alltypes; $i++) {
	    $name = $alltypes[$i];
	    $body = $types{$name};
	    $ok = 1;
	    for ($j=0; $j<= $#alltypes; $j++) {
		($i == $j) && next;
		$x = $alltypes[$j];
		if ($body =~ /$x/) {
		    # print STDERR "$body requires $x\n";
		    $ok = 0;
		    last;
		}
	    }
	    ($ok == 0) && next;
	    if ($ftype{$name}) {
		print T $ftype{$name}."\n";
	    } else {
		print T "$body $name;\n";
	    }
	    $alltypes[$i] = $alltypes[$#alltypes];
	    $#alltypes--;
	    last;
	}
    }
} else {
    # new method which uses include file order
    foreach $name (@alltypes) {
	$body = $types{$name};
	if ($ftype{$name}) {
	    print T $ftype{$name}."\n";
	} else {
	    print T "$body $name;\n";
	}
    }
}

# write message table
$msgid = 0;
foreach $msg (sort keys %msgfuncs) {
    print M "#define MSG$msg $msgid\n";
    $msgid++;
}
print M "#define MSG_ILLEGAL $msgid\n";
print C "MsgHandler msgHandlerTable[] = {\n";
foreach $msg (sort keys %msgfuncs) {
    print C "\t(MsgHandler)$msg,\n";
}
print C "\t(MsgHandler)0\n};\nint last_msg_table_entry = MSG_ILLEGAL;\n";

# write decode function
print C "char* mt2str(char m) { switch (m) {\n";
foreach $msg (sort keys %msgfuncs) {
    print C "\tcase MSG$msg: return \"$msg\";\n";
}
print C "} return \"????\"; }\n";

# write init function

print C "void initThreadVars(void) {\n";
foreach $var (sort keys %localinit) {
    $init = $localinit{$var};
    if ($init ne "") {
	print C "\tthis()->$var = $init;\n";
    }
}
print C "}\n";
close(C);

print M "\n#endif\n";

close(M);
close(H);
close(T);
close(D);

exit(0);

#####
#  get a threadtype string. modify $_. read from <F>.
#  print on pass 2, get on pass 1

sub gettypestring {
    my ($print) = @_;
    my ($typestring);

    s/threadtype//;
    $typestring = $_;
    $typestring =~ s![ \t]*//.*$!!;
    $typestring =~ s![ \t]*$!!;
    if ($print) {
	if ( $outputForBlocks ==0 ){
	    print G "//$_\n";
	}else {
	    print G "$_\n";
	}
    }
    while (!/;/) {
	$_ = <F>;
	s/[ \r\n\t]*$//;
	$typestring .= (" ".$_);
	if ($print) {
	    if($outputForBlocks ==0 ){
		print G "//$_\n";
	    }else {
		print G "$_\n";
	    }
	}
    }
    $typestring =~ s![ \t]*//.*$!!;
    $typestring =~ s![ \t]*$!!;
    $typestring =~ s!^[ \t]*!!;
    return $typestring;
}



#####
#  grab til a ;.  input $_ current line, Modify $_ for rest.  read from <F>

sub grabstmt {
    my ($line) = $_;
    my ($x);
    my ($stmt, $rest);

    while (!($line =~ /;/)) {
	$x = <F>;
	$x =~ s/[ \r\n\t]*$//;
	$x =~ s/^[ \r\n\t]*/ /;
	$line .= $x;
    }
    ($stmt, $rest) = $line =~ /^([^;]*;)(.*)$/;
    $_ = $rest;
    return $stmt;
}

sub procline {
	s/[ \r\n\t]*$//;
	if( $pass==2 && $outputForBlocks==1){
		print G "$_\n";
		return;
	}

	
	if ($_ eq "") {
	    ($pass == 2) && print G "\n";
	    return;
	}
	
	@tokens = split(/(\*\/|\/\/|\/\*|[][{}()@;]|[ \t]+|[=,\"\'.\+\-!*\/<>&^])/, $_);
	while ((scalar @tokens)>0) {
	    $t = shift @tokens;
	    
	    if ($t =~ /^\s*[a-zA-Z_][a-zA-Z_0-9]*\s*$/) {
			($var) = $t =~ /^\s*([a-zA-Z_][a-zA-Z_0-9]*)\s*$/;
			if (exists($localvar{$var})) {
			    $t =~ s/^\s*([a-zA-Z_][a-zA-Z_0-9]*)\s*$/ (this()->\1) /;
			} elsif (exists($sendfunc{$var})) {
			    # this is a send function, count args
			    @tokens = &procsend($var, @tokens);
			    $t = "";
			}
	    } elsif ($t eq "@") {
			$t = "MSG";
	    } elsif ($t eq "//") {
			@tokens = &copy2end($t, @tokens);
			$t = "";
	    } elsif ($t eq "/*") {
			@tokens = &copycomment($t, @tokens);
			$t = "";
	    }
	    ($pass == 2) && print G $t;
	}
	($pass == 2) && print G "\n";
}

sub copy2end {
    my ($start, @toks) = @_;
    ($pass == 2) && print G $start;
    foreach $start (@toks) {
	($pass == 2) && print G $start;
    }
}

sub copycomment {
    my ($start, @toks) = @_;
    ($pass == 2) && print G $start;
    while (1) {
	while ((scalar @toks)>0) {
	    $start = shift @toks;
	    ($pass == 2) && print G $start;
	    if ($start eq "*/") {
		return @toks;
	    }
	}
	$_ = <F>;
	eof &&     die("$prog: Ran out of file in comment?");
	s/[ \r\n\t]*$//;
	@toks = split(/(\*\/|\/\/|\/\*|[][{}()@;]|[ \t]+|[=,\"\'.\+\-!*\/<>&^])/, $_);
    }

}

################################################################
#	convert send(asdasd) to sendX(asdasd) where X is # of args

sub procsend {
    my ($sname, @toks) = @_;
    my $basecount = $sendfunc{$sname};
    my $line = "";
    my $parins = 0;
    my $t;
    my $argcount = 0;
    my $something = 0;
    my $addlv = 0;
    my $gotopening = 0;
    while (1) {
	while ((scalar @toks)>0) {
	    $t = shift @toks;
	    if ($t =~ /[{}]/) {
		die("$prog: unexpected brace while processing msg send/reply/etc.");
	    } elsif ($t =~ /\(/) {
		$gotopening = 1;
		if ($parins == 0) {
		    $something = 0;
		}
		if ($addlv > 0) {
		    $t .= ("_lv" . (($addlv == 2) ? "," : ""));
		}
		$addlv = 0;
		$parins++;
	    } elsif ($t =~ /\)/) {
		$parins--;
		($parins == 0) && last;
	    } elsif ($t =~ /,/) {
		if ($parins == 1) {
		    $argcount++;
		}
	    } elsif ($t =~ /^\s*[a-zA-Z_][a-zA-Z_0-9]*\s*$/) {
		$addlv = 0;
		($var) = $t =~ /^\s*([a-zA-Z_][a-zA-Z_0-9]*)\s*$/;
		if (exists($localvar{$var})) {
		    $t =~ s/^\s*([a-zA-Z_][a-zA-Z_0-9]*)\s*$/ (_lv->\1) /;
		} elsif (exists($localfunc{$var})) {
		    $addlv = $localfunc{$var};
		} elsif (exists($sendfunc{$var})) {
		    # this is a send function, count args
		    die("$prog: $var inside of $sname");
		}
	    } elsif ($t eq "@") {
		$t = "MSG";
	    } else {
		$something++;
	    }
	    $line .= $t;
	}
	(($parins == 0)&&($gotopening)) && last;
	$_ = <F>;
	eof && die("$prog: ran out file processing $sname");
	s/[ \r\n\t]*$//;
	@toks = split(/([{}(),@]|[][ \t=.\+\-!*\/<>&;^]+)/, $_);
    }
    $argcount -= $basecount;
    if ($something > 0) {
	$argcount++;
    }
    ($argcount >= 0) || die("$prog: Too few arguments ($argcount) to $sname");
    if ($argcount < 4) {
        $sname .= $argcount;
    }
    ($pass == 2) && print G $sname.$line.")";
    return @toks;
}

################################################################
#	usage

sub usage {
    print STDERR <<"EndEndEnd";

$prog [-v][-s] infile

convert infile into infile.c and create localvars.h and localtypes.h

	-V:		print version info
	-v:		verbose
	-s:		show thread vars and funcs
	-l:		don't include #line directives in output files
	-d <dir>:	set output directory
	--fullpath:
	--basePath:
        --sethPath:
	--blocks:	compile for blocks
	-c:		string to pass to gcc for compilation

Version: $versionstring

EndEndEnd
    exit 0;
}
