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
package org.mpisws.p2p.transport.wire;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Map;

import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.SocketCallback;

import rice.environment.logging.Logger;
import rice.selector.SelectionKeyHandler;

public class SocketManager extends SelectionKeyHandler implements P2PSocket<InetSocketAddress>, SocketRequestHandle<InetSocketAddress> {

  // the key to read from
  protected SelectionKey key;
  
  // the channel we are associated with
  protected SocketChannel channel;

  // the timer we use to check for stalled nodes
  protected rice.selector.TimerTask timer;

  protected TCPLayer tcp;

  Logger logger;
  
  InetSocketAddress addr;
  
  Map<String, Integer> options;
  
  protected P2PSocketReceiver reader, writer;

  
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
  public SocketManager(TCPLayer tcp, SelectionKey serverKey) throws IOException {
    this.tcp = tcp;
    logger = tcp.logger;
    
    channel = (SocketChannel) ((ServerSocketChannel) serverKey.channel()).accept();
    channel.socket().setSendBufferSize(tcp.SOCKET_BUFFER_SIZE);
    channel.socket().setReceiveBufferSize(tcp.SOCKET_BUFFER_SIZE);
    channel.configureBlocking(false);    
    addr = (InetSocketAddress)channel.socket().getRemoteSocketAddress();
    
    if (logger.level <= Logger.FINE) logger.log("(SA) " + "Accepted incoming connection from " + addr);

    key = tcp.wire.environment.getSelectorManager().register(channel, this, 0);
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
  public SocketManager(final TCPLayer tcp, final InetSocketAddress addr, final SocketCallback<InetSocketAddress> c, Map<String, Integer> options) throws IOException {
    this.tcp = tcp;
    this.options = options;
    logger = tcp.logger;
    this.addr = addr;
//    if (tcp.logger.level <= Logger.FINE) tcp.logger.log("Opening connection to " + addr);
    
    channel = SocketChannel.open();
    channel.socket().setSendBufferSize(tcp.SOCKET_BUFFER_SIZE);
    channel.socket().setReceiveBufferSize(tcp.SOCKET_BUFFER_SIZE);
    channel.configureBlocking(false);
    
    if (logger.level <= Logger.FINE) logger.log("(SM) Initiating socket connection to " + addr);
    
    if (channel.connect(addr)) {
      key = tcp.wire.environment.getSelectorManager().register(channel, this, 0);
      c.receiveResult(SocketManager.this, SocketManager.this);
    } else {
      key = tcp.wire.environment.getSelectorManager().register(channel, new SelectionKeyHandler(){
      
        @Override
        public void write(SelectionKey key) {
          SocketManager.this.write(key);
        }      
        @Override
        public void read(SelectionKey key) {
          SocketManager.this.read(key);
        }      
        @Override
        public void modifyKey(SelectionKey key) {
          SocketManager.this.modifyKey(key);
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
            if (channel.finishConnect()) {
              key = tcp.wire.environment.getSelectorManager().register(channel, SocketManager.this, key.interestOps() & ~SelectionKey.OP_CONNECT);
              c.receiveResult(SocketManager.this, SocketManager.this);
            }
          } catch (IOException e) {
            if (c == null) {
              tcp.wire.errorHandler.receivedException(addr, e);              
            } else {
              c.receiveException(SocketManager.this, e);
            }
            close();
          }
        }
      }, SelectionKey.OP_CONNECT);
    }
  }
  
  public String toString() {
    return "SM "+channel; 
  }
  
  /**
   * Method which initiates a shutdown of this socket by calling 
   * shutdownOutput().  This has the effect of removing the manager from
   * the open list.
   */
//  public void shutdown() {
//    try {
//      if (tcp.logger.level <= Logger.FINE) tcp.logger.log("Shutting down output on to " + addr);
//      
//      if (channel != null)
//        channel.socket().shutdownOutput();
//      else
//        if (logger.level <= Logger.SEVERE) logger.log( "ERROR: Unable to shutdown output on channel; channel is null!");
//
//      tcp.wire.environment.getSelectorManager().modifyKey(key);
//    } catch (IOException e) {
//      if (logger.level <= Logger.SEVERE) logger.log( "ERROR: Received exception " + e + " while shutting down output.");
//      close();
//    }
//  }

  /**
   * Method which closes down this socket manager, by closing the socket,
   * cancelling the key and setting the key to be interested in nothing
   */
  public void close() {
//    if (logger.level <= Logger.FINE) logger.log("close()");
    try {
      if (logger.level <= Logger.FINE) {
        logger.log("Closing connection to " + addr);
      }
      
      if (key != null) {
        key.cancel();
        key.attach(null);
        key = null;
      }
      
      if (channel != null) 
        channel.close();

//      if (path != null) {
//        manager.socketClosed(path, this);
//
//        Iterator i = writer.getQueue().iterator();
//        writer.reset();
//
//        /**
//         * Here, if we have not been declared dead, then we attempt to resend
//         * the messages. However, if we have been declared dead, we reroute
//         * the route messages via the pastry node, but delete any messages
//         * routed directly.
//         */
//        while (i.hasNext()) {
//          Object o = i.next();
//
//          if ((o instanceof SocketBuffer) && (manager.manager != null)) 
//            manager.manager.reroute(path.getLastHop(), (SocketBuffer) o);
//        } 
//
//        path = null;
//      }
    } catch (IOException e) {
      if (logger.level <= Logger.SEVERE) logger.log( "ERROR: Recevied exception " + e + " while closing socket!");
    }
  }

