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
 * @(#) MessageReclaim.java
 *
 * PASTMessage requesting that space used for a given file on
 * the local node be reclaimed, effectively deleting the file.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class MessageReclaim extends PASTMessage {
  
  /**
   * Whether the reclaim request was successful
   */
  protected boolean _success = false;
  
  /**
   * Builds a new request to reclaim space used by an existing file.
   * @param nodeId Source Pastry node's ID
   * @param fileId Pastry key of desired file
   * @param cred Credentials of user requesting the reclaim
   */
  public MessageReclaim(Id nodeId, Id fileId) {
    super(nodeId, fileId);
  }
  
  /**
   * Returns whether the reclaim command was successful.
   */
  public boolean getSuccess() {
    return _success;
  }
  
  /**
   * Reclaims the space in the service's storage.
   * @param service PASTService on which to act
   */
  public void performAction(final PASTServiceImpl service) {
    debug("  Reclaiming file " + getFileId() + " at node " +
          service.getId());

    Continuation reclaim = new Continuation() {
      public void receiveResult(Object o) {
        _success = ((Boolean) o).booleanValue();
        setType(RESPONSE);
        service.sendMessage(MessageReclaim.this);
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occurred during an insert!");
      }
    };

    service.getStorage().unstore((rice.pastry.Id) getFileId(), reclaim);
  }
  
  /**
   * Display this message.
   */
  public String toString() {
    String val = "RECLAIM ";
    if (getType() == REQUEST) {
      val += "Request: ";
    }
    else {
      val += "Response: ";
    }
    return val + getFileId();
  }
}
