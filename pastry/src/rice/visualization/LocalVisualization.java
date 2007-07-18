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
package rice.visualization;

import java.io.*;
import java.net.*;
import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.dist.DistNodeHandle;
import rice.visualization.client.*;
import rice.visualization.data.Data;
import rice.visualization.data.DataProvider;

public class LocalVisualization implements DataProvider {
  
  public static int PORT_OFFSET = 3847;
  public static int REFRESH_TIME = 1000;
  
  protected LocalVisualizationFrame frame;
  
  protected DistNodeHandle handle;
  
  protected Data data;
  
  protected VisualizationClient client;

  protected boolean die = false;
  
  protected Environment environment;
  protected Logger logger;
  
  public LocalVisualization(DistNodeHandle handle, Environment env) {
    this.environment = env;
    this.logger = environment.getLogManager().getLogger(LocalVisualization.class, null);

    this.handle = handle;
    this.frame = new LocalVisualizationFrame(this);
    this.data = new Data();
    
    Thread t = new Thread() {
      public void run() {
        try {
          while (! die) {
            Thread.sleep(REFRESH_TIME);
            updateData();
            frame.repaint();
          }
          
          if (client != null)
            client.close();
          
          if (logger.level <= Logger.INFO) logger.log(
              "Visualization Thread now dying...");
        } catch (Exception e) {
          if (logger.level <= Logger.WARNING) logger.logException(
              "",e);
        }
      }
    };
    
    t.start(); 
  }
  
  public Data getData() {
    return data;
  }
  
  public void exit() {
    die = true; 
  }
  
  protected void updateData() throws IOException {
    if (client == null) {
      InetSocketAddress address = new InetSocketAddress(
          handle.getInetSocketAddress().getAddress(), 
          handle.getInetSocketAddress().getPort() + PORT_OFFSET);
      client = new VisualizationClient(null, address, environment);
      client.connect();
      frame.nodeSelected(new Node(null, null), client.getData());
    }
        
    this.data = client.getData();
    
    if (this.data == null)
      throw new IOException("Data was null - likely disconnected!");
  }
}
