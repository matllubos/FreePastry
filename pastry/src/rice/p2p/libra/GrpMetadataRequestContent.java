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
public class GrpMetadataRequestContent implements ScribeContent {
    public NodeHandle from;
    public int seq;
    public String globalId = "";
    public String topicName;

    public void dump(ReplayBuffer buffer, PastryNode pn) {
	buffer.appendByte(rice.p2p.scribe.ScribeContent.idGrpMetadataRequestContent);
	if(from == null) {
	    System.out.println("GrpMetadataRequestContent.dump() : GrpMetadataRequestContent.from= null");
	    System.exit(1);
	}
	from.dump(buffer,pn);
	
	buffer.appendInt(seq);
	// We extract the tNumber from the topicName
	int tNumber = topicName2Number(topicName);
	buffer.appendInt(tNumber);
	
	buffer.appendShort(globalId.length());
	buffer.appendBytes(globalId.getBytes());
	
    }

    public GrpMetadataRequestContent(ReplayBuffer buffer, PastryNode pn) {
	Verifier.assertTrue(buffer.getByte() == ScribeContent.idGrpMetadataRequestContent);
	this.from = Verifier.restoreNodeHandle(buffer,pn);
	this.seq = buffer.getInteger();
	int tNumber = buffer.getInteger();
	this.topicName = "" + tNumber;
	int globalIdLength = buffer.getShort();
	this.globalId = new String(buffer.getByteArray(globalIdLength));
    }

    public GrpMetadataRequestContent(String topicName, NodeHandle from, int seq) {
	this.topicName = topicName;
	this.from = from;
	this.seq = seq;
    }

    public void setGlobalId(String val) {
        globalId = new String(val);
    }


    public int getSeq() {
	return seq;
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
	String s = "GrpMetadataRequestContent: ";
	s = s + "GID=" + globalId + " tName= " + topicName + " #"+seq+" from "+from + " topicName=  " + topicName;
	return s;
    }

}

