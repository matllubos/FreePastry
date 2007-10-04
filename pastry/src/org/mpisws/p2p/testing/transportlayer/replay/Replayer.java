package org.mpisws.p2p.testing.transportlayer.replay;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;

import org.mpisws.p2p.testing.transportlayer.replay.ScribeTutorial.ISASerializer;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.direct.EventSimulator;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactoryImpl;
import org.mpisws.p2p.transport.peerreview.history.stub.NullHashProvider;
import org.mpisws.p2p.transport.peerreview.replay.IdentifierSerializer;
import org.mpisws.p2p.transport.peerreview.replay.playback.ReplayLayer;

import rice.environment.Environment;
import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.environment.processing.Processor;
import rice.environment.processing.sim.SimProcessor;
import rice.environment.random.RandomSource;
import rice.environment.time.simulated.DirectTimeSource;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.transport.TLPastryNode;
import rice.selector.SelectorManager;

public class Replayer {

  public static void replayNode(final Id id, final InetSocketAddress addr, InetSocketAddress bootaddress, final long startTime, final long randSeed) throws Exception {
//  Environment env = Environment.directEnvironment();
    System.out.println(id.toStringFull()+" "+addr.getAddress().getHostAddress()+" "+bootaddress.getPort()+" "+startTime+" "+randSeed);
    
    RandomSource rs = null;
    Parameters params = new SimpleParameters(Environment.defaultParamFileArray,null);
    DirectTimeSource dts = new DirectTimeSource(startTime);
    LogManager lm = Environment.generateDefaultLogManager(dts,params);
    dts.setLogManager(lm);
    SelectorManager selector = Environment.generateDefaultSelectorManager(dts,lm);
    selector.setSelect(false);
    dts.setSelectorManager(selector);
    Processor proc = new SimProcessor(selector);
    Environment env = new Environment(selector,proc,rs,dts,lm,
        params, Environment.generateDefaultExceptionStrategy(lm));
  
    params.setInt("org.mpisws.p2p.transport.peerreview.replay_loglevel", Logger.FINER);
    
    
    final Logger simLogger = env.getLogManager().getLogger(EventSimulator.class, null);
    
    final Collection<ReplayLayer<InetSocketAddress>> replayers = new ArrayList<ReplayLayer<InetSocketAddress>>();
    
    
    PastryNodeFactory factory = new SocketPastryNodeFactory(new NodeIdFactory() {    
      public Id generateNodeId() {
        return id;
      }    
    },addr.getPort(),env) {
  
      @Override
      protected TransportLayer<InetSocketAddress, ByteBuffer> getWireTransportLayer(InetSocketAddress innermostAddress, TLPastryNode pn) throws IOException {
        IdentifierSerializer<InetSocketAddress> serializer = new ISASerializer();
        
        HashProvider hashProv = new NullHashProvider();
        SecureHistoryFactory shFactory = new SecureHistoryFactoryImpl(hashProv);
        String logName = "0x"+id.toStringFull().substring(0,6);
        SecureHistory hist = shFactory.open(logName, "r");
        
        ReplayLayer<InetSocketAddress> replay = new ReplayLayer<InetSocketAddress>(serializer,hashProv,hist,addr,startTime,pn.getEnvironment());
        replay.makeProgress();
        replayers.add(replay);
        return replay;
      }
      
    };

    EventSimulator sim = new EventSimulator(env,env.getRandomSource(),simLogger) {
      @Override
      protected boolean simulate() throws InterruptedException {
        boolean ret = super.simulate();
        try {
          for (ReplayLayer<InetSocketAddress> replay : replayers) {
            replay.makeProgress();
          }
        } catch (IOException ioe) {
          simLogger.logException("makeProgress() threw ", ioe);
        }
        return ret;
      }      
    };

    sim.setMaxSpeed(0.1f);
    sim.start();
    
    NodeHandle bootHandle = ((SocketPastryNodeFactory) factory).getNodeHandle(bootaddress);

    // construct a node, passing the null boothandle on the first loop will
    // cause the node to start its own ring
    PastryNode node = factory.newNode((rice.pastry.NodeHandle) bootHandle);
    
    
    // this is an example of th enew way
    //PastryNode node = factory.newNode(nidFactory.generateNodeId());
    //node.getBootstrapper().boot(Collections.singleton(bootaddress));
    
    // the node may require sending several messages to fully boot into the ring
    synchronized(node) {
      while(!node.isReady() && !node.joinFailed()) {
        // delay so we don't busy-wait
        node.wait(500);
        
        // abort if can't join
        if (node.joinFailed()) {
          throw new IOException("Could not join the FreePastry ring.  Reason:"+node.joinFailedReason()); 
        }
      }       
    }
    
    System.out.println("Finished creating new node: " + node);
    
    // construct a new scribe application
    MyScribeClient app = new MyScribeClient(node);
  
    // for all the rest just subscribe
    app.subscribe();
  
    // now, print the tree
    env.getTimeSource().sleep(5000);
  
    env.getTimeSource().sleep(15000);
  
    env.destroy();    
  }


  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {
//    System.out.println(args[0]+" "+args[1]+" "+args[2]+" "+args[3]);
    String hex = args[0];
    InetAddress a = InetAddress.getByName(args[1]);
    int startPort = Integer.decode(args[2]).intValue();
    InetSocketAddress addr = new InetSocketAddress(a,startPort+1);
    InetSocketAddress bootaddress = new InetSocketAddress(a,startPort);
    long startTime = Long.decode(args[3]).longValue();
    long randSeed = Long.decode(args[4]).longValue();
    
    replayNode(Id.build(hex), addr, bootaddress, startTime, randSeed);

  }

}
