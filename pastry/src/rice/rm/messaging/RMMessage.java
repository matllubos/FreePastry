/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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


package rice.rm.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.rm.*;

import java.util.Random;
import java.io.*;

/**
 * @(#) RMMessage.java
 *
 * A RM message. These messages are exchanged between the RM modules on the pastry nodes. 
 *
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi
 */
public abstract class RMMessage extends Message implements Serializable{


    /**
     * The credentials of the author for the object contained in this object
     */
    private Credentials _authorCred;
     
   
    /**
     * The ID of the source of this message.
     * Should be serializable.
     */
    protected NodeHandle _source;

    // for debugging purposes
    private int _seqno;

    public static int MAXKEYSINRANGE = 5;

    public static int SPLITFACTOR = 4;

    // This class will be used by the messaging system
    public static class KEEntry {
	private IdRange reqRange;
	private boolean hashEnabled;
	private IdSet keySet;
	private Id hash;
	private int numKeys;
	// This range is got by the intersection of the reqRange and the responsible
	// range on the responder side
	private IdRange range;

	// This constructor is to be used by the requestor for keys
	public KEEntry(IdRange _range, boolean _hEnabled) {
	    reqRange = _range;
	    hashEnabled = _hEnabled;
	    numKeys = 0;
	    keySet = null;
	    hash = null;
	    range = null;
	}

	// This constructor is to be used when the responder 
	public KEEntry(IdRange _reqRange, IdRange _range, int _numKeys, boolean _hashEnabled, Id _hash, IdSet _keySet ) {
	    reqRange = _reqRange;
	    range = _range;
	    numKeys = _numKeys;	    
	    hashEnabled = _hashEnabled;
	    hash = _hash;
	    keySet = _keySet;

	} 

	public IdRange getReqRange() {
	    return reqRange;
	}

	public IdRange getRange() {
	    return range;
	}

	public IdSet getKeySet() {
	    return keySet;
	}

	public boolean getHashEnabled() {
	    return hashEnabled;
	}
	
	public Id getHash() {
	    return hash;
	}

	public int getNumKeys() {
	    return numKeys;
	}

	public String toString() {
	    String s = "KEE(";
	    s =  s + getReqRange() + ", " + getHashEnabled();
	    return s;

	}

    }

    // Stands for the Keys Exchange protocol Pending Entry
    // this class will be used to keep track of the pending entries on the requestor side
   


    /**
     * Constructor : Builds a new RM Message
     * @param address RM Application address
     */
    public RMMessage(NodeHandle source, Address address, Credentials authorCred, int seqno) {
	super(address);
	this._source = source; 
	this._authorCred = authorCred;
	this._seqno = seqno;
    }
    

     /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public abstract void 
	handleDeliverMessage( RMImpl rm);
    

    public int getSeqno() {
	return _seqno;
    }

    public NodeHandle getSource() {
	return _source;
    }
    
    
    /**
     * Gets the author's credentials associated with this object
     * @return credentials
     */
    public Credentials getCredentials(){
	return _authorCred;
    }

    
}





