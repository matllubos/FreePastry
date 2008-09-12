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
/*
 * Created on Apr 6, 2005
 */
package rice.testing.routeconsistent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactoryImpl;
import org.mpisws.p2p.transport.peerreview.history.stub.NullHashProvider;
import org.mpisws.p2p.transport.peerreview.replay.EventCallback;
import org.mpisws.p2p.transport.peerreview.replay.playback.ReplayLayer;
import org.mpisws.p2p.transport.peerreview.replay.playback.ReplaySM;
import org.mpisws.p2p.transport.peerreview.replay.record.RecordLayer;
import org.mpisws.p2p.transport.rendezvous.IncomingPilotListener;
import org.mpisws.p2p.transport.rendezvous.OutgoingPilotListener;
import org.mpisws.p2p.transport.rendezvous.PilotManager;
import org.mpisws.p2p.transport.rendezvous.RendezvousTransportLayer;
import org.mpisws.p2p.transport.simpleidentity.InetSocketAddressSerializer;
import org.mpisws.p2p.transport.util.Serializer;

import rice.Destructable;
import rice.environment.Environment;
import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.p2p.splitstream.ChannelId;
import rice.p2p.splitstream.testing.MySplitStreamClient;
import rice.pastry.Id;
import rice.pastry.NetworkListener;
import rice.pastry.NodeHandle;
import rice.pastry.NodeHandleFactory;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.leafset.LeafSet;
import rice.pastry.routing.RouteMessage;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.socket.TransportLayerNodeHandle;
import rice.pastry.socket.nat.rendezvous.RendezvousSocketNodeHandle;
import rice.pastry.socket.nat.rendezvous.RendezvousSocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.selector.LoopObserver;

/**
 * @author Jeff Hoye
 */
public class ConsistencyPLTest implements Observer, LoopObserver, MyEvents {
  public static final boolean USE_REPLAY = false;
  
  static void setupParams(Parameters params) {
    params.setBoolean("logging_packageOnly",false);
    params.setInt("loglevel", Logger.INFO);
    
//    params.setInt("rice.environment.time.simulated_loglevel", Logger.WARNING);
    
    params.setInt("rice.pastry_loglevel", Logger.INFO);
    // ******** we overrode SPNF make sure to also override routeconsistent *******
//    params.setInt("rice.pastry.socket.SocketPastryNodeFactory_loglevel",Logger.INFO);
//    params.setInt("rice.testing.routeconsistent_loglevel", Logger.INFO);
    params.setInt("org.mpisws.p2p.transport.priority_loglevel", Logger.INFO-1);
   
//    params.setInt("org.mpisws.p2p.transport.sourceroute.manager_loglevel", Logger.ALL);
//    params.setInt("org.mpisws.p2p.transport.wire.UDPLayer_loglevel", Logger.ALL);
      params.setInt("org.mpisws.p2p.transport.wire.TCPLayer_loglevel", Logger.FINER);
//    params.setInt("rice.pastry.transport_loglevel", Logger.CONFIG);
//    params.setInt("rice.pastry.transport.PastryNode_loglevel", Logger.FINE);
//    params.setInt("org.mpisws.p2p.transport.proximity_loglevel", Logger.ALL);
//    params.setInt("org.mpisws.p2p.transport_loglevel", Logger.INFO);
//    params.setInt("org.mpisws.p2p.transport.liveness_loglevel", Logger.FINE);
    params.setInt("org.mpisws.p2p.transport.rendezvous_loglevel", Logger.FINEST);
    params.setInt("rice.pastry.socket.nat.rendezvous.RendezvousApp_loglevel", Logger.FINEST);
//    params.setInt("org.mpisws.p2p.transport.limitsockets_loglevel", Logger.FINER);
//    params.setInt("org.mpisws.p2p.transport.identity_loglevel", Logger.INFO);
//    params.setInt("org.mpisws.p2p.transport.priority_loglevel", Logger.FINEST);
    
//    params.setInt("rice.pastry.standard.RapidRerouter_loglevel", Logger.INFO);    
//    params.setInt("rice.pastry.pns.PNSApplication_loglevel", Logger.INFO);
    
    // turn on consistent join protocol's logger to make sure this is correct for consistency
//    params.setInt("rice.pastry.standard.ConsistentJoinProtocol_loglevel",Logger.INFO);
    params.setInt("rice.pastry.standard.PeriodicLeafSetProtocol_loglevel",Logger.FINE);
    // to see the JoinRequests
//    params.setInt("rice.pastry.socket.nat.rendezvous.RendezvousJoinProtocol_loglevel", Logger.CONFIG);

    params.setInt("rice.pastry.socket.nat.rendezvous_loglevel", Logger.FINE);
    // to see rapid rerouting and dropping from consistency if gave lease
//    params.setInt("rice.pastry.standard.StandardRouter_loglevel",Logger.INFO);
    
    
//    params.setInt("pastry_socket_scm_socket_buffer_size", 131072); // see if things improve with big buffer, small queue
//    params.setInt("pastry_socket_writer_max_queue_length", 3); // see if things improve with big buffer, small queue
    
//    params.setInt("rice.pastry.socket.SocketNodeHandle_loglevel",Logger.ALL);
    }
  
  public static final int startPort = 21854;
  public static final int WAIT_TO_SUBSCRIBE_DELAY = 60000;
  
  public static boolean useScribe = false;
  public static boolean useSplitStream = false;
  public static String INSTANCE = "ConsPLSplitStreamTest";

//  public static String BOOTNODE = "139.19.64.189"; // wired
//  public static String BOOTNODE = "139.19.135.114"; // wireless
  public static String BOOTNODE = "planetlab01.mpi-sws.mpg.de";
  public static String ALT_BOOTNODE = "planetlab02.mpi-sws.mpg.de";
  public static final int BASE_DELAY = 30000;
  public static final int RND_DELAY = 500000;
//  public static final int RND_DELAY = 1;
  
  
  public static boolean artificialChurn = false;
  
  //the object is just to implement the destruction policy.
  PastryNode localNode;
  LeafSet leafSet;

  static boolean isJanus = false;
  
  /**
   * Of InetSocketAddress
   */
//  static Set<InetSocketAddress> bootAddresses = Collections.synchronizedSet(new HashSet<InetSocketAddress>());
  
