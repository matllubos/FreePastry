package rice.pastry.direct;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.proximity.ProximityProvider;

import rice.environment.Environment;
import rice.environment.params.Parameters;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.ScheduledMessage;
import rice.pastry.messaging.Message;
import rice.pastry.transport.TLPastryNode;

public class NetworkSimulatorImpl implements NetworkSimulator {
  protected BasicNetworkSimulator<NodeHandle, RawMessage> simulator;
  protected RandomSource random;
  protected ProximityGenerator generator;
  protected LivenessProvider<NodeHandle> livenessProvider;
  
  // TODO: add listener to top level tl, to notify simulator listeners
  public NetworkSimulatorImpl(Environment env, ProximityGenerator generator) {
    Parameters params = env.getParameters();
    if (params.contains("pastry_direct_use_own_random")
        && params.getBoolean("pastry_direct_use_own_random")) {

      if (params.contains("pastry_direct_random_seed")
          && !params.getString("pastry_direct_random_seed").equalsIgnoreCase(
              "clock")) {
        this.random = new SimpleRandomSource(params
            .getLong("pastry_direct_random_seed"), env.getLogManager(),
            "direct");
      } else {
        this.random = new SimpleRandomSource(env.getLogManager(), "direct");
      }
    } else {
      this.random = env.getRandomSource();
    }
    generator.setRandom(random);
    this.generator = generator;
    simulator = new BasicNetworkSimulator<NodeHandle, RawMessage>(env, random);
    livenessProvider = simulator;
  }

  // ****************** passtrhougs to simulator ***************
  public Environment getEnvironment() {
    return simulator.getEnvironment();
  }

  public void setFullSpeed() {
    simulator.setFullSpeed();
  }

  public void setMaxSpeed(float rate) {
    simulator.setMaxSpeed(rate);
  }

  public void start() {
    simulator.start();
  }

  public void stop() {
    simulator.stop();
  }
  
//  /**
//   * find the closest NodeId to an input NodeId out of all NodeIds in the
//   * network
//   * 
//   * @param nid the input NodeId
//   * @return the NodeId closest to the input NodeId in the network
//   */
//  public DirectNodeHandle getClosest(DirectNodeHandle nh) {
//    Iterator<DirectNodeHandle> it = simulator.nodes.keySet().iterator();
//    DirectNodeHandle bestHandle = null;
//    float bestProx = Float.MAX_VALUE;
//    Id theId;
//
//    while (it.hasNext()) {
//      DirectPastryNode theNode = (DirectPastryNode) it.next();
//      float theProx = theNode.record.proximity(nh.getRemote().record);
//      theId = theNode.getNodeId();
//      if (!theNode.isAlive() || !theNode.isReady()
//          || theId.equals(nh.getNodeId())) {
//        continue;
//      }
//
//      if (theProx < bestProx) {
//        bestProx = theProx;
//        bestHandle = (DirectNodeHandle) theNode.getLocalHandle();
//      }
//    }
//    return bestHandle;
//  }
//
//
  // ************************* What is this? ********************** 
  private TestRecord testRecord;
  /**
   * get TestRecord
   * 
   * @return the returned TestRecord
   */
  public TestRecord getTestRecord() {
    return testRecord;
  }

  /**
   * set TestRecord
   * 
   * @param tr input TestRecord
   */
  public void setTestRecord(TestRecord tr) {
    testRecord = tr;
  }

  
  /************** SimulatorListeners handling *******************/
  List<SimulatorListener> listeners = new ArrayList<SimulatorListener>();  
  public boolean addSimulatorListener(SimulatorListener sl) {
    synchronized(listeners) {
      if (listeners.contains(sl)) return false;
      listeners.add(sl);
      return true;
    }
  }

  public boolean removeSimulatorListener(SimulatorListener sl) {
    synchronized(listeners) {
      return listeners.remove(sl);
    }
  }

  public void notifySimulatorListenersSent(Message m, NodeHandle from, NodeHandle to, int delay) {
    List<SimulatorListener> temp;
    
    // so we aren't holding a lock while iterating/calling
    synchronized(listeners) {
       temp = new ArrayList<SimulatorListener>(listeners);
    }
  
    for(SimulatorListener listener : temp) {
      listener.messageSent(m, from, to, delay);
    }
  }

  public void notifySimulatorListenersReceived(Message m, NodeHandle from, NodeHandle to) {
    List<SimulatorListener> temp;
    
    // so we aren't holding a lock while iterating/calling
    synchronized(listeners) {
       temp = new ArrayList<SimulatorListener>(listeners);
    }
  
    for(SimulatorListener listener : temp) {
      listener.messageReceived(m, from, to);
    }
  }

//  public ScheduledMessage deliverMessage(Message msg, TLPastryNode node, DirectNodeHandle from, int delay) {
//    node.deliverMess
//    return new ScheduledMessage(node, msg, simulator.deliverMessage(msg, (DirectNodeHandle)node.getLocalHandle(), from, delay));
//  }
//
//  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node, DirectNodeHandle from, int delay, int period) {
//    return new ScheduledMessage(node, msg, simulator.deliverMessage(msg, (DirectNodeHandle)node.getLocalHandle(), from, delay, period));
//  }
//
//  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node) {
//    return new ScheduledMessage(node, msg, simulator.deliverMessage(msg, (DirectNodeHandle)node.getLocalHandle()));
//  }
//
//  public ScheduledMessage deliverMessageFixedRate(Message msg, DirectPastryNode node, DirectNodeHandle from, int delay, int period) {
//    return new ScheduledMessage(node, msg, simulator.deliverMessageFixedRate(msg, (DirectNodeHandle)node.getLocalHandle(), from, delay, period));    
//  }

  public void destroy(DirectPastryNode dpn) {
    // TODO Auto-generated method stub
    
  }

  public CancellableTask enqueueDelivery(Delivery del, int delay) {
    // TODO Auto-generated method stub
    return null;
  }

  public NodeRecord generateNodeRecord() {
    return generator.generateNodeRecord();
  }

  public DirectNodeHandle getClosest(DirectNodeHandle nh) {
    // TODO Auto-generated method stub
    return null;
  }

  public boolean isAlive(DirectNodeHandle nh) {
    return simulator.isAlive(nh);
  }

  public float networkDelay(DirectNodeHandle a, DirectNodeHandle b) {
    return simulator.networkDelay(a, b);
  }

  public float proximity(DirectNodeHandle a, DirectNodeHandle b) {
    return simulator.proximity(a, b);
  }

  public void removeNode(TLPastryNode node) {
    // TODO Auto-generated method stub
    
  }

  public NodeRecord getNodeRecord(DirectNodeHandle handle) {
    return simulator.getNodeRecord(handle);
  }

  public LivenessProvider<NodeHandle> getLivenessProvider() {
    return livenessProvider;
  }

  public GenericNetworkSimulator<NodeHandle, RawMessage> getGenericSimulator() {
    return simulator;
  }

  public void registerNode(TLPastryNode dpn, NodeRecord nr) {
    simulator.registerIdentifier(dpn.getLocalHandle(), dpn.getTL(), nr);
  }
}
