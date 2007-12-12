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
package rice.pastry.socket;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.commonapi.CommonAPITransportLayer;
import org.mpisws.p2p.transport.commonapi.CommonAPITransportLayerImpl;
import org.mpisws.p2p.transport.commonapi.IdFactory;
import org.mpisws.p2p.transport.commonapi.TransportLayerNodeHandle;
import org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.mpisws.p2p.transport.identity.IdentityImpl;
import org.mpisws.p2p.transport.identity.IdentitySerializer;
import org.mpisws.p2p.transport.identity.LowerIdentity;
import org.mpisws.p2p.transport.identity.NodeChangeStrategy;
import org.mpisws.p2p.transport.identity.SanityChecker;
import org.mpisws.p2p.transport.identity.UpperIdentity;
import org.mpisws.p2p.transport.limitsockets.LimitSocketsTransportLayer;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.liveness.LivenessTransportLayer;
import org.mpisws.p2p.transport.liveness.LivenessTransportLayerImpl;
import org.mpisws.p2p.transport.liveness.OverrideLiveness;
import org.mpisws.p2p.transport.liveness.Pinger;
import org.mpisws.p2p.transport.multiaddress.MultiInetAddressTransportLayer;
import org.mpisws.p2p.transport.multiaddress.MultiInetAddressTransportLayerImpl;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.mpisws.p2p.transport.priority.PriorityTransportLayerImpl;
import org.mpisws.p2p.transport.proximity.MinRTTProximityProvider;
import org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.mpisws.p2p.transport.sourceroute.SourceRouteTransportLayer;
import org.mpisws.p2p.transport.sourceroute.SourceRouteTransportLayerImpl;
import org.mpisws.p2p.transport.sourceroute.factory.MultiAddressSourceRouteFactory;
import org.mpisws.p2p.transport.sourceroute.manager.SourceRouteManager;
import org.mpisws.p2p.transport.sourceroute.manager.SourceRouteManagerImpl;
import org.mpisws.p2p.transport.sourceroute.manager.simple.SimpleSourceRouteStrategy;
import org.mpisws.p2p.transport.wire.WireTransportLayer;
import org.mpisws.p2p.transport.wire.WireTransportLayerImpl;
import org.mpisws.p2p.transport.wire.magicnumber.MagicNumberTransportLayer;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.CloneableLogManager;
import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.processing.Processor;
import rice.environment.processing.simple.SimpleProcessor;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.pastry.Id;
import rice.pastry.JoinFailedException;
import rice.pastry.NodeHandle;
import rice.pastry.NodeHandleFactory;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.boot.Bootstrapper;
import rice.pastry.socket.nat.NATHandler;
import rice.pastry.socket.nat.StubNATHandler;
import rice.pastry.standard.ProximityNeighborSelector;
import rice.pastry.transport.BogusNodeHandle;
import rice.pastry.transport.LeafSetNHStrategy;
import rice.pastry.transport.NodeHandleAdapter;
import rice.pastry.transport.TLDeserializer;
import rice.pastry.transport.TLPastryNode;
import rice.pastry.transport.TransportPastryNodeFactory;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;

/**
 * Pastry node factory for Socket-linked nodes.
 * 
 * @version $Id: SocketPastryNodeFactory.java,v 1.6 2004/03/08 19:53:57 amislove
 *          Exp $
 * @author Alan Mislove
 */
public class SocketPastryNodeFactory extends TransportPastryNodeFactory {
  public static final byte[] PASTRY_MAGIC_NUMBER = new byte[] {0x27, 0x40, 0x75, 0x3A};
  private int port;
  protected NodeIdFactory nidFactory;
  protected RandomSource random;


  private InetAddress localAddress;

  // the ordered list of InetAddresses, from External to internal
  InetAddress[] addressList;
  
  protected int testFireWallPolicy;

  protected int findFireWallPolicy;
  
  NATHandler natHandler;
  String firewallAppName;
  int firewallSearchTries;


  public SocketPastryNodeFactory(NodeIdFactory nf, int startPort, Environment env) throws IOException {
    this(nf, null, startPort, env, null);
  }

