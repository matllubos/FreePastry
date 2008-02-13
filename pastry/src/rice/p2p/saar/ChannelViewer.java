/*
 * Created on May 4, 2005
 */
package rice.p2p.saar;


import rice.p2p.saar.singletree.*;
import rice.p2p.saar.multitree.*;
import rice.p2p.saar.blockbased.*;


import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.selector.SelectorManager;
//import rice.replay.*;
import java.util.Random;
import java.util.Vector;
import java.util.Hashtable;
import java.lang.String;
import java.io.*;
import java.net.*;
import java.util.prefs.*;
import rice.p2p.util.MathUtils;
import java.text.*;
import java.util.*;

import rice.pastry.PastryNodeFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.socket.SocketNodeHandle;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.NodeHandleSet;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.ScribeClient;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.ScribeImpl;
import rice.p2p.scribe.Topic;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.routing.RoutingTable;
import rice.pastry.routing.RouteSet;
import rice.pastry.socket.*;
import rice.pastry.leafset.*;
import rice.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.messaging.*;


/**
 * 
 * Channel Viwer is the end receiver which will receive packets and report bandwidth usage. Note that the hybrid dataplanes rely on this to exchange bandwidth/packet information
 * @author Animesh
 */
public class ChannelViewer  {

    public SaarClient saarClient;

    public Hashtable numSequence = new Hashtable();

    public double foregroundReservedRI = 0; 

    private int MAXFOREGROUNDSTRIPES = 20;

    private int numForegroundStripes = 1;

    private long[] lastForegroundPktRecvTime = new long[MAXFOREGROUNDSTRIPES];// Initialized to zero because in the pure-blockbased, this value will not be changed

    private int[] lastForegroundPktRecvSeqnum = new int[MAXFOREGROUNDSTRIPES];


    private long lastForegroundPktPushedTime = 0; // Note that all packets received in the foreground will not be pushed downwards, e.g pkts received on non-primary stripes not having children



    //public int firstForegroundPktToExpectAfterJoin; 

    public int firstForegroundPktAfterJoin = 0; 

    public long firstForegroundPktRecvTime = 0; 

    //public boolean FRAGMENTRECOVERY = true;

    private long FOREGROUNDPUBLISHPERIOD = 1000; // This is the inter packet arrival time in the foreground tree-based systems, and this value will be used by the background to determine if the bandwidth is available for background transfer

    private int numfragmentspersec = 25; 

    private int maxjitterinsec = 60;

    private boolean TRACKDUPLICATEPACKETS = false;




    public ChannelViewer(SaarClient saarClient) {
	this.saarClient = saarClient;


	if(saarClient.DATAPLANETYPE == 1) {
	    numfragmentspersec = (int)(1000/rice.p2p.saar.singletree.SingletreeClient.PUBLISHPERIOD);
	    maxjitterinsec = 15;
	} else if(saarClient.DATAPLANETYPE == 2) {
	    numfragmentspersec = ((int)(1000/rice.p2p.saar.multitree.MultitreeClient.PUBLISHPERIOD)) * rice.p2p.saar.multitree.MultitreeClient.NUMSTRIPES;
	    maxjitterinsec = 15;
	} else if(saarClient.DATAPLANETYPE == 3) {
	    numfragmentspersec = (int)(1000/rice.p2p.saar.blockbased.BlockbasedClient.PUBLISHPERIOD);
	    maxjitterinsec = 60;
	} else if(saarClient.DATAPLANETYPE == 4) {
	    numfragmentspersec = (int)(1000/rice.p2p.saar.singletree.SingletreeClient.PUBLISHPERIOD);
	    maxjitterinsec = 60;
	}  else if(saarClient.DATAPLANETYPE == 5) {
	    numfragmentspersec = ((int)(1000/rice.p2p.saar.multitree.MultitreeClient.PUBLISHPERIOD)) * rice.p2p.saar.multitree.MultitreeClient.NUMSTRIPES;
	    maxjitterinsec = 30;
	} 

	
	 
	for(int i=0; i< MAXFOREGROUNDSTRIPES; i++) {
	    lastForegroundPktRecvSeqnum[i] = 0;
	    
	}
	
    }

