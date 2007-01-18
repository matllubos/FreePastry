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
package rice.pastry.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.appsocket.*;
import rice.p2p.commonapi.exception.*;
import rice.pastry.NetworkListener;
import rice.selector.*;
import rice.selector.TimerTask;

/**
 * Private class which is tasked with reading the greeting message off of a
 * newly connected socket. This greeting message says who the socket is coming
 * from, and allows the connected to hand the socket off the appropriate node
 * handle.
 *
 * @version $Id: SocketCollectionManager.java 3061 2006-02-14 00:56:04Z jeffh $
 * @author jeffh
 */
public class SocketAppSocket extends SelectionKeyHandler implements AppSocket {

  public static final byte CONNECTION_UNKNOWN_ERROR = -1;
  public static final byte CONNECTION_UNKNOWN = -100;
  public static final byte CONNECTION_OK = 0;
  public static final byte CONNECTION_NO_APP = 1;
  public static final byte CONNECTION_NO_ACCEPTOR = 2;
  
  /**
   * The manager.
   */
  private final SocketCollectionManager manager;

  // the key to read from
  protected SelectionKey key;
  
  // the channel we are associated with
  protected SocketChannel channel;

  protected AppSocketReceiver receiver, reader, writer;
  
  ByteBuffer toWrite;
  byte connectResult = CONNECTION_UNKNOWN;
  
  
  int appId;
  
  /**
   * Constructor which accepts an incoming connection, represented by the
   * selection key. This constructor builds a new SocketManager, and waits
   * until the greeting message is read from the other end. Once the greeting
   * is received, the manager makes sure that a socket for this handle is not
   * already open, and then proceeds as normal.
   *
   * @param key The server accepting key for the channel
   * @param manager TODO
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public SocketAppSocket(SocketCollectionManager manager, SelectionKey key, int appId) throws IOException {
    this.appId = appId;
    this.manager = manager;
    acceptConnection(key);
  }
  
  /**
   * Constructor which creates an outgoing connection to the given node
   * handle using the provided address as a source route intermediate node. 
   * This creates the connection by building the socket and sending
   * accross the greeting message. Once the response greeting message is
   * received, everything proceeds as normal.
   * @param manager TODO
   *
   * @param address The ultimate destination of this socket
   * @param proxy The intermediate destination of this socket (if a source route)
   * @exception IOException An error
   */
  public SocketAppSocket(SocketCollectionManager manager, SourceRoute path, int appId, AppSocketReceiver receiver, int timeout) throws IOException {
    this.appId = appId;
    this.receiver = receiver;
    this.manager = manager;
    
    if (manager.logger.level <= Logger.FINE) manager.logger.log("Opening connection with path " + path);
    
    
    // this is a bit lazy, and could be optimized, but I'm just going to do the exact thing that a SocketManager does, but then aggregate 
    // them into a big byte[]
//     ArrayList tempList = new ArrayList();  
     SocketBuffer sb = new SocketBuffer(path, appId);
      
//     int sizeToAllocate = 0;     
//     Iterator i = tempList.iterator();
//     while(i.hasNext()) {
//       byte[] next = (byte[])i.next();
//       sizeToAllocate+=next.length;
//     }
     
//     byte[] toWriteBytes = new byte[sizeToAllocate];
//     int ptr = 0;
//     i = tempList.iterator();
//     while(i.hasNext()) {
//       byte[] next = (byte[])i.next();
//       System.arraycopy(next, 0, toWriteBytes, ptr, next.length);
//       ptr+=next.length;
//     }     
     toWrite = sb.getBuffer(); //ByteBuffer.wrap(toWriteBytes);
    // build the entire connection
     startTimer(timeout, receiver);
     createConnection(path);
  }
  
  public String toString() {
    return "SAS{"+appId+"}"+channel; 
  }
  
  /**
   * Method which initiates a shutdown of this socket by calling 
   * shutdownOutput().  This has the effect of removing the manager from
   * the open list.
   */
  public void shutdownOutput() {
    try {
      if (manager.logger.level <= Logger.FINE) manager.logger.log("Shutting down output on app connection " + this);
      
      if (channel != null)
        channel.socket().shutdownOutput();
      else
        if (manager.logger.level <= Logger.SEVERE) manager.logger.log( "ERROR: Unable to shutdown output on channel; channel is null!");

      manager.appSocketClosed(this);
      manager.pastryNode.getEnvironment().getSelectorManager().modifyKey(key);
    } catch (IOException e) {
      if (manager.logger.level <= Logger.SEVERE) manager.logger.log( "ERROR: Received exception " + e + " while shutting down output.");
      close();
    }
  }

