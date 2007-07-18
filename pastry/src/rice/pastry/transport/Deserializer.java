package rice.pastry.transport;

import rice.p2p.commonapi.rawserialization.MessageDeserializer;

public interface Deserializer {
  public void setDeserializer(int address, MessageDeserializer md);
  public void clearDeserializer(int address);
  public MessageDeserializer getDeserializer(int address);
}