  public ConsistencyPLTest(PastryNode localNode, LeafSet leafSet) {
    this.environment = localNode.getEnvironment();
    this.localNode = localNode;
    this.leafSet = leafSet;
    localNode.addObserver(this);
    localNode.getEnvironment().getSelectorManager().addLoopObserver(this);
//    leafSet.addObserver(this);
  }
  
//  public void update(Observable arg0, Object arg1) {
//    NodeSetUpdate nsu = (NodeSetUpdate) arg1;
//    if (!nsu.wasAdded()) {
//      if (localNode.isReady() && !leafSet.isComplete()
//          && leafSet.size() < (leafSet.maxSize() / 2)) {
//        // kill self
//        System.out.println("ConsistencyPLTest: "
//            + localNode.getEnvironment().getTimeSource().currentTimeMillis()
//            + " Killing self due to leafset collapse. " + leafSet);
//        System.exit(24);
//      }
//    }
//  }

  public void update(Observable observable, Object value) {
    if (value instanceof Boolean) {
      Boolean b = (Boolean)value;    
      boolean rdy = b.booleanValue();
      
      Environment env = localNode.getEnvironment();
      Parameters params = env.getParameters();
      
      long curTime = env.getTimeSource().currentTimeMillis();
      System.out.println("CPLT.update("+rdy+"):"+curTime);
//      new Exception("Stack Trace").printStackTrace();
      int num = 2;    
      
//      if (rdy) {
//        params.setInt("rice.pastry.standard.PeriodicLeafSetProtocol_loglevel",Logger.CONFIG);        
//        params.setInt("rice.pastry.standard.ConsistentJoinProtocol_loglevel",Logger.CONFIG);
//      } else {
//        params.setInt("rice.pastry.standard.PeriodicLeafSetProtocol_loglevel",Logger.ALL);
//        params.setInt("rice.pastry.standard.ConsistentJoinProtocol_loglevel",Logger.ALL);
//        num = 5;
//      }
      System.out.println("LEAFSET"+num+":"+curTime+":"+localNode.getLeafSet());
  //    System.out.println("CPLT.setReady("+rdy+"):"+localNode.getEnvironment().getTimeSource().currentTimeMillis()); 
    } else {
      System.out.println("update("+observable+","+value+")");
    }
  }
  
  private static Id generateId() {
    byte[] data = new byte[20];
    new Random(100).nextBytes(data);
    return rice.pastry.Id.build(data);
  }
  
  static class BooleanHolder {
    public boolean running = true; 
  }
  
  Environment environment;
//  static MyNetworkListener networkActivity;

  static class MyNetworkListener implements NetworkListener {      
    public static final int TRACKS = 2;
    
    int[] msgRec = new int[TRACKS];
    int[] bytesRec = new int[TRACKS];
    int[] msgSnt = new int[TRACKS];
    int[] bytesSnt = new int[TRACKS];
    int channelsOpened;
    int channelsClosed;
    
    
    /**
     * returns full status of network activity since last call, then clears everything
     * @return
     */
    public synchronized String clobber() {
      String ret = 
        "st:"+msgSnt[TYPE_TCP]+":"+bytesSnt[TYPE_TCP]+
        " su:"+msgSnt[TYPE_UDP]+":"+bytesSnt[TYPE_UDP]+
        " rt:"+msgRec[TYPE_TCP]+":"+bytesRec[TYPE_TCP]+
        " ru:"+msgRec[TYPE_UDP]+":"+bytesRec[TYPE_UDP]+
        " socks:"+channelsOpened+":"+channelsClosed;
      
      for (int i = 0; i < TRACKS; i++) {
        msgRec[i] = 0;
        bytesRec[i] = 0;
        msgSnt[i] = 0;
        bytesSnt[i] = 0;
      }
      channelsOpened = 0;
      channelsClosed = 0;          
      return ret;
    }        
    public synchronized void dataReceived(int msgAddress, short msgType, InetSocketAddress address, int size,
        int type) {
      msgRec[type&0x1]++;
      bytesRec[type&0x1]+=size;      
    }      
    public synchronized void dataSent(int msgAddress, short msgType, InetSocketAddress address, int size,
        int type) {
      msgSnt[type&0x1]++;
      bytesSnt[type&0x1]+=size;      
    }      
    public synchronized void channelClosed(InetSocketAddress addr) {
      channelsClosed++;
    }      
    public synchronized void channelOpened(InetSocketAddress addr, int reason) {
      channelsOpened++;
    }      
  }  
  // killRingTime(mins) artificialChurnTime(mins) split/scribe/none sendInterval msgSize
  public static void main(String[] args) throws Exception {
    PrintStream ps = new PrintStream(new FileOutputStream("log4.txt", true));
    System.setErr(ps);
    System.setOut(ps);
    
    System.out.println("pastry.jar date: "+new Date(new File("pastry.jar").lastModified()));

    long bootTime = System.currentTimeMillis();
    System.out.println("start of log");
    
    boolean isBootNode = false;
    InetAddress localAddress = InetAddress.getLocalHost();
    System.out.println(localAddress.getHostName());
    System.out.println(BOOTNODE);
    if (localAddress.toString().contains(BOOTNODE)) {
      isBootNode = true;      
    }
    
    if (localAddress.toString().contains("janus")) {
      isJanus = true;
    }
    
    int killRingTime = 3*60; // minutes
    if (args.length > 0) {
      killRingTime = Integer.valueOf(args[0]).intValue(); 
    }    
    
    int artificialChurnTime = 0; // minutes
    if (args.length > 1) {
      artificialChurnTime = Integer.valueOf(args[1]).intValue(); 
    }    
    
    if (args.length > 2) {
      String app = args[2];
      if (app.equalsIgnoreCase("split")) {
        useSplitStream = true;
        System.out.println("using splitstream");
      }
      if (app.equalsIgnoreCase("scribe")) {
        useScribe = true;
        System.out.println("using scribe");
      }
      
//      useSplitStream = Boolean.valueOf(args[2]).booleanValue(); 
    }    
    
    if (args.length > 3) {
      MySplitStreamClient.SEND_PERIOD = Integer.parseInt(args[3]);
      if (MySplitStreamClient.SEND_PERIOD < 100)
        MySplitStreamClient.SEND_PERIOD = 100;
    }    
    
    if (args.length > 4) {
      MySplitStreamClient.msgSize = Integer.parseInt(args[4]);
      if (MySplitStreamClient.msgSize < 24)
        MySplitStreamClient.msgSize = 24;
    }    
    
    System.out.println("bootNode:"+isBootNode);

    System.out.println("artificialChurn = "+artificialChurn+" useSplitStream = "+useSplitStream);
    
    InetSocketAddress bootaddress;
    {
      InetAddress bootaddr;
      // build the bootaddress from the command line args
      if (args.length > 6) {
        bootaddr = InetAddress.getByName(args[6]); 
      } else {
        // this code makes ricepl-1 try to boot off of ricepl-3
        // everyone else boots off of ricepl-1
        if (isBootNode) {
          bootaddr = InetAddress.getByName(ALT_BOOTNODE); 
        } else {
          bootaddr = InetAddress.getByName(BOOTNODE);
        }
      }
      int bootport = startPort;
      if (args.length > 7) {
        bootport = Integer.parseInt(args[7]);
      }
      bootaddress = new InetSocketAddress(bootaddr,bootport);
      //bootAddresses.add(bootaddress);
      
    }
    
    if (!isBootNode && !isJanus) {
      // NOTE: Since we are often starting up a bunch of nodes on planetlab
      // at the same time, we need this randomsource to be seeded by more
      // than just the clock, we will include the IP address
      // as amazing as this sounds, it happened in a network of 20 on 7/19/2005
      // also, if you think about it, I was starting all of the nodes at the same 
      // instant, and they had synchronized clocks, if they all started within 1/10th of
      // a second, then there is only 100 different numbers to seed the generator with
      // -Jeff
      long time = System.currentTimeMillis();
      try {
        byte[] foo = InetAddress.getLocalHost().getAddress();
        for (int ctr = 0; ctr < foo.length; ctr++) {
          int i = (int)foo[ctr];
          i <<= (ctr*8);
          time ^= i; 
        }
      } catch (Exception e) {
        // if there is no NIC, screw it, this is really unlikely anyway  
      }
      Thread.sleep(BASE_DELAY+new Random(time).nextInt(RND_DELAY));
    }
    
    // the port to use locally    
    int bindport = startPort;
    if (args.length > 5) {
      bindport = Integer.parseInt(args[5]);
    }
    
    runNodes(isBootNode, bindport, bootaddress, bootTime, artificialChurnTime, killRingTime);
  }
  
