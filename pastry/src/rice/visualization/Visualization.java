package rice.visualization;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

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
  
  protected Node selectedNode = null;
  protected Ring selectedRing;
  
  protected Node highlightedNode = null;
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
      bootstrapNodes[i].setVisualization(this);
    }
    selectedRing = bootstrapNodes[0];
    bootstrapNodes[0].select();
    
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
  
  public Node getSelectedNode() {
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
  
  public Node getHighlighted() {
    return highlightedNode;
  }
  
  protected void refreshData() {
    Node handle = getSelectedNode();
    if (handle != null) {
      getData(handle);
      frame.repaint();
    }
  }
  
  public Node[] getNodes() {
    return getNodes(selectedRing);
  }
  
  public Node[] getNodes(Ring r) {
    return r.getNodes();
  }
  
  
  
  public Data getData() {
    return data;
  }
  
  public void setHighlighted(Node node, Ring ring) {
    if ((highlightedNode != node) || (highlightedRing != ring)) {
      highlightedNode = node;
      highlightedRing = ring;
      frame.nodeHighlighted(node);
    }
  }
  
//  public void setSelected(InetSocketAddress addr) {
//    setSelected(addr,selectedRing);
//  }
  
  public void setSelected(InetSocketAddress addr, Ring r) {
    Node[] handles = r.getNodes();
    
    for (int i=0; i<handles.length; i++) {
      if (handles[i].handle.getAddress().equals(addr)) {
        setSelected(handles[i]);
        return;
      }
    }
  }

  public Ring getRoot() {
    return getRingByIndex(0);
  }

  class MyTimerTask extends TimerTask {
		public void run() {
      curStep++;
      getRoot().rootCenterIsStale = true;
      
      if (curStep >= NUM_STEPS) {
        cancel();		
        myTask = null;	
      }
      frame.pastryRingPanel.repaint();
		}    
  }

  int NUM_STEPS = 30;
  int curStep = NUM_STEPS;
  MyTimerTask myTask = null;
  Timer timer = new Timer();
  private void startAnimation() {
    curStep = 0;
    if (myTask != null) {
      myTask.cancel();
      myTask = null;
    }
    myTask = new MyTimerTask();
    timer.schedule(myTask,30,30);
  }

  public void selectRing(Ring r) {
    selectedRing = r;
    r.select();
    startAnimation();
    
    boolean repaint = false;
    if (selectedNode == null)
      repaint = true;
    setSelected((Node)null);
    if (repaint) {
      frame.nodeSelected(selectedNode);
    }
  }  

//  public void setSelected(NodeId id) {
//    setSelected(id,selectedRing);
//  }  

  public void setSelected(NodeId id, Ring r) {
    Node[] handles = r.getNodes();
      
    for (int i=0; i<handles.length; i++) {
      if (handles[i].handle.getNodeId().equals(id)) {
        setSelected(handles[i]);
        return;
      }
    }
  }
  
  public Node getNode(int x, int y) {
    // try the selected ring
    Node n = selectedRing.getNode(x,y);
    Ring root = getRingByIndex(0);
    // try the main ring, which will recursively try all rings
    if ((n == null) && (selectedRing != root)) { 
      n = root.getNode(x,y);
    }
    return n;
  }
  
  public Ring getRing(int x, int y) {
    // try the selected ring
    Ring root = getRingByIndex(0);
    // try the main ring, which will recursively try all rings
    Ring sel = root.getRing(x,y);
    return sel;
  }
  
  public void setSelected(Node node) {
    //Thread.dumpStack();
    if ((selectedNode == null) || (! selectedNode.equals(node))) {
      selectedNode = node;
      frame.nodeSelected(node);
    }
  }
  
  public int getState(Node node) {
    if (node.ring.clients.get(node.handle.getNodeId()) != null)
      return ((VisualizationClient) node.ring.clients.get(node.handle.getNodeId())).getState();
    else 
      return STATE_UNKNOWN;
  }
  
  public Node[] getNeighbors(Node handle) {
    if (handle.neighbors.size() == 0)
      return new Node[0];
    
    return (Node[]) handle.neighbors.toArray(new Node[0]);
  }
  
  public synchronized UpdateJarResponse updateJar(File[] files, String executionString) {
    if (selectedNode == null) {
      throw new RuntimeException("No Node Selected");
    }
    VisualizationClient client = (VisualizationClient) selectedRing.clients.get(selectedNode.handle.getNodeId());
    return client.updateJar(files,executionString);    
  }
  
  public void openDebugConsole() {
    if (selectedNode == null) {
      throw new RuntimeException("No Node Selected");
    }
    VisualizationClient client = (VisualizationClient) selectedRing.clients.get(selectedNode.handle.getNodeId());
    DebugCommandFrame consoleFrame = new DebugCommandFrame(client);
    consoleFrame.pack();
  }

//  protected Data getData(DistNodeHandle handle) {
//    return getData(handle,selectedRing);
//  }

  protected Data getData(Node handle) {
    Ring r = handle.ring;
      VisualizationClient client = (VisualizationClient) r.clients.get(handle.handle.getNodeId());
      
      if (client == null) {
        InetSocketAddress address = new InetSocketAddress(handle.handle.getAddress().getAddress(), handle.handle.getAddress().getPort() + PORT_OFFSET);
        client = new VisualizationClient(address);
        r.clients.put(handle.handle.getId(), client);
        client.connect();
      }
      
      DistNodeHandle[] handles = client.getHandles();
      
      if (handles == null) {
        handle.neighbors = new Vector(); // clear the neighbors
        return new Data();
      } else {
        handle.neighbors = new Vector(); // clear the neighbors
//        r.neighbors.put(handle.handle.getId(), handles);
      
        for (int i=0; i<handles.length; i++) 
          handle.neighbors.add(r.addNode(handles[i]));
        
        data = client.getData();
      
        return data;
      }
  }
}