  /**
   * Method which closes down this socket manager, by closing the socket,
   * cancelling the key and setting the key to be interested in nothing
   */
  public void close() {
    try {
//      System.out.println("SocketAppSocket.close()");
      if (manager.logger.level <= Logger.FINE) {
        manager.logger.log("Closing connection with " + this);
      }
      
      fireAllTimers();
      
      if (manager.pastryNode != null)
        manager.pastryNode.broadcastChannelClosed((InetSocketAddress) channel.socket().getRemoteSocketAddress());
      
      if (key != null) {
        if (manager.logger.level <= Logger.WARNING) {
          if (!manager.pastryNode.getEnvironment().getSelectorManager().isSelectorThread()) {
            manager.logger.logException("WARNING: cancelling key:"+key+" on the wrong thread.", new Exception("Stack Trace"));
          }
        }
        key.cancel();
        key.attach(null);
        key = null;
      }
      
      manager.unIdentifiedSM.remove(this);
      
      if (channel != null) 
        channel.close();

        manager.appSocketClosed(this);
    } catch (IOException e) {
      if (manager.logger.level <= Logger.SEVERE) manager.logger.log( "ERROR: Recevied exception " + e + " while closing socket!");
    }
  }
  
  private void fireAllTimers() {
    Iterator i = timers.keySet().iterator();
    while(i.hasNext()) {
      AppSocketReceiver rec = (AppSocketReceiver)i.next(); 
      rec.receiveException(this, new TimeoutException());
      TimerTask timer = (TimerTask)timers.get(rec);
      timer.cancel();
      System.out.println("fired"+timer);
      i.remove();
    }
  }

  /**
   * Method which should change the interestOps of the handler's key. This
   * method should *ONLY* be called by the selection thread in the context of
   * a select().
   *
   * @param key The key in question
   */
  public synchronized void modifyKey(SelectionKey key) {
    int flag = 0;
    if (reader != null) {
      flag |= SelectionKey.OP_READ;
    }
    if (writer != null) {
      flag |= SelectionKey.OP_WRITE;
    }
    if (toWrite != null)
      flag |= SelectionKey.OP_WRITE; 
    if (connectResult == CONNECTION_UNKNOWN)
      flag |= SelectionKey.OP_READ;
    
    //System.out.println(this+"modifyKey()"+flag);
    key.interestOps(flag);
  }

