package rice.pastry.transport;

import java.util.Map;

import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.commonapi.TransportLayerNodeHandle;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;

public interface PMessageReceipt extends MessageRequestHandle<NodeHandle, Message> {
  public NodeHandle getIdentifier();

  public Message getMessage();

  public Map<String, Integer> getOptions();
}
