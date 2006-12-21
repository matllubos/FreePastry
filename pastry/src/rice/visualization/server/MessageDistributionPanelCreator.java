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

public class MessageDistributionPanelCreator implements PanelCreator, NetworkListener {
  
  public static int NUM_DATA_POINTS = 600;
  public static int NUM_MESSAGES = 400;
  public static int UPDATE_TIME = 1000;
  
  public static String[] TYPES = new String[] {"Pastry", "Past", "Scribe", "Replication", "Post", "ePost", "Glacier", "Other"};
  public static String[] TYPE_PREFIXES = new String[] {"rice.pastry.", "rice.p2p.past.", "rice.p2p.scribe.", "rice.p2p.replication.", "rice.post.", "rice.email.", "rice.p2p.glacier.", ""};
  
  protected Vector messages = new Vector();
  
  protected Vector messageSizes = new Vector(); 
  
  protected MessageNamingService mns;
  
  public MessageDistributionPanelCreator(MessageNamingService mns) {
    this.mns = mns; 
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel networkActivityPanel = new DataPanel("Messages");
    
    Constraints dataBreakdownCons = new Constraints();
    dataBreakdownCons.gridx = 0;
    dataBreakdownCons.gridy = 0;
    dataBreakdownCons.fill = Constraints.HORIZONTAL;
    
    PieChartView dataBreakdownView = new PieChartView("Distribution by Size", 380, 200, dataBreakdownCons);
    double[] data = getMessageSizeBreakdown();
    
    for (int i=0; i<data.length; i++) 
      dataBreakdownView.addItem(TYPES[i], data[i]);
    
    networkActivityPanel.addDataView(dataBreakdownView);
    
    Constraints dataCountCons = new Constraints();
    dataCountCons.gridx = 1;
    dataCountCons.gridy = 0;
    dataCountCons.fill = Constraints.HORIZONTAL;
    
    PieChartView dataCountView = new PieChartView("Distribution by Count", 380, 200, dataCountCons);
    data = getMessageCountBreakdown();
    
    for (int i=0; i<data.length; i++) 
      dataCountView.addItem(TYPES[i], data[i]);
    
    networkActivityPanel.addDataView(dataCountView);
    
    
    Constraints leafsetCons = new Constraints();
    leafsetCons.gridx = 2;
    leafsetCons.gridy = 0;
    leafsetCons.fill = Constraints.HORIZONTAL;
    
    TableView countView = new TableView("Most Common Messages", 380, 200, leafsetCons);
    countView.setSizes(new int[] {240, 25, 45, 30});
    
    Vector unique = new Vector();
    Vector count = new Vector();
    Vector size = new Vector();
    
    String[] messages = getMessages();
    Integer[] sizes = getMessageSizes();
    
    for (int i=0; i<messages.length; i++) {
      int index = unique.indexOf(messages[i]);
      
      if (index >= 0) {
        Integer integer = (Integer) count.elementAt(index);
        count.removeElementAt(index);
        count.insertElementAt(new Integer(1 + integer.intValue()), index);
        
        Integer mSize = (Integer) size.elementAt(index);
        size.removeElementAt(index);
        size.insertElementAt(new Integer(mSize.intValue() + sizes[i].intValue()), index);
      } else {
        unique.addElement(messages[i]);
        count.addElement(new Integer(1));
        size.addElement(sizes[i]);
      }
    }
    
    Vector top10 = new Vector();
    
    int totalSize = 0; 
    
    for (int i=0; i<sizes.length; i++)
      totalSize += sizes[i].intValue();
    
    for (int i=0; i<unique.size(); i++) {
      String name = (String) unique.elementAt(i);
      name = name.substring(name.lastIndexOf(".") + 1);

      String bytes = (((Integer) size.elementAt(i)).intValue() / 1000) + " KB";
      
      int percent = (int) (((double) (((Integer) size.elementAt(i)).intValue()) / totalSize) * 100);
      int total = ((Integer) size.elementAt(i)).intValue();
      boolean inserted = false;
      
      for (int j=0; (j<top10.size()) && (! inserted); j++) {
        int thisTotal = Integer.valueOf(((String[]) top10.elementAt(j))[4]).intValue();
        if (thisTotal < total) {
          top10.insertElementAt(new String[] {name, "" + ((Integer) count.elementAt(i)).intValue(), bytes, percent + "%", total + ""}, j);
          inserted = true;
        }
      }
      
      if (! inserted)
        top10.addElement(new String[] {name, "" + ((Integer) count.elementAt(i)).intValue(), bytes, percent + "%", total + ""});
    }
    
    for (int i=0; (i<top10.size()) && (i < 11); i++) {
      String[] row = new String[4];
      String[] thisRow = (String[]) top10.elementAt(i);
      System.arraycopy(thisRow, 0, row, 0, 4);
      
      countView.addRow(row);
    }
    
    networkActivityPanel.addDataView(countView);

    
    return networkActivityPanel;
  }
  
  protected synchronized void addMessage(int msgAddress, short msgType, int size) {
    messages.add(mns.getMessageName(msgAddress, msgType));
    
    messageSizes.add(new Integer(size));
    
    if (messages.size() > NUM_MESSAGES) {
      messages.removeElementAt(0); 
      messageSizes.removeElementAt(0); 
    }     
  }
  
  protected synchronized String[] getMessages() {
    return (String[]) messages.toArray(new String[0]);
  }
  
  protected synchronized Integer[] getMessageSizes() {
    return (Integer[]) messageSizes.toArray(new Integer[0]);
  }
  
  protected double[] getMessageSizeBreakdown() {
    double[] result = new double[TYPES.length];
    String[] messages = getMessages();
    
    for (int i=0; i<messages.length; i++) {
      boolean done = false;
      
      for (int j=0; (j<TYPES.length) && (! done); j++) {
        if (messages[i].startsWith(TYPE_PREFIXES[j])) {
          result[j] += ((Integer) messageSizes.elementAt(i)).intValue();
          done = true;
        }
      }
    }
    
    return result;
  }
  
  protected double[] getMessageCountBreakdown() {
    double[] result = new double[TYPES.length];
    String[] messages = getMessages();
    
    for (int i=0; i<messages.length; i++) {
      boolean done = false;
      
      for (int j=0; (j<TYPES.length) && (! done); j++) {
        if (messages[i].startsWith(TYPE_PREFIXES[j])) {
          result[j]++;
          done = true;
        }
      }
    }
    
    return result;
  }
  
  public synchronized void dataSent(int msgAddress, short msgType, InetSocketAddress address, int size, int type) {
      addMessage(msgAddress, msgType, size);
  }
  
  public synchronized void dataReceived(int msgAddress, short msgType, InetSocketAddress address, int size, int type) {
      addMessage(msgAddress, msgType, size);
  }

  public void channelOpened(InetSocketAddress addr, int reason) {
  }

  public void channelClosed(InetSocketAddress addr) {
  }
}
