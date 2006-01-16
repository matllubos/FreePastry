/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.p2p.libra;

import rice.p2p.util.MathUtils;

import rice.replay.*;
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
 * This represents meta data which helps in forming the ESM tree. This metadata is kept at the parent node in the Anycast tree for its children nodes
 *
 */
public class ESMContent implements ScribeContent {
    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;



    public String topicName = "";

    public boolean aggregateFlag = false;

    /***  These variables will be defined only if the aggregate flag is false ********/
    // This is the actual Ip:PORT information of ESM running on the node that is making the update
    public byte[] hostIp ; // 4 bytes
    public int esmServerPort;
    // This is the esmOverlay Id which prevents loop formation
    public byte[] esmOverlayId ; // 4 bytes

    public int time; // this is the estimate remaining time in the group

    public int pathLength;
    public byte[] paramsPath ; //4*pathLength bytes


    /***  These variables are defined always *****/
    public int[] paramsLoad; // 2 ints
    public int[] paramsLoss; // 1 int
    // This helps to accurate calculate wt. averages when loss information is aggregated in tree
    public int descendants;


    
    public long lastRefreshTime; // This is the last time it was refreshed
    public NodeHandle lastRefreshParent; // The parent to which this update was sent when it was last refreshed
    // This is the last time it received an ack from the parent saying that it got the update. We can use this to dampen the periodic updates incase of stable parents and absent updates. 
    public long lastUpdateAckTime;


    public GNPCoordinate gnpCoord = null;

    public byte esmRunId = 0;

    public static class ESMOverlayId {
	public byte[] id = new byte[4];

	public ESMOverlayId(byte[] id) {
	    for(int i=0; i<4; i++) {
		this.id[i] = id[i];
	    }
	}

	// Returns 0 : equal
	// Return -1 : this is less than o
	// Retruns 1 : this is greater than o
	public int compareTo(ESMOverlayId o) {
	    int overlayIdSelf = getInt();
	    int overlayIdO = o.getInt();
	    if(overlayIdSelf == overlayIdO) {
		return 0;
	    } else if(overlayIdSelf < overlayIdO) {
		return -1;
	    } else {
		return 1;
	    }

	}

	// Returns an integer representation of the byte array
	public int getInt() {
	    int overlayid = MathUtils.byteArrayToInt(id);
	    return overlayid;
	}

	public boolean equals(ESMOverlayId o) {
	    if(o == null) {
		return false;
	    }
	    for (int i = 0; i < 4; i++) {
		if (o.id[i] != this.id[i]) {
		    return false;
		}
	    }
	    
	    return true;
	}

	public String toString() {
	    String s = "";
	    s = s + id[0] +"." + id[1]+ "." + id[2] + "." + id[3];
	    return s;
	}

    }

    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
	buffer.appendByte(rice.p2p.scribe.ScribeContent.idLibraESMContent);
	int tNumber = topicName2Number(topicName);
	buffer.appendInt(tNumber);
	if(aggregateFlag == true) {
	    buffer.appendByte(TRUE);
	} else {
	    buffer.appendByte(FALSE);
	}

	try {
	    buffer.appendShort(paramsLoad[0]);
	    buffer.appendShort(paramsLoad[1]);
	    buffer.appendShort(paramsLoss[0]);
	} catch(Exception e) {
	    System.out.println("ERROR: ESMContent.dump(), paramsLoad or paramsLoss are not initialized");
	    System.exit(1);
	}
	//System.out.println("Writing pathLength= " + this.pathLength + " buffer.pos= " + buffer.getPos());
	buffer.appendInt(time);
	
	buffer.appendShort(pathLength);
	if(pathLength >0) {
	    buffer.appendBytes(paramsPath);
	}
	buffer.appendInt(descendants);

