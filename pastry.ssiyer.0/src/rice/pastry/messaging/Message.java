//////////////////////////////////////////////////////////////////////////////
// Rice Open Source Pastry Implementation                  //               //
//                                                         //  R I C E      //
// Copyright (c)                                           //               //
// Romer Gil                   rgil@cs.rice.edu            //   UNIVERSITY  //
// Andrew Ladd                 aladd@cs.rice.edu           //               //
// Tsuen Wan Ngan              twngan@cs.rice.edu          ///////////////////
//                                                                          //
// This program is free software; you can redistribute it and/or            //
// modify it under the terms of the GNU General Public License              //
// as published by the Free Software Foundation; either version 2           //
// of the License, or (at your option) any later version.                   //
//                                                                          //
// This program is distributed in the hope that it will be useful,          //
// but WITHOUT ANY WARRANTY; without even the implied warranty of           //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            //
// GNU General Public License for more details.                             //
//                                                                          //
// You should have received a copy of the GNU General Public License        //
// along with this program; if not, write to the Free Software              //
// Foundation, Inc., 59 Temple Place - Suite 330,                           //
// Boston, MA  02111-1307, USA.                                             //
//                                                                          //
// This license has been added in concordance with the developer rights     //
// for non-commercial and research distribution granted by Rice University  //
// software and patent policy 333-99.  This notice may not be removed.      //
//////////////////////////////////////////////////////////////////////////////

package rice.pastry.messaging;

import rice.pastry.security.Credentials;

import java.io.*;
import java.util.*;

import rice.pastry.*;

/**
 * This is an abstract implementation of a message object.
 * 
 * @author Andrew Ladd
 */

public abstract class Message implements Serializable 
{
    private Address destination;
    private Credentials credentials;
    private Date theStamp;
    private NodeId senderId;

    /**
     * Gets the address of message receiver that the message is for.
     *
     * @return the destination id.
     */
    
    public Address getDestination() { return destination; }

    /**
     * Gets the credentials of the sender.
     *
     * @return credentials or null if the sender has no credentials.
     */

    public Credentials getCredentials() { return credentials; }

    /**
     * Gets the timestamp of the message, if it exists.
     *
     * @return a timestamp or null if the sender did not specify one.
     */

    public Date getDate() { return theStamp; }

    /**
     * Get sender Id.
     * 
     * @return the immediate sender's NodeId.
     */

    public NodeId getSenderId() { return senderId; }
    
    /**
     * Set sender Id. Called by NodeHandle just before dispatch, so that
     * this Id is guaranteed to belong to the immediate sender.
     * 
     * @param the immediate sender's NodeId.
     */

    public void setSenderId(NodeId id) { senderId = id; }

    /**
     * If the message has no timestamp, this will stamp the message.
     *
     * @param time the timestamp.
     *
     * @return true if the message was stamped, false if the message already had 
     * a timestamp.
     */

    public boolean stamp(Date time) 
    {
	if (theStamp.equals(null)) {
	    theStamp = time;
	    return true;
	}
	else return false;
    }

    /**
     * Constructor.
     *
     * @param dest the destination.
     */

    public Message(Address dest) 
    {
	destination = dest;
	credentials = null;
	theStamp = null;
	senderId = null;
    }

    /**
     * Constructor.
     *
     * @param dest the destination.
     * @param cred the credentials.
     */

    public Message(Address dest, Credentials cred) 
    {
	destination = dest;
	credentials = cred;
	theStamp = null;
	senderId = null;
    }

    /**
     * Constructor.
     *
     * @param dest the destination.
     * @param cred the credentials.
     * @param timestamp the timestamp
     */

    public Message(Address dest, Credentials cred, Date timestamp) 
    {
	destination = dest;
	credentials = cred;
	this.theStamp = timestamp;
	senderId = null;
    }

    /**
     * Constructor.
     *
     * @param dest the destination.
     * @param timestamp the timestamp
     */

    public Message(Address dest, Date timestamp) 
    {
	destination = dest;
	this.theStamp = timestamp;
	senderId = null;
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	destination = (Address) in.readObject();
	senderId = (NodeId) in.readObject();
    }

    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {
	out.writeObject(destination);
	out.writeObject(senderId);
    }
}
