package rice.visualization;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.Iterator;

import rice.pastry.NodeId;
import rice.pastry.dist.DistNodeHandle;
import rice.visualization.client.UpdateJarResponse;
import rice.visualization.client.VisualizationClient;
import rice.visualization.data.Data;

public class Visualization {
  
  public static int PORT_OFFSET = 3847;
  
  public static int STATE_ALIVE = VisualizationClient.STATE_ALIVE;
  public static int STATE_DEAD = VisualizationClient.STATE_DEAD;
  public static int STATE_UNKNOWN = VisualizationClient.STATE_UNKNOWN;
  public static int STATE_FAULT = VisualizationClient.STATE_FAULT;
  
  public static int REFRESH_TIME = 1000;
    
  /**
   * String name to Ring
   */
  protected Hashtable rings;
  
  /**
   * Parallel data structure to provide order
   */
  protected Ring[] ringArray;
  
  protected VisualizationFrame frame;
  
  protected DistNodeHandle selectedNode = null;
  protected Ring selectedRing;
  
  protected DistNodeHandle highlightedNode = null;
  protected Ring highlightedRing = null;
  
  protected Data data;
    

  public Visualization(Ring[] bootstrapNodes) {
    for (int i = 0; i < bootstrapNodes.length; i++) {
      System.out.println(bootstrapNodes[i]);
    }        
    ringArray = bootstrapNodes;

    this.rings = new Hashtable();
    for (int i = 0; i < bootstrapNodes.length; i++) {
      rings.put(bootstrapNodes[i].name,bootstrapNodes[i]);
    }
    selectedRing = bootstrapNodes[0];
    
    this.frame = new VisualizationFrame(this);
    
    
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
    
    //addNode(handle);
  }
  
  public DistNodeHandle getSelectedNode() {
    return selectedNode;
  }
  
  public Ring getSelectedRing() {
    return selectedRing;
  }

  /**
   * @return The number of rings.
   */  
  public int getNumRings() {
    return rings.size();
  }
  
  /**
   * This is kind of a silly way to lookup rings, but hey, this is graphics programming.
   * @param index
   * @return the index'th ring.
   */
  public Ring getRingByIndex(int index) {
    return ringArray[index];
  }
  
  public DistNodeHandle getHighlighted() {
    return highlightedNode;
  }
  
  protected void refreshData() {
    DistNodeHandle handle = getSelectedNode();
    Ring r = getSelectedRing();
    if (handle != null) {
      getData(handle,r);
      frame.repaint();
    }
  }
  
  public DistNodeHandle[] getNodes() {
    return getNodes(selectedRing);
  }
  
  public DistNodeHandle[] getNodes(Ring r) {
    return r.getNodes();
  }
  
  
  
  public Data getData() {
    return data;
  }
  
  public void setHighlighted(DistNodeHandle node, Ring r) {
    if ((highlightedNode != node) || (highlightedRing != r)) {
      highlightedNode = node;
      highlightedRing = r;
      frame.nodeHighlighted(node,r);
    }
  }
  
//  public void setSelected(InetSocketAddress addr) {
//    setSelected(addr,selectedRing);
//  }
  
  public void setSelected(InetSocketAddress addr, Ring r) {
    DistNodeHandle[] handles = r.getNodes();
    
    for (int i=0; i<handles.length; i++) {
      if (handles[i].getAddress().equals(addr)) {
        setSelected(handles[i], r);
        return;
      }
    }
  }

  public void selectRing(Ring r) {
    selectedRing = r;
//    boolean repaint = false;
//    if (selectedNode == null)
//      repaint = true;
    setSelected((DistNodeHandle)null, r);
//    if (repaint) {
//      frame.nodeSelected(selectedNode, r);
//    }
  }  

//  public void setSelected(NodeId id) {
//    setSelected(id,selectedRing);
//  }  

  public void setSelected(NodeId id, Ring r) {
    DistNodeHandle[] handles = r.getNodes();
      
    for (int i=0; i<handles.length; i++) {
      if (handles[i].getNodeId().equals(id)) {
        setSelected(handles[i],r);
        return;
      }
    }
  }
  
  public void setSelected(DistNodeHandle node, Ring r) {
    if ((selectedNode == null) || (! selectedNode.equals(node)) || (! selectedRing.equals(r))) {
      selectedNode = node;
      selectedRing = r;
      frame.nodeSelected(node,r);
    }
  }
  
  public int getState(DistNodeHandle node, Ring r) {
    if (r.clients.get(node.getNodeId()) != null)
      return ((VisualizationClient) r.clients.get(node.getNodeId())).getState();
    else 
      return STATE_UNKNOWN;
  }
  
  public DistNodeHandle[] getNeighbors(DistNodeHandle handle, Ring r) {
    if (r.neighbors.get(handle.getId()) == null)
      return new DistNodeHandle[0];
    
    return (DistNodeHandle[]) r.neighbors.get(handle.getId());
  }
  
  public synchronized UpdateJarResponse updateJar(File[] files, String executionString) {
    if (selectedNode == null) {
      throw new RuntimeException("No Node Selected");
    }
    VisualizationClient client = (VisualizationClient) selectedRing.clients.get(selectedNode.getNodeId());
    return client.updateJar(files,executionString);    
  }
  
  public void openDebugConsole() {
    if (selectedNode == null) {
      throw new RuntimeException("No Node Selected");
    }
    VisualizationClient client = (VisualizationClient) selectedRing.clients.get(selectedNode.getNodeId());
    DebugCommandFrame consoleFrame = new DebugCommandFrame(client);
    consoleFrame.pack();
  }

//  protected Data getData(DistNodeHandle handle) {
//    return getData(handle,selectedRing);
//  }

  protected Data getData(DistNodeHandle handle, Ring r) {
      VisualizationClient client = (VisualizationClient) r.clients.get(handle.getNodeId());
      
      if (client == null) {
        InetSocketAddress address = new InetSocketAddress(handle.getAddress().getAddress(), handle.getAddress().getPort() + PORT_OFFSET);
        client = new VisualizationClient(address);
        r.clients.put(handle.getId(), client);
        client.connect();
      }
      
      DistNodeHandle[] handles = client.getHandles();
      
      if (handles == null) {
        r.neighbors.remove(handle.getId());
        return new Data();
      } else {
        r.neighbors.put(handle.getId(), handles);
      
        for (int i=0; i<handles.length; i++) 
          r.addNode(handles[i]);
        
        data = client.getData();
      
        return data;
      }
  }
}