	if(gnpCoord == null) {
	    buffer.appendByte(NULL);
	} else {
	    buffer.appendByte(NONNULL);
	    gnpCoord.dump(buffer,pn);
	}
	buffer.appendByte(esmRunId);
	

    }

    public ESMContent(ReplayBuffer buffer, PastryNode pn) {
	Verifier.assertTrue(buffer.getByte() == ScribeContent.idLibraESMContent);
	int tNumber = buffer.getInteger();
	this.topicName = "" + tNumber;
	if(buffer.getByte() == TRUE) {
	    this.aggregateFlag = true;
	} else {
	    this.aggregateFlag = false;
	}
	this.paramsLoad = new int[2];
	for(int i=0; i<2; i++) {
	    this.paramsLoad[i] = buffer.getShort();
	}
	this.paramsLoss = new int[1];
	for(int i=0; i<1; i++) {
	    this.paramsLoss[i] = buffer.getShort();
	}	

	this.time = buffer.getInteger();

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
	this.descendants = buffer.getInteger();
	if(buffer.getByte() == NULL) {
	    this.gnpCoord = null;
	}else {
	    this.gnpCoord = new GNPCoordinate(buffer, pn);
	}
	this.esmRunId = buffer.getByte();
	
	//  These are variables that are transient (not sent across the wire )
	this.hostIp = null;
	this.esmServerPort = -1;
	this.esmOverlayId = null;
	this.lastRefreshParent = null;
	this.lastRefreshTime = System.currentTimeMillis();
	this.lastUpdateAckTime = System.currentTimeMillis();
	

    }
    


    // This is the default aggregate esmcontent
    public ESMContent(String topicName, GNPCoordinate gnpCoord) {
	this.topicName = topicName;
	if(gnpCoord != null) {
	    this.gnpCoord = new GNPCoordinate(gnpCoord);
	} else {
	    this.gnpCoord = null;
	}
    }

    public ESMContent(ESMContent o) {
	if(o!= null) {
	    this.esmRunId = o.esmRunId;
	    this.topicName = o.topicName;
	    this.aggregateFlag = o.aggregateFlag;
	    if(o.hostIp != null) {
		this.hostIp = new byte[4];
		for(int i=0; i<4; i++) {
		    this.hostIp[i] = o.hostIp[i];
		}
	    }
	    this.esmServerPort = o.esmServerPort;
	    if(o.esmOverlayId != null) {
		this.esmOverlayId = new byte[4];
		for(int i=0; i<4; i++) {
		    this.esmOverlayId[i] = o.esmOverlayId[i];
		}
	    }
	    if(o.gnpCoord != null) {
		this.gnpCoord = new GNPCoordinate(o.gnpCoord);
	    } else {
		this.gnpCoord = null;
	    }

	    this.paramsLoad = new int[2];
	    for(int i=0; i<2; i++) {
		this.paramsLoad[i] = o.paramsLoad[i];
	    }
	    this.paramsLoss = new int[1];
	    for(int i=0; i<1; i++) {
		this.paramsLoss[i] = o.paramsLoss[i];
	    }

	    this.time = o.time;

	    this.pathLength = o.pathLength;
	    if((pathLength == 0) || (pathLength == -1)) {
		this.paramsPath = null;
	    } else {
		this.paramsPath = new byte[4*pathLength];
		for(int i=0; i<4*pathLength; i++) {
		    this.paramsPath[i] = o.paramsPath[i];
		}
	    }
	    
	    this.descendants = o.descendants;
	    
	    this.lastRefreshParent = o.lastRefreshParent;
	    this.lastRefreshTime = o.lastRefreshTime;
	    this.lastUpdateAckTime = o.lastUpdateAckTime;
	} else {
	    System.out.println("WARNING: ESMContent() constructor called with a null ESMContent as parameter");

	} 
    }




    // This is the leaf esm content
    public ESMContent(String topicName, boolean aggregateFlag, byte[] hostIp, int esmServerPort, byte[] esmOverlayId, int[] paramsLoad, int[] paramsLoss, int time, int pathLength, byte[] paramsPath, int descendants, GNPCoordinate gnpCoord) {
	this.topicName = topicName;
	this.aggregateFlag = aggregateFlag;
	if(hostIp != null) {
	    this.hostIp = new byte[4];
	    for(int i=0; i<4; i++) {
		this.hostIp[i] = hostIp[i];
	    }
	}
	this.esmServerPort = esmServerPort;
	if(esmOverlayId != null) {
	    this.esmOverlayId = new byte[4];
	    for(int i=0; i<4; i++) {
		this.esmOverlayId[i] = esmOverlayId[i];
	    }
	}


	if(gnpCoord != null) {
	    this.gnpCoord = new GNPCoordinate(gnpCoord);
	} else {
	    this.gnpCoord = null;
	}


	this.paramsLoad = new int[2];
	for(int i=0; i<2; i++) {
	    this.paramsLoad[i] = paramsLoad[i];
	}
	this.paramsLoss = new int[1];
	for(int i=0; i<1; i++) {
	    this.paramsLoss[i] = paramsLoss[i];
	}
	this.time = time;
	this.pathLength = pathLength;
	if((pathLength == 0) || (pathLength == -1)) {
	    this.paramsPath = null;
	} else {
	    this.paramsPath = new byte[4*pathLength];
	    for(int i=0; i<4*pathLength; i++) {
	      this.paramsPath[i] = paramsPath[i];
	    }
	}
	
	this.descendants = descendants;
	
	this.lastRefreshParent = null;
	this.lastRefreshTime = System.currentTimeMillis();
	this.lastUpdateAckTime = System.currentTimeMillis();
	
      
    }
    

    public byte getESMRunId() {
	return esmRunId;
    }

    public void setESMRunId(byte val) {
	esmRunId = val;
    }

    public boolean sameExceptStayTime(ESMContent o) {
	if(o == null) {
	    return false;
	} else {
	    // We return true only if the load/loss/path ( We leave out session time) has changed
	    
	    if(aggregateFlag!= o.aggregateFlag) {
		return false;
	    } 

	    // This could affacet the way the wt avergae would look at the parent
	    if(descendants != o.descendants) {
		return false;
	    } 

	    
	    // Comapare paths
	    if(pathLength != o.pathLength) {
		return false;
	    }

	

	    if(!((gnpCoord == null) && (o.gnpCoord ==null))) {
		if((gnpCoord == null) || (o.gnpCoord == null)) {
		    // One of them is null and the other is not
		    return false;
		} else {
		    // Both are non-null
		    if(!gnpCoord.negligibleChange(o.gnpCoord)) {
			return false;
		} 
		    
		}
	    }
	    
	    if(pathLength > 0){
		for(int j=0; j< 4*pathLength; j++) {
		    if(paramsPath[j] != o.paramsPath[j]) {
			return false;
		    }
		}
	    }
	    
	    // Compare load metric
	    if((paramsLoad[0] != o.paramsLoad[0]) || (paramsLoad[1] != o.paramsLoad[1])) {
		return false;
	    }

	    // Comapare the loss metric ( within 5 percent)
	    //if((paramsLoss[0] != o.paramsLoss[0])) {
	    if(Math.abs(paramsLoss[0] - o.paramsLoss[0]) > 5) {
		return false;
	    }
	    return true;
	}

    }


    // return true if both are only slightly different
    public boolean negligibleChange(ESMContent o) {
	return false;
	
	/*
	if(aggregateFlag!= o.aggregateFlag) {
	    return false;
	} 

	// This could affacet the way the wt avergae would look at the parent
	if(descendants != o.descendants) {
	    return false;
	} 


	// Comapare remaining times. If the stay-time has not changed more than 5 sec, it is negligible. However if either of the values are very small, we 
	if((Math.abs(time - o.time) > 5) || ((time!= o.time) && ((time <5) || (o.time < 5)))) {
	    return false;
	}


	// Comapare paths
	if(pathLength != o.pathLength) {
	    return false;
	}

	

	if(!((gnpCoord == null) && (o.gnpCoord ==null))) {
	    if((gnpCoord == null) || (o.gnpCoord == null)) {
		// One of them is null and the other is not
		return false;
	    } else {
		// Both are non-null
		if(!gnpCoord.negligibleChange(o.gnpCoord)) {
		    return false;
		} 

	    }
	}

	if(pathLength > 0){
	    for(int j=0; j< 4*pathLength; j++) {
		if(paramsPath[j] != o.paramsPath[j]) {
		    return false;
		}
	    }
	}
	
	// Compare load metric
	if((paramsLoad[0] != o.paramsLoad[0]) || (paramsLoad[1] != o.paramsLoad[1])) {
	    return false;
	}

	// Comapare the loss metric ( within 5 percent)
	//if((paramsLoss[0] != o.paramsLoss[0])) {
	if(Math.abs(paramsLoss[0] - o.paramsLoss[0]) > 5) {
	    return false;
	}
	return true;
	*/
    }
    
    
    

    

    public void setLastRefreshParent(NodeHandle parent) {
	lastRefreshParent = parent;
    }
	
	
    public void setLastRefreshTime(long time) {
	lastRefreshTime = time;
    }

    public void setLastUpdateAckTime(long time) {
	lastUpdateAckTime = time;
    }


    public void setGNPCoord(GNPCoordinate coord) {
	if(coord == null) {
	    gnpCoord = null;
	} else {
	    gnpCoord = new GNPCoordinate(coord);
	}
	
    }

    public GNPCoordinate getGNPCoord() {
	return gnpCoord;
    }
    


    // This sets the flag when the intermediate Scribe node propagates aggregate information of children
    public void setAggregateFlag() {
	aggregateFlag = true;

    }

    public boolean hasAggregateFlagSet() {
	return aggregateFlag;
    }


    public boolean hasSpareBandwidth() {
	int usedSlots = paramsLoad[0];
	int totalSlots = paramsLoad[1];
	int spareSlots = totalSlots - usedSlots;
	if(spareSlots > 0) {
	    return true;
	} else {
	    return false;
	}
    }


    public int getSpareBandwidth() {
	int usedSlots = paramsLoad[0];
	int totalSlots = paramsLoad[1];
	int spareSlots = totalSlots - usedSlots;
	return spareSlots;
    }


    public boolean hasGoodPerformance() {
	int lossRate = paramsLoss[0];
	if(lossRate < LibraTest.LOSSTHRESHOLD) {
	    return true;
	} else {
	    return false;
	}
    }

    public int getLoss() {
	return paramsLoss[0];
    }


    public int getDepth() {
	return pathLength;
    }


    public int getRemainingTime() {
	return time;
    }


    
    public double getDistance(GNPCoordinate requestorCoord) {
	if((gnpCoord==null) || (requestorCoord==null)) {
	    return Double.MAX_VALUE;
	} else {
	    return requestorCoord.distance(gnpCoord);
	}
    }



    public boolean hasNoLoops(byte[] requestorIdArray) {
	if((pathLength == -1) || (pathLength == 0)) {
	    // This is an aggregate ESMContent
	    return true;
	}
	ESMOverlayId requestorId = new ESMOverlayId(requestorIdArray);
	//System.out.println("requestorId: " + requestorId); 
	for(int i=0; i<pathLength; i++) {
	    byte[] pathIdArray = new byte[4];
	    for(int j=0; j<4; j++) {
		pathIdArray[j] = paramsPath[4*i + j];
	    }
	    ESMOverlayId pathId = new ESMOverlayId(pathIdArray);
	    //System.out.println("pathId[" + i + "]= " + pathId);
	    if(pathId.equals(requestorId)) {
		return false;
	    }
	}
	return true;
	
    }


    public boolean allowFastConvergence(int pathLengthRequestor, byte[] paramsPathRequestor, byte[] requestorIdArray) {
	
	if(!aggregateFlag) {
	    if(pathLengthRequestor == 0) {
		System.out.println("FastConvergenceLink established: Requestor.pathlength=0");
		return true;
	    }
	    // We make sure the invariant is that the updates received from ESM have pathlength non zero
	    if(pathLength == 0) {
		System.out.println("ERROR: allowFastConvergence(): esmcontent.pathLength is zero");
		System.exit(1);
	    }
	    // We will find the common ancestor
	    ESMOverlayId[] reqIdArray = overlayIdPath(pathLengthRequestor, paramsPathRequestor);
	    ESMOverlayId[] myIdArray = overlayIdPath(pathLength, paramsPath);
	    
	    boolean caFound = false; // ca stands for common ancestor
	    int caPosRequestor = -1;
	    int caPosSelf = -1;
	    ESMOverlayId idX ; // This is the child of the ca in the requestor path
	    ESMOverlayId idY ; // This is the child of the ca in the self path
	    // If common ancestor not found then return false
	    for(int i=0; i< pathLengthRequestor; i++) {
		ESMOverlayId id = reqIdArray[i];
		int pos = posInPath(myIdArray,id);
		if(pos != -1) {
		    // We found a common ancestor
		    caFound = true;
		    caPosRequestor = i;
		    caPosSelf = pos;
		    break;
		} 
	    }
	    if(!caFound) {
		System.out.println("FastConvergenceLink declined: CA missing");
		return false;
	    } else {
		// If common found then find X and Y and use comparator function
		if(caPosRequestor == 0) {
		    // idX - requestor's id 
		    idX = new ESMOverlayId(requestorIdArray);
		} else {
		    idX = reqIdArray[caPosRequestor-1];
		}
		if(caPosSelf == 0) {
		    // idY - self's id 
		    idY = new ESMOverlayId(esmOverlayId); 
		} else {
		    idY = myIdArray[caPosSelf-1];
		}
		
		if(idY.compareTo(idX) > 0 ) {
		    System.out.println("FastConvergenceLink established: idY > idX");
		    return true;
		} else {
		    System.out.println("FastConvergenceLink declined: idY < idX");
		    return false;
		}

	    }
	    
	} else {
	    System.out.println("ERROR: allowFastConvergence(): esmcontent.aggregateFlag=true");
	    System.exit(1);
	}
	
	System.out.println("ERROR: allowFastConvergence(): should not reach here");
	System.exit(1);
	return false;



    }

    // Finds the position of this id in the path, returns -1 if not found
    private int posInPath(ESMOverlayId[] idPath, ESMOverlayId id) {
	for(int i=0; i< idPath.length; i++) {
	    if(idPath[i].compareTo(id) == 0) {
		return i;
	    }
	}
	return -1;
    }

    
    private ESMOverlayId[] overlayIdPath(int length, byte[] array) {
	
	int arraypos = 0;
	ESMOverlayId[] idArray = new ESMOverlayId[length];
	for(int i=0; i< length; i++) {
	    byte[] subarray = new byte[4];
	    for(int j=0; j<4; j++) {
		subarray[j] = array[arraypos];
		arraypos ++;
	    }
	    idArray[i] = new ESMOverlayId(subarray);
	}
	return idArray;

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
	String s = "[ ESMContent: ";
	s = s + "T:" + topicName + ", " + "D:" + descendants + ", " + "AFlag: " + aggregateFlag;
	s = s  + ", " + "Load(" + paramsLoad[0]+ "," + paramsLoad[1]+ "), " + "Loss(" + paramsLoss[0]+ ")" + " Time(" + time + ")";
	s =s + " ]";
	/*
	s =s + ", " + "HostIp:";
	if(hostIp == null) {
	    s = s + "null";
	} else {
	    s = s + new ESMOverlayId(hostIp);
	}
	s = s + ", " + "Port:"  + esmServerPort + ", " + "esmOverlayId:";
	if(esmOverlayId == null) {
	    s = s + "null";
	} else {
	    s = s + new ESMOverlayId(esmOverlayId);
	}
	s = s  + ", " + "Load(" + paramsLoad[0]+ "," + paramsLoad[1]+ "), " + "Loss(" + paramsLoss[0]+ ")" + "Time(" + time + ")";
	s = s + ", " + "PathLength(" + pathLength + ")";
	s = s + ", " + "Path[";
	if(paramsPath == null) {
	    s = s + "null";
	} else {
	    for(int i=0; i<pathLength; i++) {
		String id = "";
		for(int j=0; j<4; j++) {
		    id = id + paramsPath[4*i +j] + ".";
		}
		s = s + id + ",";
	    }
	}
	s = s + "]";
	s = s + " GNP:" + gnpCoord;
	*/



	return s;
    }

}

