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

package rice.pastry.wire.messaging.datagram;

import java.io.*;

import rice.pastry.*;

/**
 * Message which is a "transport" message in the datagram protocol. It simply
 * wraps an internal message for sending across the wire.
 *
 * @version $Id: DatagramTransportMessage.java,v 1.2 2002/09/09 01:19:49
 *      amislove Exp $
 * @author Alan Mislove
 */
public class DatagramTransportMessage extends DatagramMessage {

    // the wrapped object
    private Object o;

    /**
     * Builds a DatagramMessage given an object to wrap and a packet number
     *
     * @param o The object to wrap
     * @param num The "packet number"
     * @param source DESCRIBE THE PARAMETER
     * @param destination DESCRIBE THE PARAMETER
     */
    public DatagramTransportMessage(NodeId source, NodeId destination, int num, Object o) {
        super(source, destination, num);
        this.o = o;
    }

    /**
     * Returns the iternal wrapped object.
     *
     * @return The internal object
     */
    public Object getObject() {
        return o;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @return DESCRIBE THE RETURN VALUE
     */
    public String toString() {
        return "DatagramTransportMsg num " + num + " wrapping " + o;
    }
}
