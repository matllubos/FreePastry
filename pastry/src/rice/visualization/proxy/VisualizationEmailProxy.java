package rice.visualization.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import rice.email.proxy.EmailProxy;
import rice.p2p.multiring.MultiringNode;
import rice.pastry.dist.DistNodeHandle;
import rice.pastry.dist.DistPastryNode;
import rice.proxy.Parameters;
import rice.p2p.glacier.v2.GlacierImpl;
import rice.p2p.past.*;
import rice.p2p.aggregation.AggregationImpl;
import rice.persistence.*;
import rice.visualization.*;
import rice.visualization.Visualization;
import rice.visualization.server.*;

public class VisualizationEmailProxy extends EmailProxy {
    
  protected VisualizationServer server;
  protected VisualizationServer globalServer;
  
  protected InetSocketAddress serverAddress;
  protected InetSocketAddress globalServerAddress;
  
  public Parameters start(Parameters parameters) throws Exception { 
    super.start(parameters);
    
    if (parameters.getBooleanParameter("visualization_enable")) {
      DistPastryNode pastry = (DistPastryNode) ((MultiringNode) node).getNode();
      int visualizationPort = ((DistNodeHandle) pastry.getLocalHandle()).getAddress().getPort() + Visualization.PORT_OFFSET;
      
      sectionStart("Starting Visualization services");
      stepStart("Creating Visualization Server");
      try {
        this.serverAddress = new InetSocketAddress(InetAddress.getLocalHost(), 
                                                   visualizationPort);
      } catch (IOException e) {
        stepDone(FAILURE, e + "");
      }
      
      server = new VisualizationServer(serverAddress, pastry, immutableStorage, cert, new Object[] {pastry, immutablePast, immutableStorage});
      server.addPanelCreator(new OverviewPanelCreator(timer));
      NetworkActivityPanelCreator network = new NetworkActivityPanelCreator(timer);
      server.addPanelCreator(network);
      MessageDistributionPanelCreator message = new MessageDistributionPanelCreator();
      server.addPanelCreator(message);
      RecentMessagesPanelCreator recent = new RecentMessagesPanelCreator();
      server.addPanelCreator(recent);
      server.addPanelCreator(new PastryPanelCreator());
      server.addPanelCreator(new SourceRoutePanelCreator());
      server.addPanelCreator(new MultiPersistencePanelCreator(timer, new String[] {"Immutable", "Mutable", "Pending", "Delivered", "Glacier Immutable", "Glacier Mutable", "Aggregation Waiting"},
                                                              new StorageManagerImpl[] {immutableStorage, mutableStorage, pendingStorage, deliveredStorage, glacierImmutableStorage, glacierMutableStorage, aggrWaitingStorage}));
      server.addPanelCreator(new MultiPASTPanelCreator(timer, new String[] {"Immutable", "Mutable", "Pending", "Delivered"},
                                                       new PastImpl[] {(PastImpl) realImmutablePast, (PastImpl) mutablePast, pendingPast, deliveredPast}));
      server.addPanelCreator(new GCPanelCreator(timer, realImmutablePast));

      if (immutablePast instanceof AggregationImpl) {
        server.addPanelCreator(new AggregationPanelCreator(timer, (AggregationImpl) immutablePast)); 
        Past aggregateStore = ((AggregationImpl)immutablePast).getAggregateStore();
        if (aggregateStore instanceof GlacierImpl)
          server.addPanelCreator(new GlacierPanelCreator((GlacierImpl) aggregateStore));
      }
      
      server.addPanelCreator(new QueuePanelCreator(timer, DistPastryNode.QUEUE, rice.persistence.PersistentStorage.QUEUE));
      if (smtp != null)
        server.addPanelCreator(new EmailPanelCreator(timer, smtp));
      
      if (immutablePast instanceof DebugCommandHandler)
        server.addDebugCommandHandler((DebugCommandHandler)immutablePast);
      if (mutablePast instanceof DebugCommandHandler)
        server.addDebugCommandHandler((DebugCommandHandler)mutablePast);
      
      pastry.addNetworkListener(network);
      pastry.addNetworkListener(recent);
      pastry.addNetworkListener(message);
      stepDone(SUCCESS);
      
      try {
        stepStart("Starting Visualization Server on port " + visualizationPort);
        Thread t = new Thread(server, "Visualization Server Thread");
        t.start();
        stepDone(SUCCESS);
        
        if (parameters.getBooleanParameter("visualization_client_enable")) {
          stepStart("Launching Visualization Client");
          Ring[] r = new Ring[1];
          r[0] = new Ring("global", null, (DistNodeHandle) pastry.getLocalHandle());
          Visualization visualization = new Visualization(r);
          stepDone(SUCCESS);
        }
      } catch (Exception e) {
        System.err.println("ERROR: Unable to launch Visualization server - continuing - " + e);
      }
      
      
      if (globalNode != null) {
        DistPastryNode gpastry = (DistPastryNode) ((MultiringNode) globalNode).getNode();
        int globalVisualizationPort = ((DistNodeHandle) gpastry.getLocalHandle()).getAddress().getPort() + Visualization.PORT_OFFSET;

        stepStart("Creating Global Visualization Server");
        try {
          this.globalServerAddress = new InetSocketAddress(InetAddress.getLocalHost(), globalVisualizationPort);
        } catch (IOException e) {
          stepDone(FAILURE, e + "");
        }
        
        globalServer = new VisualizationServer(globalServerAddress, gpastry, null, globalCert, new Object[] {gpastry});
        globalServer.addPanelCreator(new OverviewPanelCreator(timer));
        NetworkActivityPanelCreator gnetwork = new NetworkActivityPanelCreator(timer);
        globalServer.addPanelCreator(gnetwork);
        MessageDistributionPanelCreator gmessage = new MessageDistributionPanelCreator();
        globalServer.addPanelCreator(gmessage);
        RecentMessagesPanelCreator grecent = new RecentMessagesPanelCreator();
        globalServer.addPanelCreator(grecent);
        globalServer.addPanelCreator(new PastryPanelCreator());
        globalServer.addPanelCreator(new SourceRoutePanelCreator());
        
        gpastry.addNetworkListener(gnetwork);
        gpastry.addNetworkListener(grecent);
        gpastry.addNetworkListener(gmessage);
        stepDone(SUCCESS);
        
        try {
          stepStart("Starting Global Visualization Server on port " + globalVisualizationPort);
          Thread t = new Thread(globalServer, "Global Visualization Server Thread");
          t.start();
          stepDone(SUCCESS);
        } catch (Exception e) {
          System.err.println("ERROR: Unable to launch Visualization server - continuing - " + e);
          stepDone(FAILURE);
        }
      }
    }
    
    dialogPrint("\n\nYour ePOST is now booted and ready.  You can connect your mail client\n" +
                "with the instructions shown on the http://www.epostmail.org website.\n");
    
    return parameters;
  }
  
  public static void main(String[] args) {
    VisualizationEmailProxy proxy = new VisualizationEmailProxy();
    proxy.start();
  }
  
}
