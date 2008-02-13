package rice.p2p.saar;

import rice.p2p.util.MathUtils;
import java.io.*;
import java.util.*;
//import rice.replay.*;
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

/**
 * This represents metadata that will be used for two purposes -
 * a) propagated up the SAAR Scribe tree, 
 * b) we use it for encompassing the requestors state while anycasting and setting the anycast thresholds
 *
 */
public abstract class SaarContent implements ScribeContent {
    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;


    public static int UPWARDAGGREGATION = 1;
    public static int DOWNWARDPROPAGATE = 2;



    /** Fields used by both anycast/aggregation ****/
    public int mode; // this mode is usually UPWARDAGGREGATION/DOWNWARDPROPAGATE/Additional modes specific to the particular SaarContent
    public String topicName;  
    public int tNumber;


    
    /** Fields used by the aggregation algorithm ***/
    public GNPCoordinate gnpCoordAggregator = null; // The SaarImpl will set the localGNPCoordinates before updating the SaarContent to the parent in the tree
    public NodeHandle aggregator; // This is the node which did the aggregation of the content


    /*** Fields uped by anycast trversal *****/
    public GNPCoordinate gnpCoordAnycastRequestor = null; // The SaarImpl will set the localGNPCoordinates of the local node initiating the anycast 
    public NodeHandle anycastRequestor;
    public int satisfyThreshold;     // This will stop the anycast as soon as we have found 'satisfythreshold' number of metadata satisfying predicate
    public int traversalThreshold;     // This control the overhead of the anycast and ensures that we do not traverse more than 'traversalThreshold' nodes in the Scribe tree
    public int numScribeIntermediates = 0; // This is the number of nodes that was actually visited. This variable will be used in two ways depending on the mode in which the content is being used a) when used in ANYCAST mode it tacks the anycast traversal length b) When used in downward propagation, it will be used to indicate the depth of a node in the control tree
    public Vector msgPath ; // Elements are NodeIndex of type (PlNodeindex,vIndex)
    public NodeHandle lastInPath = null; // This avoids adding the local node twice due to the artifact of forwardMsg() being called twice
    public String anycastGlobalId = ""; // This will be used for debugging via tracing the path of the anycast traversal
    


    public static class NodeIndex implements Serializable {
	public int bindIndex; // This is the node index corresponding to the bindAddress
	public int jvmIndex; // This is one instance of Pastry JVM
	public int vIndex; // This is one instance of a Pastry virtual node

	public NodeIndex(int bindIndex, int jvmIndex, int vIndex) {
	    this.bindIndex = bindIndex;
	    this.jvmIndex = jvmIndex;
	    this.vIndex = vIndex;
	}

//	public void dump(ReplayBuffer buffer, PastryNode pn) {
//	    buffer.appendShort(bindIndex);
//	    buffer.appendShort(jvmIndex);
//	    buffer.appendShort(vIndex);
//
//	}
//
//	public NodeIndex(ReplayBuffer buffer, PastryNode pn) {
//	    bindIndex = buffer.getShort();
//	    jvmIndex = buffer.getShort();
//	    vIndex = buffer.getShort();
//	}


	public boolean equals(Object obj) {
	    if ((obj == null) || (!(obj instanceof NodeIndex)))
		return false;
	    NodeIndex nIndex = (NodeIndex) obj;
	    if ((bindIndex != nIndex.bindIndex) || (jvmIndex != nIndex.jvmIndex) || (vIndex != nIndex.vIndex)) {
		return false;
	    }
	    return true;
	}

	public String toString() {
	    String s = "(";
	    s = s +bindIndex +":" + jvmIndex + ":" + vIndex;
	    s = s + ")";
	    return s;
	}


    }


    public SaarContent(int mode, String topicName, int tNumber) {
	this.mode = mode;
	this.topicName = topicName;
	this.tNumber = tNumber;
	gnpCoordAggregator = null; 
	aggregator = null; 
	gnpCoordAnycastRequestor = null; 
	anycastRequestor = null;
	satisfyThreshold = -1; 
	traversalThreshold = -1; 
	numScribeIntermediates = 0; 
	msgPath = new Vector(); 
	lastInPath = null; 
	anycastGlobalId = ""; 

    }


    public SaarContent(SaarContent o) {
	this.mode = o.mode;
	this.topicName = o.topicName;
	this.tNumber = o.tNumber;
	this.gnpCoordAggregator = o.gnpCoordAggregator; 
	this.aggregator = o.aggregator; 
	this.gnpCoordAnycastRequestor = o.gnpCoordAnycastRequestor; 
	this.anycastRequestor = o.anycastRequestor;
	this.satisfyThreshold = o.satisfyThreshold;
	this.traversalThreshold = o.traversalThreshold;
	this.numScribeIntermediates = o.numScribeIntermediates;
	this.msgPath = new Vector();
	for(int i=0; i< o.msgPath.size(); i++) {
	    this.msgPath.add(o.msgPath.elementAt(i));
	}
	this.lastInPath = o.lastInPath;
    }


