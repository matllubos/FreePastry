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


package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.rm.*;

import java.util.Random;
import java.io.*;

/**
 * @(#) InsertMsg.java
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class InsertMsg extends TestMessage implements Serializable{


    private Id objectKey;

    /**
     * The object that needs to be inserted. 
     */
    private Object object;


    /**
     */
    public InsertMsg(NodeHandle source, Address address, Id _objectKey,Object _content, Credentials authorCred) {
	super(source,address, authorCred);
	this.object = _content;
	this.objectKey = _objectKey;
    }



    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void handleDeliverMessage( RMRegrTestApp testApp) {
	Id objectKey;
	Object object;

	//System.out.println(testApp.getNodeId() + " received RMInsert msg ");
	// This is a local insert 
	objectKey = getObjectKey();
	object = getObject();
	testApp.store(objectKey, object);

	// We now send a Ack
	InsertResponseMsg msg;
	msg = new InsertResponseMsg(testApp.getLocalHandle(),testApp.getAddress(), objectKey, testApp.getCredentials());

	testApp.route(null, msg, getSource());
	
    }
    

    /**
     * Gets the objectKey of the object.
     * @return objectKey
     */
    public Id getObjectKey(){
	return objectKey;
    }
    


    /**
     * Gets the object contained in this message
     * @return object 
     */
    public Object getObject(){
	return object;
    }

    
}





