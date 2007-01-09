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
package rice.visualization.client;

import java.io.*;
import java.net.*;
import java.security.*;

import rice.visualization.*;
import rice.visualization.data.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.dist.*;
import rice.p2p.util.*;

public class VisualizationClient {
  
  public static int STATE_ALIVE = 1;
  public static int STATE_DEAD = 2;
  public static int STATE_UNKNOWN = 3;
  public static int STATE_FAULT = 5;
  
  protected InetSocketAddress address;
  
  protected Socket socket;
  
  protected int state;
  
  protected PrivateKey privateKey;
  
  protected PublicKey publicKey;
  
  protected ObjectOutputStream oos;
  
  protected ObjectInputStream ois;
  
  protected Environment environment;
  protected Logger logger;
  
  public VisualizationClient(PrivateKey key, InetSocketAddress address, Environment env) {
    environment = env;
    logger = environment.getLogManager().getLogger(VisualizationClient.class, null);

    this.address = address;
    this.state = STATE_ALIVE;
    this.socket = new Socket();
    this.privateKey = key;
  }
  
  public int getState() {
    return state;
  }

  public void connect() {
    //System.outt.println(this+".connect()");
    if (! socket.isConnected()) {
      try {
        socket.connect(address);
        
        if (privateKey != null) {        
          ois = new ObjectInputStream(new EncryptedInputStream(privateKey, socket.getInputStream()));
          publicKey = (PublicKey) ois.readObject();        
          oos = new ObjectOutputStream(new EncryptedOutputStream(publicKey, socket.getOutputStream(),
              environment.getParameters().getInt("p2p_util_encryptedOutputStream_buffer")));
        } else {
          ois = new ObjectInputStream(socket.getInputStream());
          oos = new ObjectOutputStream(socket.getOutputStream());
        }
        
        this.state = STATE_ALIVE;
      } catch (IOException e) {
        this.state = STATE_DEAD;
      } catch (ClassNotFoundException e) {
        this.state = STATE_DEAD;
      }
    } 
  }
  
  public void close() throws IOException {
    socket.close();
  }
  
  public InetSocketAddress getAddress() {
    return address;
  }
  
  public synchronized DistNodeHandle[] getHandles() {
    try {
      oos.writeObject(new NodeHandlesRequest());
      oos.flush();
      DistNodeHandle[] result = (DistNodeHandle[]) ois.readObject();

      this.state = STATE_ALIVE;
      return result;
    } catch (IOException e) {
      this.state = STATE_DEAD;
      if (logger.level <= Logger.SEVERE) logger.logException("Client ("+address+"): Exception " + e + " thrown.",e);
      //e.printStackTracee();
      try {
        socket.close();
      } catch (IOException f) {
        if (logger.level <= Logger.SEVERE) logger.logException("Client: Exception " + f + " thrown closing.",f);
      }
      
      return null;
    } catch (ClassNotFoundException e) {
      this.state = STATE_UNKNOWN;
      if (logger.level <= Logger.SEVERE) logger.logException("Client: Exception " + e + " thrown.",e);
      
      return null;
    }
  }
  
  public synchronized Data getData() {
    try {
      oos.writeObject(new DataRequest());
      oos.flush();      
      Data result = (Data) ois.readObject();
      
      this.state = STATE_ALIVE;
      return result;
    } catch (IOException e) {
      this.state = STATE_DEAD;
      if (logger.level <= Logger.SEVERE) logger.logException("Client: Exception " + e + " thrown.",e);

      try {
        socket.close();
      } catch (IOException f) {
        if (logger.level <= Logger.SEVERE) logger.logException("Client: Exception " + f + " thrown closing.",f);
      }
      
      return null;
    } catch (ClassNotFoundException e) {
      this.state = STATE_UNKNOWN;
      if (logger.level <= Logger.SEVERE) logger.logException("Client: Exception " + e + " thrown.",e);

      return null;
    }
  }
  
  public synchronized String executeCommand(String command) throws Exception {
    oos.writeObject(new DebugCommandRequest(command));
    oos.flush();
    DebugCommandResponse result = (DebugCommandResponse) ois.readObject();
    if (result.getResponseCode() == 202)
      return result.getResponse();
        
    return "*** Error code "+result.getResponseCode()+" ***\n"+result.getResponse();
  }
  
  public synchronized UpdateJarResponse updateJar(File[] files, String executionString) {
    try {
      UpdateJarRequest ujr = new UpdateJarRequest(files, environment);
      if (executionString != null) {
        ujr.executeCommand = executionString;
      }
      oos.writeObject(ujr);
      oos.flush();
      UpdateJarResponse result = (UpdateJarResponse) ois.readObject();
      
      this.state = STATE_ALIVE;
      return result;
    } catch (IOException e) {
      this.state = STATE_DEAD;
      if (logger.level <= Logger.SEVERE) logger.logException("Client: Exception " + e + " thrown.",e);

      return null;
    } catch (ClassNotFoundException e) {
      this.state = STATE_UNKNOWN;
      if (logger.level <= Logger.SEVERE) logger.logException("Client: Exception " + e + " thrown.",e);

      return null;
    }    
  }
}
