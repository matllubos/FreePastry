/*
 * Created on Jul 30, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.pastry.socket.testing;

import java.io.Serializable;

import rice.p2p.commonapi.Id;
import rice.pastry.socket.SocketNodeHandle;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class PseudoSocketNodeHandle implements Serializable {
	public Id id;
  public int epoch;
  
  public PseudoSocketNodeHandle(SocketNodeHandle snh) {
		id = snh.getId();
		epoch = snh.getEpoch();
  }
  
  public boolean equals(SocketNodeHandle snh) {
  	return ((snh.getEpoch() == epoch) && (snh.getId().equals(id)));
  }
  
  
  public boolean equals(Object arg0) {
		throw new RuntimeException("equals not supported");
  }

  public String toString() {
    return "[PSNH: " + id + "@" + epoch + "]";
  }

}
