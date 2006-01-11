/*
 * Created on Jan 11, 2006
 */
package rice.pastry;


public interface NodeSetListener {
  public void nodeSetUpdate(NodeSetEventSource nodeSetEventSource, NodeHandle handle, boolean added);
}