  public SocketPastryNodeFactory(NodeIdFactory nf, InetAddress bindAddress, int startPort, Environment env, NATHandler handler) throws IOException {
    super(env);
//    if (env.getTimeSource() instanceof DirectTimeSource) {
//      throw new IllegalArgumentException("SocketPastryNodeFactory is not compatible with the DirectTimeSource in the environment.  Please use the SimpleTimeSource or an equivalent.");
//    }
    
    environment = env;
    nidFactory = nf;
    port = startPort;
    Parameters params = env.getParameters();
    
    firewallSearchTries = params.getInt("nat_find_port_max_tries");
    firewallAppName = params.getString("nat_app_name");
    this.natHandler = handler;
    localAddress = bindAddress;
    if (localAddress == null) {
      if (params.contains("socket_bindAddress")) {
        localAddress = params.getInetAddress("socket_bindAddress");
      }
    }
    
    // user didn't specify localAddress via param or config file, ask OS
    if (localAddress == null) {
      localAddress = InetAddress.getLocalHost();
      
      Socket temp = null;
//      ServerSocket test = null;
      ServerSocket test2 = null;
//      try {
//        test = new ServerSocket();
//        test.bind(new InetSocketAddress(localAddress, port));
//      } catch (SocketException e) {
      if (localAddress.isLoopbackAddress() &&
          !params.getBoolean("pastry_socket_allow_loopback")) {
      try {
        // os gave us the loopback address, and the user doesn't want that
        
        // try the internet
        temp = new Socket(params.getString("pastry_socket_known_network_address"), 
            params.getInt("pastry_socket_known_network_address_port"));
        if (temp.getLocalAddress().equals(localAddress)) throw new IllegalStateException("Cannot bind to "+localAddress+":"+port);
        localAddress = temp.getLocalAddress();
        temp.close();
        temp = null;
        
        if (logger.level <= Logger.WARNING)
          logger.log("Error binding to default IP, using " + localAddress+":"+port);
        
        try {
          test2 = new ServerSocket();
          test2.bind(new InetSocketAddress(localAddress, port));
        } catch (SocketException e2) {
          throw new IllegalStateException("Cannot bind to "+localAddress+":"+port,e2);
        }                
      } finally {
//        try {
//          if (test != null)
//            test.close();
//        } catch (Exception e) {}
        try {
          if (test2 != null)
            test2.close();
        } catch (Exception e) {}
        try {
          if (temp != null)
            temp.close();
        } catch (Exception e) {}
      }
      }
    }

    // see if there is a firewall
    if (natHandler == null) {
      if (params.contains("nat_handler_class")) {
        try {
          Class natHandlerClass = Class.forName(params.getString("nat_handler_class"));
          Class[] args = {Environment.class, InetAddress.class};
  //        Class[] args = new Class[2];
  //        args[0] = environment.getClass();
  //        args[1] = InetAddress.class;
          Constructor constructor = natHandlerClass.getConstructor(args);
          Object[] foo = {environment, this.localAddress};
          natHandler = (NATHandler)constructor.newInstance(foo);
        } catch (ClassNotFoundException e) {
          if (logger.level <= Logger.INFO) logger.log("Didn't find UPnP libs, skipping UPnP");
          natHandler = new StubNATHandler(environment, this.localAddress);
//          natHandler = new SocketNatHandler(environment, new InetSocketAddress(localAddress,port), pAddress);
        } catch (NoClassDefFoundError e) {
          if (logger.level <= Logger.INFO) logger.log("Didn't find UPnP libs, skipping UPnP");
          natHandler = new StubNATHandler(environment, this.localAddress);
//          natHandler = new SocketNatHandler(environment, new InetSocketAddress(localAddress,port), pAddress);
        } catch (Exception e) {
          if (logger.level <= Logger.WARNING) logger.logException("Error constructing NATHandler.",e);
          throw new RuntimeException(e);
        }
      } else {
        natHandler = new StubNATHandler(environment, this.localAddress);
//      natHandler = new SBBINatHandler(environment, this.localAddress);
      }
    }    
  }

  // ********************** abstract methods **********************
  public NodeHandle getLocalHandle(TLPastryNode pn, NodeHandleFactory nhf, Object localNodeInfo) {
    SocketNodeHandleFactory pnhf = (SocketNodeHandleFactory)nhf;
    MultiInetSocketAddress proxyAddress = (MultiInetSocketAddress)localNodeInfo;
    return pnhf.getNodeHandle(proxyAddress, pn.getEnvironment().getTimeSource().currentTimeMillis(), pn.getNodeId());
  }
  
  public NodeHandleFactory getNodeHandleFactory(TLPastryNode pn) {
    return new SocketNodeHandleFactory(pn);
  }
  
