package rice.pastry.direct;
import java.lang.*;

import java.util.*;

import rice.environment.Environment;
import rice.environment.random.RandomSource;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Euclidean network topology and idealized node life. Emulates a network of nodes that are randomly
 * placed in a plane. Proximity is based on euclidean distance in the plane.
 *
 * @version $Id$
 * @author Andrew Ladd
 * @author Rongmei Zhang
 */
public class EuclideanNetwork implements NetworkSimulator {

  private RandomSource rng;
  private HashMap nodeMap;
  private Vector msgQueue;

  private TestRecord testRecord;

  /**
   * Constructor.
   */
  public EuclideanNetwork(Environment e) {
    rng = e.getRandomSource(); //new Randon(PastrySeed.getSeed());
    nodeMap = new HashMap();
    msgQueue = new Vector();
    testRecord = null;
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
      return true; //throw new Error("asking about node alive for unknown node");
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

      for (int i = 0; i < handles.length; i++) {
        if (alive) {
          handles[i].notifyObservers(NodeHandle.DECLARED_LIVE);
        } else {
          handles[i].notifyObservers(NodeHandle.DECLARED_DEAD);
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

  public void deliverMessage(Message msg, PastryNode node) {
    if (isAlive(msg.getSenderId())) {
      MessageDelivery md = new MessageDelivery(msg, node);
      msgQueue.addElement(md);
    }
  }

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
   * 
   * @version $Id$
   * @author amislove
   */
  private class MessageDelivery {
    private Message msg;
    private PastryNode node;

    /**
     * Constructor for MessageDelivery.
     */
    public MessageDelivery(Message m, PastryNode pn) {
      msg = m;
      node = pn;
    }

    public void deliver() {
      //System.outt.println("delivering to " + node);
      //System.outt.println(msg);
      if (isAlive(msg.getSenderId()))
        node.receiveMessage(msg);

    } 
  }

  /**
   * Initialize a random Euclidean NodeRecord
   *
   * @version $Id$
   * @author amislove
   */
  private class NodeRecord {
    /**
     * The euclidean position.
     */
    public int x, y;

    public boolean alive;

    public Vector handles;

    /**
     * Constructor for NodeRecord.
     *
     * @param nh 
     */
    public NodeRecord(DirectNodeHandle nh) {
      x = rng.nextInt() % 10000;
      y = rng.nextInt() % 10000;

      alive = true;
      handles = new Vector();
      handles.add(nh);
    }


    public int proximity(NodeRecord nr) {
      int dx = x - nr.x;
      int dy = y - nr.y;

      return ((int) Math.sqrt(dx * dx + dy * dy));
    }
  }
}
