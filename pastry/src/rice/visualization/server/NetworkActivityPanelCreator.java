package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

import java.awt.*;
import java.net.*;
import java.util.*;

public class NetworkActivityPanelCreator implements PanelCreator, NetworkListener {
  
  public static int NUM_DATA_POINTS = 600;
  public static int NUM_MESSAGES = 200;
  public static int UPDATE_TIME = 1000;
  
  protected Vector sent = new Vector();
  
  protected Vector received = new Vector();

  protected Vector times = new Vector();
  
  protected Vector addresses = new Vector();
  
  protected Vector addressSizes = new Vector(); 
  
  protected double sentTotal = 0;
  
  protected double receivedTotal = 0;
  
  public NetworkActivityPanelCreator() {
    Thread t = new Thread("Network Activity Panel Monitor Thread") {
      public void run() {
        while (true) {
          try {
            updateData();
            Thread.currentThread().sleep(UPDATE_TIME);
          } catch (InterruptedException e) {
          }
        }
      }
    };
    
    t.start();
  }
  
  public DataPanel createPanel(Object[] objects) {
    DataPanel networkActivityPanel = new DataPanel("Network Activity");
    
    GridBagConstraints dataSentCons = new GridBagConstraints();
    dataSentCons.gridx = 0;
    dataSentCons.gridy = 0;
    dataSentCons.fill = GridBagConstraints.HORIZONTAL;
    
    LineGraphView dataSentView = new LineGraphView("Data Sent", 380, 200, dataSentCons, "Time (sec)", "Data (B)", false);
    dataSentView.addSeries("Data Sent", getTimeArray(), getSentArray(), Color.green);
    
    GridBagConstraints dataReceivedCons = new GridBagConstraints();
    dataReceivedCons.gridx = 1;
    dataReceivedCons.gridy = 0;
    dataReceivedCons.fill = GridBagConstraints.HORIZONTAL;
    
    LineGraphView dataReceivedView = new LineGraphView("Data Received", 380, 200, dataReceivedCons, "Time (sec)", "Data (B)", false);
    dataReceivedView.addSeries("Data Received", getTimeArray(), getReceivedArray(), Color.red);
    
    GridBagConstraints dataBreakdownCons = new GridBagConstraints();
    dataBreakdownCons.gridx = 2;
    dataBreakdownCons.gridy = 0;
    dataBreakdownCons.fill = GridBagConstraints.HORIZONTAL;
    
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
      timesA[i] = (double) ((((Long) times.elementAt(i)).longValue() - offset) / 1000);
    
    return timesA;
  }
  
  protected synchronized double[] getSentArray() {
    double[] sentA = new double[sent.size()];
    
    for (int i=0; i<sentA.length; i++) 
      sentA[i] = ((Double) sent.elementAt(i)).doubleValue();
    
    return sentA;
  }
  
  protected synchronized double[] getReceivedArray() {
    double[] receivedA = new double[received.size()];
    
    for (int i=0; i<receivedA.length; i++) 
      receivedA[i] = ((Double) received.elementAt(i)).doubleValue();
    
    return receivedA;
  }
  
  protected synchronized void updateData() {
    sent.add(new Double(resetSentTotal()));
    received.add(new Double(resetReceivedTotal()));
    times.add(new Long(System.currentTimeMillis()));
    
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
  
  public synchronized void dataSent(Object message, InetSocketAddress address, int size) {
    sentTotal += size;
    addMessage(address, size);
  }
  
  public synchronized void dataReceived(Object message, InetSocketAddress address, int size) {
    receivedTotal += size;
    addMessage(address, size);
  }
}