  public NodeHandleAdapter getNodeHanldeAdapter(
      final TLPastryNode pn, 
      NodeHandleFactory handleFactory2, 
      TLDeserializer deserializer) throws IOException {

    Environment environment = pn.getEnvironment();
    
    SocketNodeHandle localhandle = (SocketNodeHandle)pn.getLocalHandle();
    final SocketNodeHandleFactory handleFactory = (SocketNodeHandleFactory)handleFactory2;
    
    MultiInetSocketAddress localAddress = localhandle.eaddress;
    MultiInetSocketAddress proxyAddress = localAddress;
    MultiAddressSourceRouteFactory esrFactory = getMultiAddressSourceRouteFactory(pn);

    // wire layer
    TransportLayer<InetSocketAddress, ByteBuffer> wtl = getWireTransportLayer(localAddress.getInnermostAddress(), pn);

    // magic number layer
    TransportLayer<InetSocketAddress, ByteBuffer> mntl = getMagicNumberTransportLayer(wtl,pn);

    // Limited sockets layer
    TransportLayer<InetSocketAddress, ByteBuffer> lstl = getLimitSocketsTransportLayer(mntl,pn);
       
    // MultiInet layer
    TransportLayer<MultiInetSocketAddress, ByteBuffer> etl = getMultiAddressSourceRouteFactory(lstl, pn, localAddress);

    // SourceRoute<MultiInet> layer
    TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> srl = getSourceRouteTransportLayer(etl, pn, esrFactory);
    
    // Identity (who knows how to simplify this one...)
    IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, 
        ByteBuffer, SourceRoute<MultiInetSocketAddress>> identity = getIdentityImpl(pn, handleFactory);

    // LowerIdentity
    TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> lowerIdentityLayer = getLowerIdentityLayer(srl, pn, identity);
    
    // Liveness
    TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer> ltl = getLivenessTransportLayer(lowerIdentityLayer, pn);

    // Source Route Manager
    TransLivenessProximity<MultiInetSocketAddress, ByteBuffer> srm = getSourceRouteManagerLayer(
        ltl.getTransportLayer(), ltl.getLivenessProvider(), ltl.getPinger(), pn, proxyAddress, esrFactory);
    
    // Priority
    TransportLayer<MultiInetSocketAddress, ByteBuffer> priorityTL = getPriorityTransportLayer(
        srm.getTransportLayer(), srm.getLivenessProvider(), srm.getProximityProvider(), pn);

    // UpperIdentiy
    TransLivenessProximity<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> upperIdentityLayer = getUpperIdentityLayer(
        priorityTL, pn, identity, srm.getLivenessProvider(), srm.getProximityProvider(), ltl.getOverrideLiveness());
    
    // CommonAPI
    TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> commonAPItl = getCommonAPITransportLayer(
        upperIdentityLayer.getTransportLayer(), pn, deserializer);
        
    NodeHandleAdapter nha = new NodeHandleAdapter(
        commonAPItl, 
        upperIdentityLayer.getLivenessProvider(), 
        upperIdentityLayer.getProximityProvider());

    return nha;
  }
  
//  protected class SPNFIdentitySerializer implements IdentitySerializer<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, SourceRoute<MultiInetSocketAddress>> {
//    private TLPastryNode pn;
//
//    private SocketNodeHandleFactory factory;
//    private SocketPastryNodeFactory spnFactory;
//
//    protected SPNFIdentitySerializer(TLPastryNode pn, SocketNodeHandleFactory factory, SocketPastryNodeFactory spnFactory) {
//      this.pn = pn;
//      this.factory = factory;
//      this.spnFactory = spnFactory;
//    }
//
//    public TransportLayerNodeHandle<MultiInetSocketAddress> deserialize(
//        InputBuffer buf, SourceRoute<MultiInetSocketAddress> i)
//        throws IOException {
//      long epoch = buf.readLong();
//      Id nid = Id.build(buf);
//      
//      SocketNodeHandle ret = new SocketNodeHandle(i.getLastHop(), epoch, nid, pn);
//      return (TransportLayerNodeHandle<MultiInetSocketAddress>) factory.coalesce(ret);
//    }
//
//    public void serialize(OutputBuffer buf,
//        TransportLayerNodeHandle<MultiInetSocketAddress> i)
//        throws IOException {
//      // SocketNodeHandle handle = (SocketNodeHandle)i;
//      // i.getAddress()
//      long epoch = i.getEpoch();
//      Id nid = (rice.pastry.Id) i.getId();
//      // logger.log("serialize("+i+") epoch:"+i.getEpoch()+" nid:"+nid);
//      buf.writeLong(epoch);
//      nid.serialize(buf);
//    }
//
//    public MultiInetSocketAddress translateDown(
//        TransportLayerNodeHandle<MultiInetSocketAddress> i) {
//      return i.getAddress();
//    }
//
//    public MultiInetSocketAddress translateUp(SourceRoute<MultiInetSocketAddress> i) {
//      return i.getLastHop();
//    }
//  }

  protected interface TransLiveness<Identifier, MessageType> {
    TransportLayer<Identifier, MessageType> getTransportLayer();
    LivenessProvider<Identifier> getLivenessProvider();
    OverrideLiveness<Identifier> getOverrideLiveness();
    Pinger<Identifier> getPinger();
  }

  protected interface TransLivenessProximity<Identifier, MessageType> {
    TransportLayer<Identifier, ByteBuffer> getTransportLayer(); 
    LivenessProvider<Identifier> getLivenessProvider();
    ProximityProvider<Identifier> getProximityProvider();
  }
  
  protected MultiAddressSourceRouteFactory getMultiAddressSourceRouteFactory(TLPastryNode pn) {
    return new MultiAddressSourceRouteFactory();
  }
  
  protected TransportLayer<InetSocketAddress, ByteBuffer> getWireTransportLayer(InetSocketAddress innermostAddress, TLPastryNode pn) throws IOException {
    Environment environment = pn.getEnvironment();
    WireTransportLayer wtl = new WireTransportLayerImpl(innermostAddress,environment, null);    
    return wtl;
  }

  protected TransportLayer<InetSocketAddress, ByteBuffer> getMagicNumberTransportLayer(TransportLayer<InetSocketAddress, ByteBuffer> wtl, TLPastryNode pn) {
    Environment environment = pn.getEnvironment();
    MagicNumberTransportLayer<InetSocketAddress> mntl = 
      new MagicNumberTransportLayer<InetSocketAddress>(wtl,environment,null,PASTRY_MAGIC_NUMBER, 5000);
    return mntl;
  }

  protected TransportLayer<InetSocketAddress, ByteBuffer> getLimitSocketsTransportLayer(
      TransportLayer<InetSocketAddress, ByteBuffer> mntl, TLPastryNode pn) {
    Environment environment = pn.getEnvironment();
    LimitSocketsTransportLayer<InetSocketAddress, ByteBuffer> lstl = 
      new LimitSocketsTransportLayer<InetSocketAddress, ByteBuffer>(environment.getParameters().getInt("pastry_socket_scm_max_open_sockets"),mntl,environment);
    return lstl;
  }
  

  protected TransportLayer<MultiInetSocketAddress, ByteBuffer> getMultiAddressSourceRouteFactory(
      TransportLayer<InetSocketAddress, ByteBuffer> mntl, 
      TLPastryNode pn, 
      MultiInetSocketAddress localAddress) {
    return new MultiInetAddressTransportLayerImpl(localAddress, mntl, pn.getEnvironment(), null, null);
  }

  protected TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> getSourceRouteTransportLayer(
      TransportLayer<MultiInetSocketAddress, ByteBuffer> etl, 
      TLPastryNode pn, 
      MultiAddressSourceRouteFactory esrFactory) {
    Environment environment = pn.getEnvironment();
    SourceRouteTransportLayer<MultiInetSocketAddress> srl = 
      new SourceRouteTransportLayerImpl<MultiInetSocketAddress>(esrFactory,etl,environment, null);
    return srl;
  }

