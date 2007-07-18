package org.mpisws.p2p.testing.transportlayer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.wire.WireTransportLayerImpl;

import rice.environment.Environment;
import rice.environment.logging.CloneableLogManager;

public class WireTest extends TLTest<InetSocketAddress> {
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    TLTest.setUpBeforeClass();
    logger = env.getLogManager().getLogger(WireTest.class, null);
    
    int startPort = START_PORT;
    InetAddress addr = InetAddress.getLocalHost();    
//    InetAddress addr = InetAddress.getByName("10.0.0.10");
    alice = buildTL("alice", addr, startPort, env);
    bob = buildTL("bob", addr, startPort+1, env);
  }
  
  private static TransportLayer buildTL(String name, InetAddress addr, int port, Environment env) throws IOException {
    Environment env_a = new Environment(
        env.getSelectorManager(), 
        env.getProcessor(), 
        env.getRandomSource(), 
        env.getTimeSource(), 
        ((CloneableLogManager) env.getLogManager()).clone(name),
        env.getParameters(), 
        env.getExceptionStrategy());    
    env.addDestructable(env_a);    
    InetSocketAddress addr_a = new InetSocketAddress(addr,port);
    return new WireTransportLayerImpl(addr_a,env_a, null);
  }

  @Test
  public void bogus() {} 
  
  @Override
  public InetSocketAddress getBogusIdentifier(InetSocketAddress local) throws IOException {
    return new InetSocketAddress(InetAddress.getLocalHost(), START_PORT-2);
  }

}
