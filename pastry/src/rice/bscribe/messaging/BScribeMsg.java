package rice.bscribe.messaging;

import rice.bscribe.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.*;

import java.io.*;
import java.util.*;

/**
 * @(#) BScribeMsg.java
 *
 * A BScribe message 
 *
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi
 */
public class BScribeMsg extends Message implements Serializable{

    public static final int REJOIN = 1; 
    public static final int POSITIVEPATHUPDATE = 2;
    public static final int NEGATIVEPATHUPDATE = 3;
    public static final int DROPPED = 4;
    public static final int TAKECHILD = 5;
    public static final int TAKECHILDNEGATIVEPATHUPDATE = 6;
    
    public int type;
    public NodeId channelId;
    public NodeId spareId;
    public Vector listOfSiblings;
    public Vector path;
    public Vector traversed;
    public NodeHandle source;
    public NodeId lastHop;
    public NodeHandle childToAdopt;

    // Constructor for the REJOIN type of message
    public BScribeMsg(Address address,int p_type, NodeId p_channelId, NodeId p_spareId, NodeHandle p_source, NodeId p_lastHop) {
	super(address);
	this.type = p_type;
	this.channelId = p_channelId;
	this.spareId = p_spareId;
	this.source = p_source;
	this.listOfSiblings = new Vector();
	this.traversed = new Vector();
	this.lastHop = p_lastHop;

    }
    
    

    // Constructor for the DROPPED & TAKECHILDNEGATIVEPATHUPDATE type of message
    public BScribeMsg(Address address,int p_type, NodeId p_channelId) {
	super(address);
	this.type = p_type;
	this.channelId = p_channelId;
    }


    // Constructor for the TAKECHILD type of message
    public BScribeMsg(Address address,int p_type, NodeId p_channelId, NodeHandle p_childToAdopt) {
	super(address);
	this.type = p_type;
	this.channelId = p_channelId;
	this.childToAdopt = p_childToAdopt;
    }



    // Constructor for the POSITIVEPATHUPDATE & NEGATIVEPATHUPDATE type of message
    public BScribeMsg(Address address,int p_type, NodeId p_channelId , Vector p_path) {
	super(address);
	this.type = p_type;
	this.channelId = p_channelId;
	this.path = p_path;
    }
      
}



