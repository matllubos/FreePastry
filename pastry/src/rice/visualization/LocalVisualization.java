package rice.visualization;

import java.io.*;
import java.net.*;
import java.util.*;

import rice.pastry.NodeId;
import rice.pastry.dist.DistNodeHandle;
import rice.visualization.client.*;
import rice.visualization.data.Data;
import rice.visualization.data.DataProvider;

public class LocalVisualization implements DataProvider {
  
  public static int PORT_OFFSET = 3847;
  public static int REFRESH_TIME = 1000;
  
  protected LocalVisualizationFrame frame;
  
  protected DistNodeHandle handle;
  
  protected Data data;
  
  protected VisualizationClient client;

  protected boolean die = false;
  
  public LocalVisualization(DistNodeHandle handle) {
    this.handle = handle;
    this.frame = new LocalVisualizationFrame(this);
    
    Thread t = new Thread() {
      public void run() {
        try {
          while (! die) {
            Thread.currentThread().sleep(REFRESH_TIME);
            updateData();
            frame.repaint();
          }
          
          if (client != null)
            client.close();
          
          System.out.println("Visualization Thread now dying...");
        } catch (Exception e) {
          System.out.println(e);
        }
      }
    };
    
    t.start(); 
  }
  
  public Data getData() {
    return data;
  }
  
  public void exit() {
    die = true; 
  }
  
  protected void updateData() throws IOException {
    if (client == null) {
      InetSocketAddress address = new InetSocketAddress(handle.getAddress().getAddress(), handle.getAddress().getPort() + PORT_OFFSET);
      client = new VisualizationClient(address);
      client.connect();
      frame.nodeSelected(new Node(null, null), client.getData());
    }
        
    this.data = client.getData();
    
    if (this.data == null)
      throw new IOException("Data was null - likely disconnected!");
  }
}