  static boolean restartNode;
  public static void runNodes(
      boolean isBootNode, int bindport, InetSocketAddress bootaddress, 
      long bootTime, int artificialChurnTime, int killRingTime) throws Exception {
    restartNode = true;
    while(restartNode) { // to allow churn
      Environment env;
      if (USE_REPLAY) {
        env = RecordLayer.generateEnvironment();
      } else {
        env = new Environment();
      }

      Parameters p = env.getParameters(); 
      setupParams(p);

      boolean testFirewall = true;
      if (testFirewall) {      
        p.setBoolean("rendezvous_test_firewall", true);
        // should require boot node to not be firewalled
        p.setBoolean("rendezvous_test_makes_bootstrap", isBootNode);
        if (isJanus) {
          p.setFloat("rendezvous_test_num_firewalled", 1.0f);
        } else {
          p.setFloat("rendezvous_test_num_firewalled", 0.5f);
        }
      }      
      
      // Generate the NodeIds Randomly
      NodeIdFactory nidFactory = new RandomNodeIdFactory(env);

      final ArrayList<RecordLayer<InetSocketAddress>> historyHolder = new ArrayList<RecordLayer<InetSocketAddress>>();

      // construct the PastryNodeFactory, this is how we use rice.pastry.socket
      SocketPastryNodeFactory factory = new RendezvousSocketPastryNodeFactory(nidFactory, bindport, env, false)
//      SocketPastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport, env)
      {
        @Override
        protected RandomSource cloneRandomSource(Environment rootEnvironment, Id nodeId, LogManager lman) {
          long randSeed = rootEnvironment.getRandomSource().nextLong();
          logger.log("RandSeed for "+nodeId.toStringFull()+" "+randSeed);
                   
          return new SimpleRandomSource(randSeed, lman);    
        }
//        @Override
//        protected TLDeserializer getTLDeserializer(NodeHandleFactory handleFactory, PastryNode pn) {
//          // TODO Auto-generated method stub
//          return super.getTLDeserializer(handleFactory, pn);
//        }

//        @Override
//        protected IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, ByteBuffer, SourceRoute<MultiInetSocketAddress>> getIdentityImpl(PastryNode pn, SocketNodeHandleFactory handleFactory) throws IOException {          
//          final IdentityImpl<TransportLayerNodeHandle<MultiInetSocketAddress>, MultiInetSocketAddress, ByteBuffer, SourceRoute<MultiInetSocketAddress>> ret = super.getIdentityImpl(pn, handleFactory);
//          environment.getSelectorManager().getTimer().schedule(new TimerTask() {          
//            @Override
//            public void run() {
//              ret.printMemStats(Logger.FINER);
//            }          
//          }, 60000, 60000);                    
//          return ret;
//        }

        @Override
        protected TransportLayer<InetSocketAddress, ByteBuffer> getWireTransportLayer(InetSocketAddress innermostAddress, PastryNode pn) throws IOException {
          if (!USE_REPLAY) return super.getWireTransportLayer(innermostAddress, pn);
          System.out.println("Initializing RecordLayer "+pn.getNodeId());
          RecordLayer<InetSocketAddress> ret = new RecordLayer<InetSocketAddress>(super.getWireTransportLayer(innermostAddress, pn), "0x"+pn.getNodeId().toStringBare(), new InetSocketAddressSerializer(), pn.getEnvironment());
//          recorders.put(pn, ret);
          historyHolder.add(ret);
          return ret;
        }

//        @Override
//        protected TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> 
//          getCommonAPITransportLayer(
//              TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, ByteBuffer> upperIdentity, 
//              PastryNode pn, TLDeserializer deserializer) {
//          
//          final TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> tl = 
//            super.getCommonAPITransportLayer(upperIdentity, pn, deserializer);
//
//          TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> ret = 
//            new TransportLayer<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage>(){          
//            
//            public void destroy() {
//              tl.destroy();
//            }
//          
//            public void setErrorHandler(ErrorHandler<TransportLayerNodeHandle<MultiInetSocketAddress>> handler) {
//              tl.setErrorHandler(handler);
//            }
//          
//            public void setCallback(final TransportLayerCallback<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> callback) {
//              tl.setCallback(new MyCallback(callback, environment, logger));
//            }
//          
//            public MessageRequestHandle<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> sendMessage(TransportLayerNodeHandle<MultiInetSocketAddress> i, RawMessage m, MessageCallback<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> deliverAckToMe, Map<String, Object> options) {
//              if (printMe(m)) logger.log("sendMessage("+i+","+m+")");
//              return tl.sendMessage(i, m, deliverAckToMe, options);
//            }
//          
//            public SocketRequestHandle<TransportLayerNodeHandle<MultiInetSocketAddress>> openSocket(TransportLayerNodeHandle<MultiInetSocketAddress> i, SocketCallback<TransportLayerNodeHandle<MultiInetSocketAddress>> deliverSocketToMe, Map<String, Object> options) {
//              return tl.openSocket(i, deliverSocketToMe, options);
//            }
//          
//            public TransportLayerNodeHandle<MultiInetSocketAddress> getLocalIdentifier() {
//              return tl.getLocalIdentifier();
//            }
//          
//            public void acceptSockets(boolean b) {
//              tl.acceptSockets(b);
//            }
//          
//            public void acceptMessages(boolean b) {
//              tl.acceptMessages(b);
//            }          
//          };
//          
//          return ret;
//        }

//        @Override
//        protected TransportLayer<MultiInetSocketAddress, ByteBuffer> getPriorityTransportLayer(
//            TransportLayer<MultiInetSocketAddress, ByteBuffer> trans, 
//            LivenessProvider<MultiInetSocketAddress> liveness, 
//            ProximityProvider<MultiInetSocketAddress> prox, 
//            PastryNode pn) {
//          final PriorityTransportLayerImpl<MultiInetSocketAddress> ret = 
//            (PriorityTransportLayerImpl<MultiInetSocketAddress>)super.getPriorityTransportLayer(trans, liveness, prox, pn);          
//          environment.getSelectorManager().getTimer().schedule(new TimerTask() {          
//            @Override
//            public void run() {
//              ret.printMemStats(Logger.FINEST);
//            }          
//          }, 60000, 60000);          
//          return ret;
//        }

//        @Override
//        protected TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer>
//          getLivenessTransportLayer(
//            TransportLayer<SourceRoute<MultiInetSocketAddress>, ByteBuffer> tl, 
//            PastryNode pn) {
//          
//          TransLiveness<SourceRoute<MultiInetSocketAddress>, ByteBuffer> ltl = 
//            super.getLivenessTransportLayer(tl, pn);
//          
//          ltl.getLivenessProvider().addLivenessListener(new LivenessListener<SourceRoute<MultiInetSocketAddress>>(){    
//            public void livenessChanged(SourceRoute<MultiInetSocketAddress> i, int val, Map<String, Object> options) {
//              logger.log("SR.livenessChanged("+i+","+val+")");
//            }
//          });
//          return ltl;
//        } 
      };
      runNode(env, factory, isBootNode, bindport, bootaddress, bootTime, artificialChurnTime, killRingTime, historyHolder);
    }
  }
  
//  public static void replayNode(final Id id, final InetSocketAddress addr, final Collection<InetSocketAddress> bootAddrCandidates, long startTime, long randSeed) throws Exception {
////    this.bootaddress = bootaddress;
//    boolean isBootNode = false;
//    InetAddress localAddress = InetAddress.getLocalHost();
//    System.out.println(localAddress.getHostName());
//    System.out.println(BOOTNODE);
//    if (localAddress.toString().contains(BOOTNODE)) {
//      isBootNode = true;      
//    }
//    
//    final ArrayList<ReplayLayer<InetSocketAddress>> replayers = new ArrayList<ReplayLayer<InetSocketAddress>>();
//    
//    Environment env = ReplayLayer.generateEnvironment(id.toString(), startTime, randSeed);
//    final SocketPastryNodeFactory factory = new SocketPastryNodeFactory(new NodeIdFactory() {    
//      public Id generateNodeId() {
//        return id;
//      }    
//    },addr.getPort(), env)
//    {
//
//      @Override
//      public NodeHandle getLocalHandle(PastryNode pn, NodeHandleFactory nhf, Object localNodeInfo) {
//        SocketNodeHandle ret = (SocketNodeHandle)super.getLocalHandle(pn, nhf, localNodeInfo);
//        logger.log(ret.toStringFull());
//        return ret;
//      }
//      
//      @Override
//      protected RandomSource cloneRandomSource(Environment rootEnvironment, Id nodeId, LogManager lman) {
//        return rootEnvironment.getRandomSource();    
//      }
//      
//      @Override
//      protected TransportLayer<InetSocketAddress, ByteBuffer> getWireTransportLayer(InetSocketAddress innermostAddress, PastryNode pn) throws IOException {
//        IdentifierSerializer<InetSocketAddress> serializer = new ISASerializer();
//        
//        HashProvider hashProv = new NullHashProvider();
//        SecureHistoryFactory shFactory = new SecureHistoryFactoryImpl(hashProv, pn.getEnvironment());
//        String logName = "0x"+id.toStringFull().substring(0,6);
//        SecureHistory hist = shFactory.open(logName, "r");
//        
//        ReplayLayer<InetSocketAddress> replay = new ReplayLayer<InetSocketAddress>(serializer,hashProv,hist,addr,pn.getEnvironment());
//        replay.registerEvent(new EventCallback(){
//        
//          public void replayEvent(short type, InputBuffer entry) {
//            throw new RuntimeException("Not implemented.");
//          }
//        
//        }, EVT_BOOT, EVT_SUBSCRIBE_PUBLISH, EVT_SHUTDOWN);
//        replayers.add(replay);
//        return replay;
//      }
//    };
//    
//    System.out.println("bootAddrCandidates "+bootAddrCandidates);
//    
//    
//    // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
//    
//    // need this mechanism to make the construction of the TL atomic, looking for better solution...
//    final ArrayList<PastryNode> holder = new ArrayList<PastryNode>();
//    env.getSelectorManager().invoke(new Runnable(){    
//      public void run() {
//        synchronized(holder) {
//          holder.add(factory.newNode());        
//          holder.notify();
//        }
//      }    
//    });
//    
//    synchronized(holder) {
//      while(holder.isEmpty()) {
//        holder.wait();
//      }
//    }
//    
//    final PastryNode node = holder.get(0); //factory.newNode();
//    
//    System.out.println("node: "+node.getLocalHandle().getEpoch());
//    
////    if (isBootNode) {
////      // go ahead and start a new ring
////    } else {
////      node.getBootstrapper().boot(Arrays.asList(bootAddressCandidates));
////      // don't boot your own ring unless you are ricepl-1
////      System.out.println("Couldn't find bootstrap... exiting.");        
////      break; // restart join process
////    }
//    
//    node.addLivenessListener(new LivenessListener<NodeHandle>() {      
//      HashMap<NodeHandle, Integer> lastVal = new HashMap<NodeHandle, Integer>();
//      HashMap<NodeHandle, Exception> lastStack = new HashMap<NodeHandle, Exception>();
////      HashMap<MultiInetSocketAddress, NodeHandle> up = new HashMap<MultiInetSocketAddress, NodeHandle>();
//      
//      Logger logger = node.getEnvironment().getLogManager().getLogger(LivenessListener.class, null);
//      public void livenessChanged(NodeHandle i, int val, Map<String, Object> options) {
////        if (i.getId().toString().startsWith("<0x000")) {
////          logger.logException("livenessChanged1("+i+","+val+")", new Exception("Stack Trace"));                
////        } else {        
//        i.getAddress();
//          logger.log("livenessChanged1("+i+","+val+")"+i.getId().toString());
//          new Exception("livenessChanged1("+i+","+val+"):"+i.getEpoch()).printStackTrace();
//          
//          // this code prints stack trace if the value was notified, but didn't change
//          if ((lastVal.containsKey(i) && lastVal.get(i).equals(val)) /*|| (up.containsKey(i.getAddress()) && !up.get(i.getAddress()).equals(i))*/) {
//              System.out.println("livenessChanged-not:");
//              lastStack.get(i).printStackTrace();
//              System.out.println("new:");
//              new Exception("Stack Trace").printStackTrace();
//          }
////          up.put((MultiInetSocketAddress)i.getAddress(), i);
//          lastVal.put(i, val);
//          lastStack.put(i,new Exception("Stack Trace"));
//          
////        }
////        logger.log("livenessChanged("+i+","+val+")");
//      }      
//    });
////    node.addNetworkListener(networkActivity);
//    
////    InetSocketAddress[] boots = new InetSocketAddress[6];
////    boots[0] = new InetSocketAddress(InetAddress.getByName("ricepl-1.cs.rice.edu"), startPort);
////    boots[1] = new InetSocketAddress(InetAddress.getByName("ricepl-2.cs.rice.edu"), startPort);
////    boots[2] = new InetSocketAddress(InetAddress.getByName("ricepl-3.cs.rice.edu"), startPort);
////    boots[3] = new InetSocketAddress(InetAddress.getByName("planetlab2.cs.umass.edu"), startPort);
////    boots[4] = new InetSocketAddress(InetAddress.getByName("planet1.scs.cs.nyu.edu"), startPort);
////    boots[5] = new InetSocketAddress(InetAddress.getByName("planetlab2.cs.cornell.edu"), startPort);
//    
////    PartitionHandler ph = new PartitionHandler(node, (SocketPastryNodeFactory)factory, boots);
////    ph.start(node.getEnvironment().getSelectorManager().getTimer());
//    
//    System.out.println("BOOTUP:"+env.getTimeSource().currentTimeMillis());
//
////    final String nodeString = node.toString();
//    
//    final LeafSet ls = node.getLeafSet();
//    new ConsistencyPLTest(node, ls);
//   
//    System.out.println("STARTUP "+env.getTimeSource().currentTimeMillis()+" "+node);    
//    
//    if (useScribe) {
//      // this is to do scribe stuff
//      MyScribeClient app = new MyScribeClient(node);      
//      app.subscribe();
//      if (isBootNode) {
//        app.startPublishTask(); 
//      }
//    }
//    
//    final ArrayList<MySplitStreamClient> appHolder = new ArrayList<MySplitStreamClient>();
//    MySplitStreamClient app = null;
//    if (useSplitStream) {
//      app = new MySplitStreamClient(node, INSTANCE);      
//      appHolder.add(app);
//      ChannelId CHANNEL_ID = new ChannelId(generateId());    
//      app.attachChannel(CHANNEL_ID);
//      
////      if (!isBootNode) {
////        System.out.println("Sleeping(2) for "+WAIT_TO_SUBSCRIBE_DELAY+" at "+env.getTimeSource().currentTimeMillis());
////        Thread.sleep(WAIT_TO_SUBSCRIBE_DELAY);
////        System.out.println("Done(2) sleeping at "+env.getTimeSource().currentTimeMillis());
////      }   
//      
////      app.subscribeToAllChannels();    
////      app.startPublishTask(); 
//    }  
//
//    ReplaySM sim = (ReplaySM)env.getSelectorManager();
//    ReplayLayer<InetSocketAddress> replay = replayers.get(0);
//    replay.makeProgress(); // get rid of INIT event
//    sim.setVerifier(replay);
//     
//    sim.start();
//
//  }
  
