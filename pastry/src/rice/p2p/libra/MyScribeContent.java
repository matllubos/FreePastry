/*
 * Created on May 4, 2005
 */
package rice.p2p.libra;
import rice.replay.*;
import rice.pastry.PastryNode;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;
import java.util.*;

/**
 * @author Jeff Hoye
 */

// This is used for anycasting
public class MyScribeContent implements ScribeContent {
    public NodeHandle from;
    public int seq;
    public boolean anycast;
    public String topicName;
    public byte[] esmIdArray = new byte[4];
    public GNPCoordinate gnpCoord = null;
    public String globalId = "";

    public byte esmRunId = 0;

    public Vector traversedTimes = new Vector();

    public int numRefused = 0; // This is incremented every time the anycast() method is called on the node at the node refused it 

    // These fields contain the pathlength/existing path to root of the ESM client for the topic
    public int pathLength = 0;
    public byte[] paramsPath;

   
    // Elements are NodeIndex of type (PlNodeindex,vIndex)
    public Vector msgPath ;
    public NodeHandle lastInPath = null; // This avoids adding the local node twice due to the artifact of forwardMsg() being called twice


    public static class NodeIndex {
	public int bindIndex; // This is the node index corresponding to the bindAddress
	public int jvmIndex; // This is one instance of Pastry JVM
	public int vIndex; // This is one instance of a Pastry virtual node

	public NodeIndex(int bindIndex, int jvmIndex, int vIndex) {
	    this.bindIndex = bindIndex;
	    this.jvmIndex = jvmIndex;
	    this.vIndex = vIndex;
	}

	public void dump(ReplayBuffer buffer, PastryNode pn) {
	    buffer.appendShort(bindIndex);
	    buffer.appendShort(jvmIndex);
	    buffer.appendShort(vIndex);

	}

	public NodeIndex(ReplayBuffer buffer, PastryNode pn) {
	    bindIndex = buffer.getShort();
	    jvmIndex = buffer.getShort();
	    vIndex = buffer.getShort();
	}


	public boolean equals(Object obj) {
	    if ((obj == null) || (!(obj instanceof NodeIndex)))
		return false;
	    NodeIndex nIndex = (NodeIndex) obj;
	    if ((bindIndex != nIndex.bindIndex) || (jvmIndex != nIndex.jvmIndex) || (vIndex != nIndex.vIndex)) {
		return false;
	    }
	    return true;
	}

    }



    public void dump(ReplayBuffer buffer, PastryNode pn) {
	buffer.appendByte(rice.p2p.scribe.ScribeContent.idLibraMyScribeContent);
	if(from == null) {
	    System.out.println("MyScribeContent.dump() : MyScribeContent.from= null");
	    System.exit(1);
	}
	from.dump(buffer,pn);
	buffer.appendInt(seq);
	if(anycast) {
	    buffer.appendByte(Verifier.TRUE);
	} else {
	    buffer.appendByte(Verifier.FALSE);
	}
	// We extract the tNumber from the topicName
	int tNumber = topicName2Number(topicName);
	buffer.appendInt(tNumber);
	buffer.appendBytes(esmIdArray);
	if(gnpCoord == null) {
	    buffer.appendByte(Verifier.NULL);
	} else {
	    buffer.appendByte(Verifier.NONNULL);
	    gnpCoord.dump(buffer,pn);
	}

	buffer.appendByte(esmRunId);
	buffer.appendInt(numRefused);
	buffer.appendShort(globalId.length());
	buffer.appendBytes(globalId.getBytes());
	
	buffer.appendShort(pathLength);
	if(pathLength >0) {
	    buffer.appendBytes(paramsPath);
	}
	buffer.appendShort(msgPath.size());
	for(int i=0; i<msgPath.size(); i++) {
	    NodeIndex nIndex = (NodeIndex) msgPath.elementAt(i);
	    nIndex.dump(buffer,pn);
	}

	if(lastInPath == null) {
	    buffer.appendByte(Verifier.NULL);
	} else {
	    buffer.appendByte(Verifier.NONNULL);
	    lastInPath.dump(buffer,pn);
	}



    }

