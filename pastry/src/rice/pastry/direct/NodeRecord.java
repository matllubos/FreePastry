/*
 * Created on Nov 8, 2005
 */
package rice.pastry.direct;

public interface NodeRecord {

  double networkDelay(NodeRecord nrb);
  
  int proximity(NodeRecord nrb);
  
}
