PACKAGE = rice.p2p.glacier

SOURCE = $(notdir $(wildcard $(SOURCE_DIR)/$(PACKAGE_LOC)/*.java))

RMI_SOURCE =

CLSROOT = /home/ahae/cvs/pastry/classes/rice/
SRCROOT = /home/ahae/cvs/pastry/src/rice/

MAIN       = 

include $(JAVA_DEV_ROOT)/Makefile

test: $(CLSROOT)/p2p/past/PastImpl.class $(CLSROOT)/p2p/glacier/GlacierImpl.class $(CLSROOT)/p2p/glacier/testing/GlacierTest.class
	make -C testing/
	rm -rf /home/ahae/cvs/pastry/src/rice/p2p/glacier/FreePastry-Storage-Root/
	java -classpath /home/ahae/cvs/pastry/classes/:/home/ahae/cvs/pastry/lib/xmlpull_1_1_3_4a.jar:/home/ahae/cvs/pastry/lib/xpp3-1.1.3.4d_b2.jar rice.p2p.glacier.testing.GlacierTest 

$(CLSROOT)/p2p/past/%.class: $(SRCROOT)/p2p/past/%.java
	make -C $(SRCROOT)/p2p/past all
        
$(CLSROOT)/p2p/glacier/%.class: $(SRCROOT)/p2p/glacier/%.java
	make -C $(SRCROOT)/p2p/glacier all
        
$(CLSROOT)/p2p/glacier/testing/%.class: $(SRCROOT)/p2p/glacier/testing/%.java
	make -C $(SRCROOT)/p2p/glacier/testing all