    public MyScribeContent(ReplayBuffer buffer, PastryNode pn) {
	Verifier.assertTrue(buffer.getByte() == ScribeContent.idLibraMyScribeContent);
	this.from = Verifier.restoreNodeHandle(buffer,pn);
	this.seq = buffer.getInteger();
	if(buffer.getByte() == Verifier.TRUE) {
	    this.anycast = true;
	} else {
	    this.anycast = false;
	}
	int tNumber = buffer.getInteger();
	this.topicName = "" + tNumber;
	this.esmIdArray = buffer.getByteArray(4);
	if(buffer.getByte() == Verifier.NULL) {
	    this.gnpCoord = null;
	}else {
	    this.gnpCoord = new GNPCoordinate(buffer, pn);
	}	

	this.esmRunId = buffer.getByte();
	this.numRefused = buffer.getInteger();
	int globalIdLength = buffer.getShort();
	this.globalId = new String(buffer.getByteArray(globalIdLength));
	this.pathLength = buffer.getShort();
	if(this.pathLength > 100) {
	    System.out.println("WARNING : Read pathLength= " + this.pathLength + " assumedRead=0 ");
	    this.pathLength = 0; // This is a bug where when we write '-1' it is read as 65535
	    this.paramsPath = null;
	}
	try {
	    if(this.pathLength >0) {
		this.paramsPath = new byte[4*this.pathLength];
		for(int i=0; i< (4*this.pathLength); i++) {
		    this.paramsPath[i] = buffer.getByte(); 
		}
	    }
	} catch(Exception e) {
	    System.exit(1);
	}
	msgPath = new Vector();
	int msgPathLength = buffer.getShort();
	for(int i=0; i< msgPathLength; i++) {
	    NodeIndex nIndex = new NodeIndex(buffer,pn);
	    msgPath.add(nIndex);
	}

	byte nullVal = buffer.getByte();
	if(nullVal == Verifier.NULL) {
	    lastInPath = null;
	}else {
	    lastInPath = Verifier.restoreNodeHandle(buffer,pn);
	}


    }


  /**
   * @param from Who sent the message.
   */
    public MyScribeContent(String topicName, NodeHandle from, int seq, boolean anycast, GNPCoordinate gnpCoord, int pathLength, byte[] paramsPath) {
	this.topicName = topicName;
	this.from = from;
	this.seq = seq;
	this.anycast = anycast;
	if(gnpCoord!= null) {
	    this.gnpCoord = new GNPCoordinate(gnpCoord);
	}	
	this.pathLength = pathLength;
	if(this.pathLength > 0) {
	    this.paramsPath = new byte[4*this.pathLength];
	    for(int i=0; i< (4*this.pathLength); i++) {
		this.paramsPath[i] = paramsPath[i]; 
	    }
	}
	msgPath = new Vector();
      
    }

    
    //public void appendTraversedTime(String val) {
    //traversedTimes.add(new String(val));
    //} 
    

    // plIndex - got from the NameToIpCoded.nds file
    // vIndex - virtual node index
    public void addToMsgPath(NodeHandle currHandle, int bindIndex, int jvmIndex, int vIndex) {
	if(currHandle.equals(lastInPath)) {
	    // The local node has already been added
	    return;
	}
	//int currSize = msgPath.size();
	//if(currSize > 0) {
	//  int lastVal = ((Integer)msgPath.elementAt(currSize - 1)).intValue();
	//  if(lastVal == plIndex) {
	//// This is an artifact of the forward call in PastryAppl being invoked on inbound/outbound
	//	return;
	//  }
	//}

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


    public void setESMRunId(byte val) {
	esmRunId = val;
    }

    public byte getESMRunId() {
	return esmRunId;
    }

    public void setGlobalId(String val) {
        globalId = new String(val);
    }



    public void setESMIdArray(byte[] esmIdArray) {
	for(int i=0; i<4; i++) {
	    this.esmIdArray[i] = esmIdArray[i];
	}
       
    }


    public int getSeq() {
	return seq;
    }

    public GNPCoordinate getGNPCoord() {
	return gnpCoord;
    }

    public byte[] getESMIdArray() {
	return esmIdArray;
    }


    private int  topicName2Number(String name) {
	try {
	    int tNumber = Integer.parseInt(name);
	    return tNumber;
	} catch(Exception e) {
	    System.out.println("ERROR: TopicNumber could not be extracted from " + name);
	    return -1;
	}
    }

    public String toString() {
	String s = "";
	

	if (anycast) {
	    s = "MyScribeContent (anycast) " + "GID=" + globalId;
	    /*
	    s = s + " RequestorPathLength: " + pathLength + " RequestorPath[ " ;
	    int pos = 0;
	    for(int i=0; i < pathLength; i++) {
		for(int j=0; j<4; j++) {
		    s = s + paramsPath[pos] + ".";
		    pos ++;
		}
		s =s + " ";
	    }
	    s = s + "]";
	    */
	} else {
	    s = "MyScribeContent " + " tName= " + topicName + " #"+seq;    
	}  

	return s;
    }
}
