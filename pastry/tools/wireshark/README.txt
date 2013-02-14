This document is a HOWTO for the FreePastry plugin testers. It describes how to build the wireshark plugin, how to use it and how to update it.

A complete documentation about wireshark can be found here:
http://www.wireshark.org/docs/wsdg_html/


Notes:
The FreePastry plugin code is compliant with the 0.99.5 build of wireshark.

If, for some reasons, you need to build the plugin for a wireshark/etheral version prior to 0.99.5, you have to:
  * edit packet-freepastry.c
  * find the function get_freepastry_pdu_len (~line 1229) and update arguments list 
  from: static guint get_freepastry_pdu_len(packet_info *pinfo _U_, tvbuff_t *tvb, int offset)
  to: static guint get_freepastry_pdu_len(tvbuff_t *tvb, int offset)

Directly compile the plugin (not recommended):
  You can try to directly compile the plugin for your system and to copy the library into an already installed wireshark directory (plugins/<version>).
Unfortunately, chances are it does not work. 

Compile Wireshark + FreePastry Plugin:
You will have to compile the whole source and then to update some top-level files.

1) Install the source for wireshark:
Unix:http://www.wireshark.org/docs/wsdg_html/#ChSetupUNIX
Windows: http://www.wireshark.org/docs/wsdg_html/#ChSetupWin32 (follow the steps through section 2.2.7)

2) copy freepastry plugin
  option a) from the freepastry src distribution
    copy <freepastry-src-folder>/tools/wireshark/ <wireshark-src-folder>/plugins/freepastry/
  option b) from the freepastry svn server
    cd <wireshark-src-folder>/plugins/
    svn co --username anonymous --password anonymous https://svn.mpi-sws.mpg.de/svn/freepastry/trunk/pastry/tools/wireshark freepastry
    
3. Add FreePastry to wireshark's make files:
You must apply the following changes:

* Unix:

configure (~line 31812)
	add plugins/freepastry/Makefile to the list of ac_config_files

configure.in (~line 1371)
	add plugins/freepastry/Makefile to the list of AC_OUTPUT

plugins/Makefile.am (~line 32)
	add "freepastry \" to the list of SUBDIRS


plugins/Makefile.in  (~line 333)
	add "freepastry \" to the list of SUBDIRS


* Windows:

/Makefile.nmake
  copy the plugin dll to the distribution
  xcopy ".\plugins\freepastry\freepastry.dll" $(INSTALL_DIR)\plugins\$(VERSION) /d (~line 873)


plugins/Makefile.nmake
	add "freepastry \" to the list of "all" directive (~line 17)
	
	add the freepastry directive (~line 69)
freepastry::
	cd freepastry
	$(MAKE) /$(MAKEFLAGS) -f Makefile.nmake
	cd ..

	add the following text for the clean directive (~line 173)
	cd freepastry
	$(MAKE) /$(MAKEFLAGS) -f Makefile.nmake clean
	cd ..

	add the following text for the distclean directive (~line 245)
	cd freepastry
	$(MAKE) /$(MAKEFLAGS) -f Makefile.nmake distclean
	cd ..

	add the following text for the maintainer-clean directive (~line 317)
	cd freepastry
	$(MAKE) /$(MAKEFLAGS) -f Makefile.nmake maintainer-clean
	cd ..

	add "xcopy freepastry\*.dll $(VERSION) /d" to the install-plugins directive (~line 382)

	
4. Build

UNIX:
Just run these commands:
./configure
make
make install 

If the configure fails, you simply need to install the missing binary/library with your favorite installation package tool (yum, apt-get...)
Tips: If your OS is a fedora core 6, use ./configure --without-net-snmp --without-ucd-snmp
Note: The resulting library is likely to be compatible with the official binary package of wireshark/ethereal of your system

Windows:
continue on to section 2.2.8: http://www.wireshark.org/docs/wsdg_html/#id4716991
Note: Windows Wireshark binaries are compiled with VC6. If you do not have this (old) compiler, you will have to compile the whole source and use this home-made build. 

5. Test

Here are some basic filtering options:

-Show only the FreePastry KBR traffic: freepastry
-Show only the Scribe traffic: scribe
-Show only the Past traffic: past
-Show only the GCPast traffic: gcpast
-Show only the Replication Manager traffic: replication
-Show only traffic related to an Id starting with 0xAE4567...: freepastry.id contains "AE4567"

A complete list of available filter parameters is available in the expression window (accessible from the filter toolbar)
Common objects are directly registered with freepastry.xxxx. i.e: If you are looking for the target field (Id type) in a Scribe Message, the correct filter to use is freepastry.id... 

Note: CommonApi application dissectors are experimental and not fully tested. If you detect a problem, please report the bug to dav176fr@yahoo.fr.
It will be easier for me if you include a pcap file corresponding to the capture. However, do not forget that I could be able to see personal information from your traces (be careful about what you are sending to me and use the filter option with the "save as" wireshark command) 

6. Code

Here are some documents you must have to read before starting to code
http://www.wireshark.org/docs/wsdg_html/#ChapterDissection
doc/README.plugins
doc/README.developer
doc/README.malloc

If you need write a dissector for a new common api application, packet-past.c can be a good source of inspiration.
If you want your dissector be called by the freepastry dissector, you have to find out what are the 16 first bits of your subaddress (in a PastryEndpoint Message) and register your dissector with the "commonapi.app" key.
exemple: 
void
proto_reg_handoff_myapp(void)
{
  static int Initialized=FALSE;
  if (!Initialized) {
    myapp_handle = create_dissector_handle(dissect_myapp, proto_myapp);
    dissector_add("commonapi.app", 0x1234, myapp_handle);
  }
}

The FreePastry dissector only tags the AppSocket traffic. It does not offer the possibility to give the control of AppSocket traffic to another dissector.
If someone needs this functionality, I could probably add a call for an heuristic dissector to handle it...

7. Plan for the future

Include the plugin into the official distribution (When FreePastry 2.0 is out)
Write a wiki page for freepastry http://wiki.wireshark.org/ProtocolReference

