package rice.visualization;

import java.net.*;
import rice.p2p.commonapi.*;

public class VisualizationNode {
    
  protected Id id;
  
  protected boolean alive;
  
  protected boolean fault;
  
  protected InetSocketAddress address;
  
  protected VisualizationNode(Id id, boolean alive, boolean fault,
                              InetSocketAddress address) {
    this.id = id;
    this.alive = alive;
    this.fault = fault;
    this.address = address;
  }
  
  public Id getId() {
    return id;
  }
  
  public boolean isAlive() {
    return alive;
  }
  
  public boolean isFault() {
    return fault;
  }
  
  public InetSocketAddress getAddress() {
    return address;
  }
  
}