    public void setForegroundPublishPeriod(long val) {
	FOREGROUNDPUBLISHPERIOD = val;
    }

    public long getForegroundPublishPeriod() {
	return FOREGROUNDPUBLISHPERIOD;
    }

    public long getCurrentTimeMillis() {
	return saarClient.getCurrentTimeMillis();
    }


    public void myPrint(String s, int priority) {
	if (saarClient.logger.level <= priority) saarClient.logger.log(s);
    }

    public void printHybridStats() {
	long currtime = getCurrentTimeMillis(); 
	if(SaarTest.logLevel <= 850) myPrint("hybridstats(" + "foregroundBroadcastSeqnum: " + getForegroundBroadcastSeqnum() + ", backgroundBroadcastSeqnum: " + getBackgroundBroadcastSeqnum() + ", firstForegroundPktRecvTime: " + firstForegroundPktRecvTime + ", curtime: " + currtime + ", nodedegree: " + saarClient.nodedegree + ", foregroundReservedRI: " + foregroundReservedRI  + ", firstForegroundPktAfterJoin: " + firstForegroundPktAfterJoin + ", lastForegroundPktRecvTime[0]: " + lastForegroundPktRecvTime[0] +  ", lastForegroundPktRecvSeqnum[0]: " + lastForegroundPktRecvSeqnum[0] + " Note: Foreground/Background sequence number rates may be different", 850);
    }


    public void setForegroundBroadcastSeqnum(int val) {
	if(SaarTest.logLevel <= 875) myPrint("viewer: setForegroundBroadcastSeqnum(" + val + ")", 875);
	rice.p2p.saar.simulation.SaarSimTest.foregroundBroadcastSeqnum = val;
    }

    public int getForegroundBroadcastSeqnum() {
	return rice.p2p.saar.simulation.SaarSimTest.foregroundBroadcastSeqnum;

    }


    public void setBackgroundBroadcastSeqnum(int val) {
	if(SaarTest.logLevel <= 875) myPrint("viewer: setBackgroundBroadcastSeqnum(" + val + ")", 875);
	rice.p2p.saar.simulation.SaarSimTest.backgroundBroadcastSeqnum = val;
    }

    public int getBackgroundBroadcastSeqnum() {
	return rice.p2p.saar.simulation.SaarSimTest.backgroundBroadcastSeqnum;

    }

    
    // We assume that either of the singletee/multitree is the foreground dataplane
    public void setForegroundReservedRI(double val) {
	foregroundReservedRI = val;
    }


    public void setLastForegroundPktPushedTime(long val) {
	lastForegroundPktPushedTime = val;

    }

    public long getLastForegroundPktPushedTime() {
	return lastForegroundPktPushedTime;
    }


    public void setNumForegroundStripes(int val) {
	numForegroundStripes = val;
    }

    public int getNumForegroundStripes() {
	return numForegroundStripes;
    }
    

    public void setLastForegroundPktRecvTime(long val, int stripeId) {
	lastForegroundPktRecvTime[stripeId] = val;

    }

    public long getLastForegroundPktRecvTime(int stripeId) {
	return lastForegroundPktRecvTime[stripeId];
    }
    
    public long getLastForegroundPktRecvTimeAcrossAllStripes() {
        
	long max = 0;
	for(int i=0; i< numForegroundStripes; i++) {
	    if(lastForegroundPktRecvTime[i] > max) {
		max = lastForegroundPktRecvTime[i];
	    }
	}
	return max;
    }

    
    public void setLastForegroundPktRecvSeqnum(int val, int stripeId) {
	lastForegroundPktRecvSeqnum[stripeId] = val;

    }
    

    public int getLastForegroundPktRecvSeqnum(int stripeId) {
	return lastForegroundPktRecvSeqnum[stripeId];
    }




    public int getLastForegroundPktRecvSeqnumAcrossAllStripes() {
	
	int max = 0;
	for(int i=0; i< numForegroundStripes; i++) {
	    if(lastForegroundPktRecvSeqnum[i] > max) {
		max = lastForegroundPktRecvSeqnum[i];
	    }
	}
	return max;
    }


