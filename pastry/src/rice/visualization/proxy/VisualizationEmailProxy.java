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
import rice.visualization.*;
import rice.visualization.Visualization;
import rice.visualization.server.*;

public class VisualizationEmailProxy extends EmailProxy {
    
  protected VisualizationServer server;
  
  protected InetSocketAddress serverAddress;

  public static String[][] DEFAULT_PARAMETERS = new String[][] {{"visualization_enable", "false"},
  {"visualization_port", "10002"},
  {"visualization_client_enable", "false"}
  };
  
  public Parameters start(Parameters parameters) throws Exception { 
    initializeParameters(super.start(parameters), DEFAULT_PARAMETERS);
    
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
      
      server = new VisualizationServer(serverAddress, pastry, immutableStorage, new Object[] {pastry, immutablePast, immutableStorage});
      server.addPanelCreator(new OverviewPanelCreator(timer));
      NetworkActivityPanelCreator network = new NetworkActivityPanelCreator(timer);
      server.addPanelCreator(network);
      MessageDistributionPanelCreator message = new MessageDistributionPanelCreator();
      server.addPanelCreator(message);
      RecentMessagesPanelCreator recent = new RecentMessagesPanelCreator();
      server.addPanelCreator(recent);
      server.addPanelCreator(new PastryPanelCreator());
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
          r[0] = new Ring("global",(DistNodeHandle) pastry.getLocalHandle());
          Visualization visualization = new Visualization(r);
          stepDone(SUCCESS);
        }
      } catch (Exception e) {
        System.err.println("ERROR: Unable to launch Visualization server - continuing - " + e);
      }
    }
    
    return parameters;
  }
  
  public static void main(String[] args) {
    VisualizationEmailProxy proxy = new VisualizationEmailProxy();
    proxy.start();
  }
  
}
