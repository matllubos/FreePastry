/*
 * Created on Jan 11, 2006
 */
package rice.pastry;

public interface NodeSetEventSource {
  public void addNodeSetListener(NodeSetListener listener);
  public void removeNodeSetListener(NodeSetListener listener);
}
