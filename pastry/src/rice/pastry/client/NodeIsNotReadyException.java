package rice.pastry.client;

import rice.pastry.NodeHandle;

public class NodeIsNotReadyException extends Exception {
  NodeHandle handle;
  
  public NodeIsNotReadyException(NodeHandle handle) {
    this.handle = handle;
  }

}
