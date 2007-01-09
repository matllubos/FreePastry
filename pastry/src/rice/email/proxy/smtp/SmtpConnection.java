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
package rice.email.proxy.smtp;

import java.io.*;
import java.net.*;

import rice.environment.logging.Logger;

public class SmtpConnection {

    // TODO: clean up getting localhost name
    private static final int TIMEOUT_MILLIS = 1000 * 30;
    private InetAddress serverAddress;
    private SmtpServer server;
    protected Logger logger;

    // networking/io stuff
    Socket sock;
    InetAddress clientAddress;
    public PrintWriter out;
    //public BufferedReader in;
    public StreamTokenizer in;
    SmtpHandler handler;
    String heloName;

    public SmtpConnection(SmtpHandler handler, Socket sock, SmtpServer server) throws IOException {
      serverAddress = server.getLocalHost();
        if (sock != null) {
          this.sock = sock;
          sock.setSoTimeout(TIMEOUT_MILLIS);
          clientAddress = sock.getInetAddress();
          OutputStream o = sock.getOutputStream();
          InputStream i = sock.getInputStream();
          out = new PrintWriter(o, true);
          in = new StreamTokenizer(new InputStreamReader(i));
          logger = server.getEnvironment().getLogManager().getLogger(SmtpConnection.class, null);  
          in.resetSyntax();
          in.eolIsSignificant(false);
          in.wordChars(1, Integer.MAX_VALUE);
          in.whitespaceChars(10, 10);
        }
          
      this.server = server;
        this.handler = handler;
    }
    
    public SmtpServer getServer() {
      return server;
    }
    
    public StreamTokenizer getTokenizer() {
      return in;
    }

    public void println(String line) {
        if (logger.level <= Logger.FINEST) logger.log(
            "S: " + line);
        out.print(line + "\r\n");
        out.flush();
    }

    public String readLine() throws IOException {
      int result = in.nextToken();
      
      if (result == in.TT_WORD) {
        if (logger.level <= Logger.FINEST) logger.log(
            "C: " + in.sval);
        return in.sval;
      } else if (result == in.TT_NUMBER) {
        if (logger.level <= Logger.FINEST) logger.log(
            "C*:" + in.nval);
        return "" + in.nval;
      } else {
        return null;
      }
    }

    public String getClientAddress() {
        return clientAddress.getHostName();
    }

    public InetAddress getServerAddress() {
        return serverAddress;
    }

    public String getServerGreetingsName() {
        InetAddress serverAddress = getServerAddress();

        if (serverAddress != null)
            return serverAddress.toString();
        else
            return System.getProperty("user.name"); 
    }

    public String getHeloName() {
        return heloName;
    }

    public void setHeloName(String n) {
        heloName = n;
    }

    public void quit() {
        handler.quit();
    }

    public boolean isLocal() throws IOException {
      return (sock.getInetAddress().isLoopbackAddress() ||
              sock.getInetAddress().equals(server.getLocalHost()));
    }
}
