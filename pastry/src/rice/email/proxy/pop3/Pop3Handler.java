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
package rice.email.proxy.pop3;

import rice.email.proxy.pop3.commands.*;
import rice.email.proxy.user.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

public class Pop3Handler {
  
  Pop3CommandRegistry _registry;
  Pop3Connection _conn;
  UserManager _manager;
  Pop3State _state;
  boolean _quitting;
  String _currentLine;
  Environment environment;
  InetAddress localHost;
  Logger logger;
  
  public Pop3Handler(InetAddress localHost, Pop3CommandRegistry registry, UserManager manager, Environment env) {
    this.localHost = localHost;
    _registry = registry;
    _manager = manager;
    environment = env;
    logger = environment.getLogManager().getLogger(Pop3Handler.class, null);
  }
  
  public InetAddress getLocalHost() {
    return localHost;
  }
  
  public void handleConnection(Socket socket) throws IOException {
    try {
      _conn = new Pop3Connection(this, socket, environment);
      _state = new Pop3State(_manager);
      _quitting = false;
      
      sendGreetings();
      
      while (!_quitting) {
        handleCommand();
      }
      
      _conn.close();
    } catch (SocketTimeoutException ste) {
      _conn.println("421 Service shutting down and closing transmission channel");
    }
    catch (Exception e) {
      if (logger.level <= Logger.WARNING) logger.logException("", e);
    } finally {
      try {
        socket.close();
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException("", ioe);
      }
    }
  }
  
  void sendGreetings() {          
    String challenge = "<" + environment.getTimeSource().currentTimeMillis() + "@localhost>";
//    try {
      challenge = "<" + environment.getTimeSource().currentTimeMillis() + "@" + getLocalHost().getHostName() + ">";
//    } catch (UnknownHostException e) {
//    } 

    _state.setChallenge(challenge);
    
    _conn.println("+OK POP3 server ready " + challenge);
  }
  
  void handleCommand() throws IOException {
    _currentLine = _conn.readLine();
    
    if (_currentLine == null) {
      quit();
      
      return;
    }
    
    String commandName = new StringTokenizer(_currentLine, " ").nextToken().toUpperCase();
    Pop3Command command = _registry.getCommand(commandName);
    
    if (command == null) {
      _conn.println("-ERR Command not recognized");
    } else if (! command.isValidForState(_state)) {
      _conn.println("-ERR Command not valid for this state");
    } else {
      command.execute(_conn, _state, _currentLine);
    }
  }
  
  public void quit() {
    _quitting = true;
  }
}