  protected IdentitySerializer<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, SourceRoute<MultiInetSocketAddress>> getIdentiySerializer(TLPastryNode pn, SocketNodeHandleFactory handleFactory) {
    return new SPNFIdentitySerializer(pn, handleFactory);
  }

  protected IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, 
                       MultiInetSocketAddress, ByteBuffer, 
                       SourceRoute<MultiInetSocketAddress>> 
      getIdentityImpl(final TLPastryNode pn, final SocketNodeHandleFactory handleFactory) throws IOException {
    Environment environment = pn.getEnvironment();
    SocketNodeHandle localhandle = (SocketNodeHandle)pn.getLocalHandle();
    
    IdentitySerializer<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, SourceRoute<MultiInetSocketAddress>> serializer = 
      getIdentiySerializer(pn, handleFactory);

    SimpleOutputBuffer buf = new SimpleOutputBuffer();
    serializer.serialize(buf, localhandle);
    byte[] localHandleBytes = new byte[buf.getWritten()];
    System.arraycopy(buf.getBytes(), 0, localHandleBytes, 0, localHandleBytes.length);
    
    IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, 
        ByteBuffer, SourceRoute<MultiInetSocketAddress>> identity = 
      new IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, 
            ByteBuffer, SourceRoute<MultiInetSocketAddress>>(
          localHandleBytes, serializer, 
          new NodeChangeStrategy<TransportLayerNodeHandle<MultiInetSocketAddress>>(){
            public boolean canChange(
                TransportLayerNodeHandle<MultiInetSocketAddress> oldDest, 
                TransportLayerNodeHandle<MultiInetSocketAddress> newDest) {
//              if (false) logger.log("");
//              if (logger.level <= Logger.FINE) logger.log("canChange("+oldDest+","+newDest+","+i+")");
              if (newDest.getAddress().equals(oldDest.getAddress())) {
                if (logger.level <= Logger.INFO) logger.log("canChange("+oldDest+","+newDest+")");
                if (newDest.getEpoch() > oldDest.getEpoch()) {
                  if (logger.level <= Logger.INFO) logger.log("canChange("+oldDest+":"+oldDest.getEpoch()+","+newDest+":"+newDest.getEpoch()+"):true");
                  return true;
                }
              } else {
                throw new RuntimeException("canChange("+oldDest+","+newDest+") doesn't make any sense, these aren't comparable to eachother.");
              }
              if (logger.level <= Logger.INFO) logger.log("canChange("+oldDest+":"+oldDest.getEpoch()+","+newDest+":"+newDest.getEpoch()+"):false");
              return false;
              
//            if (false) logger.log("");
//            if (logger.level <= Logger.FINE) logger.log("canChange("+oldDest+","+newDest+","+i+")");
//            return ((newDest.getAddress().equals(i.getLastHop()) && (newDest.getEpoch() > oldDest.getEpoch());
            }          
          }, 
          new SanityChecker<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress>() {
          
            public boolean isSane(TransportLayerNodeHandle<MultiInetSocketAddress> upper,
                MultiInetSocketAddress middle) {
              return upper.getAddress().equals(middle);
            }
          
          },environment);
    return identity;
  }

  protected TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> getLowerIdentityLayer(
      TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> srl, 
      TLPastryNode pn, 
      IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, 
                   MultiInetSocketAddress, 
                   ByteBuffer, 
                   SourceRoute<MultiInetSocketAddress>> identity) {
    
    identity.initLowerLayer(srl, null);
    LowerIdentity<SourceRoute<MultiInetSocketAddress>, ByteBuffer> lowerIdentityLayer = identity.getLowerIdentity();
    return lowerIdentityLayer;
  }

  protected TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer> getLivenessTransportLayer(
      TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> tl, 
      TLPastryNode pn) {
    Environment environment = pn.getEnvironment();
    int checkDeadThrottle = environment.getParameters().getInt("pastry_socket_srm_check_dead_throttle"); // 300000

    final LivenessTransportLayerImpl<SourceRoute<MultiInetSocketAddress>> ltl = 
      new LivenessTransportLayerImpl<SourceRoute<MultiInetSocketAddress>>(tl,environment, null, checkDeadThrottle);

    return new TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer>(){    
        public TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> getTransportLayer() {
          return ltl;
        }
        public LivenessProvider<SourceRoute<MultiInetSocketAddress>> getLivenessProvider() {
          return ltl;
        }
        public OverrideLiveness<SourceRoute<MultiInetSocketAddress>> getOverrideLiveness() {
          return ltl;
        }
        public Pinger<SourceRoute<MultiInetSocketAddress>> getPinger() {
          return ltl;
        }    
    };
  }
  
  protected TransLivenessProximity<MultiInetSocketAddress, ByteBuffer> getSourceRouteManagerLayer(
      TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> ltl, 
      LivenessProvider<SourceRoute<MultiInetSocketAddress>> livenessProvider, 
      Pinger<SourceRoute<MultiInetSocketAddress>> pinger, 
      TLPastryNode pn, 
      MultiInetSocketAddress proxyAddress, 
      MultiAddressSourceRouteFactory esrFactory) throws IOException {
    Environment environment = pn.getEnvironment();
    LeafSetNHStrategy nhStrategy = new LeafSetNHStrategy();
    nhStrategy.setLeafSet(pn.getLeafSet());

    SimpleSourceRouteStrategy<MultiInetSocketAddress> srStrategy = 
      new SimpleSourceRouteStrategy<MultiInetSocketAddress>(proxyAddress,esrFactory,nhStrategy,environment);
//    TransportLayer<EpochInetSocketAddress, ByteBuffer> srm = 
    MinRTTProximityProvider<SourceRoute<MultiInetSocketAddress>> prox = 
      new MinRTTProximityProvider<SourceRoute<MultiInetSocketAddress>>(pinger, environment);
    final SourceRouteManager<MultiInetSocketAddress> srm = 
      new SourceRouteManagerImpl<MultiInetSocketAddress>(esrFactory,ltl,livenessProvider,prox,environment,srStrategy);
    return new TransLivenessProximity<MultiInetSocketAddress, ByteBuffer>(){    
        public TransportLayer<MultiInetSocketAddress, ByteBuffer> getTransportLayer() {
          return srm;
        }    
        public ProximityProvider<MultiInetSocketAddress> getProximityProvider() {
          return srm;
        }    
        public LivenessProvider<MultiInetSocketAddress> getLivenessProvider() {
          return srm;
        }    
    };
  }

