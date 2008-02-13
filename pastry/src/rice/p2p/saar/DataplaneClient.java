/*
 * Created on May 4, 2005
 */
package rice.p2p.saar;

import java.util.*;
import java.lang.Double;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;
import rice.environment.logging.Logger;


// This is the interface that will be implemented by the data plane client
public abstract class DataplaneClient {

    public SaarClient saarClient; // this is an instance of the saarClient at this local node
    public int tNumber;
    public SaarTopic saartopic; // The control plane will be using the redundant topics saartopic but the dataplane will only utilize the saartopic.baseTopic
    public String topicName;
    public int dataplaneType; // 1 - single-tree, 2 - multi-tree, 3 - block-based

    public double nodedegreeControlAndData; // This the total RI based on the upstream bandwidth, but only prt of it is utilizable by dataplane
    public double nodedegree; // This is the ration of node's-bandwidth that can be used for the dataplane/stream-bandwidth

    public boolean amMulticastSource; 
    public boolean amVirtualSource; 
    
    



    public DataplaneClient(SaarClient saarClient, int tNumber, SaarTopic saartopic, String topicName, int dataplaneType, double nodedegreeControlAndData, boolean amMulticastSource, boolean amVirtualSource) {
	this.saarClient = saarClient;
	this.tNumber = tNumber;
	this.saartopic = saartopic;
	this.topicName = topicName;
	this.dataplaneType = dataplaneType;
	this.nodedegreeControlAndData = nodedegreeControlAndData;
	this.nodedegree = nodedegreeControlAndData;   // This is the initial value
	this.amMulticastSource = amMulticastSource;
	this.amVirtualSource = amVirtualSource;
	
    }



    public void myPrint(String s, int priority) {
	  if (saarClient.logger.level <= priority) saarClient.logger.log(s);
    }


    
    // Should implement the necessary operations when a node joins the data overlay
    public abstract void join();
    
    // This is invoked periodically every second and should implement the desired dataplane maintenance algorithms
    public abstract void dataplaneMaintenance();
    

    // This upcall is invoked when the dataplaneclient is subscribed to the topic, the local node should invoke SAARAPI's update(SaarContent) with its desired metadata 
    public abstract void controlplaneUpdate(boolean forceUpdate) ;


    // Should implement the necessary operations when a node leaves the data overlay
    public abstract void leave();

    // When an anycast message is delivered at the local node, it either accepts it and terminates the anycast. Also the dataplane implements its own policy of responding back to the anycast requestor. In addition to the Saartopic it specifies the topic of the redundant Scribe tree for this Saar group 
    public abstract boolean recvAnycast(SaarTopic saartopic, Topic topic, SaarContent content);
    
    // When a SAAR Publish message (used for downward propagation) is delivered at the local node. In addiiton to the Saartopic, it also specifies which redundant tree this grpSummary corresponds to
    public abstract void grpSummary(SaarTopic saartopic, Topic topic, SaarContent content);
    

    // This notifies us when we receive a failure for a anycast. In addiiton to the Saartopic, it also specifies which redundant tree this anycast failure corresponds to
    public abstract void recvAnycastFail(SaarTopic saartopic, Topic topic, NodeHandle failedAtNode, SaarContent content);

   
    // This is the func that will be invoked for all messages that the dataplaneClinet internally uses. This function acts as a demultiplexor for messages in the scope of the specific dataplaneclient to do the necessary functions
    public abstract void recvDataplaneMessage(SaarTopic saartopic, Topic topic, SaarDataplaneMessage sMsg);


    // This means that the upper channelviewer layer received the block from some out-of-protocol means. This is a hack we use to get notified of the blocks across the different dataplanes in the hybrid protocol
    public abstract void alreadyReceivedSequenceNumber(int seq);

}
