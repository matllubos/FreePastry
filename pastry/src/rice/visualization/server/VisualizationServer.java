package rice.visualization.server;

import java.io.*;
import java.net.*;

import rice.visualization.*;
import rice.visualization.data.*;
import rice.visualization.client.*;
import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.awt.*;
import java.util.*;
import javax.swing.*;

public class VisualizationServer implements Runnable {
  
  protected InetSocketAddress address;
  
  protected Object[] objects;
  
  protected Vector panelCreators;
  
  protected ServerSocket server;
  
  protected PastryNode node;
    
  public VisualizationServer(InetSocketAddress address, PastryNode node, Object[] objects) {
    this.address = address;
    this.objects = objects;
    this.node = node;
    this.panelCreators = new Vector();
  }
  
  public void addPanelCreator(PanelCreator creator) {
    panelCreators.addElement(creator);
  }
  
  public void run() {
    try {    
      server = new ServerSocket();
      server.bind(address);

      while (true) {
        final Socket socket = server.accept();
        
        Thread t = new Thread() {
          public void run() {
            handleConnection(socket);
          }
        };
        
        t.start();
      }
    } catch (IOException e) {
      System.out.println("Server: Exception " + e + " thrown.");
    }
  }
  
  public InetSocketAddress getAddress() {
    return address;
  }
  
  protected void handleConnection(Socket socket) {
    try {
      while (true) {
        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        Object object = ois.readObject();
        
        if (object instanceof DataRequest) {
          ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
          oos.writeObject(getData());          
        } else if (object instanceof NodeHandlesRequest) {
          Hashtable handles = new Hashtable();
          
          addLeafSet(handles);
          addRoutingTable(handles);
          
          Collection collection = handles.values();
          ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
          oos.writeObject(collection.toArray(new DistNodeHandle[0]));       
        }
      }
    } catch (IOException e) {
      System.out.println("Server: Exception " + e + " thrown.");
    } catch (ClassNotFoundException e) {
      System.out.println("Server: Exception " + e + " thrown.");
    }
  }
  
  protected void addLeafSet(Hashtable handles) {
    LeafSet leafset = node.getLeafSet();
    
    for (int i=-leafset.ccwSize(); i<=leafset.cwSize(); i++) 
      handles.put(leafset.get(i).getId(), (DistNodeHandle) leafset.get(i)); 
  }
  
  protected void addRoutingTable(Hashtable handles) {
    RoutingTable routingTable = node.getRoutingTable();
    
    for (int i=0; i>=routingTable.numRows(); i++) {
      RouteSet[] row = routingTable.getRow(i);
      
      for (int j=0; j<row.length; j++) 
        if ((row[j] != null) && (row[j].closestNode() != null))
          handles.put(row[j].closestNode().getNodeId(),  (DistNodeHandle) row[j].closestNode());
    }
  }
  
  protected Data getData() {
    Data data = new Data();
    
    for (int i=0; i<panelCreators.size(); i++) {
      PanelCreator creator = (PanelCreator) panelCreators.elementAt(i);
      DataPanel panel = creator.createPanel(objects);
      
      if (panel != null) {
        data.addDataPanel(panel);
        
        for (int j=0; j<panel.getDataViewCount(); j++) {
          panel.getDataView(j).setData(data);
        }
      }
    }
    
    return data;
  }

}