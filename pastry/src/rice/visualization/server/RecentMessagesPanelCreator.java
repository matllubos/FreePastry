package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

import java.net.*;
import java.util.*;

public class RecentMessagesPanelCreator implements PanelCreator, NetworkListener {
  
  public static int NUM_MESSAGES = 11;
  
  public static String[] TYPES = new String[] {"Pastry", "Past", "Scribe", "Replication", "Post", "ePost", "Glacier", "Other"};
  public static String[] TYPE_PREFIXES = new String[] {"rice.pastry.", "rice.p2p.past.", "rice.p2p.scribe.", "rice.p2p.replication.", "rice.post.", "rice.email.", "rice.p2p.glacier.", ""};
  
  protected Vector sentMessages = new Vector();
  protected Vector sentMessageAddresses = new Vector();
  protected Vector sentMessageSizes = new Vector(); 
  
  protected Vector receivedMessages = new Vector();
  protected Vector receivedMessageAddresses = new Vector();
  protected Vector receivedMessageSizes = new Vector(); 
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel networkActivityPanel = new DataPanel("Recent Messages");
    
    Constraints leafsetCons = new Constraints();
    leafsetCons.gridx = 0;
    leafsetCons.gridy = 0;
    leafsetCons.fill = Constraints.HORIZONTAL;
    
    TableView countView = new TableView("Recently Sent Messages", 380, 200, leafsetCons);
    countView.setSizes(new int[] {135, 35, 65, 110});
    
    for (int i=0; i<sentMessages.size(); i++) {
      String message = (String) sentMessages.elementAt(i);
      String bytes = (((Integer) sentMessageSizes.elementAt(i)).intValue() / 1000) + " KB";
      String type = getType(message);
      message = message.substring(message.lastIndexOf(".") + 1);
      InetSocketAddress address = (InetSocketAddress) sentMessageAddresses.elementAt(i);                  
      
      countView.addRow(new String[] {message, bytes, type, address.getAddress().getHostAddress() + ":" + address.getPort()});
      
    }
      
    networkActivityPanel.addDataView(countView);
    
    Constraints leafsetCons2 = new Constraints();
    leafsetCons.gridx = 1;
    leafsetCons.gridy = 0;
    leafsetCons.fill = Constraints.HORIZONTAL;
    
    TableView countView2 = new TableView("Recently Received Messages", 380, 200, leafsetCons2);
    countView2.setSizes(new int[] {135, 35, 65, 110});
    
    for (int i=0; i<receivedMessages.size(); i++) {
      String message = (String) receivedMessages.elementAt(i);
      String bytes = (((Integer) receivedMessageSizes.elementAt(i)).intValue() / 1000) + " KB";
      String type = getType(message);
      message = message.substring(message.lastIndexOf(".") + 1);
      InetSocketAddress address = (InetSocketAddress) receivedMessageAddresses.elementAt(i);                  
      
      countView2.addRow(new String[] {message, bytes, type, address.getAddress().getHostAddress() + ":" + address.getPort()});
    }
    
    networkActivityPanel.addDataView(countView2);

    
    return networkActivityPanel;
  }

  protected String getType(String message) {
    for (int j=0; j<TYPES.length; j++) {
      if (message.startsWith(TYPE_PREFIXES[j])) {
        return TYPES[j];
      }
    }
  
    return "Unknown";
  }
  
  protected synchronized void addMessage(Object obj, InetSocketAddress address, int size, Vector location, Vector locationAddresses, Vector locationSizes) {
    if (obj instanceof rice.pastry.wire.messaging.datagram.DatagramTransportMessage) {
      addMessage(((rice.pastry.wire.messaging.datagram.DatagramTransportMessage) obj).getObject(), address, size, location, locationAddresses, locationSizes);
    } else if (obj instanceof rice.pastry.wire.messaging.socket.SocketTransportMessage) {
      addMessage(((rice.pastry.wire.messaging.socket.SocketTransportMessage) obj).getObject(), address, size, location, locationAddresses, locationSizes);
    } else if (obj instanceof rice.pastry.routing.RouteMessage) {
      addMessage(((rice.pastry.routing.RouteMessage) obj).unwrap(), address, size, location, locationAddresses, locationSizes);
    } else if (obj instanceof rice.pastry.commonapi.PastryEndpointMessage) {
      addMessage(((rice.pastry.commonapi.PastryEndpointMessage) obj).getMessage(), address, size, location, locationAddresses, locationSizes);
    } else {
      location.add(obj.getClass().getName());
      locationAddresses.add(address);
      locationSizes.add(new Integer(size));
      
      if (location.size() > NUM_MESSAGES) {
        location.removeElementAt(0); 
        locationAddresses.removeElementAt(0);
        locationSizes.removeElementAt(0); 
      }
    } 
  }
  
  public synchronized void dataSent(Object message, InetSocketAddress address, int size) {
      addMessage(message, address, size, sentMessages, sentMessageAddresses, sentMessageSizes);
  }
  
  public synchronized void dataReceived(Object message, InetSocketAddress address, int size) {
    addMessage(message, address, size, receivedMessages, receivedMessageAddresses, receivedMessageSizes);
  }
}
