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

package rice.p2p.past.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) ContinuationMessage.java
 *
 * This class the abstraction of a message used internally by Past which serves
 * as a continuation.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public abstract class ContinuationMessage extends PastMessage implements Continuation {

  // the response data
  private Object response;

  // the response exception, if one is thrown
  private Exception exception;

  /**
    * Constructor which takes a unique integer Id, as well as the
   * data to be stored
   *
   * @param uid The unique id
   * @param id The location to be stored
   * @param source The source address
   * @param dest The destination address
   * @param content The data to be stored
   */
  protected ContinuationMessage(int uid, Id source, Id dest) {
    super(uid, source, dest); 
  }

  /**
    * Method which builds a response for this message, using the provided
   * object as a result.
   *
   * @param o The object argument
   */
  public void receiveResult(Object o) {
    setResponse();
    response = o;
  }

  /**
    * Method which builds a response for this message, using the provided
   * exception, which was thrown
   *
   * @param e The exception argument
   */
  public void receiveException(Exception e) {
    setResponse();
    exception = e;
  }

  /**
    * Method by which this message is supposed to return it's response.
   *
   * @param c The continuation to return the reponse to.
   */
  public void returnResponse(Continuation c) {
    if (exception == null)
      c.receiveResult(response);
    else
      c.receiveException(exception);
  }

  /**
   * Returns the response
   *
   * @return The response
   */
  public Object getResponse() {
    return response;
  }
}

