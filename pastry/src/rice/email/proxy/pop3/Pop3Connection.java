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
package rice.email.proxy.pop3;

import rice.email.proxy.util.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;

import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;


public class Pop3Connection {
  
  // networking stuff
  private static final int TIMEOUT_MILLIS = 1000 * 60 * 30;
  Socket _socket;
  InetAddress _clientAddress;
  
  // IO stuff
  BufferedReader _in;
  PrintWriter _out;
  
  Pop3Handler handler;

  Environment environment;
  
  Logger logger;
  
  public Pop3Connection(Pop3Handler handler, Socket socket, Environment env) throws IOException {
    this.handler = handler;
    this.environment = env;
    this.logger = environment.getLogManager().getLogger(Pop3Connection.class, null);
    configureSocket(socket);
    configureStreams();
  }
  
  private void configureStreams() throws IOException {
    OutputStream o = _socket.getOutputStream();
    InputStream i = _socket.getInputStream();
    _out = new PrintWriter(o, true);
    _in = new BufferedReader(new InputStreamReader(i));
  }
  
  private void configureSocket(Socket socket) throws SocketException {
    _socket = socket;
    _socket.setSoTimeout(TIMEOUT_MILLIS);
    _clientAddress = _socket.getInetAddress();
  }
  
  public void quit() {
    try {
      close();
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException("",e);
    }
  }
  
  public void close() throws IOException {
    _socket.close();
  }
  
  public void println(String line) {
    if (logger.level <= Logger.FINEST) logger.log("S: " + line);
    _out.print(line);
    println();
  }
  
  public void print(String line) {
    if (logger.level <= Logger.FINEST) logger.log(line);
    _out.print(line);
  }
  
  public void println() {
    if (logger.level <= Logger.FINEST) logger.log("");
    _out.print("\r\n");
    _out.flush();
  }
  
  public void print(Reader in) throws IOException {
    StreamUtils.copy(in, _out);
    _out.flush();
  }
  
  public String readLine() throws IOException {
    String line = _in.readLine();
    if (logger.level <= Logger.FINEST) logger.log("C: " + line);
    
    return line;
  }
  
  public String getClientAddress() {
    return _clientAddress.toString();
  }

  public Environment getEnvironment() {
    return environment;
  }
}