  protected TransportLayer<MultiInetSocketAddress, ByteBuffer> getPriorityTransportLayer(TransportLayer<MultiInetSocketAddress, ByteBuffer> trans, LivenessProvider<MultiInetSocketAddress> liveness, ProximityProvider<MultiInetSocketAddress> prox, TLPastryNode pn) {
    Environment environment = pn.getEnvironment();
    PriorityTransportLayer<MultiInetSocketAddress> priorityTL = 
      new PriorityTransportLayerImpl<MultiInetSocketAddress>(
          trans,
          liveness,
          prox,
          environment,
          environment.getParameters().getInt("pastry_socket_writer_max_msg_size"),
          environment.getParameters().getInt("pastry_socket_writer_max_queue_length"),
          null);
    return priorityTL;
  }

  protected TransLivenessProximity<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> getUpperIdentityLayer(
//  protected UpperIdentity<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> getUpperIdentityLayer(
      TransportLayer<MultiInetSocketAddress, ByteBuffer> priorityTL, 
      TLPastryNode pn, 
      IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, 
                   MultiInetSocketAddress, 
                   ByteBuffer, 
                   SourceRoute<MultiInetSocketAddress>> identity, 
      LivenessProvider<MultiInetSocketAddress> live,
      ProximityProvider<MultiInetSocketAddress> prox,
      OverrideLiveness<SourceRoute<MultiInetSocketAddress>> overrideLiveness) {
    
    SocketNodeHandle localhandle = (SocketNodeHandle)pn.getLocalHandle();
    identity.initUpperLayer(localhandle, priorityTL, live, prox, overrideLiveness);    
    final UpperIdentity<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> upperIdentityLayer = identity.getUpperIdentity();
    return new TransLivenessProximity<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer>(){    
        public TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> getTransportLayer() {
          return upperIdentityLayer;
        }    
        public ProximityProvider<TransportLayerNodeHandle<MultiInetSocketAddress>> getProximityProvider() {
          return upperIdentityLayer;
        }    
        public LivenessProvider<TransportLayerNodeHandle<MultiInetSocketAddress>> getLivenessProvider() {
          return upperIdentityLayer;
        }    
    };
  }

  protected TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> getCommonAPITransportLayer(
      TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> upperIdentity, 
      TLPastryNode pn, TLDeserializer deserializer) {
    final Environment environment = pn.getEnvironment();
    IdFactory idFactory = new IdFactory(){    
      public rice.p2p.commonapi.Id build(InputBuffer buf) throws IOException {
        return Id.build(buf);
      }    
    };
    

    CommonAPITransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>> commonAPItl = 
    new CommonAPITransportLayerImpl<TransportLayerNodeHandle<MultiInetSocketAddress>>(
        upperIdentity, 
        idFactory, 
        deserializer,
        new ErrorHandler<TransportLayerNodeHandle<MultiInetSocketAddress>>() {          
          Logger logger = environment.getLogManager().getLogger(SocketPastryNodeFactory.class, null);
          public void receivedUnexpectedData(
              TransportLayerNodeHandle<MultiInetSocketAddress> id, byte[] bytes,
              int location, Map<String, Object> options) {
            if (logger.level <= Logger.WARNING) {
              // make this pretty
              String s = "";
              int numBytes = 8;
              if (bytes.length < numBytes) numBytes = bytes.length;
              for (int i = 0; i < numBytes; i++) {
                s+=bytes[i]+","; 
              }
              logger.log("Unexpected data from "+id+" "+s);
            }
          }
        
          public void receivedException(
              TransportLayerNodeHandle<MultiInetSocketAddress> i, Throwable error) {
            if (logger.level <= Logger.INFO) {
              if (error instanceof NodeIsFaultyException) {                  
                NodeIsFaultyException nife = (NodeIsFaultyException)error;
                logger.log("Dropping message "+nife.getAttemptedMessage()+" to "+nife.getIdentifier()+" because it is faulty.");
                if (i.isAlive()) {
                  NodeHandle<MultiInetSocketAddress> nh = (NodeHandle<MultiInetSocketAddress>)i;
                  logger.logException("NodeIsFaultyException thrown for non-dead node. "+i+" "+nh.getLiveness(),nife);
                }
              }
            }
          }          
        },
        environment); 
    return commonAPItl;
  }

  protected Bootstrapper getBootstrapper(TLPastryNode pn, 
      NodeHandleAdapter tl, 
      NodeHandleFactory handleFactory,
      ProximityNeighborSelector pns, Object localNodeData) {

    TLBootstrapper bootstrapper = new TLBootstrapper(pn, tl.getTL(), (SocketNodeHandleFactory)handleFactory, pns, localNodeData);
    return bootstrapper;
  }