    public static SaarContent duplicate(SaarContent content) {
	if (content == null) {
	    return null;
	} else {
	    return content.duplicate();
	}
    }


    public static SaarContent aggregate(SaarContent content1, SaarContent content2) {
	if((content1 == null) && (content2 == null)) {
	    return null;
	} else if(content1 == null) {
	    return content2.aggregate(null);
	} else if(content2 == null) {
	    return content1.aggregate(null);
	} else {
	    return content1.aggregate(content2);
	}
    }


    // Given the anycast requestor's content, the anycast traversal logic requires the dataplane to implement if the content satisfies the boolean predicate 
    public abstract boolean predicateSatisfied(SaarContent anycastrequestorContent);


    // This method will be checked before sending an update. If return value is tue we can dampen the update
    public abstract boolean negligibleChangeUpwardUpdate(SaarContent otherContent);


    // This method will be checked before sending an update. If return value is tue we can dampen the update
    public abstract boolean negligibleChangeDownwardPropagate(SaarContent otherContent);




    // Given the anycast requestor's content, the anycast traversal logic requires the dataplane to implement an ordering function. This method assumes that both the contents satisfy the boolean predicate.
    // Returns  0 : both are equal (when both are equal the SaarPolicy takes care of randomizing them)
    //         -1 : this ('>' or 'isSuperiorTo') otherContent
    //         +1 : this ('>' or 'isSuperiorTo') otherContent 
    // Also note that you may want to prioritize non-agregate metadata over aggregate metadata so that the anycast traversal completes quicker
    public abstract int compare(SaarContent anycastrequestorContent, SaarContent otherContent);


    // This implements the aggregation function of the SaarContent
    public abstract SaarContent aggregate(SaarContent otherContent);


    public abstract SaarContent duplicate();


    public void setGNPCoordAggregator(GNPCoordinate gnpCoord) {
	this.gnpCoordAggregator = gnpCoord;

    }

    public void setAggregator(NodeHandle handle) {
	this.aggregator = handle;
    }


    public void setGNPCoordAnycastRequestor(GNPCoordinate gnpCoord) {
	this.gnpCoordAnycastRequestor = gnpCoord;

    }


    public void setAnycastRequestor(NodeHandle handle) {
	this.anycastRequestor = handle;
    }


    public void setSatisfyThreshold(int val) {
	satisfyThreshold = val;
    }


    public void setTraversalThreshold(int val) {
	traversalThreshold = val;
    }


    public void setNumScribeIntermediates(int val) {
	numScribeIntermediates = val;
    }


    public void setAnycastGlobalId(String val) {
	anycastGlobalId = val;
    }

    
    // plIndex - got from the NameToIpCoded.nds file
    // vIndex - virtual node index
    public void addToMsgPath(NodeHandle currHandle, int bindIndex, int jvmIndex, int vIndex) {
	if(currHandle.equals(lastInPath)) {
	    // The local node has already been added
	    return;
	}
	lastInPath = currHandle;
	msgPath.add(new NodeIndex(bindIndex, jvmIndex, vIndex));
    }


    // This returns the path of the message
    public NodeIndex[] getMsgPath() {
	NodeIndex[] array = new NodeIndex[msgPath.size()];
	for(int i=0; i<msgPath.size(); i++) {
	    array[i] = (NodeIndex)msgPath.elementAt(i);
	}
	return array;
    }


    public String pathAsString(NodeIndex[] plIndices) {
	String s = "";
	NodeIndex val = null;
	
	s = s + "[ ";
	for(int i=0; i< plIndices.length; i++) {
	    val = plIndices[i];
	    //s = s + plNodes[val].nodeName + " "; 
	    
	    if(SaarTest.MODELNET) {
		s =s + val.bindIndex + ":" + val.jvmIndex + ":" + val.vIndex + " ";
	    } else {
		//s =s + ((rice.pastry.socket.SocketPastryNode)node).getPLName(val.bindIndex) + ":" + val.jvmIndex + ":" + val.vIndex + " ";
		s =s + "-1" + ":" + val.jvmIndex + ":" + val.vIndex + " ";
	    }
	}
	s = s + "]";
	return s;
    }

    

    public abstract String toString();


    // The benefits of GNP are minimal, so GNP is switched off by default and therefore we dont count those costs
    // The msgPath is also for debuggin gpurpose and is not needed really.
    public int getbasecostofsaarcontentInBytes() {
	int val = 0;
	val = val + 1 + 2 + 5 + 5 + 3 + 5;
	return val;
    }
   

}





