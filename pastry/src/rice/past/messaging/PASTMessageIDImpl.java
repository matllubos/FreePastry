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

import java.util.Random;
import java.io.Serializable;

/**
 * @(#) PASTMessageIDImpl.java
 *
 * Implements PASTMessageID providing a key to be used to uniquely
 * identify messages.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class PASTMessageIDImpl 
  implements PASTMessageID, Serializable {
  
  /**
   * Internally, the ID is represented by a 
   * Long.
   */
  private Long _idCode;
  
  /**
   * Used to randomly generate Longs.
   */
  private static final Random _rand = new Random();
  
  /**
   * Constructor. Returns a randomly generated ID
   */
  PASTMessageIDImpl() {
    _idCode = new Long(_rand.nextLong());
  }
  
  /**
   * Indicates whether some other object is equal
   * to this one.
   */
  public boolean equals(Object obj) {
    return ((obj instanceof PASTMessageIDImpl) &&
            (((PASTMessageIDImpl) obj).getIDCode().equals(_idCode)));
  }
  
  /**
   * Returns the hashcode value of the object.
   */
  public int hashCode() {
    return _idCode.intValue();
  }
  
  /**
   * Returns the string representation of the node.
   */
  public String toString() {
    return _idCode.toString();
  }
  
  /**
   * Returns the internal representation of the id.
   * Used for .equals().
   */
  protected Long getIDCode() {
    return _idCode;
  }
}
