package rice.visualization.proxy;

import rice.visualization.*;
import rice.visualization.server.*;
import rice.email.proxy.*;
import rice.proxy.*;

import java.io.*;
import java.net.*;

import rice.p2p.multiring.*;

import rice.pastry.dist.*;

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
          Visualization visualization = new Visualization((DistNodeHandle) pastry.getLocalHandle());
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
