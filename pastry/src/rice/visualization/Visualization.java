package rice.visualization;

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;

import rice.visualization.data.*;
import rice.visualization.client.*;
import rice.visualization.server.*;

import rice.p2p.commonapi.*;
import rice.pastry.commonapi.*;
import rice.pastry.dist.*;
import rice.pastry.*;

public class Visualization {
  
  public static int PORT_OFFSET = 3847;
  
  public static int STATE_ALIVE = VisualizationClient.STATE_ALIVE;
  public static int STATE_DEAD = VisualizationClient.STATE_DEAD;
  public static int STATE_UNKNOWN = VisualizationClient.STATE_UNKNOWN;
  public static int STATE_FAULT = VisualizationClient.STATE_FAULT;
  
  public static int REFRESH_TIME = 1000;
  
  protected Vector nodes;
  
  protected VisualizationFrame frame;
  
  protected DistNodeHandle selected = null;
  
  protected DistNodeHandle highlighted = null;
  
  protected Hashtable clients;
  
  protected Hashtable neighbors;
  
  protected Data data;
    
  public Visualization(DistNodeHandle handle) {
    this.nodes = new Vector();
    this.frame = new VisualizationFrame(this);
    this.clients = new Hashtable();
    this.neighbors = new Hashtable();
    
    Thread t = new Thread() {
      public void run() {
        try {
          while (true) {
            Thread.currentThread().sleep(REFRESH_TIME);
            refreshData();
          }
        } catch (Exception e) {
          System.out.println(e);
        }
      }
    };
    
    t.start(); 
    
    addNode(handle);
  }
  
  public void addNode(DistNodeHandle handle) {
    DistNodeHandle[] distnodes = getNodes();
    
    for (int i=0; i<distnodes.length; i++) 
      if (distnodes[i].getNodeId().equals(handle.getNodeId()))
        return;
    
    nodes.addElement(handle);
  }
  
  public DistNodeHandle getSelected() {
    return selected;
  }
  
  public DistNodeHandle getHighlighted() {
    return highlighted;
  }
  
  protected void refreshData() {
    DistNodeHandle handle = getSelected();
    if (handle != null) {
      getData(handle);
      frame.repaint();
    }
  }
  
  public Data getData() {
    return data;
  }
  
  public void setHighlighted(DistNodeHandle node) {
    if (highlighted != node) {
      highlighted = node;
      frame.nodeHighlighted(node);
    }
  }
  
  public void setSelected(InetSocketAddress addr) {
    DistNodeHandle[] handles = getNodes();
    
    for (int i=0; i<handles.length; i++) {
      if (handles[i].getAddress().equals(addr)) {
        setSelected(handles[i]);
        return;
      }
    }
  }
  
  public void setSelected(NodeId id) {
    DistNodeHandle[] handles = getNodes();
      
    for (int i=0; i<handles.length; i++) {
      if (handles[i].getNodeId().equals(id)) {
        setSelected(handles[i]);
        return;
      }
    }
  }
  
  public void setSelected(DistNodeHandle node) {
    if ((selected == null) || (! selected.equals(node))) {
      selected = node;
      frame.nodeSelected(node);
    }
  }
  
  public int getState(DistNodeHandle node) {
    if (clients.get(node.getNodeId()) != null)
      return ((VisualizationClient) clients.get(node.getNodeId())).getState();
    else 
      return STATE_UNKNOWN;
  }
  
  public DistNodeHandle[] getNodes() {
    return (DistNodeHandle[]) nodes.toArray(new DistNodeHandle[0]);
  }
  
  public DistNodeHandle[] getNeighbors(DistNodeHandle handle) {
    if (neighbors.get(handle.getId()) == null)
      return new DistNodeHandle[0];
    
    return (DistNodeHandle[]) neighbors.get(handle.getId());
  }
  
  public synchronized UpdateJarResponse updateJar(File[] files, String executionString) {
    if (selected == null) {
      throw new RuntimeException("No Node Selected");
    }
    VisualizationClient client = (VisualizationClient) clients.get(selected.getNodeId());
    return client.updateJar(files,executionString);    
  }
  
  public void openDebugConsole() {
    if (selected == null) {
      throw new RuntimeException("No Node Selected");
    }
    VisualizationClient client = (VisualizationClient) clients.get(selected.getNodeId());
    DebugCommandFrame consoleFrame = new DebugCommandFrame(client);
    consoleFrame.pack();
  }

  protected Data getData(DistNodeHandle handle) {
      VisualizationClient client = (VisualizationClient) clients.get(handle.getNodeId());
      
      if (client == null) {
        InetSocketAddress address = new InetSocketAddress(handle.getAddress().getAddress(), handle.getAddress().getPort() + PORT_OFFSET);
        client = new VisualizationClient(address);
        clients.put(handle.getId(), client);
        client.connect();
      }
      
      DistNodeHandle[] handles = client.getHandles();
      
      if (handles == null) {
        neighbors.remove(handle.getId());
        return new Data();
      } else {
        neighbors.put(handle.getId(), handles);
      
        for (int i=0; i<handles.length; i++) 
          addNode(handles[i]);
        
        data = client.getData();
      
        return data;
      }
  }
}
