/*
 * Created on Aug 5, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket.messaging;

import java.util.ArrayList;
import java.util.Collection;

import rice.pastry.NodeHandle;
import rice.pastry.churn.LivenessLeafSet;
import rice.pastry.churn.Probe;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketNodeHandle;

/**
 * @author Jeff Hoye
 */
public abstract class SocketProbe extends SocketMessage implements Probe {
  public SocketNodeHandle sender;
  public SocketNodeHandle receiver;
  public LivenessLeafSet leafSet;
  public Collection failedSet;
  private int state;



  public SocketProbe(SocketNodeHandle sender, SocketNodeHandle receiver, LeafSet leafSet, Collection failedSet, int joinState) {
    this.sender = sender;
    this.receiver = receiver;
    this.leafSet = new LivenessLeafSet(leafSet);
    this.failedSet = failedSet;
    state = joinState; 
  }

  public Collection getFailedset() {
    return failedSet;
  }

  public LivenessLeafSet getLeafset() {
    return leafSet;
  }

  public NodeHandle getSender() {
    return sender;
  }
  
  public int getState() {
    return state;
  }
  
  public String toString() {
    return leafSet.toString();
  }

	public abstract boolean isResponse();
}
