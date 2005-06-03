package rice.email.proxy.web;

import rice.email.proxy.util.*;
import rice.email.proxy.user.*;
import rice.email.proxy.web.pages.*;
import rice.environment.Environment;

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
    _conn = new WebConnection(this, socket);
    
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
      System.out.println("Detected connection error " + e + " - closing.");
    } catch (WebException e) {
      _conn.error(e.getStatus(), e.getMessage());
    } 
  }
  
  public Environment getEnvironment() {
    return environment;
  }
}