  class TLBootstrapper implements Bootstrapper<InetSocketAddress>
  {
    InetSocketAddress localAddr;
    TLPastryNode pn;
    TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> tl;
    SocketNodeHandleFactory handleFactory;
    ProximityNeighborSelector pns;
    Logger logger;
    
    // we need to store this in a mutable final object
    final List<LivenessListener<NodeHandle>> listener = new ArrayList<LivenessListener<NodeHandle>>();
    
    public TLBootstrapper(TLPastryNode pn, 
        TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> tl, 
        SocketNodeHandleFactory handleFactory,
        ProximityNeighborSelector pns,
        Object localAddr) {
      this.logger = pn.getEnvironment().getLogManager().getLogger(TLBootstrapper.class, null);
      this.pn = pn;
      this.tl = tl;
      this.handleFactory = handleFactory;
      this.pns = pns;
      this.localAddr = ((MultiInetSocketAddress)localAddr).getInnermostAddress();
    }

    /**
     * This method is a bit out of order to make it work on any thread.  The method itself is non-blocking.  
     * 
     * The problem is that we don't have any NodeHandles yet, only Ip addresses.  So, we're going to check liveness on a 
     * bogus node handle, and when the WrongEpochAddress message comes back, the bogus node will be faulty, but a new
     * node (the real NodeHandle at that address) will come alive.  We can discover this by installing a LivenessListener
     * on the transport layer.
     * 
     * Here is what happens:
     * Create the "bogus" NodeHandles and store them in tempBootHandles 
     * Register LivenessListener on transport layer.
     * Ping all of the "bogus" NodeHandles
     * When liveness changes to Non-Faulty it means we have found a new node.
     * When we collect all of the bootaddresses, or there is a timeout, we call continuation.receiveResult()
     * Continuation.receiveResult() unregisters the LivenessListener and then calls ProximityNeighborSelection.nearNodes()
     * PNS.nearNodes() calls into the continuation which calls PastryNode.doneNode() 
     */
    public void boot(Collection<InetSocketAddress> bootaddresses_temp) {
      if (logger.level <= Logger.FINE) logger.log("boot("+bootaddresses_temp+")");
      final Collection<InetSocketAddress> bootaddresses;
      if (bootaddresses_temp == null) {
        bootaddresses = Collections.EMPTY_LIST;
      } else {
        bootaddresses = bootaddresses_temp;
      }
      
      final boolean seed = environment.getParameters().getBoolean("rice_socket_seed") || bootaddresses.isEmpty() || bootaddresses.contains(localAddr);
      
      if (bootaddresses.isEmpty() ||
          (bootaddresses.size() == 1 && seed)) {
        if (logger.level <= Logger.INFO) logger.log("boot() calling pn.doneNode(empty)");
        pn.doneNode(Collections.EMPTY_LIST); 
        return;
      }
      
      // bogus handles
      final Collection<SocketNodeHandle> tempBootHandles = new ArrayList<SocketNodeHandle>(bootaddresses.size());
      
      // real handles
      final Collection<rice.pastry.NodeHandle> bootHandles = 
        new HashSet<rice.pastry.NodeHandle>();
      
      TransportLayerNodeHandle<MultiInetSocketAddress> local = tl.getLocalIdentifier();
      InetSocketAddress localAddr = local.getAddress().getInnermostAddress();
      
      // fill in tempBootHandles
      for (InetSocketAddress addr : bootaddresses) { 
        if (logger.level <= Logger.FINER) logger.log("addr:"+addr+" local:"+localAddr);
        if (!addr.equals(localAddr)) {
          tempBootHandles.add(handleFactory.getNodeHandle(new MultiInetSocketAddress(addr), -1, Id.build()));
        }
      }

      // this is the end of the task, but we have to declare it here
      final Continuation beginPns = new Continuation<Collection<NodeHandle>, Exception>(){
        boolean done = false; // to make sure this is only called once
        /**
         * This is usually going to get called twice.  The first time when bootHandles is complete,
         * the second time on a timeout.
         * 
         * @param result
         */
        public void receiveResult(Collection<NodeHandle> initialSet) {
          // make sure this only gets called once
          if (done) return;
          done = true;
          if (logger.level <= Logger.FINE) logger.log("boot() beginning pns with "+initialSet);
          
          // remove the listener
          pn.getLivenessProvider().removeLivenessListener(listener.get(0));
          
          // do proximity neighbor selection
          pns.getNearHandles(initialSet, new Continuation<Collection<NodeHandle>, Exception>(){
          
            public void receiveResult(Collection<NodeHandle> result) {
              // done!!!
              if (!seed && result.isEmpty()) {
                pn.joinFailed(new JoinFailedException("Cannot join ring.  All bootstraps are faulty."+bootaddresses));
                return;
              }
              if (logger.level <= Logger.INFO) logger.log("boot() calling pn.doneNode("+result+")");
              pn.doneNode(result);
            }
          
            public void receiveException(Exception exception) {
              // TODO Auto-generated method stub          
            }
          
          });
        }
      
        public void receiveException(Exception exception) {
          // TODO Auto-generated method stub          
        }      
      };


      // Create the listener for the "real" nodes coming online based on WrongAddress messages from the "Bogus" ones
      listener.add( 
        new LivenessListener<NodeHandle>() {
          Logger logger = pn.getEnvironment().getLogManager().getLogger(SocketPastryNodeFactory.class, null);
          public void livenessChanged(NodeHandle i2, int val, Map<String, Object> options) {
            SocketNodeHandle i = (SocketNodeHandle)i2;
//            logger.logException("livenessChanged("+i+","+val+")", new Exception("Stack Trace"));
            if (logger.level <= Logger.FINE) logger.log("livenessChanged("+i+","+val+")");
//            System.out.println("here");
            if (val <= LIVENESS_SUSPECTED && i.getEpoch() != -1L) {
              boolean complete = false;
              
              // add the new handle
              synchronized(bootHandles) {
                bootHandles.add((SocketNodeHandle)i);
                if (bootHandles.size() == tempBootHandles.size()) {
                  complete = true;
                }
              }
              if (complete) {
                beginPns.receiveResult(bootHandles);
              }
            }
          }        
        });

      if (logger.level <= Logger.FINE) logger.log("boot() adding liveness listener");
      // register the listener
      pn.getLivenessProvider().addLivenessListener(listener.get(0));

      if (logger.level <= Logger.FINE) logger.log("boot() checking liveness");
      // check liveness on the bogus nodes
      for (SocketNodeHandle h : tempBootHandles) {
        pn.getLivenessProvider().checkLiveness(h, null);
      }

      // need to synchronize, because this can be called on any thread
      synchronized(bootHandles) {
        if (bootHandles.size() < tempBootHandles.size()) {          
          // only wait 10 seconds for the nodes
          environment.getSelectorManager().schedule(new TimerTask(){
          
            @Override
            public void run() {
              if (logger.level <= Logger.FINE) logger.log("boot() timer expiring, attempting to start pns (it may have already started)");

              beginPns.receiveResult(bootHandles);
            }            
          }, 20000);
        }
      }

      // the root node (no boot addresses)
      if (tempBootHandles.isEmpty()) {
        if (logger.level <= Logger.FINE) logger.log("invoking receiveResult (this is probably the first node in the ring)");
        environment.getSelectorManager().invoke(new Runnable(){          
          public void run() {
            beginPns.receiveResult(bootHandles);
          }          
        });
      }
      
      if (logger.level <= Logger.FINE) logger.log("boot() returning");
      
//      // use the WrongEpochMessage to fetch the identity
//      logger.log("boot");
//      first = bootaddresses.iterator().next();
//      tl.addLivenessListener(this);
//      logger.log("boot:"+first);
//      tl.checkLiveness((TLNodeHandle)handleFactory.getNodeHandle(new EpochInetSocketAddress(first), Id.build()));
    }

//    public void livenessChanged(TransportLayerNodeHandle<EpochInetSocketAddress> i, int val) {
//      if (i.getAddress().equals(first)) {
//        if (val != LivenessTransportLayer.LIVENESS_DEAD_FOREVER) {
//          logger.log("Should not happen: livenessChanged("+i+","+val+")");
//          return;
//        }
//        // now we have the NodeHandle
//        
//        
//        tl.removeLivenessListener(this);
//      }
//    }    
  }
  
