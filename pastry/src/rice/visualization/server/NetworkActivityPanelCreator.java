/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.visualization.server;

import rice.visualization.data.*;
import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.selector.*;

import java.net.*;
import java.util.*;

public class NetworkActivityPanelCreator implements PanelCreator, NetworkListener {
  
  public static int NUM_DATA_POINTS = 600;
  public static int NUM_MESSAGES = 200;
  public static int UPDATE_TIME = 60000;
  
  protected Vector sent = new Vector();
  
  protected Vector received = new Vector();

  protected Vector times = new Vector();
  
  protected Vector addresses = new Vector();
  
  protected Vector addressSizes = new Vector(); 
  
  protected double sentTotal = 0;
  
  protected double receivedTotal = 0;
  
  protected Environment environment;
  
  public NetworkActivityPanelCreator(Environment env) {
    this.environment = env;
    env.getSelectorManager().getTimer().scheduleAtFixedRate(new rice.selector.TimerTask() {
      public void run() {
        updateData();
      }
    }, 0, UPDATE_TIME);
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel networkActivityPanel = new DataPanel("Network");
    
    Constraints dataSentCons = new Constraints();
    dataSentCons.gridx = 0;
    dataSentCons.gridy = 0;
    dataSentCons.fill = Constraints.HORIZONTAL;
    
    LineGraphView dataSentView = new LineGraphView("Data Sent", 380, 200, dataSentCons, "Time (m)", "Data (KB)", false, false);
    dataSentView.addSeries("Data Sent", getTimeArray(), getSentArray(), Color.green);
    
    Constraints dataReceivedCons = new Constraints();
    dataReceivedCons.gridx = 1;
    dataReceivedCons.gridy = 0;
    dataReceivedCons.fill = Constraints.HORIZONTAL;
    
    LineGraphView dataReceivedView = new LineGraphView("Data Received", 380, 200, dataReceivedCons, "Time (m)", "Data (KB)", false, false);
    dataReceivedView.addSeries("Data Received", getTimeArray(), getReceivedArray(), Color.red);
    
    Constraints dataBreakdownCons = new Constraints();
    dataBreakdownCons.gridx = 2;
    dataBreakdownCons.gridy = 0;
    dataBreakdownCons.fill = Constraints.HORIZONTAL;
    
    PieChartView linkDistributionView = new PieChartView("Link Distribution", 380, 200, dataBreakdownCons);
    Vector addresses = new Vector();
    Vector sizes = new Vector();
    
    InetSocketAddress[] addressList = getAddresses();
    Integer[] sizeList = getSizes();
    
    for (int i=0; i<addressList.length; i++) {
      int index = addresses.indexOf(addressList[i]);

      if (index >= 0) {
        Integer integer = (Integer) sizes.elementAt(index);
        sizes.removeElementAt(index);
        sizes.insertElementAt(new Integer(sizeList[i].intValue() + integer.intValue()), index);
      } else {
        addresses.addElement(addressList[i]);
        sizes.addElement(sizeList[i]);
      }
    }
    
    for (int i=0; i<addresses.size(); i++) 
      linkDistributionView.addItem(((InetSocketAddress) addresses.elementAt(i)).getAddress().getHostAddress() + ":" + ((InetSocketAddress) addresses.elementAt(i)).getPort(),
                                   (double) ((Integer) sizes.elementAt(i)).intValue());
    
    networkActivityPanel.addDataView(linkDistributionView);
    networkActivityPanel.addDataView(dataSentView);
    networkActivityPanel.addDataView(dataReceivedView);
    
    return networkActivityPanel;
  }
  
  protected synchronized double[] getTimeArray() {
    double[] timesA = new double[times.size()];
    long offset = ((Long) times.elementAt(0)).longValue();
    
    for (int i=0; i<timesA.length; i++) 
      timesA[i] = (double) ((((Long) times.elementAt(i)).longValue() - offset) / UPDATE_TIME);
    
    return timesA;
  }
  
  protected synchronized double[] getSentArray() {
    double[] sentA = new double[sent.size()];
    
    for (int i=0; i<sentA.length; i++) 
      sentA[i] = ((Double) sent.elementAt(i)).doubleValue() / 1024;
    
    return sentA;
  }
  
  protected synchronized double[] getReceivedArray() {
    double[] receivedA = new double[received.size()];
    
    for (int i=0; i<receivedA.length; i++) 
      receivedA[i] = ((Double) received.elementAt(i)).doubleValue() / 1024;
    
    return receivedA;
  }
  
  protected synchronized void updateData() {
    sent.add(new Double(resetSentTotal()));
    received.add(new Double(resetReceivedTotal()));
    times.add(new Long(environment.getTimeSource().currentTimeMillis()));
    
    if (sent.size() > NUM_DATA_POINTS) {
      sent.removeElementAt(0); 
      received.removeElementAt(0); 
      times.removeElementAt(0); 
    }
  }
  
  protected synchronized void addMessage(InetSocketAddress address, int size) {    
    addresses.add(address);
    addressSizes.add(new Integer(size));
    
    if (addresses.size() > NUM_MESSAGES) {
      addresses.removeElementAt(0); 
      addressSizes.removeElementAt(0); 
    }
  }
  
  protected synchronized InetSocketAddress[] getAddresses() {
    return (InetSocketAddress[]) addresses.toArray(new InetSocketAddress[0]);
  }
  
  protected synchronized Integer[] getSizes() {
    return (Integer[]) addressSizes.toArray(new Integer[0]);
  }
    
  protected synchronized double resetSentTotal() {
    double result = sentTotal;
    sentTotal = 0;
    
    return result;
  }
  
  protected synchronized double resetReceivedTotal() {
    double result = receivedTotal;
    receivedTotal = 0;
    
    return result;
  }
  
  public synchronized void dataSent(int msgAddress, short msgType, InetSocketAddress address, int size, int type) {
    sentTotal += size;
    addMessage(address, size);
  }
  
  public synchronized void dataReceived(int msgAddress, short msgType, InetSocketAddress address, int size, int type) {
    receivedTotal += size;
    addMessage(address, size);
  }

  public void channelOpened(InetSocketAddress addr, int reason) {
  }

  public void channelClosed(InetSocketAddress addr) {
  }
}
