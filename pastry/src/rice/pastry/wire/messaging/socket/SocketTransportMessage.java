/**
 * "FreePastry" Peer-to-Peer Application Development Substrate Copyright 2002,
 * Rice University. All rights reserved. Redistribution and use in source and
 * binary forms, with or without modification, are permitted provided that the
 * following conditions are met: - Redistributions of source code must retain
 * the above copyright notice, this list of conditions and the following
 * disclaimer. - Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. -
 * Neither the name of Rice University (RICE) nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. This software is provided by RICE and the
 * contributors on an "as is" basis, without any representations or warranties
 * of any kind, express or implied including, but not limited to,
 * representations or warranties of non-infringement, merchantability or fitness
 * for a particular purpose. In no event shall RICE or contributors be liable
 * for any direct, indirect, incidental, special, exemplary, or consequential
 * damages (including, but not limited to, procurement of substitute goods or
 * services; loss of use, data, or profits; or business interruption) however
 * caused and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of the use
 * of this software, even if advised of the possibility of such damage.
 */

package rice.pastry.wire.messaging.socket;

import java.io.*;

import rice.pastry.*;

/**
 * Class which represents a wrapper message sent across the socket-based
 * protocol - it has another message inside of it.
 *
 * @version $Id: SocketTransportMessage.java,v 1.3 2003/06/10 18:29:03 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class SocketTransportMessage extends SocketMessage {

    private Object o;

    private NodeId destination;

    /**
     * Constructs a new message wrapping another object.
     *
     * @param o The object to be wrapped.
     * @param destination DESCRIBE THE PARAMETER
     */
    public SocketTransportMessage(Object o, NodeId destination) {
        super();
        this.o = o;
        this.destination = destination;
    }

    /**
     * Returns the wrapped message
     *
     * @return The internally wrapped message.
     */
    public Object getObject() {
        return o;
    }

    /**
     * Returns the destination node id
     *
     * @return The destination node id
     */
    public NodeId getDestination() {
        return destination;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @return DESCRIBE THE RETURN VALUE
     */
    public String toString() {
        return "{" + o + "}";
    }

}
