package rice.p2p.util;

import java.io.IOException;

import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;

import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.rawserialization.RawMessage;

public class MCAdapter implements MessageCallback<NodeHandle, RawMessage> {
  MessageCallback<NodeHandle, Message> internal;
  private MRHAdapter handle;
  
  
  public MCAdapter(MessageCallback<NodeHandle, Message> deliverAckToMe, MRHAdapter handle) {
    this.internal = deliverAckToMe;
    this.handle = handle;
  }

  public void ack(MessageRequestHandle<NodeHandle, RawMessage> msg) {
    internal.ack(handle);
  }

  public void sendFailed(MessageRequestHandle<NodeHandle, RawMessage> msg, IOException reason) {
    internal.sendFailed(handle, reason);    
  }

}