  public NodeHandle getNodeHandle(InetSocketAddress bootstrap, int i) {
    return getNodeHandle(bootstrap);
  }

  public NodeHandle getNodeHandle(InetSocketAddress bootstrap) {
    return new BogusNodeHandle(bootstrap);
  }

  public void getNodeHandle(InetSocketAddress[] bootstraps, Continuation c) {
    c.receiveResult(getNodeHandle(bootstraps, 0));
  }

  public NodeHandle getNodeHandle(InetSocketAddress[] bootstraps, int int1) {
    return new BogusNodeHandle(bootstraps);
  }


  /**
   * Method which creates a Pastry node from the next port with a randomly
   * generated NodeId.
   * 
   * @param bootstrap Node handle to bootstrap from.
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(NodeHandle bootstrap) {
    return newNode(bootstrap, nidFactory.generateNodeId());
  }

  /**
   * Method which creates a Pastry node from the next port with the specified nodeId 
   * (or one generated from the NodeIdFactory if not specified)
   * 
   * @param bootstrap Node handle to bootstrap from.
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(NodeHandle bootstrap, InetSocketAddress proxy) {
    return newNode(bootstrap, nidFactory.generateNodeId(), proxy);
  }
  
  /**
   * Method which creates a Pastry node from the next port with the specified nodeId 
   * (or one generated from the NodeIdFactory if not specified)
   * 
   * @param bootstrap Node handle to bootstrap from.
   * @param nodeId if non-null, will use this nodeId for the node, rather than using the NodeIdFactory
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(final NodeHandle bootstrap, Id nodeId) {
    return newNode(bootstrap, nodeId, null);
  }
  
  /**
   * Need to boot manually.
   * 
   * n.getBootstrapper().boot(addresses);
   * 
   * @return
   */
  public PastryNode newNode() {
    return newNode(nidFactory.generateNodeId(), null);
  }
  
