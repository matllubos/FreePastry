package rice.p2p.util;

import java.util.Map;

import org.mpisws.p2p.transport.MessageRequestHandle;

import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.p2p.util.rawserialization.JavaSerializedMessage;

/**
 * Addapts a RawMessage to a normal Message
 * @author Jeff Hoye
 *
 */
public class MRHAdapter implements MessageRequestHandle<NodeHandle, Message> {
  MessageRequestHandle<NodeHandle, RawMessage> internal;
  
  public void setInternal(MessageRequestHandle<NodeHandle, RawMessage> name) {
    this.internal = name;    
  }
  
  public NodeHandle getIdentifier() {
    return internal.getIdentifier();
  }

  public Message getMessage() {
    RawMessage rawMessage = internal.getMessage();
    if (rawMessage.getType() == 0) {
      return ((JavaSerializedMessage)rawMessage).getMessage();
    }
    return rawMessage;
  }

  public Map getOptions() {
    return internal.getOptions();
  }

  public boolean cancel() {
    return internal.cancel();
  }

}
