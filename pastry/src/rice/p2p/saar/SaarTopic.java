
package rice.p2p.saar;

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



/*
 * SaarTopic creates redundant topics for a given topic and uses this to implement multiple redundant Scribe trees per SAAR anycast group
 *
 */

public class SaarTopic {
    public Topic baseTopic; // The base topic will help us form the redundant topics, but note that we will not have any Scribe tree corresponding to the basetopic. i.e if NUMTREES = 1, redundantTopics[0] is used as the topic for the single Scribe tree
    public Topic[] redundantTopics;

    // This is the number of redundant Scribe trees we have corresponding to each Saar anycast group
    public static int NUMTREES = 1;  // default is a single tree


    
    public SaarTopic(Topic baseTopic, int routingBase) {
	System.out.println("Creating SaarTopic for BaseTopic: " + baseTopic);
	this.baseTopic = baseTopic;
	redundantTopics = new Topic[NUMTREES];
	rice.pastry.Id baseTopicId = (rice.pastry.Id) baseTopic.getId();
	for(int k=0; k < NUMTREES; k++) {
	    Id myTopicId = baseTopicId.getAlternateId(NUMTREES, routingBase, k);
	    redundantTopics[k] = new Topic(myTopicId);
	    System.out.println("SaarTopic:" + baseTopic + "RedundantTopic[" + k + "]: " + redundantTopics[k]);
	}
	
    }


    public String toString() {
	return "[SAARTOPIC: " + baseTopic + "]";
    }

}