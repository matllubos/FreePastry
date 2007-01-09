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
package rice.email.proxy.web;

import rice.email.proxy.util.*;
import rice.email.proxy.user.*;
import rice.email.proxy.web.pages.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

public class WebHandler {
  
  protected static WebPageRegistry registry = new WebPageRegistry();
  
  static {
    registry.load();
  }
  
  // protocol and configuration global stuff
  protected Workspace _workspace;
  protected UserManager _userManager;
  protected WebConnection _conn;
  protected WebState state;
  protected Environment environment;
  
  public WebHandler(UserManager userManager, Workspace workspace, WebState state, Environment env) {
    _workspace = workspace;
    _userManager = userManager;
    this.state = state;
    this.environment = env;
  }
  
  public void handleConnection(Socket socket) throws IOException {
    _conn = new WebConnection(this, socket, environment);
    
    try {
      String request = _conn.readRequest();
      WebPage page = registry.getPage(request);
        
      if (page != null) {
        if ((state.getUser() == null) && (page.authenticationRequired())) {
          _conn.error(_conn.STATUS_AUTH_REQUIRED, "Authentication is required for this page.");
        } else {
          page.execute(_conn, state);
        }
      } else {
        _conn.error(_conn.STATUS_NOT_FOUND, "The requested page '" + request + "' was not found.");
      }
    } catch (SocketTimeoutException ste) {
      _conn.println("421 Service shutting down and closing transmission channel");
    } catch (IOException e) {
      Logger logger = environment.getLogManager().getLogger(WebHandler.class, null);
      if (logger.level <= Logger.WARNING) logger.logException(
          "Detected connection error " + e + " - closing.",e);
    } catch (WebException e) {
      _conn.error(e.getStatus(), e.getMessage());
    } 
  }
  
  public Environment getEnvironment() {
    return environment;
  }
}
