package rice.email.proxy.pop3;

import rice.email.proxy.pop3.commands.*;
import rice.email.proxy.user.*;

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
  
  public Pop3Handler(Pop3CommandRegistry registry, UserManager manager) {
    _registry = registry;
    _manager = manager;
  }
  
  public void handleConnection(Socket socket) throws IOException {
    try {
      _conn = new Pop3Connection(this, socket);
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
      e.printStackTrace();
    } finally {
      try {
        socket.close();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
  }
  
  void sendGreetings() {          
    String challenge = "<" + System.currentTimeMillis() + "@localhost>";
    try {
      challenge = "<" + System.currentTimeMillis() + "@" + InetAddress.getLocalHost().getHostName() + ">";
    } catch (UnknownHostException e) {
    } 

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