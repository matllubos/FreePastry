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

import rice.p2p.commonapi.*;

import java.util.Random;
import java.io.*;

/**
 * @(#) MessageInsert.java
 *
 * PASTMessage requesting a file be inserted on the local node.
 *
 * @version $Id$
 * @author Charles Reis
 * 
 * @deprecated This version of PAST has been deprecated - please use the version
 *   located in the rice.p2p.past package.
 */
public class MessageInsert extends PASTMessage {
  
  /**
   * The file to insert.
   */
  protected Serializable _file;
  
  /**
   * Whether the insert was successful (on a response).
   */
  protected boolean _success = false;
  
  /**
   * Builds a new request to insert a file.
   *
   * @param nodeId Source Pastry node's ID
   * @param fileId Pastry key of file
   * @param file File to be stored
   */
  public MessageInsert(Id nodeId, 
                       Id fileId, 
                       Serializable file) {
    super(nodeId, fileId);
    _file = file;
  }
  
  /**
   * Returns whether the insert was successful.
   *
   * @return Whether or not it was successful
   */
  public boolean getSuccess() {
    return _success;
  }
  
  /**
   * Inserts this message's file into the service.
   *
   * @param service PASTService on which to act
   */
  public void performAction(final PASTServiceImpl service) {
    debug("  Inserting file " + getFileId() + " at node " +
          service.getId());

    final Continuation insert = new Continuation() {
      public void receiveResult(Object o) {
        _success = ((Boolean) o).booleanValue();
        setType(RESPONSE);
        service.sendMessage(MessageInsert.this);
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occurred during an insert!");
      }
    };

    Continuation check = new Continuation() {
      public void receiveResult(Object o) {
        if (! ((Boolean) o).booleanValue()) {
          service.getStorage().store((rice.pastry.Id) getFileId(), _file, insert);
        } else {
          // Already exists, return false
          _success = false;
          setType(RESPONSE);
          service.sendMessage(MessageInsert.this);
        }
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occurred during an insert!");
      }
    };

    service.getStorage().exists((rice.pastry.Id) getFileId(), check);
  }
  
  /**
   * Display this message.
   *
   * @return The string representing this message
   */
  public String toString() {
    String val = "INSERT ";
    if (getType() == REQUEST) {
      val += "Request: ";
    }
    else {
      val += "Response: ";
    }
    return val + getFileId() + ": " + _file;
  }
}
