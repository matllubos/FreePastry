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

package rice.pastry.direct;
import java.lang.*;

import java.util.*;

import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Sphere network topology and idealized node life. Emulates a network of nodes that are randomly
 * placed on a sphere. Proximity is based on euclidean distance on the sphere.
 *
 * @version $Id$
 * @author Y. Charlie Hu
 * @author Rongmei Zhang
 */
public class SphereNetwork implements NetworkSimulator {

  private Random rng;
  private HashMap nodeMap;
  private Vector msgQueue;

  private TestRecord testRecord;

  /**
   * Constructor.
   */
  public SphereNetwork() {
    rng = new Random(PastrySeed.getSeed());
    nodeMap = new HashMap();
    msgQueue = new Vector();
  }

  /**
   * testing if a NodeId is alive
   *
   * @param nid the NodeId being tested
   * @return true if nid is alive false otherwise
   */
  public boolean isAlive(NodeId nid) {
    NodeRecord nr = (NodeRecord) nodeMap.get(nid);

    if (nr == null) {
      throw new Error("asking about node alive for unknown node");
    }

    return nr.alive;
  }

  /**
   * get TestRecord
   *
   * @return the returned TestRecord
   */
  public TestRecord getTestRecord() {
    return testRecord;
  }

  /**
   * find the closest NodeId to an input NodeId out of all NodeIds in the network
   *
   * @param nid the input NodeId
   * @return the NodeId closest to the input NodeId in the network
   */
  public DirectNodeHandle getClosest(NodeId nid) {
    Iterator it = nodeMap.values().iterator();
    DirectNodeHandle bestHandle = null;
    int bestProx = Integer.MAX_VALUE;
    DirectNodeHandle itHandle;
    NodeId itId;
    NodeRecord itRecord;

    while (it.hasNext()) {
      itRecord = (NodeRecord) it.next();
      itHandle = (DirectNodeHandle) itRecord.handles.elementAt(0);
      itId = itHandle.getNodeId();
      if (!itHandle.isAlive() || !itHandle.getLocalNode().isReady() || nid == itId) {
        continue;
      }
      if (proximity(nid, itId) < bestProx) {
        bestProx = proximity(nid, itId);
        bestHandle = itHandle;
      }
    }
    return bestHandle;
  }

  /**
   * set the liveliness of a NodeId
   *
   * @param nid the NodeId being set
   * @param alive the value being set
   */
  public void setAlive(NodeId nid, boolean alive) {
    NodeRecord nr = (NodeRecord) nodeMap.get(nid);

    if (nr == null) {
      throw new Error("setting node alive for unknown node");
    }

    if (nr.alive != alive) {
      nr.alive = alive;

      DirectNodeHandle[] handles = (DirectNodeHandle[]) nr.handles.toArray(new DirectNodeHandle[0]);

      // notify all handles which exist on currently alive nodes
      for (int i = 0; i < handles.length; i++) {
        if (handles[i].getLocalNode().getNodeId().equals(nid) ||
          isAlive(handles[i].getLocalNode().getNodeId())) {
          if (alive) {
            handles[i].notifyObservers(NodeHandle.DECLARED_LIVE);
          } else {
            handles[i].notifyObservers(NodeHandle.DECLARED_DEAD);
          }
        }
      }
    }
  }

  /**
   * set TestRecord
   *
   * @param tr input TestRecord
   */
  public void setTestRecord(TestRecord tr) {
    testRecord = tr;
  }

  /**
   * register a new node
   *
   * @param nh the DirectNodeHandle being registered
   */

  public void registerNodeId(DirectNodeHandle nh) {
    NodeId nid = nh.getNodeId();

    if (nodeMap.get(nid) != null) {
      NodeRecord record = (NodeRecord) nodeMap.get(nid);
      record.handles.add(nh);
    } else {
      nodeMap.put(nid, new NodeRecord(nh));
    }
  }

  /**
   * computes the proximity between two NodeIds
   *
   * @param a the first NodeId
   * @param b the second NodeId
   * @return the proximity between the two input NodeIds
   */
  public int proximity(NodeId a, NodeId b) {
    NodeRecord nra = (NodeRecord) nodeMap.get(a);
    NodeRecord nrb = (NodeRecord) nodeMap.get(b);

    if (nra == null ||
      nrb == null) {
      throw new Error("asking about node proximity for unknown node(s)");
    }

    return nra.proximity(nrb);
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param msg DESCRIBE THE PARAMETER
   * @param node DESCRIBE THE PARAMETER
   */
  public void deliverMessage(Message msg, PastryNode node) {
    MessageDelivery md = new MessageDelivery(msg, node);

    msgQueue.addElement(md);
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean simulate() {
    if (msgQueue.size() == 0) {
      return false;
    }

    MessageDelivery md = (MessageDelivery) msgQueue.firstElement();

    msgQueue.removeElementAt(0);

    md.deliver();

    return true;
  }

  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author amislove
   */
  private class MessageDelivery {
    private Message msg;
    private PastryNode node;

    /**
     * Constructor for MessageDelivery.
     *
     * @param m DESCRIBE THE PARAMETER
     * @param pn DESCRIBE THE PARAMETER
     */
    public MessageDelivery(Message m, PastryNode pn) {
      msg = m;
      node = pn;
    }

    /**
     * DESCRIBE THE METHOD
     */
    public void deliver() {
      //System.out.println("delivering to " + node);
      //System.out.println(msg);

      node.receiveMessage(msg);
      //System.out.println("----------------------");
    }
  }

  /**
   * Initialize a random Sphere NodeRecord
   *
   * @version $Id$
   * @author amislove
   */
  private class NodeRecord {
    /**
     * DESCRIBE THE FIELD
     */
    public double theta, phi;
    /**
     * DESCRIBE THE FIELD
     */
    public boolean alive;
    /**
     * DESCRIBE THE FIELD
     */
    public Vector handles;

    /**
     * Constructor for NodeRecord.
     *
     * @param nh DESCRIBE THE PARAMETER
     */
    public NodeRecord(DirectNodeHandle nh) {
      theta = Math.asin(2.0 * rng.nextDouble() - 1.0);
      phi = 2.0 * Math.PI * rng.nextDouble();

      alive = true;
      handles = new Vector();
      handles.add(nh);
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param nr DESCRIBE THE PARAMETER
     * @return DESCRIBE THE RETURN VALUE
     */
    public int proximity(NodeRecord nr) {
      return (int) (10000 * Math.acos(Math.cos(phi - nr.phi) * Math.cos(theta) * Math.cos(nr.theta) +
        Math.sin(theta) * Math.sin(nr.theta)));
    }
  }
}

