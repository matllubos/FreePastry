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

package rice.caching;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.caching.messaging.*;

/**
 * This interface is exported by Caching Manager for any applications which need to
 * use the dynamic-caching functionality.
 *
 * @version $Id$
 *
 * @author Alan mislove
 */
public class CachingManager extends PastryAppl {

  // the client on top of this caching manager
  private CachingManagerClient client;

  // the credentials of this caching manager
  private Credentials credentials;

  /**
   * Constructor which takes a pastry node, a client to run on top of this manager,
   * and an instance name, which is recommended, but not required, to be the same as the
   * instance of the client.
   *
   * @param node The local pastry node
   * @param client The client this manager will work with
   * @param instance A unique instance name of this manager/client
   */
  public CachingManager(PastryNode node, CachingManagerClient client, String instance) {
    super(node, client.getClass().getName() + instance);
    
    this.client = client;
    this.credentials = new PermissiveCredentials();
  }

  /**
   * Methods which returns the credentials of this PastryAppl.
   *
   * @return The credentials
   */
  public Credentials getCredentials() {
    return credentials;
  }

  /**
   * Method by which a client informs the caching manager that an object should
   * be propogated in the dynamic cache.  This methods *MUST* be called on the node
   * which satisfies the request of the given message.  The calling client should call
   * this method, and then send the result back to the callee.  This method will cause
   * this caching manager to forward the result one hop towards the callee, and insert
   * the object in that cache.
   *
   * @param message The message which was the lookup
   * @param id The id (or key) of the lookup
   * @param obj The resulting object
   */
  public void cache(CacheLookupMessage message, NodeId id, Object obj) {
    NodeHandle dest = message.getPreviousNode();
    CachingManagerMessage cmsg = new CachingManagerMessage(address, id, obj);

    routeMsgDirect(dest, cmsg, credentials, null);
  }

  /**
   * Method which clients *MUST* call whenever a message is about to be forwarded.
   *
   * @param message The message about to be forwarded.
   */
  public void forward(CacheLookupMessage message) {
    message.addHop(thePastryNode.getLocalHandle());
  }

  /**
   * Method by which the caching manager receives messages through pastry.
   *
   * @param message The message which has arrived.
   */
  public void messageForAppl(Message message) {
    if (message instanceof CachingManagerMessage) {
      CachingManagerMessage cmsg = (CachingManagerMessage) message;
      client.cache(cmsg.getId(), cmsg.getObject());
    }
  }
}
