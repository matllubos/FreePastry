/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.pastry.socket.nat.rendezvous;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.commonapi.CommonAPITransportLayerImpl;
import org.mpisws.p2p.transport.identity.IdentityImpl;
import org.mpisws.p2p.transport.identity.IdentitySerializer;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.nat.FirewallTLImpl;
import org.mpisws.p2p.transport.rendezvous.ContactDeserializer;
import org.mpisws.p2p.transport.rendezvous.PilotFinder;
import org.mpisws.p2p.transport.rendezvous.PilotManager;
import org.mpisws.p2p.transport.rendezvous.RendezvousContact;
import org.mpisws.p2p.transport.rendezvous.RendezvousGenerationStrategy;
import org.mpisws.p2p.transport.rendezvous.RendezvousStrategy;
import org.mpisws.p2p.transport.rendezvous.RendezvousTransportLayerImpl;
import org.mpisws.p2p.transport.sourceroute.SourceRoute;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.random.RandomSource;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.pastry.NodeHandle;
import rice.pastry.NodeHandleFactory;
import rice.pastry.NodeIdFactory;
import rice.pastry.join.JoinProtocol;
import rice.pastry.leafset.LeafSet;
import rice.pastry.leafset.LeafSetProtocol;
import rice.pastry.pns.PNSApplication;
import rice.pastry.routing.RoutingTable;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketNodeHandleFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.socket.TransportLayerNodeHandle;
import rice.pastry.socket.nat.NATHandler;
import rice.pastry.standard.ConsistentJoinProtocol;
import rice.pastry.standard.PeriodicLeafSetProtocol;
import rice.pastry.standard.ProximityNeighborSelector;
import rice.pastry.transport.NodeHandleAdapter;
import rice.pastry.transport.TLPastryNode;


/**
 * This class assembles the rendezvous layer with the rendezvous app.
 * 
 * Need to think about where this best goes, but for now, we'll put it just above the magic number layer.
 * 
 * @author Jeff Hoye
 *
 */
public class RendezvousSocketPastryNodeFactory extends SocketPastryNodeFactory {
  protected RandomSource random;

  
  /**
   * The local node's contact state.  
   * 
   * TODO: Configure this
   */
  byte localContactState = RendezvousSocketNodeHandle.CONTACT_DIRECT;
  
  public RendezvousSocketPastryNodeFactory(NodeIdFactory nf, InetAddress bindAddress, int startPort, Environment env, NATHandler handler) throws IOException {
    super(nf, bindAddress, startPort, env, handler);
    init();
  }

  public RendezvousSocketPastryNodeFactory(NodeIdFactory nf, int startPort, Environment env) throws IOException {
    super(nf, startPort, env);
    init();
  }
  
  private void init() {
    random = environment.getRandomSource();
  }
    
  @Override
  protected JoinProtocol getJoinProtocol(TLPastryNode pn, LeafSet leafSet,
      RoutingTable routeTable, Object localNodeData, LeafSetProtocol lsProtocol) {
    RendezvousJoinProtocol jProtocol = new RendezvousJoinProtocol(pn,
        pn.getLocalHandle(), routeTable, leafSet, (PeriodicLeafSetProtocol)lsProtocol);
    jProtocol.register();
    return jProtocol;    
  }


  @Override
  protected TransportLayer<InetSocketAddress, ByteBuffer> getMagicNumberTransportLayer(TransportLayer<InetSocketAddress, ByteBuffer> wtl, TLPastryNode pn) {
    TransportLayer<InetSocketAddress, ByteBuffer> mtl = super.getMagicNumberTransportLayer(wtl, pn);
    
    return getRendezvousTransportLayer(mtl, pn);
  }

  @Override
  protected IdentitySerializer<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, SourceRoute<MultiInetSocketAddress>> getIdentiySerializer(TLPastryNode pn, SocketNodeHandleFactory handleFactory) {
    return new RendezvousSPNFIdentitySerializer(pn, handleFactory);
  }

  protected TransportLayer<InetSocketAddress, ByteBuffer> getRendezvousTransportLayer(TransportLayer<InetSocketAddress, ByteBuffer> mtl, TLPastryNode pn) {
    RendezvousTransportLayerImpl<InetSocketAddress, RendezvousSocketNodeHandle> ret = new RendezvousTransportLayerImpl<InetSocketAddress, RendezvousSocketNodeHandle>(
        mtl, 
        CommonAPITransportLayerImpl.DESTINATION_IDENTITY, 
        (RendezvousSocketNodeHandle)pn.getLocalHandle(), 
        getContactDeserializer(pn),
        getRendezvousGenerator(pn), 
        new PilotFinder() {        
          public RendezvousContact findPilot(RendezvousContact i) {
            return null;
          }        
        }, // todo, add this later
        getRendezvousStrategy(pn), 
        pn.getEnvironment());
    generatePilotStrategy(pn, ret);
    return ret;
  }
  
  protected void generatePilotStrategy(TLPastryNode pn, RendezvousTransportLayerImpl<InetSocketAddress, RendezvousSocketNodeHandle> rendezvousLayer) {
    // only do this if firewalled
    if (!((RendezvousSocketNodeHandle)pn.getLocalHandle()).canContactDirect())
      new LeafSetPilotStrategy<RendezvousSocketNodeHandle>(pn.getLeafSet(),rendezvousLayer, pn.getEnvironment());    
  }

