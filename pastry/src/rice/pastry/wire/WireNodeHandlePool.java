
package rice.pastry.wire;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

/**
 * The WireNodeHandlePool controls all of the node handles in use by the
 * WirePastryNode. It ensures that there is only one node handle for each
 * respective pastry node.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class WireNodeHandlePool extends DistNodeHandlePool {

  private HashMap handles;

  private WirePastryNode pastryNode;

  /**
   * Constructor.
   *
   * @param spn The WirePastryNode this pool will serve.
   */
  public WireNodeHandlePool(WirePastryNode spn) {
    super();

    pastryNode = spn;
    handles = new HashMap();
  }

  /**
   * Looks up the WireNodeHandle given a NodeId
   *
   * @param nodeId the nodeId you want a Handle for
   * @return the handle you requested
   */
  public WireNodeHandle get(NodeId nodeId) {
    return (WireNodeHandle) handles.get(nodeId);
  }

  /**
   * tells all the WireNodeHandles they have been killed.
   *
   */
  public void notifyKilled() {
    synchronized(handles) {
      Iterator i = handles.values().iterator();
      while (i.hasNext()) {
        ((WireNodeHandle)i.next()).notifyKilled();
      }
    }
  }

  /**
   * The method verifies a WireNodeHandle. If a node handle to the pastry node
   * has never been seen before, an entry is added, and this node handle is
   * referred to in the future. Otherwise, this method returns the previously
   * verified node handle to the pastry node.
   *
   * @param han DESCRIBE THE PARAMETER
   * @return The node handle to use to talk to the pastry node.
   */
  public synchronized DistNodeHandle coalesce(DistNodeHandle han) {
    DistCoalesedNodeHandle handle = (DistCoalesedNodeHandle) han;
    if ((handles.get(handle.getNodeId()) == null) || (handles.get(handle.getNodeId()) == handle)) {
      handles.put(handle.getNodeId(), handle);
      handle.setIsInPool(true);
    } else {
      handle.setIsInPool(false);
    }

    DistNodeHandle response = (DistNodeHandle) handles.get(handle.getNodeId());

    return response;
  }

  public String toString() {
    String response = "";

    Iterator i = handles.keySet().iterator();

    while (i.hasNext()) {
      Object o = i.next();
      response += o + "\t->\t" + handles.get(o) + "\n";
    }

    return response;
  }

  /**
   * general logging method
   *
   * @param s string to log
   */
  private void debug(String s) {
    if (Log.ifp(6)) {
      System.out.println(pastryNode.getNodeId() + " (P): " + s);
    }
  }
}
