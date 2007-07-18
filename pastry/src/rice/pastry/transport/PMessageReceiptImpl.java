package rice.pastry.transport;

import java.util.Map;

import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.commonapi.TransportLayerNodeHandle;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;

public class PMessageReceiptImpl implements PMessageReceipt {
  MessageRequestHandle<NodeHandle, RawMessage> internal;
  Message message;
  
  public PMessageReceiptImpl(Message msg) {
    this.message = msg;
  }

  public NodeHandle getIdentifier() {
    return (NodeHandle)internal.getIdentifier();
  }

  public Message getMessage() {
    return message;
  }

  public Map<String, Integer> getOptions() {
    return internal.getOptions();
  }

  public boolean cancel() {
    return internal.cancel();
  }

  public void setInternal(MessageRequestHandle<NodeHandle, RawMessage> name) {
    this.internal = internal;
  }

}
