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

package rice.past.messaging;

import rice.past.*;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.util.Random;
import java.io.*;

/**
 * @(#) PASTMessage.java
 *
 * Super class for messages used in PAST.
 *
 * @version $Id$
 * @author Charles Reis
 */
public abstract class PASTMessage extends Message implements Serializable {
  
  // ----- Message Types -----
  
  public static final int REQUEST = 1;
  public static final int RESPONSE = 2;
  
  // ----- Member Fields -----
  
  /**
   * A key for uniquely identifying the message.
   */
  protected PASTMessageID _messageID;
  
  /**
   * Whether this is a request or a response.
   */
  protected int _messageType;
  
  /**
   * The fileId of the file, to be used as destination.
   */
  protected Id _fileId;
  
  /**
   * Constructor
   * @param source NodeId of source Pastry node
   * @param fileId NodeId of file (destination node)
   * @param messageType whether this is a request or a response
   */
  public PASTMessage(Address address, NodeId source, Id fileId, int messageType) {
    super(address);
    _messageID = new PASTMessageIDImpl();
    _messageType = messageType;
    _fileId = fileId;
    setSenderId(source);
  }
  
  /**
   * Constructor
   * @param source NodeId of source Pastry node
   * @param fileId NodeId of file (destination node)
   */
  public PASTMessage(Address address, NodeId source, Id fileId) {
    this(address, source, fileId, REQUEST);
  }
  
  /**
   * Gets this message's identifier.
   */
  public PASTMessageID getID() { 
    return _messageID; 
  }
  
  /**
   * Sets this message's identifier.
   * @param messageID new ID of message
   */
  public void setID(PASTMessageID messageID) {
    _messageID = messageID; 
  }
  
  /**
   * Gets this message's type.
   */
  public int getType() {
    return _messageType;
  }
  
  /**
   * Sets this message's type.
   * @param messageType REQUEST or RESPONSE
   */
  public void setType(int messageType) {
    _messageType = messageType;
  }
  
  /**
   * Gets the source NodeId for this message.
   */
  public NodeId getSource() {
    return getSenderId();
  }
  
  /**
   * Gets the fileId for this file, which is used as the destination.
   */
  public Id getFileId() {
    return _fileId;
  }
  
  /**
   * Performs this message's action after it is delivered.
   * @param service PASTService on which to act
   */
  public abstract void performAction(PASTServiceImpl service);
  
  /**
   * Force subclasses to implement toString.
   */
  public abstract String toString();
  
  /**
   * Print a debug message if the PASTServiceImpl.DEBUG flag is enabled.
   */
  protected void debug(String message) {
    if (PASTServiceImpl.DEBUG) {
      System.out.println("PASTMessage:  " + message);
    }
  }
}
