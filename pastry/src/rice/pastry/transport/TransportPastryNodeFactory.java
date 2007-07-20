package rice.pastry.transport;

import java.io.IOException;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.params.Parameters;
import rice.environment.random.RandomSource;
import rice.p2p.commonapi.CancellableTask;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.NodeHandleFactory;
import rice.pastry.PastryNodeFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.MessageDispatch;
import rice.pastry.routing.RouteSet;
import rice.pastry.routing.RoutingTable;
import rice.pastry.standard.ConsistentJoinProtocol;
import rice.pastry.standard.PeriodicLeafSetProtocol;
import rice.pastry.standard.RapidRerouter;
import rice.pastry.standard.StandardRouteSetProtocol;
import rice.pastry.standard.StandardRouter;

public abstract class TransportPastryNodeFactory extends PastryNodeFactory {

  /**
   * Large period (in seconds) means infrequent, 0 means never.
   */
  protected int leafSetMaintFreq;

  protected int routeSetMaintFreq;


  /**
   * Constructor.
   * 
   * Here is order for bind address 1) bindAddress parameter 2) if bindAddress
   * is null, then parameter: socket_bindAddress (if it exists) 3) if
   * socket_bindAddress doesn't exist, then InetAddress.getLocalHost()
   * 
   * @param nf The factory for building node ids
   * @param bindAddress which address to bind to
   * @param startPort The port to start creating nodes on
   * @param env The environment.
   */
  public TransportPastryNodeFactory(Environment env) {
    super(env);
    Parameters params = env.getParameters();
    leafSetMaintFreq = params.getInt("pastry_leafSetMaintFreq");
    routeSetMaintFreq = params.getInt("pastry_routeSetMaintFreq");
  }
    
  protected TLPastryNode nodeHandleHelper(Id nodeId, Environment environment, Object localNodeData) throws IOException {
    TLPastryNode pn = new TLPastryNode(nodeId, environment);    
    final NodeHandleFactory handleFactory = getNodeHandleFactory(pn);
    final NodeHandle localhandle = getLocalHandle(pn, handleFactory, localNodeData);    
    
    TLDeserializer deserializer = new TLDeserializer(handleFactory, environment);
  
    MessageDispatch msgDisp = new MessageDispatch(pn);
    RoutingTable routeTable = new RoutingTable(localhandle, rtMax, rtBase,
        pn);
    LeafSet leafSet = new LeafSet(localhandle, lSetSize, routeTable);
    StandardRouter router = new RapidRerouter(pn, msgDisp);
    pn.setElements(localhandle, msgDisp, leafSet, routeTable, router);
  
    
    NodeHandleAdapter nha = getNodeHanldeAdapter(pn, handleFactory, deserializer);
    
    pn.setSocketElements(localhandle, leafSetMaintFreq, routeSetMaintFreq, 
        nha, nha, nha, deserializer, handleFactory, nha);
  
  //  final Logger lLogger = pn.getEnvironment().getLogManager().getLogger(TransportPastryNodeFactory.class, null);
  //  identity.getUpperIdentity().addLivenessListener(new LivenessListener<TransportLayerNodeHandle<MultiInetSocketAddress>>() {    
  //    public void livenessChanged(
  //        TransportLayerNodeHandle<MultiInetSocketAddress> i, int val) {
  //      if (val != 1) {
  //        lLogger.log("liveness:"+i+" "+val);
  //      }
  //    }    
  //  });
  //  
  //  ltl.addPingListener(new PingListener<SourceRoute<MultiInetSocketAddress>>() {    
  //    public void pingResponse(SourceRoute<MultiInetSocketAddress> i, int rtt, Map<String, Integer> options) {
  //      lLogger.log("response:"+i);
  //    }    
  //    public void pingReceived(SourceRoute<MultiInetSocketAddress> i, Map<String, Integer> options) {
  //      lLogger.log("ping:"+i);
  //    }    
  //  });
    
    StandardRouteSetProtocol rsProtocol = new StandardRouteSetProtocol(pn,
        routeTable, environment);
    
    router.register();
    rsProtocol.register();
  
    PeriodicLeafSetProtocol lsProtocol = new PeriodicLeafSetProtocol(pn,
        localhandle, leafSet, routeTable);
    lsProtocol.register();
    ConsistentJoinProtocol jProtocol = new ConsistentJoinProtocol(pn,
        localhandle, routeTable, leafSet, lsProtocol);
    jProtocol.register();
    
    return pn;
  }

  
  protected abstract NodeHandle getLocalHandle(TLPastryNode pn, 
      NodeHandleFactory handleFactory, Object localNodeData) throws IOException;
  protected abstract NodeHandleAdapter getNodeHanldeAdapter(TLPastryNode pn, 
      NodeHandleFactory handleFactory, TLDeserializer deserializer)throws IOException;
  protected abstract NodeHandleFactory getNodeHandleFactory(TLPastryNode pn) throws IOException;

  @Override
  public LeafSet getLeafSet(NodeHandle handle) throws IOException {
    throw new IllegalStateException("Not implemented.");
  }

  @Override
  public CancellableTask getLeafSet(NodeHandle handle, Continuation c) {
    throw new IllegalStateException("Not implemented.");
  }

  @Override
  public int getProximity(NodeHandle local, NodeHandle handle) {
    throw new IllegalStateException("Not implemented.");
  }

  @Override
  public RouteSet[] getRouteRow(NodeHandle handle, int row) throws IOException {
    throw new IllegalStateException("Not implemented.");
  }

  @Override
  public CancellableTask getRouteRow(NodeHandle handle, int row, Continuation c) {
    throw new IllegalStateException("Not implemented.");
  }

}