  protected ContactDeserializer<InetSocketAddress, RendezvousSocketNodeHandle> getContactDeserializer(final TLPastryNode pn) {
    return new ContactDeserializer<InetSocketAddress, RendezvousSocketNodeHandle>(){
    
      public Map<String, Object> getOptions(RendezvousSocketNodeHandle high) {
//        Map<String, Object> options = new HashMap<String, Object>();
//        options.put(IdentityImpl.NODE_HANDLE_FROM_INDEX,high);
//        return options;
        return null;
      }
    
      public RendezvousSocketNodeHandle deserialize(InputBuffer sib) throws IOException {
        return (RendezvousSocketNodeHandle)pn.readNodeHandle(sib);
      }
    
      public InetSocketAddress convert(RendezvousSocketNodeHandle high) {
        // TODO: is this the correct one?
        return high.eaddress.getAddress(0);
      }

      public ByteBuffer serialize(RendezvousSocketNodeHandle i)
          throws IOException {
        SimpleOutputBuffer sob = new SimpleOutputBuffer();
        i.serialize(sob);
        return sob.getByteBuffer();
      }
    
    };
  }

  protected RendezvousGenerationStrategy<RendezvousSocketNodeHandle> getRendezvousGenerator(TLPastryNode pn) {
    // TODO Auto-generated method stub
    return null;
  }
  
  @Override
  protected ProximityNeighborSelector getProximityNeighborSelector(TLPastryNode pn) {    
    if (environment.getParameters().getBoolean("transport_use_pns")) {
      RendezvousPNSApplication pns = new RendezvousPNSApplication(pn);
      pns.register();
      return pns;
    }
  
    // do nothing
    return new ProximityNeighborSelector(){    
      public Cancellable getNearHandles(Collection<NodeHandle> bootHandles, Continuation<Collection<NodeHandle>, Exception> deliverResultToMe) {
        deliverResultToMe.receiveResult(bootHandles);
        return null;
      }    
    };
  }


  /**
   * This is an annoying hack.  We can't register the RendezvousApp until registerApps(), but we need it here.
   * 
   * This table temporarily holds the rendezvousApps until they are needed, then it is deleted.
   */
  Map<TLPastryNode, RendezvousApp> rendezvousApps = new HashMap<TLPastryNode, RendezvousApp>();
  protected RendezvousStrategy<RendezvousSocketNodeHandle> getRendezvousStrategy(TLPastryNode pn) {
    RendezvousApp app = new RendezvousApp(pn);
    rendezvousApps.put(pn,app);
    return app;
  }
  
  @Override
  protected void registerApps(TLPastryNode pn, LeafSet leafSet, RoutingTable routeTable, NodeHandleAdapter nha, NodeHandleFactory handleFactory, Object localNodeData) {
    super.registerApps(pn, leafSet, routeTable, nha, handleFactory, localNodeData);
    RendezvousApp app = rendezvousApps.remove(pn);
    app.register();
  }
  
  @Override
  public NodeHandleFactory getNodeHandleFactory(TLPastryNode pn) {
    return new RendezvousSNHFactory(pn);
  }
  
  /**
   * Used with getWireTL to make sure to return the bootstrap as not firewalled.
   */
  boolean firstNode = true;
  
  @Override
  public NodeHandle getLocalHandle(TLPastryNode pn, NodeHandleFactory nhf, Object localNodeInfo) {
    byte contactState = localContactState;    
    
    // this code is for testing
    Parameters p = environment.getParameters();    
    if (firstNode && p.getBoolean("rendezvous_test_makes_bootstrap")) {
      firstNode = false;
      // this just guards the next part
    } else if (p.getBoolean("rendezvous_test_firewall")) {
      if (random.nextFloat() <= p.getFloat("rendezvous_test_num_firewalled")) {
        contactState = RendezvousSocketNodeHandle.CONTACT_FIREWALLED;
      }
    }
    
    RendezvousSNHFactory pnhf = (RendezvousSNHFactory)nhf;
    MultiInetSocketAddress proxyAddress = (MultiInetSocketAddress)localNodeInfo;
    SocketNodeHandle ret = pnhf.getNodeHandle(proxyAddress, pn.getEnvironment().getTimeSource().currentTimeMillis(), pn.getNodeId(), contactState);
    
    // this code is for logging    
    if (contactState != localContactState && logger.level <= Logger.INFO) {
      switch(contactState) {
      case RendezvousSocketNodeHandle.CONTACT_DIRECT:
        logger.log(ret+" is not firewalled.");
        break;
      case RendezvousSocketNodeHandle.CONTACT_FIREWALLED:
        logger.log(ret+" is firewalled.");
        break;
      }
    }
    
    return ret;
  }

  /**
   * For testing, may return a FirewallTL impl for testing.
   */
  @Override
  protected TransportLayer<InetSocketAddress, ByteBuffer> getWireTransportLayer(InetSocketAddress innermostAddress, TLPastryNode pn) throws IOException {
    TransportLayer<InetSocketAddress, ByteBuffer> baseTl = super.getWireTransportLayer(innermostAddress, pn);
    RendezvousSocketNodeHandle handle = (RendezvousSocketNodeHandle)pn.getLocalHandle();
    if (!handle.canContactDirect()) {
      return new FirewallTLImpl<InetSocketAddress, ByteBuffer>(baseTl,5000,pn.getEnvironment());
    }
    
    // do the normal thing
    return baseTl; 
  }
}
