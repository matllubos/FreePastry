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
package rice.email.proxy.imap;

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

import java.util.regex.*;



/**
 * A <code>Socket</code> wrapper for use by  {@link
 * rice.email.proxy.imap.commands.AbstractImapCommand ImapCommands}.
 */
public class ImapConnection
{

    // This 5-minute timeout isn't strictly-speaking legal.
    // The specs say 30 minutes.
    private static final int TIMEOUT_MILLIS = 1000 * 60 * 30;

    // networking/io stuff
    Socket _socket;
    public PrintWriter _out;
    public BufferedReader _in;
    InetAddress _clientAddress;
    SpyInputStream _spy;
    Environment environment;
    protected Logger logger;
    
    // see quit()
    Quittable _handler;

    public ImapConnection(Quittable handler, Socket sock, Environment env)
                   throws IOException
    {
      this.environment = env;
      this.logger = env.getLogManager().getLogger(getClass(), null);
        // networking
        if (sock != null) {
          _socket = sock;
          _socket.setSoTimeout(TIMEOUT_MILLIS);
          _clientAddress = _socket.getInetAddress();

          // streams
          OutputStream out = _socket.getOutputStream();
          InputStream in = _socket.getInputStream();

        /* cheap logging trick */

          // _spy = new SpyInputStream(in, System.out);
          _out = new PrintWriter(out, true);
          _in = new BufferedReader(new InputStreamReader(in));
        }

        // protocol
        _handler = handler;
    }

    /**
     * Writes a string and newline to the output stream.  Currently
     * also writes to System.out for logging purposes.
     * 
     * <p>
     * Command implementations, in general, should not this method.
     * They should instead use one of the methods in {@link
     * rice.email.proxy.imap.commands.AbstractImapCommand}.
     * </p>
     */
    public void println(String line)
    {
      if (logger.level <= Logger.FINEST) logger.log(
        "S: " + line);
        _out.print(line);
        _out.print("\r\n");
        _out.flush();
    }

    /**
     * Sometimes you don't need a newline.
     * 
     * <p>
     * Most command implementations should use one of the methods in
     * {@link rice.email.proxy.imap.commands.AbstractImapCommand}. Some
     * commands, like FETCH, require complex replies that are best
     * implemented by writing to an ImapConnection in small chunks.
     * </p>
     */
    public void print(String string)
    {
      if (logger.level <= Logger.FINEST) logger.log(
          string);
        _out.print(string);
    }

    /**
     * Sends the entire contents of a Reader to the client, and
     * closes it.
     * 
     * <p>
     * This method is useful for commands like FETCH, which need to
     * send the contents of StoredMessages.
     * </p>
     * 
     * <p>
     * The data written will not be logged
     * </p>
     */
    public void print(Reader in)
               throws IOException
    {
      String result = StreamUtils.toString(in);
      print(result);
    }

    /**
     * Allows reading directly from the input stream.
     * 
     * <p>
     * There is only one command at the moment which has a good
     * excuse to use this method: APPEND. If at some point it seems
     * like a good idea to allow IMAP literals in any part of any
     * command, then excuses might be given to other classes.
     * </p>
     * 
     * <p>
     * The data written will not be logged
     * </p>
     */
    public BufferedReader getReader() {
        return _in;
    }

    /**
     * Reads a line sent by the client.
     * 
     * <p>
     * The only classes with an excuse to use this method at the
     * moment are AppendCommand and ParserImapHandler.
     * </p>
     *
     * @throws DisconnectedException If the socket has been disconnected
     */
    public String readLine() throws IOException {
      try {
        String line = _in.readLine();

        /* if client has disconnected, make sure socket is closed and throw exception */
        if (line == null) {
          if (logger.level <= Logger.FINEST) logger.log(
              "C: <disconnected>");

            close();
            throw new DisconnectedException();
        } else {
            /* more crude debug logging */
          if (logger.level <= Logger.FINEST) logger.log(
              "C: " + line);

            return line;
        }
      } catch (java.net.SocketException e) {
        close();
        
        throw new DisconnectedException();
      }
    }

    /**
     * Specifies that no more commands should be processed after the
     * current on finishes.
     * 
     * @see rice.email.proxy.imap.Quittable#quit()
     */
    public void quit() {
        _handler.quit();
    }

    /**
     * Closes the underlying socket.
     */
    void close() throws IOException {
        _socket.close();
    }

    /**
     * Returns identifying information (IP and/or hostname) of the
     * client.
     * 
     * <p>
     * For debugging/logging only, at present. The output format of
     * this method may change.
     * </p>
     */
    public String getClientAddress() {
        return _clientAddress.toString();
    }

    /**
     * returns the environment
     */
    public Environment getEnvironment() {
      return environment;
    }
}