  static boolean running;
  
  public static void runNode(final Environment env, final SocketPastryNodeFactory factory, boolean isBootNode, int bindport, InetSocketAddress bootaddress, 
      long bootTime, int artificialChurnTime, int killRingTime, final ArrayList<RecordLayer<InetSocketAddress>> historyHolder) throws Exception {
    { // old while loop
//      final Environment env = RecordLayer.generateEnvironment(); //new Environment();
      
      final Environment environment = env;
            
      Parameters params = environment.getParameters(); 
      
      if (isBootNode) {
        params.setBoolean("rice_socket_seed", true);
      }
      
      setupParams(params);
      
      // log everything while booting, this gets turned off on SETREADY
//      params.setInt("org.mpisws.p2p.transport_loglevel",Logger.ALL);
//      params.setInt("rice.pastry.socket.SocketPastryNodeFactory_loglevel",Logger.ALL);
      
//      if (args.length > 0) {
//        int theVal = Integer.parseInt(args[0]);
//        if (theVal >= 0) {
//          env.getParameters().setInt("pastry_socket_srm_num_source_route_attempts", theVal);           
//        } else {    // it's negative, try varying it based on time      
//          long now = env.getTimeSource().currentTimeMillis();
//          now/=1000; 
//          now%=86400; //1 day's seconds
//          now/=3600; // hour 0-23
//          now/=2; // 0-11;
//          now*=2; // 0-22 by 2
//        
//          env.getParameters().setInt("pastry_socket_srm_num_source_route_attempts", (int)now); 
//        }
//      }
      
            
      System.out.println("BOOTUP:"+env.getTimeSource().currentTimeMillis());
//      System.out.println("Ping Neighbor Period:"+env.getParameters().getInt("pastry_protocol_periodicLeafSet_ping_neighbor_period"));
//      System.out.println("Ping Num Source Route attempts:"+env.getParameters().getInt("pastry_socket_srm_num_source_route_attempts"));
      

//    networkActivity = new MyNetworkListener();
      final BooleanHolder imaliveRunning = new BooleanHolder();
//      final Runtime r = Runtime.getRuntime();
//      new Thread(new Runnable() {
//        public void run() {
//          while(imaliveRunning.running) {
////            String foo = networkActivity.clobber();
////            System.out.println("ImALIVE:"+environment.getTimeSource().currentTimeMillis()+" "+foo);
//            long free = r.freeMemory();
//            long total = r.totalMemory();
//            long allocated = total-free;
//            System.out.println("ImALIVE:"+environment.getTimeSource().currentTimeMillis()+" a:"+allocated+" f:"+free+" t:"+total);
//                
//            try {
//              Thread.sleep(15000);
//            } catch (Exception e) {}
//          } 
//        }
//      },"ImALIVE").start();
      
      // test port bindings before proceeding
      int tries = 0;
      boolean success = false;
      while(!success) {
        try {
          InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getLocalHost(),bindport);
          
          // udp test
          DatagramChannel channel = DatagramChannel.open();
          channel.configureBlocking(false);
          channel.socket().bind(bindAddress);
          channel.close();
          
          ServerSocketChannel channel1 = ServerSocketChannel.open();
          channel1.configureBlocking(false);
          channel1.socket().bind(bindAddress);
          channel1.close();
          
          success = true;
        } catch (Exception e) {
          System.out.println("Couldn't bind on port "+bindport+" trying "+(bindport+1));
          bindport++; 
          tries++;
          if (tries > 100) {
            System.out.println("Too many bind attempts, shutting down.");
            System.exit(25);
          }
        }
      }
      
        
//      InetSocketAddress[] bootAddressCandidates = (InetSocketAddress[])bootAddresses.toArray(new InetSocketAddress[0]);
      // This will return null if we there is no node at that location
//      NodeHandle bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootAddressCandidates, 30000);
      final Collection<InetSocketAddress> bootAddrCandidates = new ArrayList<InetSocketAddress>();
      bootAddrCandidates.add(bootaddress);
//      synchronized(bootAddresses) {
//        int ctr = 10;
//        Iterator<InetSocketAddress> i = bootAddresses.iterator();
//        while(ctr > 0 && i.hasNext()) {
//          bootAddrCandidates.add(i.next());
//          ctr--;
//        }
//      }
      System.out.println("bootAddrCandidates "+bootAddrCandidates);
      
      
      // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
//      final PastryNode node = factory.newNode();
      
      // need this mechanism to make the construction of the TL atomic, looking for better solution...
      final ArrayList<PastryNode> holder = new ArrayList<PastryNode>();
      env.getSelectorManager().invoke(new Runnable(){    
        public void run() {
          synchronized(holder) {
            holder.add((PastryNode)factory.newNode());        
            holder.notify();
          }
        }    
      });
      
      synchronized(holder) {
        while(holder.isEmpty()) {
          holder.wait();
        }
      }
      
      running = true;
      final PastryNode node = holder.get(0); //factory.newNode();
      ((PilotManager<RendezvousSocketNodeHandle>)node.getVars().get(RendezvousSocketPastryNodeFactory.RENDEZVOUS_TL))
          .addIncomingPilotListener(new MyPilotListener(node.getEnvironment().getLogManager().getLogger(IncomingPilotListener.class, null)));
      ((PilotManager<RendezvousSocketNodeHandle>)node.getVars().get(RendezvousSocketPastryNodeFactory.RENDEZVOUS_TL))
          .addOutgoingPilotListener(new MyPilotListener(node.getEnvironment().getLogManager().getLogger(OutgoingPilotListener.class, null)));
      
      node.addDestructable(new Destructable(){      
        public void destroy() {
          new Exception("Destroy Stack Trace").printStackTrace();
          running = false;
        }      
      });      
      
      System.out.println("node: "+((TransportLayerNodeHandle)node.getLocalHandle()).getEpoch());
      
//      if (isBootNode) {
//        // go ahead and start a new ring
//      } else {
//        node.getBootstrapper().boot(Arrays.asList(bootAddressCandidates));
//        // don't boot your own ring unless you are ricepl-1
//        System.out.println("Couldn't find bootstrap... exiting.");        
//        break; // restart join process
//      }

      node.addLivenessListener(new LivenessListener<NodeHandle>() {   
        HashMap<NodeHandle, Integer> lastVal = new HashMap<NodeHandle, Integer>();
        HashMap<NodeHandle, Exception> lastStack = new HashMap<NodeHandle, Exception>();
        HashMap<MultiInetSocketAddress, NodeHandle> up = new HashMap<MultiInetSocketAddress, NodeHandle>();

        Logger logger = node.getEnvironment().getLogManager().getLogger(LivenessListener.class, null);
        public void livenessChanged(NodeHandle i2, int val, Map<String, Object> options) {
          TransportLayerNodeHandle i = (TransportLayerNodeHandle)i2;
//          if (i.getId().toString().startsWith("<0x000")) {
//            logger.logException("livenessChanged1("+i+","+val+")", new Exception("Stack Trace"));                
//          } else {
          logger.log("livenessChanged1("+i+","+val+")"+i.getId().toString()+" "+i.getEpoch());
//          new Exception("livenessChanged1("+i+","+val+"):"+i.getEpoch()).printStackTrace();
          
          // this code prints stack trace if the value was notified, but didn't change
          if ((lastVal.containsKey(i) && lastVal.get(i).equals(val))) {
              System.out.println("livenessChanged-not:");
              lastStack.get(i).printStackTrace();
              System.out.println("new:");
              new Exception("Stack Trace").printStackTrace();
          }
          if ((up.containsKey(i.getAddress()) && !up.get(i.getAddress()).equals(i))) {
            System.out.println("livenessChanged different node:"+up.get(i.getAddress()));
//            new Exception("Stack Trace").printStackTrace();
        }
          up.put((MultiInetSocketAddress)i.getAddress(), i);
          lastVal.put(i, val);
          lastStack.put(i,new Exception("Stack Trace"));
        }      
      });
//      node.addNetworkListener(networkActivity);
      
//      InetSocketAddress[] boots = new InetSocketAddress[6];
//      boots[0] = new InetSocketAddress(InetAddress.getByName("ricepl-1.cs.rice.edu"), startPort);
//      boots[1] = new InetSocketAddress(InetAddress.getByName("ricepl-2.cs.rice.edu"), startPort);
//      boots[2] = new InetSocketAddress(InetAddress.getByName("ricepl-3.cs.rice.edu"), startPort);
//      boots[3] = new InetSocketAddress(InetAddress.getByName("planetlab2.cs.umass.edu"), startPort);
//      boots[4] = new InetSocketAddress(InetAddress.getByName("planet1.scs.cs.nyu.edu"), startPort);
//      boots[5] = new InetSocketAddress(InetAddress.getByName("planetlab2.cs.cornell.edu"), startPort);
      
//      PartitionHandler ph = new PartitionHandler(node, (SocketPastryNodeFactory)factory, boots);
//      ph.start(node.getEnvironment().getSelectorManager().getTimer());
      
      final String nodeString = node.toString();
      Thread shutdownHook = new Thread() {
        public void run() { System.out.println("SHUTDOWN "+environment.getTimeSource().currentTimeMillis()+" "+nodeString); }
      };
      
      Runtime.getRuntime().addShutdownHook(shutdownHook);
      
      final LeafSet ls = node.getLeafSet();
      new ConsistencyPLTest(node, ls);
     
      
      System.out.println("STARTUP "+env.getTimeSource().currentTimeMillis()+" "+node);    
      
//      NodeSetListener preObserver = 
//        new NodeSetListener() {
//          public void nodeSetUpdate(NodeSetEventSource set, NodeHandle handle, boolean added) {
//            System.out.println("LEAFSET4:"+environment.getTimeSource().currentTimeMillis()+":"+ls);
//            bootAddresses.add(((SocketNodeHandle)handle).getInetSocketAddress());
//          }
//        };
//        
//      ls.addNodeSetListener(new NodeSetListener() {
//        public void nodeSetUpdate(NodeSetEventSource set, NodeHandle handle, boolean added) {
//          int num = 1;
//          if (!node.isReady()) num = 4;
//          System.out.println("LEAFSET"+num+":"+environment.getTimeSource().currentTimeMillis()+":"+ls);
//          bootAddresses.add(((SocketNodeHandle)handle).getInetSocketAddress());
//        }
//      });
  
      if (useScribe) {
        // this is to do scribe stuff
        MyScribeClient app = new MyScribeClient(node);      
        app.subscribe();
        if (isBootNode) {
          app.startPublishTask(); 
        }
      }
      
      final ArrayList<MySplitStreamClient> appHolder = new ArrayList<MySplitStreamClient>();
      MySplitStreamClient app = null;
      if (useSplitStream) {
        app = new MySplitStreamClient(node, INSTANCE);      
        appHolder.add(app);
        ChannelId CHANNEL_ID = new ChannelId(generateId());    
        app.attachChannel(CHANNEL_ID);
        
//        if (!isBootNode) {
//          System.out.println("Sleeping(2) for "+WAIT_TO_SUBSCRIBE_DELAY+" at "+env.getTimeSource().currentTimeMillis());
//          Thread.sleep(WAIT_TO_SUBSCRIBE_DELAY);
//          System.out.println("Done(2) sleeping at "+env.getTimeSource().currentTimeMillis());
//        }   
        
//        app.subscribeToAllChannels();    
//        app.startPublishTask(); 
      }  
      // this is to cause different connections to open
      // TODO: Implement

      environment.getSelectorManager().invoke(new Runnable() {
        public void run() {
          if (USE_REPLAY) {
            try {
              historyHolder.get(0).logEvent(EVT_BOOT);
            } catch (IOException ioe) {
              ioe.printStackTrace();
            }
          }
          System.out.println("Booting "+bootAddrCandidates);
          node.getBootstrapper().boot(bootAddrCandidates);
        }
      });
      
//      ls.addNodeSetListener(preObserver);  
      
//      boolean ALL_LOGGING_WHEN_CANT_JOIN = false;
//      
//      int setLLBackTo = params.getInt("loglevel"); // BROKEN!!! this is a string, not an int
//      if (params.contains("org.mpisws.p2p.transport_loglevel")) {
//        setLLBackTo = params.getInt("org.mpisws.p2p.transport_loglevel");
//      }
      
      long lastTimePrinted = 0;
      while(!node.isReady() && !node.joinFailed() && running) {
        // delay so we don't busy-wait
        long now = env.getTimeSource().currentTimeMillis();
        if (now-lastTimePrinted > 3*60*1000) {
          System.out.println("LEAFSET5:"+env.getTimeSource().currentTimeMillis()+":"+ls);          
          
          // the first time we take 3 mins to join
//          if (ALL_LOGGING_WHEN_CANT_JOIN) {
//            if (lastTimePrinted != 0) {
//              params.setInt("org.mpisws.p2p.transport_loglevel",Logger.ALL);
//            }
//          }
          lastTimePrinted = now;
        }
        Thread.sleep(1000);
      }
      
//      if (ALL_LOGGING_WHEN_CANT_JOIN) {
//        params.setInt("org.mpisws.p2p.transport_loglevel",setLLBackTo);
//      }    
            
      if (!running || node.joinFailed()) {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        System.out.println("Join failed. "+env.getTimeSource().currentTimeMillis()+":"+node+":"+ls);
        node.joinFailedReason().printStackTrace();
        System.out.println("SHUTDOWN "+env.getTimeSource().currentTimeMillis()+" "+node);
        //              System.exit(25);
//        node.destroy(); // done in env.destroy()
        env.destroy();              
        int waittime = env.getRandomSource().nextInt(30000)+30000;
        System.out.println("Waiting for "+waittime+" millis before restarting.");
        Thread.sleep(waittime); // wait up to 1 minute

        return; // restartNode
      }
      
      System.out.println("SETREADY:"+env.getTimeSource().currentTimeMillis()+" "+node);
      
//      params.setInt("org.mpisws.p2p.transport_loglevel",Logger.WARNING);
//      params.setInt("rice.pastry.socket.SocketPastryNodeFactory_loglevel",Logger.INFO);
//      setupParams(params);
      
      if (useSplitStream) {
        env.getSelectorManager().invoke(new Runnable(){
        
          public void run() {
            if (USE_REPLAY) {
              try {
                historyHolder.get(0).logEvent(EVT_SUBSCRIBE_PUBLISH);
              } catch (IOException ioe) {
                ioe.printStackTrace();
              }
            }
            appHolder.get(0).subscribeToAllChannels();    
            appHolder.get(0).startPublishTask();             
          }        
        });
      }
      
//      ls.deleteNodeSetListener(preObserver);
  
      int maxLeafsetSize = ls.getUniqueCount();
      while(running) {
        int num = 2;
        if (!node.isReady()) num = 5;
        System.out.println("LEAFSET"+num+":"+env.getTimeSource().currentTimeMillis()+":"+ls);
        Thread.sleep(1*60*1000);
        maxLeafsetSize = Math.max(ls.getUniqueCount(), maxLeafsetSize);
        long testTime = env.getTimeSource().currentTimeMillis()-bootTime;
        if (artificialChurnTime == 0) {
          artificialChurn = false;
        } else {
          artificialChurn = true;
        }
        
//        if (testTime > artificialChurnTime*1000*60) artificialChurn = true;
        if ((killRingTime > 0) && testTime > killRingTime*1000*60) {
          restartNode = false;
          artificialChurn = true;
        }
        
        // the leafset shrunk too much
        boolean leafsetTooSmall = (maxLeafsetSize > 12 && ls.getUniqueCount() < 4);
        boolean killSelfToCauseChurn = artificialChurn && (env.getRandomSource().nextInt(artificialChurnTime*2) == 0);
        
        boolean killSelf = killSelfToCauseChurn || leafsetTooSmall;
        
        if (killSelf) {
          if (!isBootNode) {                        
            imaliveRunning.running = false;
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            System.out.println("Killing self to cause churn. "+env.getTimeSource().currentTimeMillis()+":"+node+":"+ls);
            System.out.println("SHUTDOWN "+env.getTimeSource().currentTimeMillis()+" "+node);
            //              System.exit(25);
//              node.destroy(); // done in env.destroy()
            try {
              env.getSelectorManager().invoke(new Runnable() {
                public void run() {        
                  if (USE_REPLAY) {
                    try {
                      historyHolder.get(0).logEvent(EVT_SHUTDOWN);
                    } catch (IOException ioe) {
                      ioe.printStackTrace();
                    }                      
                  }
                  env.destroy();   
                }
              });             
              running = false;
              int waittime = env.getRandomSource().nextInt(30000)+30000;
              System.out.println("Waiting for "+waittime+" millis before restarting.");
              Thread.sleep(waittime); // wait up to 1 minute
            } catch (Exception e) {
              // the program exited abnormally, oh well, keep going
            }
          }
        }
      }    
    }
  }
  
  static class MyPilotListener implements OutgoingPilotListener<RendezvousSocketNodeHandle>, IncomingPilotListener<RendezvousSocketNodeHandle> {
    Map<InetAddress, List<RendezvousSocketNodeHandle>> record = new HashMap<InetAddress, List<RendezvousSocketNodeHandle>>();
    Logger logger;
    int numPilots = 0;
    
    public MyPilotListener(Logger logger) {
      this.logger = logger;
    }
    
    public void pilotOpening(RendezvousSocketNodeHandle i) {
      InetSocketAddress isa = i.eaddress.getOutermostAddress();
      InetAddress ia = isa.getAddress();
      List<RendezvousSocketNodeHandle> list = record.get(ia);
      if (list == null) {
        list = new ArrayList<RendezvousSocketNodeHandle>();
        record.put(ia, list);
      }
      int numHandles = 0;
      for (RendezvousSocketNodeHandle handle : list) {
        if (handle.equals(i)) {
          numHandles++;
        }
      }      
      numPilots++;
      logger.log("pilotOpened("+i+") pilots:"+numPilots+" handles:"+numHandles+" addrs:"+list.size());              
      list.add(i);      
    }
    
    public void pilotClosed(RendezvousSocketNodeHandle i) {
      InetSocketAddress isa = i.eaddress.getOutermostAddress();
      InetAddress ia = isa.getAddress();
      List<RendezvousSocketNodeHandle> list = record.get(ia);
      if (list == null) {
        logger.logException("pilotClosed("+i+") no record of "+ia, new Exception("Stack Trace."));
        return;
      }      
      if (!list.remove(i)) {
        logger.logException("pilotClosed("+i+") no record.", new Exception("Stack Trace."));        
        return;
      }
      numPilots--;
      int numHandles = 0;
      for (RendezvousSocketNodeHandle handle : list) {
        if (handle.equals(i)) {
          numHandles++;
        }
      }      
      logger.log("pilotClosed("+i+") pilots:"+numPilots+" handles:"+numHandles+" addrs:"+list.size());              
    }
  }

  public static boolean printMe(RawMessage m) {
    if (m instanceof RouteMessage) return false;
    if (m.getClass().getName().startsWith("rice.pastry.pns")) return false;
    return true;
  }
  

  static class MyCallback implements TransportLayerCallback<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> {              
    TransportLayerCallback<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> callback;
    Logger logger;
    Environment environment;
    
    public MyCallback(TransportLayerCallback<TransportLayerNodeHandle<MultiInetSocketAddress>, RawMessage> callback, Environment env, Logger logger) {
      this.callback = callback;
      this.logger = logger;
      this.environment = env;
    }

    public void messageReceived(
        TransportLayerNodeHandle<MultiInetSocketAddress> i, 
        RawMessage m,
        Map<String, Object> options) throws IOException {
      
      if (printMe(m)) logger.log("messageReceived("+i+","+m+")");
      callback.messageReceived(i, m, options);
    }
  
    public void incomingSocket(P2PSocket<TransportLayerNodeHandle<MultiInetSocketAddress>> s)
        throws IOException {
      callback.incomingSocket(s);
    }              
  }
  
  public int delayInterest() {
    return 15000;
  }

  public void loopTime(int loopTime) {
    System.out.println("loopTime("+loopTime+"):"+environment.getTimeSource().currentTimeMillis());
    System.out.println("LEAFSET5:"+(environment.getTimeSource().currentTimeMillis()-loopTime+delayInterest())+":"+localNode.getLeafSet());
  }  
}