  /**
   * Method which creates a Pastry node from the next port with the specified nodeId 
   * (or one generated from the NodeIdFactory if not specified)
   * 
   * @param bootstrap Node handle to bootstrap from.
   * @param nodeId if non-null, will use this nodeId for the node, rather than using the NodeIdFactory
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(NodeHandle nodeHandle, Id id, InetSocketAddress proxyAddress) {
    PastryNode n = newNode(id, proxyAddress);
    if (nodeHandle == null) {
      n.getBootstrapper().boot(null); 
    } else {
      BogusNodeHandle bnh = (BogusNodeHandle)nodeHandle;
      n.getBootstrapper().boot(bnh.addresses);
    }
    return n;
  }

  /**
   * Method which creates a Pastry node from the next port with the specified nodeId 
   * (or one generated from the NodeIdFactory if not specified)
   * 
   * @param bootstrap Node handle to bootstrap from.
   * @param nodeId if non-null, will use this nodeId for the node, rather than using the NodeIdFactory
   * @param pAddress The address to claim that this node is at - used for proxies
   *          behind NATs
   * @return A node with a random ID and next port number.
   */
  public synchronized PastryNode newNode(Id nodeId,
      InetSocketAddress pAddress) {
    try {
      return newNode(nodeId, pAddress, true); // fix the method just
                                                          // below if you change
                                                          // this
    } catch (BindException e) {

      if (logger.level <= Logger.WARNING)
        logger.log("Warning: " + e);

      if (environment.getParameters().getBoolean(
          "pastry_socket_increment_port_after_construction")) {
        port++;
        try {
          return newNode(nodeId, pAddress); // recursion, this will
                                                        // prevent from things
                                                        // getting too out of
                                                        // hand in
          // case the node can't bind to anything, expect a
          // StackOverflowException
        } catch (StackOverflowError soe) {
          if (logger.level <= Logger.SEVERE)
            logger
                .log("SEVERE: SocketPastryNodeFactory: Could not bind on any ports!"
                    + soe);
          throw soe;
        }
      } else {
        
        // clean up Environment
        if (this.environment.getParameters().getBoolean(
            "pastry_factory_multipleNodes")) {
          environment.destroy();
        }
        
        throw new RuntimeException(e);
      }
    } catch (IOException ioe) {
      
      throw new RuntimeException(ioe);      
    }
  }

  protected synchronized PastryNode newNode(Id nodeId,
      InetSocketAddress pAddress, boolean throwException) throws IOException {
    if (!throwException)
      return newNode(nodeId, pAddress); // yes, this is sort of
                                                    // bizarre
    // the idea is that we can't throw an exception by default because it will
    // break reverse compatibility
    // so this method gets called twice if throwException is false. But the
    // second time,
    // it will be called with true, but will be
    // wrapped with the above function which will catch the exception.
    // -Jeff May 12, 2006
//    if (bootstrap == null)
//      if (logger.level <= Logger.WARNING)
//        logger
//            .log("No bootstrap node provided, starting a new ring binding to address "
//                + localAddress + ":" + port + "...");

    // this code builds a different environment for each PastryNode
    Environment environment = cloneEnvironment(this.environment, nodeId);
    
    Parameters params = environment.getParameters();
//    System.out.println(environment.getLogManager());

    MultiInetSocketAddress localAddress = null;
    MultiInetSocketAddress proxyAddress = null;
    localAddress = getEpochAddress(port);
    proxyAddress = localAddress;
    
    if (environment.getParameters().getBoolean("pastry_socket_increment_port_after_construction"))
      port++; // this statement must go after the construction of srManager
              // because the

    TLPastryNode pn = nodeHandleHelper(nodeId, environment, proxyAddress);
        
    return pn;
  }
  
  protected Environment cloneEnvironment(Environment rootEnvironment, Id nodeId) {
    Environment ret = rootEnvironment;
    if (rootEnvironment.getParameters().getBoolean("pastry_factory_multipleNodes")) {
  
      // new logManager
      LogManager lman = cloneLogManager(rootEnvironment, nodeId);
      
      // new selector
      SelectorManager sman = cloneSelectorManager(rootEnvironment, nodeId, lman);
      
      // new processor
      Processor proc = cloneProcessor(rootEnvironment, nodeId, lman);
      
      // new random source
      RandomSource rand = cloneRandomSource(rootEnvironment, nodeId, lman);
      
      // build the environment
      ret = new Environment(sman, proc, rand, rootEnvironment.getTimeSource(), lman,
          rootEnvironment.getParameters(), rootEnvironment.getExceptionStrategy());
    
      // gain shared fate with the rootEnvironment
      rootEnvironment.addDestructable(ret);     
    }
    return ret;
  }

  protected LogManager cloneLogManager(Environment rootEnvironment, Id nodeId) {
    LogManager lman = rootEnvironment.getLogManager();
    if (lman instanceof CloneableLogManager) {
      lman = ((CloneableLogManager) rootEnvironment
          .getLogManager()).clone("0x" + nodeId.toStringBare());
    }
    return lman;
  }
  

  protected SelectorManager cloneSelectorManager(Environment rootEnvironment, Id nodeId, LogManager lman) {
    SelectorManager sman = rootEnvironment.getSelectorManager();
    if (rootEnvironment.getParameters().getBoolean("pastry_factory_selectorPerNode")) {
      sman = new SelectorManager(nodeId.toString() + " Selector",
          rootEnvironment.getTimeSource(), lman);
    }
    return sman;
  }
  
  protected Processor cloneProcessor(Environment rootEnvironment, Id nodeId, LogManager lman) {
    Processor proc = rootEnvironment.getProcessor();
    if (rootEnvironment.getParameters().getBoolean("pastry_factory_processorPerNode")) {
      proc = new SimpleProcessor(nodeId.toString() + " Processor");
    }

    return proc;
  }
  
  protected RandomSource cloneRandomSource(Environment rootEnvironment, Id nodeId, LogManager lman) {
    long randSeed = rootEnvironment.getRandomSource().nextLong();
    return new SimpleRandomSource(randSeed, lman);    
  }
  
  /**
   * Method which constructs an InetSocketAddres for the local host with the
   * specifed port number.
   * 
   * @param portNumber The port number to create the address at.
   * @return An InetSocketAddress at the localhost with port portNumber.
   */
  private MultiInetSocketAddress getEpochAddress(int portNumber) {
    MultiInetSocketAddress result = null;

    result = new MultiInetSocketAddress(new InetSocketAddress(localAddress,
        portNumber));
    return result;
  }


  // *************** Delme **************
  public static InetSocketAddress verifyConnection(int i, InetSocketAddress addr, InetSocketAddress[] addr2, Environment env, Logger l) {
    return null;
  }  
}
