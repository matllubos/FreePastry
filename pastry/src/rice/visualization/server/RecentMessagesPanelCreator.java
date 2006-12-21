/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
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
  
  MessageNamingService mns;

  public RecentMessagesPanelCreator(MessageNamingService mns) {
    this.mns = mns; 
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel networkActivityPanel = new DataPanel("Recent");
    
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
  
  protected synchronized void addMessage(String msgName, InetSocketAddress address, int size, Vector location, Vector locationAddresses, Vector locationSizes) {
    location.add(msgName);
    locationAddresses.add(address);
    locationSizes.add(new Integer(size));
    
    if (location.size() > NUM_MESSAGES) {
      location.removeElementAt(0); 
      locationAddresses.removeElementAt(0);
      locationSizes.removeElementAt(0); 
    }
  }
  
  public synchronized void dataSent(int msgAddress, short msgType, InetSocketAddress address, int size, int type) {
      addMessage(mns.getMessageName(msgAddress, msgType), address, size, sentMessages, sentMessageAddresses, sentMessageSizes);
  }
  
  public synchronized void dataReceived(int msgAddress, short msgType, InetSocketAddress address, int size, int type) {
    addMessage(mns.getMessageName(msgAddress, msgType), address, size, receivedMessages, receivedMessageAddresses, receivedMessageSizes);
  }

  public void channelOpened(InetSocketAddress addr, int reason) {
  }

  public void channelClosed(InetSocketAddress addr) {
  }
}
