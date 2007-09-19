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
package rice.pastry.transport;

import java.io.IOException;
import java.util.Collection;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.params.Parameters;
import rice.environment.random.RandomSource;
import rice.p2p.commonapi.CancellableTask;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.NodeHandleFactory;
import rice.pastry.PastryNodeFactory;
import rice.pastry.boot.Bootstrapper;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.MessageDispatch;
import rice.pastry.pns.PNSApplication;
import rice.pastry.routing.RouteSet;
import rice.pastry.routing.RoutingTable;
import rice.pastry.standard.ConsistentJoinProtocol;
import rice.pastry.standard.PeriodicLeafSetProtocol;
import rice.pastry.standard.ProximityNeighborSelector;
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
    
    TLDeserializer deserializer = getTLDeserializer(handleFactory,pn);
  
    MessageDispatch msgDisp = new MessageDispatch(pn);
    RoutingTable routeTable = new RoutingTable(localhandle, rtMax, rtBase,
        pn);
    LeafSet leafSet = new LeafSet(localhandle, lSetSize, routeTable);
    StandardRouter router = new RapidRerouter(pn, msgDisp);
    pn.setElements(localhandle, msgDisp, leafSet, routeTable, router);
  
    
    NodeHandleAdapter nha = getNodeHanldeAdapter(pn, handleFactory, deserializer);

    pn.setSocketElements(localhandle, leafSetMaintFreq, routeSetMaintFreq, 
        nha, nha, nha, deserializer, handleFactory);
    
    ProximityNeighborSelector pns = getProximityNeighborSelector(pn);
    
    Bootstrapper bootstrapper = getBootstrapper(pn, nha, handleFactory, pns);
    
    pn.setBootstrapper(bootstrapper);
  
    
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

  
  protected TLDeserializer getTLDeserializer(NodeHandleFactory handleFactory, TLPastryNode pn) {
    TLDeserializer deserializer = new TLDeserializer(handleFactory, pn.getEnvironment());
    return deserializer;
  }

  /**
   * Can be overridden.
   * @param pn
   * @return
   */
  protected ProximityNeighborSelector getProximityNeighborSelector(TLPastryNode pn) {    
    if (environment.getParameters().getBoolean("transport_use_pns")) {
      PNSApplication pns = new PNSApplication(pn);
      pns.register();
      return pns;
    }
  
    // do nothing
    return new ProximityNeighborSelector(){    
      public void getNearHandles(Collection<NodeHandle> bootHandles, Continuation<Collection<NodeHandle>, Exception> deliverResultToMe) {
        deliverResultToMe.receiveResult(bootHandles);
      }    
    };
  }
    
  protected abstract NodeHandle getLocalHandle(TLPastryNode pn, 
      NodeHandleFactory handleFactory, Object localNodeData) throws IOException;
  protected abstract NodeHandleAdapter getNodeHanldeAdapter(TLPastryNode pn, 
      NodeHandleFactory handleFactory, TLDeserializer deserializer)throws IOException;
  protected abstract NodeHandleFactory getNodeHandleFactory(TLPastryNode pn) throws IOException;
  protected abstract Bootstrapper getBootstrapper(TLPastryNode pn, 
      NodeHandleAdapter tl, 
      NodeHandleFactory handleFactory,
      ProximityNeighborSelector pns);  
}
