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

package rice.pastry.direct;

import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Interface to an object which is simulating the network.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public interface NetworkSimulator 
{
    /**
     * Registers a node handle with the simulator.
     *
     * @param nh the node handle to register.
     */

    public void registerNodeId(NodeHandle nh);

    /**
     * Checks to see if a node id is alive.
     *
     * @param nid a node id.
     *
     * @return true if alive, false otherwise.
     */

    public boolean isAlive(NodeId nid);

    /**
     * Determines proximity between two nodes.
     *
     * @param a a node id.
     * @param b another node id.
     *
     * @return proximity of b to a.
     */

    public int proximity(NodeId a, NodeId b);

    /**
     * Deliver message.
     *
     * @param msg message to deliver.
     * @param node the Pastry node to deliver it to.
     */

    public void deliverMessage(Message msg, PastryNode node);

    /**
     * Simulate one message delivery.
     *
     * @return true if a message was delivered, false otherwise.
     */

    public void setTestRecord( TestRecord tr );
    public TestRecord getTestRecord();
    public NodeHandle getClosest(NodeId nid);

    public boolean simulate();
}
