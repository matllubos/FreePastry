package rice.visualization.proxy;

import rice.visualization.*;
import rice.visualization.server.*;
import rice.email.proxy.*;

import java.io.*;
import java.net.*;

import rice.pastry.dist.*;

public class VisualizationEmailProxy extends EmailProxy {
    
  protected VisualizationServer server;
  
  protected InetSocketAddress serverAddress;
  
  protected int visualizationPort;
  
  protected static boolean visualization = false;
  
  public VisualizationEmailProxy(String[] args) {
    super(args);
  }
  
  public void start() {
    super.start();
    
    visualizationPort = ((DistNodeHandle) pastry.getLocalHandle()).getAddress().getPort() + Visualization.PORT_OFFSET;
    
    sectionStart("Starting Visualization services");
    stepStart("Creating Visualization Server");
    try {
      this.serverAddress = new InetSocketAddress(InetAddress.getLocalHost(), 
                                                 visualizationPort);
    } catch (IOException e) {
      stepDone(FAILURE, e + "");
    }
    
    server = new VisualizationServer(serverAddress, pastry, new Object[] {pastry, past, storage});
    server.addPanelCreator(new OverviewPanelCreator());
    NetworkActivityPanelCreator network = new NetworkActivityPanelCreator();
    server.addPanelCreator(network);
    MessageDistributionPanelCreator message = new MessageDistributionPanelCreator();
    server.addPanelCreator(message);
    RecentMessagesPanelCreator recent = new RecentMessagesPanelCreator();
    server.addPanelCreator(recent);
    server.addPanelCreator(new PastryPanelCreator());
    server.addPanelCreator(new PASTPanelCreator());
    
    ((DistPastryNode) pastry).addNetworkListener(network);
    ((DistPastryNode) pastry).addNetworkListener(recent);
    ((DistPastryNode) pastry).addNetworkListener(message);
    stepDone(SUCCESS);
    
    stepStart("Starting Visualization Server on port " + visualizationPort);
    Thread t = new Thread(server);
    t.start();
    stepDone(SUCCESS);

    if (visualization) {
      stepStart("Launching Visualization Client");
      Visualization visualization = new Visualization((DistNodeHandle) pastry.getLocalHandle());
      stepDone(SUCCESS);
    }
  }
  
  public void parseArgs(String[] args) {
    super.parseArgs(args);
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-visualization")) {
        visualization = true;
        break;
      }
    }
  }
  
  public static void main(String[] args) {
    VisualizationEmailProxy proxy = new VisualizationEmailProxy(args);
    proxy.start();
  }
  
}