  /**
   * Specified by the SelectionKeyHandler interface - calling this tells this
   * socket manager that the connection has completed and we can now
   * read/write.
   *
   * @param key The key which is connectable.
   */
  public void connect(SelectionKey key) {
    try {
      // deregister interest in connecting to this socket
      if (channel.finishConnect()) 
        key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);

      if (manager.logger.level <= Logger.FINE) manager.logger.log("(SM) Found connectable channel - completed connection");
    } catch (Exception e) {
      if (manager.logger.level <= Logger.FINE) manager.logger.logException(
          "(SM) Unable to connect to " + this,e);
      exceptionAndClose(e);
    }
  }

  /**
   * Reads from the socket attached to this connector.
   *
   * @param key The selection key for this manager
   */
  public void read(SelectionKey key) {
    //System.out.println(this+"Reading");
    if (connectResult == CONNECTION_UNKNOWN) {
      try {
        clearTimer(receiver); 
        manager.pastryNode.getEnvironment().getSelectorManager().modifyKey(key);
        ByteBuffer answer = ByteBuffer.allocate(1);
        ((SocketChannel)key.channel()).read(answer);
        answer.clear();
        connectResult = answer.get();
        //System.out.println(this+"Read "+connectResult);
        switch(connectResult) {
          case CONNECTION_OK:
            receiver.receiveSocket(this); // on connector side
            return;
          case CONNECTION_NO_APP:
            exceptionAndClose(new AppNotRegisteredException(appId));
            return;
          case CONNECTION_NO_ACCEPTOR:
            exceptionAndClose(new NoReceiverAvailableException());            
            return;
          default:
            exceptionAndClose(new AppSocketException("Unknown error "+connectResult));
            return;
        }
      } catch (IOException ioe) {
        exceptionAndClose(ioe); 
      }
      return;
    }
    AppSocketReceiver temp = reader;
    clearTimer(reader);
    reader = null;
    temp.receiveSelectResult(this, true, false);
    manager.pastryNode.getEnvironment().getSelectorManager().modifyKey(key);
  }

  /**
   * AppSocketReceiver -> Timer
   */
  Hashtable timers = new Hashtable();
  
  private void startTimer(int millis, final AppSocketReceiver theReceiver) {    
    if (millis <= 0) return;
    clearTimer(theReceiver);
    TimerTask timer = new TimerTask() {
    
      public void run() {
        timers.remove(theReceiver);
        theReceiver.receiveException(SocketAppSocket.this, new TimeoutException());
        close();
      }    
    }; 
    
//    System.out.println("startTimer()"+timer);
//    new Exception("stack trace").printStackTrace(System.out);
    timers.put(theReceiver, timer);
    manager.pastryNode.getEnvironment().getSelectorManager().getTimer().schedule(timer, millis);
  }
  
  private void clearTimer(AppSocketReceiver theReceiver) {
    if (theReceiver == null) return;
    TimerTask timer = (TimerTask)timers.remove(theReceiver);
//    System.out.println("Clearing "+timer);
    if (timer == null) return;
    timer.cancel();
    timer = null;
  }

  private void exceptionAndClose(Exception e) {
    clearTimer(receiver);
    receiver.receiveException(SocketAppSocket.this, e);    
    close();
  }
  
  /**
   * Writes to the socket attached to this socket manager.
   *
   * @param key The selection key for this manager
   */
  public synchronized void write(SelectionKey key) {
    if (toWrite != null) {
      try {
       // System.out.println(this+"SocketAppSocket.wroteHeader."+toWrite.remaining());
        ((SocketChannel)key.channel()).write(toWrite);        
      } catch (IOException ioe) {
        exceptionAndClose(ioe); 
      }
      
      if (toWrite.hasRemaining()) {
        // write will be called later
        return;
      } else {
        toWrite = null;
//        receiver.receiveSocket(this); moved to read
      }
    }
    
    if (writer == null) {
      key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
      return;
    }
    AppSocketReceiver temp = writer;
    clearTimer(writer);
    writer = null;
    temp.receiveSelectResult(this, false, true);
    manager.pastryNode.getEnvironment().getSelectorManager().modifyKey(key);
  }

  /**
   * Accepts a new connection on the given key
   *
   * @param serverKey The server socket key
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  protected void acceptConnection(SelectionKey key) throws IOException {      
    //System.out.println("accept connection");
    connectResult = CONNECTION_OK;
    this.channel = (SocketChannel) key.channel();
    this.key = manager.pastryNode.getEnvironment().getSelectorManager().register(key.channel(), this, 0);
    // lookup acceptor
    toWrite = ByteBuffer.allocate(1);
    try {
      manager.pastryNode.acceptAppSocket(this, appId);
      toWrite.put(CONNECTION_OK);
    } catch (AppNotRegisteredException anre) {
      if (manager.logger.level <= Logger.WARNING) manager.logger.log("Sending error to connecter "+channel+" "+anre);
      toWrite.put(CONNECTION_NO_APP);
    } catch (NoReceiverAvailableException nrae) {
      if (manager.logger.level <= Logger.WARNING) manager.logger.log("Sending error to connecter "+channel+" "+nrae);
      toWrite.put(CONNECTION_NO_ACCEPTOR);
    } catch (AppSocketException ase) {
      if (manager.logger.level <= Logger.WARNING) manager.logger.log("Sending error to connecter "+channel+" "+ase);
      toWrite.put(CONNECTION_UNKNOWN_ERROR);
    }
    toWrite.clear();
    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    
    
    if (manager.logger.level <= Logger.FINE) manager.logger.log(
        "(SM) Accepted app connection from " + 
        channel.socket().getRemoteSocketAddress());
  }

  /**
   * Creates the outgoing socket to the remote handle
   *
   * @param address The accress to connect to
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  protected void createConnection(final SourceRoute path) throws IOException {
    this.channel = SocketChannel.open();
    this.channel.socket().setSendBufferSize(manager.SOCKET_BUFFER_SIZE);
    this.channel.socket().setReceiveBufferSize(manager.SOCKET_BUFFER_SIZE);
    this.channel.configureBlocking(false);
    this.key = manager.pastryNode.getEnvironment().getSelectorManager().register(channel, this, 0);
    
    if (manager.logger.level <= Logger.FINE) manager.logger.log("(SM) Initiating socket connection to path " + path);
    
    manager.pastryNode.broadcastChannelOpened(path.getFirstHop().getAddress(manager.localAddress), NetworkListener.REASON_APP_SOCKET_NORMAL);

    if (this.channel.connect(path.getFirstHop().getAddress(manager.localAddress))) 
      this.key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
    else 
      this.key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
  }

  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    //System.out.println(this+"read");
    return channel.read(dsts, offset, length);
  }

  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    //System.out.println(this+"write("+srcs.length+","+offset+","+length+")");
    return channel.write(srcs, offset, length);
  }

  public synchronized void register(boolean wantToRead, boolean wantToWrite, int timeout, AppSocketReceiver receiver) {
    if (wantToRead) {
      reader = receiver; 
    }
    if (wantToWrite) {
      writer = receiver; 
    }
    startTimer(timeout, receiver);
    manager.pastryNode.getEnvironment().getSelectorManager().modifyKey(key);    
  }
}