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

import java.net.*;
import rice.pastry.*;

import rice.pastry.wire.*;

/**
 * Class which represents a greeting in the socket-based pastry protocol. It
 * contains the InetSocketAddress and nodeId of the socket-initiating node.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class HelloMessage extends SocketCommandMessage {

    private InetSocketAddress address;

    private NodeId nodeId;

    private NodeId dest;

    /**
     * Constructor
     *
     * @param pn DESCRIBE THE PARAMETER
     * @param dest DESCRIBE THE PARAMETER
     */
    public HelloMessage(WirePastryNode pn, NodeId dest) {
        super();
        address = ((WireNodeHandle) pn.getLocalHandle()).getAddress();
        nodeId = pn.getNodeId();
        this.dest = dest;
    }

    /**
     * Returns the address of the source of this message.
     *
     * @return The address of the source of the message.
     */
    public InetSocketAddress getAddress() {
        return address;
    }

    /**
     * Returns the NodeId of the source
     *
     * @return The NodeId of the source of this message.
     */
    public NodeId getNodeId() {
        return nodeId;
    }

    /**
     * Returns the NodeId of the source
     *
     * @return The NodeId of the source of this message.
     */
    public NodeId getDestination() {
        return dest;
    }

    /**
     * Returns the appropriate response for this HelloMessage
     *
     * @param pn The local pastry node
     * @return A response message that should be sent to sender
     */
    public HelloResponseMessage getResponse(WirePastryNode pn) {
        return new HelloResponseMessage(pn, nodeId);
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @return DESCRIBE THE RETURN VALUE
     */
    public String toString() {
        return "HelloMessage from " + address + "(" + nodeId + ") to " + dest;
    }
}
