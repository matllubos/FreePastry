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
package rice.email.proxy.web;

import rice.email.*;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;
import rice.email.proxy.mailbox.postbox.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

import java.io.*;
import java.net.*;
import java.util.*;

public class WebServerImpl extends Thread implements WebServer {
  
  protected boolean quit = false;
  protected int port;
  protected ServerSocket server;
  protected UserManager manager;  
  protected Workspace workspace;  
  protected EmailService email;
  protected Environment environment;
  protected Logger logger;
  protected HashMap states;
  
  public WebServerImpl(int port, EmailService email, UserManager manager, Environment env) throws IOException {
    super("Web Server Thread");
    this.environment = env;
    logger = environment.getLogManager().getLogger(WebServerImpl.class, null);
    this.port = port;
    this.email = email;
    this.manager = manager;
    this.workspace = new InMemoryWorkspace();
    this.states = new HashMap();
    
    initialize();
  }
  
  public int getPort() {
    return port;
  }
  
  public void initialize() throws IOException {
    server = new ServerSocket(port);
  }
  
  protected WebState getWebState(InetAddress address) {
    WebState result = (WebState) states.get(address);
    
    if (result == null) {
      result = new WebState(manager);
      states.put(address, result);
    }
    
    return result;
  }
  
  public void run() {
    try {
      while (! quit) {
        final Socket socket = server.accept();
        
        if (logger.level <= Logger.INFO) logger.log("Accepted web connection from " + socket.getInetAddress());
        
        
        Thread thread = new Thread("Web Server Thread for " + socket.getInetAddress()) {
          public void run() {
            try {
              WebHandler handler = new WebHandler(manager, workspace, getWebState(socket.getInetAddress()), environment);
              handler.handleConnection(socket);
            } catch (IOException e) {
              if (logger.level <= Logger.WARNING) logger.logException("IOException occurred during handling of connection - " , e);
            }
          }
        };
        
        thread.start();
      }
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException("IOException occurred during accepting of connection - " , e);
    }
  }
  
}
