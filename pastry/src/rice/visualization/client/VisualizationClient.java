package rice.visualization.client;

import java.io.*;
import java.net.*;

import rice.visualization.*;
import rice.visualization.data.*;
import rice.pastry.*;
import rice.pastry.dist.*;

public class VisualizationClient {
  
  public static int STATE_ALIVE = 1;
  public static int STATE_DEAD = 2;
  public static int STATE_UNKNOWN = 3;
  public static int STATE_FAULT = 5;
  
  protected InetSocketAddress address;
  
  protected Socket socket;
  
  protected int state;
  
  public VisualizationClient(InetSocketAddress address) {
    this.address = address;
    this.state = STATE_ALIVE;
    this.socket = new Socket();
  }
  
  public int getState() {
    return state;
  }

  public void connect() {
    if (! socket.isConnected()) {
      try {
        socket.connect(address);
        this.state = STATE_ALIVE;
      } catch (IOException e) {
        this.state = STATE_DEAD;
      }
    } 
  }
  
  public InetSocketAddress getAddress() {
    return address;
  }
  
  public synchronized DistNodeHandle[] getHandles() {
    try {
      ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
      oos.writeObject(new NodeHandlesRequest());
      ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
      DistNodeHandle[] result = (DistNodeHandle[]) ois.readObject();
      
      this.state = STATE_ALIVE;
      return result;
    } catch (IOException e) {
      this.state = STATE_DEAD;
      System.out.println("Client: Exception " + e + " thrown.");
      
      return null;
    } catch (ClassNotFoundException e) {
      this.state = STATE_UNKNOWN;
      System.out.println("Client: Exception " + e + " thrown.");
      
      return null;
    }
  }
  
  public synchronized Data getData() {
    try {
      ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
      oos.writeObject(new DataRequest());
      ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
      Data result = (Data) ois.readObject();
      
      this.state = STATE_ALIVE;
      return result;
    } catch (IOException e) {
      this.state = STATE_DEAD;
      System.out.println("Client: Exception " + e + " thrown.");

      return null;
    } catch (ClassNotFoundException e) {
      this.state = STATE_UNKNOWN;
      System.out.println("Client: Exception " + e + " thrown.");

      return null;
    }
  }
  
  public synchronized UpdateJarResponse updateJar(File[] files, String executionString) {
    try {
      ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
      UpdateJarRequest ujr = new UpdateJarRequest(files);
      if (executionString != null) {
        ujr.executeCommand = executionString;
      }
      oos.writeObject(ujr);
      ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
      UpdateJarResponse result = (UpdateJarResponse) ois.readObject();
      
      this.state = STATE_ALIVE;
      return result;
    } catch (IOException e) {
      this.state = STATE_DEAD;
      System.out.println("Client: Exception " + e + " thrown.");

      return null;
    } catch (ClassNotFoundException e) {
      this.state = STATE_UNKNOWN;
      System.out.println("Client: Exception " + e + " thrown.");

      return null;
    }    
  }
  
}