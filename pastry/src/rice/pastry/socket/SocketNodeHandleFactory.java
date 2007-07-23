package rice.pastry.socket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.mpisws.p2p.transport.commonapi.TransportLayerNodeHandle;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.NodeHandleFactory;
import rice.pastry.transport.TLPastryNode;

public class SocketNodeHandleFactory implements NodeHandleFactory {
  TLPastryNode pn;
  Map<MultiInetSocketAddress, SocketNodeHandle> handles;
  Map<SocketNodeHandle, SocketNodeHandle> handleSet;
  
  Logger logger;
  
  public SocketNodeHandleFactory(TLPastryNode pn) {
    this.pn = pn;
    this.logger = pn.getEnvironment().getLogManager().getLogger(SocketNodeHandleFactory.class, null);
    
    handles = new HashMap<MultiInetSocketAddress, SocketNodeHandle>();
    handleSet = new HashMap<SocketNodeHandle, SocketNodeHandle>();
  }
  
  
  /**
   * This is kind of weird, may need to rethink this.
   * 
   * @param i
   * @param id
   * @return
   */
  public SocketNodeHandle getNodeHandle(MultiInetSocketAddress i, long epoch, Id id) {
    if (handles.containsKey(i)) {
      SocketNodeHandle ret = handles.get(i);
      if (ret.getEpoch() == epoch && ret.getId().equals(id)) {
        return ret;        
      } else {
        // this is kind of dangerous because this dictionary is necessay for the identity layer, and could be 
        // poisoned with this method 
        
        if (logger.level <= Logger.WARNING) logger.log("getNodeHandle("+i+","+epoch+","+id+") replacing "+ret);
      }      
    }
    SocketNodeHandle handle = new SocketNodeHandle(i, epoch, id, pn);
    handleSet.put(handle, handle);
    handles.put(i, handle);
    return handle;
  }

  public NodeHandle readNodeHandle(InputBuffer buf) throws IOException {
//    TLNodeHandle handle = TLNodeHandle.build(buf, pn);
//    TLNodeHandle old = handles.get(handle.eaddress);
//    
//    if (handle.equals(old)) {
//      return old; 
//    }
//        
//    if (old != null) {
//      if (logger.level <= Logger.INFO) logger.log("readNodeHandle(): nodeHandle changed old:"+old+" new:"+handle);
//    }
//    handleSet.put(handle, handle);
//    handles.put(handle.eaddress, handle);
//    
//    return handle;
    return coalesce(SocketNodeHandle.build(buf, pn));
  }
  
//  public org.mpisws.p2p.transport.commonapi.NodeHandleFactory<MultiInetSocketAddress> getTLInterface() {
//    return tlInterface;
//  }

  public NodeHandle coalesce(NodeHandle h) {
    SocketNodeHandle handle = (SocketNodeHandle)h;
    if (handleSet.containsKey(handle)) {
      return handleSet.get(handle);
    }
    
    handle.setLocalNode(pn);
    
    handles.put(handle.eaddress, handle);
    handleSet.put(handle, handle);
    return handle;
  }


  public TransportLayerNodeHandle<MultiInetSocketAddress> lookupNodeHandle(MultiInetSocketAddress i) {
    return handles.get(i);
  }

}
