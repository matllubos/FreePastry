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
package rice.email.proxy.smtp;

import rice.email.proxy.smtp.commands.*;
import rice.email.proxy.smtp.manager.*;
import rice.email.proxy.util.*;
import rice.email.proxy.user.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.post.proxy.PostProxy;

import java.io.IOException;

import java.net.*;

class SmtpHandler {

  // protocol and configuration global stuff
  SmtpCommandRegistry _registry;
  SmtpManager _manager;
  Workspace _workspace;
  SmtpServer _server;
  UserManager _userManager;

  // session stuff
  SmtpConnection _conn;
  SmtpState _state;

  // command parsing stuff
  boolean _quitting;
  String _currentLine;
  boolean authenticate;

  Environment environment;
  
  public SmtpHandler(SmtpCommandRegistry registry, SmtpManager manager, Workspace workspace, SmtpServer server, UserManager userManager, boolean authenticate, Environment env) {
    this.environment = env;
    _registry = registry;
    _manager = manager;
    _workspace = workspace;
    _server = server;
    _userManager = userManager;
    this.authenticate = authenticate;
  }

  public void handleConnection(Socket socket) throws IOException {
    _conn = new SmtpConnection(this, socket, _server);
    _state = new SmtpState(_workspace, _userManager, environment);
    _state.setRemote(socket.getInetAddress());

    try {
      _quitting = false;

      sendGreetings();

      while (!_quitting) {
        handleCommand();
      }

    } catch (SocketTimeoutException ste) {
      _conn.println("421 Service shutting down and closing transmission channel");
    } catch (IOException e) {
      Logger logger = environment.getLogManager().getLogger(SmtpHandler.class, null);
      if (logger.level <= Logger.WARNING) logger.logException(
          "Detected connection error " + e + " - closing.", e);
    } finally {
      _state.clearMessage();
    }
  }

  protected void sendGreetings() {
    _conn.println("220 " + _conn.getServerGreetingsName() + " SMTP ePOST "+ PostProxy.version);
  }

  protected void handleCommand() throws IOException {
    _currentLine = _conn.readLine();

    if (_currentLine == null) {
      quit();
      return;
    }
    
    _currentLine = _currentLine.trim();

    // eliminate invalid line lengths before parsing
    if (! commandLegalSize()) {
      return;
    }

    String commandName = _currentLine.substring(0, 4).toUpperCase();

    SmtpCommand command = _registry.getCommand(commandName);

    if (command == null) {
      _conn.println("500 Command not recognized");
      return;
    }
    
    if (authenticate && (_state.getUser() == null) && command.authenticationRequired()) {
      _conn.println("530 Authentication required");
      return;
    }
    
    command.execute(_conn, _state, _manager, _currentLine);
  }

  private boolean commandLegalSize() {
    if (_currentLine.length() < 4) {
      _conn.println("500 Invalid command. Must be 4 characters");

      return false;
    }

    if (_currentLine.length() > 4 &&
        _currentLine.charAt(4) != ' ') {
      _conn.println("500 Invalid command. Must be 4 characters");

      return false;
    }

    if (_currentLine.length() > 1000) {
      _conn.println("500 Command too long.  1000 character maximum.");

      return false;
    }

    return true;
  }

  public void quit() {
    _quitting = true;
  }
}
