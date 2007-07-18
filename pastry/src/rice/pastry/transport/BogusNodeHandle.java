package rice.pastry.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;

public class BogusNodeHandle extends NodeHandle {
  public Collection<InetSocketAddress> addresses;
  
  public BogusNodeHandle(InetSocketAddress address) {
    addresses = Collections.singletonList(address);
  }

  public BogusNodeHandle(InetSocketAddress[] bootstraps) {
    addresses = Arrays.asList(bootstraps);
  }

  @Override
  public boolean equals(Object obj) {
    throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
  }

  @Override
  public int getLiveness() {
    throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
  }

  @Override
  public Id getNodeId() {
    throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
  }

  @Override
  public int hashCode() {
    throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
  }

  @Override
  public boolean ping() {
    throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
  }

  @Override
  public int proximity() {
    throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
  }

  @Override
  public void receiveMessage(Message msg) {
    throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
  }

  @Override
  public void serialize(OutputBuffer buf) throws IOException {
    throw new IllegalStateException("This NodeHandle is Bogus, don't use it.");
  }

}
