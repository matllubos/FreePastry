package rice.visualization.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import rice.email.proxy.EmailProxy;
import rice.p2p.multiring.MultiringNode;
import rice.pastry.dist.DistNodeHandle;
import rice.pastry.dist.DistPastryNode;
import rice.proxy.Parameters;
import rice.visualization.*;
import rice.visualization.Visualization;
import rice.visualization.server.MessageDistributionPanelCreator;
import rice.visualization.server.NetworkActivityPanelCreator;
import rice.visualization.server.OverviewPanelCreator;
import rice.visualization.server.PASTPanelCreator;
import rice.visualization.server.PastryPanelCreator;
import rice.visualization.server.RecentMessagesPanelCreator;
import rice.visualization.server.VisualizationServer;

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
      server.addPanelCreator(new OverviewPanelCreator());
      NetworkActivityPanelCreator network = new NetworkActivityPanelCreator();
      server.addPanelCreator(network);
      MessageDistributionPanelCreator message = new MessageDistributionPanelCreator();
      server.addPanelCreator(message);
      RecentMessagesPanelCreator recent = new RecentMessagesPanelCreator();
      server.addPanelCreator(recent);
      server.addPanelCreator(new PastryPanelCreator());
      server.addPanelCreator(new PASTPanelCreator());
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
