package rice.visualization.server;

import java.io.*;
import java.net.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.multiring.*;
import rice.p2p.util.*;
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
import java.security.*;

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
  
  protected RingCertificate cert;
  
  protected KeyPair keypair;

  protected Environment environment;
  
  protected Logger logger;
  
  public VisualizationServer(InetSocketAddress address, PastryNode node, StorageManager storage, RingCertificate cert, Object[] objects, Environment env) {
    this.environment = env;
    this.logger = environment.getLogManager().getLogger(VisualizationServer.class, null);
    this.address = address;
    this.objects = objects;
    this.node = node;
    this.cert = cert;
    this.storage = storage;
    this.panelCreators = new Vector();
    this.NAchecker = new NetworkActivityChecker();
    this.FDSchecker = new FreeDiskSpaceChecker();
    
    ((DistPastryNode) node).addNetworkListener(NAchecker);
    this.debugCommandHandlers = new Vector();
    addDebugCommandHandler(new FileCommandHandler(environment));  
    
    this.keypair = SecurityUtils.generateKeyAsymmetric();
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
      if (logger.level <= Logger.SEVERE) logger.logException(
          "Server: Exception " + e + " thrown.",e);
    }
  }
  
  public InetSocketAddress getAddress() {
    return address;
  }
  
  protected void handleConnection(Socket socket) {
    try {
      ObjectOutputStream oos = null;
      ObjectInputStream ois = null;
      
      // first, send across our public key, encrypted
      if (socket.getInetAddress().isLoopbackAddress() || socket.getInetAddress().equals(address.getAddress())) {
        oos = new ObjectOutputStream(socket.getOutputStream());
        ois = new ObjectInputStream(socket.getInputStream());
      } else {
        oos = new ObjectOutputStream(new EncryptedOutputStream(cert.getKey(), socket.getOutputStream(), 
            environment.getParameters().getInt("p2p_util_encryptedOutputStream_buffer")));
        oos.writeObject(keypair.getPublic());
        oos.flush();
        ois = new ObjectInputStream(new EncryptedInputStream(keypair.getPrivate(), socket.getInputStream()));
      }
      
      while (true) {
        Object object = ois.readObject();
        
        if (object instanceof DataRequest) {
          oos.writeObject(getData());          
        } else if (object instanceof NodeHandlesRequest) {
          Hashtable handles = new Hashtable();
          
          addLeafSet(handles);
          addRoutingTable(handles);
          
          Collection collection = handles.values();
          oos.writeObject(collection.toArray(new DistNodeHandle[0])); 
        } else if (object instanceof UpdateJarRequest) {
          handleUpdateJarRequest((UpdateJarRequest)object,oos);
        } else if (object instanceof DebugCommandRequest) {
          DebugCommandRequest dcr = (DebugCommandRequest) object;
          
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
        
        oos.flush();
      }
    } catch (IOException e) {
      if (logger.level <= Logger.SEVERE) logger.logException(
          "Server: Exception " + e + " thrown.",e);
    } catch (ClassNotFoundException e) {
      if (logger.level <= Logger.SEVERE) logger.logException(
          "Server: Exception " + e + " thrown.",e);
    } catch (SecurityException e) {
      if (logger.level <= Logger.SEVERE) logger.logException(
          "Server: Exception " + e + " thrown.",e);
    } finally {
      try {
        socket.close();
      } catch (IOException e) {
        if (logger.level <= Logger.SEVERE) logger.logException(
            "Server: Exception " + e + " thrown closing.",e);
      }
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
      if (logger.level <= Logger.WARNING) logger.logException("",e1);
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
    ((DistPastryNode)node).destroy();
  
    // sleep for a while
    try {
      Thread.sleep(req.getWaitTime());
    } catch (InterruptedException ie) {
      if (logger.level <= Logger.WARNING) logger.logException("",ie);
    }    
    
//    System.outt.println("restarting with command:\""+restartCommand+"\"");
          
//    Process p = Runtime.getRuntime().exec(restartCommand);
//    System.outt.println("Process:"+p);
    System.exit(2);
    
    
  }
  
  protected void addLeafSet(Hashtable handles) {
    LeafSet leafset = node.getLeafSet();
    
    for (int i=-leafset.ccwSize(); i<=leafset.cwSize(); i++) 
      handles.put(leafset.get(i).getId(), (DistNodeHandle) leafset.get(i)); 
  }
  
  protected void addRoutingTable(Hashtable handles) {
    RoutingTable routingTable = node.getRoutingTable();
    
    for (int i=0; i<routingTable.numRows(); i++) {
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
   
    protected long lastSent = environment.getTimeSource().currentTimeMillis();

    protected long lastReceived = environment.getTimeSource().currentTimeMillis();
    
    public void dataSent(Object message, InetSocketAddress address, int size, int type) {
      lastSent = environment.getTimeSource().currentTimeMillis();
    }
    
    public void dataReceived(Object message, InetSocketAddress address, int size, int type) {
      lastReceived = environment.getTimeSource().currentTimeMillis();
    }
    
    public void checkForErrors() {
      int sent = (int) ((environment.getTimeSource().currentTimeMillis() - lastSent)/1000);
      if (sent > 60) {
        if (logger.level <= Logger.WARNING) logger.log(
            "WARNING: No message has been sent in over " + sent + " seconds.");
      }
      
      int received = (int) ((environment.getTimeSource().currentTimeMillis() - lastReceived)/1000);
      if (received > 60) {
        if (logger.level <= Logger.WARNING) logger.log(
            "WARNING: No message has been received in over " + received + " seconds.");
      }
    }

    public void channelOpened(InetSocketAddress addr, int reason) {
    }

    public void channelClosed(InetSocketAddress addr) {
    }
  
  }
  
  public class FreeDiskSpaceChecker {
    
    byte[] data = new byte[5000];
    
    public void checkForErrors() {
      final rice.p2p.commonapi.Id id  = generateId();
      
      storage.store(id, null, data, new Continuation() {
        public void receiveResult(Object o) {
          if (! (o.equals(new Boolean(true)))) { 
            if (logger.level <= Logger.SEVERE) logger.log(
                "SEVERE: Attempt to store data under " + id + " failed with " + o);
          } else {
            storage.unstore(id, new Continuation() {
              public void receiveResult(Object o) {
                if (! (o.equals(new Boolean(true)))) 
                  if (logger.level <= Logger.SEVERE) logger.log(
                      "SEVERE: Attempt to store data under " + id + " failed with " + o);
              }
              
              public void receiveException(Exception e) {
                if (logger.level <= Logger.SEVERE) logger.log(
                    "SEVERE: Attempt to store data under " + id + " failed with " + e);
              }
            });
          }
        }
        
        public void receiveException(Exception e) {
          if (logger.level <= Logger.SEVERE) logger.log(
              "SEVERE: Attempt to store data under " + id + " failed with " + e);
        }
      });
    }
  }
  
  private rice.p2p.commonapi.Id generateId() {
    byte[] data = new byte[20];
    environment.getRandomSource().nextBytes(data);
    IdFactory factory = new MultiringIdFactory(node.getId(), new PastryIdFactory(environment));
    return factory.buildId(data);
  }

}
