package rice.visualization.proxy;

import rice.visualization.*;
import rice.visualization.server.*;

import java.io.*;
import java.net.*;

import rice.pastry.dist.*;

public class VisualizationProxy {
    
  protected DistNodeHandle handle;
  
  protected DistPastryNodeFactory factory;
  
  protected Visualization visualization;
    
  public VisualizationProxy(String[] args) {
    parseArgs(args);
  }
  
  public void start() {
    visualization = new Visualization(handle);
  }
  
  public void parseArgs(String[] args) {
    int protocol = DistPastryNodeFactory.PROTOCOL_WIRE;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-protocol") && i+1 < args.length) {
        String s = args[i+1];
        
        if (s.equalsIgnoreCase("wire"))
          protocol = DistPastryNodeFactory.PROTOCOL_WIRE;
        else if (s.equalsIgnoreCase("rmi"))
          protocol = DistPastryNodeFactory.PROTOCOL_RMI;
        else if (s.equalsIgnoreCase("socket"))
          protocol = DistPastryNodeFactory.PROTOCOL_SOCKET;
        else
          System.out.println("ERROR: Unsupported protocol: " + s);
        
        break;
      }
    } 
    
    factory = DistPastryNodeFactory.getFactory(null, protocol, 0);
    
    String bootstrap_host = "localhost";
    int bootstrap_port = 5009;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i+1 < args.length) {
        String str = args[i+1];
        int index = str.indexOf(':');
        if (index == -1) {
          bootstrap_host = str;
        } else {
          bootstrap_host = str.substring(0, index);
          int tmpport = Integer.parseInt(str.substring(index + 1));
          if (tmpport > 0) {
            bootstrap_port = tmpport;
          }
        }
        
        break;
      }
    }
    
    handle = (DistNodeHandle) factory.generateNodeHandle(new InetSocketAddress(bootstrap_host, bootstrap_port));
  }
  
  public static void main(String[] args) {
    VisualizationProxy proxy = new VisualizationProxy(args);
    proxy.start();
  }
  
}