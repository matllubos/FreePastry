package rice.visualization.server;

import java.io.*;
import java.net.*;

import rice.p2p.multiring.*;
import rice.p2p.commonapi.IdFactory;

import rice.*;
import rice.persistence.*;
import rice.visualization.*;
import rice.visualization.data.*;
import rice.visualization.client.*;
import rice.pastry.*;
import rice.pastry.commonapi.*;
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

  protected Vector debugCommandHandlers;
  
  protected ServerSocket server;
  
  protected PastryNode node;
  
  protected StorageManager storage;
  
  protected boolean willAcceptNewJars = true;
  
  protected boolean willAcceptNewRestartCommandLine = true;
  
  private String restartCommand = null;
  
  protected NetworkActivityChecker NAchecker;
  
  protected FreeDiskSpaceChecker FDSchecker;
  
  protected Random rng = new Random();
    
  public VisualizationServer(InetSocketAddress address, PastryNode node, StorageManager storage, Object[] objects) {
    this.address = address;
    this.objects = objects;
    this.node = node;
    this.storage = storage;
    this.panelCreators = new Vector();
    this.NAchecker = new NetworkActivityChecker();
    this.FDSchecker = new FreeDiskSpaceChecker();
    
    ((DistPastryNode) node).addNetworkListener(NAchecker);
    this.debugCommandHandlers = new Vector();
    addDebugCommandHandler(new FileCommandHandler());    
  }
  
  public void addPanelCreator(PanelCreator creator) {
    panelCreators.addElement(creator);
  }
  
  public void addDebugCommandHandler(DebugCommandHandler handler) {
    debugCommandHandlers.addElement(handler);
  }
  
  public void run() {
    try {    
      server = new ServerSocket();
      server.bind(address);

      while (true) {
        final Socket socket = server.accept();
        
        Thread t = new Thread("Visualization Server Thread for " + socket.getRemoteSocketAddress()) {
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
//        } else if (object instanceof ErrorsRequest) {
//          NAchecker.checkForErrors();
//          FDSchecker.checkForErrors();
//          ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
//          oos.writeObject(getErrors());
        } else if (object instanceof UpdateJarRequest) {
          ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
          handleUpdateJarRequest((UpdateJarRequest)object,oos);
        } else if (object instanceof DebugCommandRequest) {
          DebugCommandRequest dcr = (DebugCommandRequest) object;
          ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
          
          boolean responseSent = false;
          for (int i=0; (i<debugCommandHandlers.size()) && !responseSent; i++) {
            DebugCommandHandler handler = (DebugCommandHandler) debugCommandHandlers.elementAt(i);
            String thisResponse = handler.handleDebugCommand(dcr.command);
            if (thisResponse != null) {
              oos.writeObject(new DebugCommandResponse(dcr.command, thisResponse, 202));
              responseSent = true;
            }
          }
          
          if (!responseSent)
            oos.writeObject(new DebugCommandResponse(dcr.command, "Bad Request", 400));
        }
      }
    } catch (IOException e) {
      System.out.println("Server: Exception " + e + " thrown.");
    } catch (ClassNotFoundException e) {
      System.out.println("Server: Exception " + e + " thrown.");
    }
  }

  protected void handleUpdateJarRequest(
       UpdateJarRequest req, 
       ObjectOutputStream oos)
        throws IOException {
    if (!willAcceptNewJars) {
      oos.writeObject(new UpdateJarResponse(UpdateJarResponse.FILE_COPY_NOT_ALLOWED));      
      return;
    }

    // this will most likely be an IOException
    Exception e = null;

    // try to write the files
    try {
      req.writeFiles();
    } catch (Exception e1) {
      e = e1;
      e.printStackTrace(); 
    }          
    
    UpdateJarResponse ujr;

    if (req.executeCommand != null) {   
      if (willAcceptNewRestartCommandLine) {      
        restartCommand = req.executeCommand;
        ujr = new UpdateJarResponse(e);
      } else {
        ujr = new UpdateJarResponse(e,UpdateJarResponse.NEW_EXEC_NOT_ALLOWED);        
      }
    } else {
      ujr = new UpdateJarResponse(e);      
    }
    // respond with the exception if there was one
    oos.writeObject(ujr);

    // kill the node
    ((DistPastryNode)node).resign();
  
    // sleep for a while
    try {
      Thread.sleep(req.getWaitTime());
    } catch (InterruptedException ie) {
      ie.printStackTrace();
    }    
    
//    System.out.println("restarting with command:\""+restartCommand+"\"");
          
//    Process p = Runtime.getRuntime().exec(restartCommand);
//    System.out.println("Process:"+p);
    System.exit(2);
    
    
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
  
  protected String[] getErrors() {
    return ((DistPastryNode) node).getErrors();
  }
  
  protected Data getData() {
    Data data = new Data();
    
    for (int i=0; i<panelCreators.size(); i++) {
      PanelCreator creator = (PanelCreator) panelCreators.elementAt(i);
      DataPanel panel = creator.createPanel(objects);
      
      if (panel != null) {
        data.addDataPanel(panel);
        
        setData(panel, data);
      }
    }
    
    return data;
  }
  
  protected void setData(DataPanel panel, Data data) {
    if (panel instanceof MultiDataPanel) {
      DataPanel[] panels = ((MultiDataPanel) panel).getDataPanels();
      
      for (int i=0; i<panels.length; i++)
        setData(panels[i], data);
    } else {
      for (int j=0; j<panel.getDataViewCount(); j++) {
        panel.getDataView(j).setData(data);
      }
    }
  }
    

  /**
   * @param string
   * @param args
   */
  public void setRestartCommand(String string, String[] args) {
    restartCommand = string;
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        restartCommand = restartCommand.concat(" "+args[i]);    
      }
    }
  }
  
  public class NetworkActivityChecker implements NetworkListener {
   
    protected long lastSent = System.currentTimeMillis();

    protected long lastReceived = System.currentTimeMillis();
    
    public void dataSent(Object message, InetSocketAddress address, int size) {
      lastSent = System.currentTimeMillis();
    }
    
    public void dataReceived(Object message, InetSocketAddress address, int size) {
      lastReceived = System.currentTimeMillis();
    }
    
    public void checkForErrors() {
      int sent = (int) ((System.currentTimeMillis() - lastSent)/1000);
      if (sent > 60)
        ((DistPastryNode) node).addError("WARNING: No message has been sent in over " + sent + " seconds.");
      
      int received = (int) ((System.currentTimeMillis() - lastReceived)/1000);
      if (received > 60)
        ((DistPastryNode) node).addError("WARNING: No message has been received in over " + received + " seconds.");
    }
  
  }
  
  public class FreeDiskSpaceChecker {
    
    byte[] data = new byte[5000];
    
    public void checkForErrors() {
      final rice.p2p.commonapi.Id id  = generateId();
      
      storage.store(id, null, data, new Continuation() {
        public void receiveResult(Object o) {
          if (! (o.equals(new Boolean(true)))) 
            ((DistPastryNode) node).addError("SEVERE: Attempt to store data under " + id + " failed with " + o);
          else
            storage.unstore(id, new Continuation() {
              public void receiveResult(Object o) {
                if (! (o.equals(new Boolean(true)))) 
                  ((DistPastryNode) node).addError("SEVERE: Attempt to store data under " + id + " failed with " + o);
              }
              
              public void receiveException(Exception e) {
                ((DistPastryNode) node).addError("SEVERE: Attempt to store data under " + id + " failed with " + e);
              }
            });
        }
        
        public void receiveException(Exception e) {
          ((DistPastryNode) node).addError("SEVERE: Attempt to store data under " + id + " failed with " + e);
        }
      });
    }
  }
  
  private rice.p2p.commonapi.Id generateId() {
    byte[] data = new byte[20];
    rng.nextBytes(data);
    IdFactory factory = new MultiringIdFactory(node.getId(), new PastryIdFactory());
    return factory.buildId(data);
  }

}
