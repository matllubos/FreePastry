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

import rice.*;

import rice.past.*;

import rice.pastry.NodeId;
import rice.pastry.messaging.Message;
import rice.pastry.security.Credentials;

import java.util.Random;
import java.io.*;

/**
 * @(#) MessageExists.java
 *
 * PASTMessage detemining if a file exists at a given id.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class MessageExists extends PASTMessage {
  
  /**
   * Content to be returned
   */
  protected boolean _exists = false;
  
  /**
   * Builds a new request to see if a file exists.
   * @param nodeId Source Pastry node's ID
   * @param fileId Pastry key of desired file
   */
  public MessageExists(NodeId nodeId, NodeId fileId) {
    super(nodeId, fileId);
  }
  
  /**
   * Returns whether the file exists.
   */
  public boolean exists() {
    return _exists;
  }
  
  /**
   * Looks up the file in the given service's storage.
   * @param service PASTService on which to act
   */
  public void performAction(final PASTServiceImpl service) {
    debug("  Seeing if file " + getFileId() + " exists, at node " +
          service.getPastryNode().getNodeId());

    Continuation c = new Continuation() {
      public void receiveResult(Object o) {
        _exists = ((Boolean) o).booleanValue();

        debug("File was found (" + _exists + ") at " + service.getPastryNode().getNodeId());
        
        setType(RESPONSE);
        service.sendMessage(MessageExists.this);
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occured during an exists call.");
      }
    };

    service.getStorage().exists(getFileId(), c);
  }
  
  /**
   * Display this message.
   */
  public String toString() {
    String val = "EXISTS ";
    if (getType() == REQUEST) {
      val += "Request: ";
    }
    else {
      val += "Response: ";
    }
    return val + getFileId();
  }
}