  /**
   * The entry point for outgoing messages - messages from here are ensocketQueued
   * for transport to the remote node
   *
   * @param message DESCRIBE THE PARAMETER
   */
//  public Cancellable send(ByteBuffer message, int priority, Continuation<ByteBuffer, Exception> ack) {
//    Envelope e = new Envelope(message, priority, ack); 
//    pending.put(e);
//    tcp.wire.environment.getSelectorManager().modifyKey(key);
//    return e;
//  }

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
    key.interestOps(flag);
  }

  /**
   * Reads from the socket attached to this connector.
   *
   * @param key The selection key for this manager
   */
  public void read(SelectionKey key) {
    P2PSocketReceiver temp = null;
    synchronized(this) {
      if (reader == null) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        return;
      }
      temp = reader;
      reader = null;
    } // synchronized(this)
    try {
      temp.receiveSelectResult(this, true, false);
    } catch (IOException ioe) {
      temp.receiveException(this, ioe);
    }
    tcp.wire.environment.getSelectorManager().modifyKey(key);
  }

  /**
   * Writes to the socket attached to this socket manager.
   *
   * @param key The selection key for this manager
   */
  public void write(SelectionKey key) {
    P2PSocketReceiver temp = null;
    synchronized(this) {
      if (writer == null) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        return;
      }
      temp = writer;
//      clearTimer(writer);
      writer = null;
    }
    try {
      temp.receiveSelectResult(this, false, true);
    } catch (IOException ioe) {
      temp.receiveException(this, ioe);
    }
    tcp.wire.environment.getSelectorManager().modifyKey(key);
  }

  public synchronized void register(final boolean wantToRead, final boolean wantToWrite, P2PSocketReceiver receiver) {
    if (key == null) throw new IllegalStateException("Socket "+this+" is already closed.");

    // this check happens before setting the reader because we don't want to change any state if the exception is going ot be thrown
    // so don't put this check down below!
    if (wantToWrite) {
      if (writer != null) {
        if (writer != receiver) throw new IllegalStateException("Already registered "+writer+" for writing, you can't register "+receiver+" for writing as well!"); 
      }
    }
    
    if (wantToRead) {
      if (reader != null) {
        if (reader != receiver) throw new IllegalStateException("Already registered "+reader+" for reading, you can't register "+receiver+" for reading as well!"); 
      }
      reader = receiver; 
    }
    
    if (wantToWrite) {
      writer = receiver; 
    }
    tcp.wire.environment.getSelectorManager().modifyKey(key);        
  }
  
  /**
   * Method which initiates a shutdown of this socket by calling 
   * shutdownOutput().  This has the effect of removing the manager from
   * the open list.
   */
  public void shutdownOutput() {    
    boolean closeMe = false;
    synchronized(this) {
      if (key == null) throw new IllegalStateException("Socket already closed.");
      try {
        if (logger.level <= Logger.FINE) logger.log("Shutting down output on app connection " + this);
        
        // do we need to do this?  or does this happen twice now?
//        manager.appSocketClosed(this);
        
        if (channel != null)
          channel.socket().shutdownOutput();
        else
          if (logger.level <= Logger.SEVERE) logger.log( "ERROR: Unable to shutdown output on channel; channel is null!");
  
      } catch (IOException e) {
        if (logger.level <= Logger.SEVERE) logger.log( "ERROR: Received exception " + e + " while shutting down output.");
        closeMe = true;
      }
    } // synchronized(this)
    tcp.wire.environment.getSelectorManager().modifyKey(key);
    
    // close has it's own synchronization semantics, don't want to be holding a lock when calling
    if (closeMe) {
      close();
    }
  }

  public long read(ByteBuffer dst) throws IOException {
    long ret = channel.read(dst);
    if (logger.level <= Logger.FINER) {
      if (logger.level <= Logger.FINEST) {
        logger.log(this+"read("+ret+"):"+Arrays.toString(dst.array()));
      } else {
        logger.log(this+"read("+ret+")");
      }
    }    
    return ret;
  }
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    //System.out.println(this+"read");
    return channel.read(dsts, offset, length);
  }

  public long write(ByteBuffer src) throws IOException {
    long ret = channel.write(src);
    if (logger.level <= Logger.FINER) {
      if (logger.level <= Logger.FINEST) {
        logger.log(this+"write("+ret+"):"+Arrays.toString(src.array()));
      } else {
        logger.log(this+"write("+ret+")");
      }
    }
    return ret;
  }
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    //System.out.println(this+"write("+srcs.length+","+offset+","+length+")");
    return channel.write(srcs, offset, length);
  }

  public boolean cancel() {
    if (key == null) return false;
    
    close();
    return true;
  }
  
  private void exceptionAndClose(IOException e) {
//    clearTimer(receiver);
    tcp.wire.errorHandler.receivedException(addr, e);    
    close();
  }

  public InetSocketAddress getIdentifier() {
    return addr;
  }

  public Map<String, Integer> getOptions() {
    return options;
  }

//  TreeSet<Envelope> pendingMessages;  
//  private int envSeq = Integer.MIN_VALUE;
//  class Envelope implements Comparable<Envelope>, Cancellable {
//    ByteBuffer message;
//    // for ordering
//    int priority;
//    int seq;
//    Continuation<ByteBuffer, Exception> ack;
//    
//    public Envelope(ByteBuffer message, int priority, Continuation<ByteBuffer, Exception> ack) {
//      this.message = message;
//      this.priority = priority;
//      this.ack = ack;
//      this.seq = envSeq++;
//    }
//
//    public int compareTo(Envelope that) {
//      int ret = that.priority - this.priority;
//      if (ret == 0)
//        ret = that.seq - this.seq;
//      return ret;
//    }
//
//    public boolean cancel() {
//      return pendingMessages.remove(this);
//    }
//  }
}
