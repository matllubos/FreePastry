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

package rice.pastry.socket;

import rice.pastry.messaging.*;

/**
 * Class which represents the address of the SocketPingManager.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SocketPingManagerAddress implements Address {

    /**
     * The only instance of SocketPingManagerAddress ever created.
     */
    private static SocketPingManagerAddress _instance;

    /**
     * Returns the single instance of SocketPingManagerAddress.
     */
    public static SocketPingManagerAddress instance() {
      if(null == _instance) {
        _instance = new SocketPingManagerAddress();
      }
      return _instance;
    }

    /**
     * Code representing address.
     */
    public int _code = 0x76dc83a9;

    /**
     * Private constructor for singleton pattern.
     */
    private SocketPingManagerAddress() {}

    /**
     * Returns the code representing the address.
     */
    public int hashCode() { return _code; }

    /**
     * Determines if another object is equal to this one.
     * Simply checks if it is an instance of SocketPingManagerAddress
     * since there is only one instance ever created.
     */
    public boolean equals(Object obj) {
      return (obj instanceof SocketPingManagerAddress);
    }
  }