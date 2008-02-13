package rice.p2p.saar.multitree;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.p2p.scribe.messaging.ScribeMessage;
import rice.pastry.PastryNode;
import rice.p2p.commonapi.Id;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;

/**
 */
public class PathToRootInfoMsg extends SaarDataplaneMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    public rice.p2p.commonapi.Id[] pathToRoot;
 
    public int stripeId;

//    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
//
//    }
//    
//    public PathToRootInfoMsg(ReplayBuffer buffer, PastryNode pn) {
//	super(buffer,pn);
//    }


    public PathToRootInfoMsg(NodeHandle source, Topic topic, rice.p2p.commonapi.Id[] pathToRoot, int stripeId) {
	super(source, topic);
	
	if(pathToRoot == null) {
	    this.pathToRoot = null;
	} else {
	    this.pathToRoot = new Id[pathToRoot.length];
	    for(int i=0; i<this.pathToRoot.length; i++) {
		this.pathToRoot[i] = pathToRoot[i];
	    }
	}

	this.stripeId = stripeId;

    }

    public rice.p2p.commonapi.Id[] getPathToRoot() {
	return pathToRoot;
    }


    public String getPathToRootAsString() {
	String s = "pathToRoot:" + pathToRoot.length + ", [";
	for(int i=0; i< pathToRoot.length; i++) {
	    s = s + pathToRoot[i] + ","; 
	}
	s = s +"]";
	return s;
    }


    // The tree based protocols use this buffermap only for debugging purpose, not used in practice
    public int getSizeInBytes() {
	return pathToRoot.length;
    }
    
    /**
     * Returns a String representation of this ack
     *
     * @return A String
     */
    public String toString() {
	return "PathToRootInfoMsg: " + topic + " source= " + source + " " + getPathToRootAsString() + " stripeId: " + stripeId;
    }

}