    // Note that this function assumes that val is currently missing
    public boolean shouldRecoverViaBackground(int val) {
	boolean ret; 
	int correspondingStripeId = val % numForegroundStripes;
	long currtime = getCurrentTimeMillis(); 
	int expectedSeqNum = lastForegroundPktRecvSeqnum[correspondingStripeId] + numForegroundStripes * ((int)(((currtime - lastForegroundPktRecvTime[correspondingStripeId])/FOREGROUNDPUBLISHPERIOD)));
	
	if(lastForegroundPktRecvSeqnum[correspondingStripeId] > val) {
	    ret = true;
	} else {
	    if(val < (expectedSeqNum - numForegroundStripes)) {
		ret = true;
	    } else {
		ret = false;
	    }

	}
	if(SaarTest.logLevel <= 875) myPrint("viewer: shouldRecoverViaBackground(val: " + val + ", lastRecvSeqnumOnStripe: " + lastForegroundPktRecvSeqnum[correspondingStripeId] + ", lastRecvTimeOnStripe: " + lastForegroundPktRecvTime[correspondingStripeId] + ", foregroundpublishperiod: " + FOREGROUNDPUBLISHPERIOD + ", expectedSeqNum: " + expectedSeqNum + ", ret: " + ret + " )", 875);
	
	return ret;
    }


    //public void setFirstForegroundPktToExpectAfterJoin(int val) {
    //firstForegroundPktToExpectAfterJoin = val;
    //}


    // public int getFirstForegroundPktToExpectAfterJoin() {
    //return firstForegroundPktToExpectAfterJoin ;

    //}



    public void setFirstForegroundPktAfterJoin(int val) {
	long currtime = getCurrentTimeMillis(); 
	firstForegroundPktAfterJoin = val;
	firstForegroundPktRecvTime = currtime;
    }



    public int getFirstForegroundPktAfterJoin() {
	return firstForegroundPktAfterJoin ;

    }

    
    public void initialize() {
	numSequence.clear();
    }

    // This is when one dataplane in the layer below receives a particular sequence number. This information will be propagated below to the other dataplanes. IMPORTANT: These sequence numbers are as interpreted by the blockbased protocol
    public void receivedSequenceNumber(int seq, int dataplanetype) {
	long currtime = getCurrentTimeMillis(); 
	
	if(TRACKDUPLICATEPACKETS) {
	    // We will also use this to delete old seqnums from the cache
	    for(int k =0; k < 10; k++) {
		int targetSeqNumToRemove = seq - (numfragmentspersec*maxjitterinsec) - k; // 1000 assuming that we publish at most 25 blocks per second and remove blocks that are 40 seconds old
		numSequence.remove(new Integer(targetSeqNumToRemove));
		
	    }
	    
	    int numPkts = 0;
	    if(numSequence.containsKey(new Integer(seq))) {
		numPkts = ((Integer)numSequence.get(seq)).intValue();
		if(SaarTest.logLevel <= 880) myPrint("DUPLICATEPACKET(" + currtime + "," + seq + "," + dataplanetype + ")", 880);
	    } else {
		if(SaarTest.logLevel <= 880) myPrint("channelviewerdeliver(" + currtime + "," + seq + "," + dataplanetype + ")", 880);
	    }
	    numSequence.put(new Integer(seq), new Integer(numPkts + 1));
	} else {
	    if(SaarTest.logLevel <= 880) myPrint("channelviewerdeliver(" + currtime + "," + seq + "," + dataplanetype + ")", 880);
	}

	for(int tNumber=0; tNumber< saarClient.NUMGROUPS; tNumber++) {
	    if(saarClient.dataplanesEnabled[tNumber] == 0) {
		    continue;
	    }
	    
	    if(saarClient.allTopics[tNumber].isSubscribed) {
		saarClient.allTopics[tNumber].dataplaneClient.alreadyReceivedSequenceNumber(seq);
		    
	    }
	    
	}
	

    }

}


