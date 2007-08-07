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
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.MessageCallback;

import rice.Continuation;
import rice.Destructable;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.selector.SelectionKeyHandler;

public class UDPLayer extends SelectionKeyHandler implements Destructable {
  public static final Map<String, Integer> OPTIONS;  
  static {
    Map<String, Integer> map = new HashMap<String, Integer>();
    map.put(WireTransportLayer.OPTION_TRANSPORT_TYPE, WireTransportLayer.TRANSPORT_TYPE_DATAGRAM);
    OPTIONS = Collections.unmodifiableMap(map);    
  }
  
  Logger logger;
  
  // the channel used from talking to the network
  private DatagramChannel channel;

  // the key used to determine what has taken place
  private SelectionKey key;
  
  // the size of the buffer used to read incoming datagrams must be big enough
  // to encompass multiple datagram packets
  public int DATAGRAM_RECEIVE_BUFFER_SIZE;
  
  // the size of the buffer used to send outgoing datagrams this is also the
  // largest message size than can be sent via UDP
  public int DATAGRAM_SEND_BUFFER_SIZE;
  
  /**
   * We always send this, and only pass up messages that match this.
   * 
   * If the message doesn't match this, then we call the error handler.
   * 
   * Usually a magic number/version
   * 
   * TODO: Extend this to accept different versions, perhaps have a different layer-callback/version
   */
  
  List<Envelope> pendingMsgs;
  
  WireTransportLayerImpl wire;
  
  ByteBuffer readBuffer;
  
  public UDPLayer(WireTransportLayerImpl wire) throws IOException {
    this.wire = wire;
    
    this.logger = wire.environment.getLogManager().getLogger(UDPLayer.class, null);

    this.pendingMsgs = new LinkedList<Envelope>();
    openServerSocket();
  }

  /**
   * The ack is not the end to end, it's called when actually sent
   * 
   * @param destination
   * @param m
   * @param deliverAckToMe ack is when the message is sent to the wire
   */
  public MessageRequestHandle<InetSocketAddress, ByteBuffer> sendMessage(
      InetSocketAddress destination, 
      ByteBuffer msg,
      MessageCallback<InetSocketAddress, ByteBuffer> deliverAckToMe, 
      Map<String, Integer> options) {
    //logger.log("sendMessage("+destination+","+msg+","+deliverAckToMe+")"); 
    Envelope envelope;
    if (logger.level <= Logger.FINER-3) logger.log("sendMessage("+destination+","+msg+","+deliverAckToMe+")"); 
//    try {
    envelope = new Envelope(destination, msg, deliverAckToMe, options);
      synchronized (pendingMsgs) {        
        pendingMsgs.add(envelope);
      }

      wire.environment.getSelectorManager().modifyKey(key);
//    } catch (IOException e) {
//      if (logger.level <= Logger.SEVERE) logger.log(
//          "ERROR: Received exceptoin " + e + " while enqueuing ping " + msg);
//    }
      return envelope;
  }

  protected void openServerSocket() throws IOException {
//    logger.log("openServerSocket("+wire.bindAddress+")");
    Parameters p = wire.environment.getParameters();
    DATAGRAM_RECEIVE_BUFFER_SIZE = p.getInt("transport_wire_datagram_receive_buffer_size");
    DATAGRAM_SEND_BUFFER_SIZE = p.getInt("transport_wire_datagram_send_buffer_size");

    // allocate enough bytes to read data
    this.readBuffer = ByteBuffer.allocateDirect(DATAGRAM_SEND_BUFFER_SIZE);

    try {
      // bind to the appropriate port
      channel = DatagramChannel.open();
      channel.configureBlocking(false);
      channel.socket().setReuseAddress(true);
      channel.socket().bind(wire.bindAddress);
      channel.socket().setSendBufferSize(DATAGRAM_SEND_BUFFER_SIZE);
      channel.socket().setReceiveBufferSize(DATAGRAM_RECEIVE_BUFFER_SIZE);

      key = wire.environment.getSelectorManager().register(channel, this, 0);
      key.interestOps(SelectionKey.OP_READ);
      if (logger.level <= Logger.INFO) logger.log("UDPLayer binding to "+wire.bindAddress);
    } catch (IOException e) {
//      if (logger.level <= Logger.SEVERE) logger.log(
//          "PANIC: Error binding datagram server to address " + localAddress + ": " + e);
      throw e;
    }
//    logger.log("openServerSocket("+wire.bindAddress+")1");
  }
  

  
  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   */
  public void read(SelectionKey key) {
//    logger.log("read");

    try {
      InetSocketAddress address = null;
      
      while ((address = (InetSocketAddress) channel.receive(readBuffer)) != null) {
        readBuffer.flip();

        if (readBuffer.remaining() > 0) {
          readHeader(address);
          readBuffer.clear();
        } else {
          if (logger.level <= Logger.INFO) logger.log(
            "(PM) Read from datagram channel, but no bytes were there - no bad, but wierd.");
          break;
        }
      }
    } catch (IOException e) {
      wire.errorHandler.receivedException(null, e);
//      if (logger.level <= Logger.WARNING) logger.logException(
//          "ERROR (datagrammanager:read): ", e);
    } finally {
      readBuffer.clear();
    }
  }

  protected void readHeader(InetSocketAddress address) throws IOException {
    // see if we have the header
//    if (readBuffer.remaining() < wire.HEADER.length) {
//      byte[] remaining = new byte[readBuffer.remaining()];      
//      readBuffer.get(remaining);
//      wire.errorHandler.receivedUnexpectedData(address, remaining);
//      return;
//    }
    // get the header
//    byte[] header = new byte[wire.HEADER.length];
//    readBuffer.get(header, 0, wire.HEADER.length);
//    if (!Arrays.equals(header, wire.HEADER)) {
//      wire.errorHandler.receivedUnexpectedData(address, header);      
//      return;
//    }
    if (logger.level <= Logger.FINE) 
      logger.log("readheader("+address+","+readBuffer.remaining()+")");
    byte[] remaining = new byte[readBuffer.remaining()];      
    readBuffer.get(remaining);
    wire.messageReceived(address, ByteBuffer.wrap(remaining));    
  }
  

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   */
  public void write(SelectionKey key) {
//    logger.log("write");
    Envelope write = null;
    
    try {
      synchronized (pendingMsgs) {
        Iterator<Envelope> i = pendingMsgs.iterator();

        while (i.hasNext()) {
          write = i.next();          
          try {            
//            byte[] whole_msg = new byte[wire.HEADER.length+write.msg.remaining()];
//            System.arraycopy(wire.HEADER, 0, whole_msg, 0, wire.HEADER.length);
//            write.msg.get(whole_msg, wire.HEADER.length, write.msg.remaining());
//            ByteBuffer buf = ByteBuffer.wrap(whole_msg);

            int len = write.msg.remaining();
            if (logger.level <= Logger.FINEST) {
              logger.log("writing "+len+" to "+write.destination);
            }
            if (channel.send(write.msg, write.destination) == len) {
//              wire.msgSent(write.destination, whole_msg, WireTransportLayer.TRANSPORT_TYPE_DATAGRAM);
              if (write.continuation != null) write.continuation.ack(write);
              i.remove();              
            } else {
              break;
            }
          } catch (IOException e) {
            if (write.continuation == null) {
              wire.errorHandler.receivedException(write.destination, e);
            } else {
              write.continuation.sendFailed(write, e);
            }
            i.remove();
            //throw e;
            return; // to get another call to write() later
          }
        }
      }
    } catch (Exception e) {
      if (logger.level <= Logger.WARNING) {
        // This code prevents this line from filling up logs during some kinds of network outages
        // it makes this error only be printed 1ce/second
//        long now = timeSource.currentTimeMillis();
//        if (lastTimePrinted+1000 > now) return;
//        lastTimePrinted = now;
        
        logger.logException(
          "ERROR (datagrammanager:write) to " + write.destination, e);
      }        
    } finally {
      if (pendingMsgs.isEmpty()) 
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
    }
  }

  long lastTimePrinted = 0;
  
  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   */
  public void modifyKey(SelectionKey key) {
    synchronized (pendingMsgs) {
      if (! pendingMsgs.isEmpty()) 
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }
  }
  
  /**
   * Internal class which holds a pending datagram
   *
   * @author amislove
   */
  public class Envelope implements MessageRequestHandle<InetSocketAddress, ByteBuffer> {
    protected InetSocketAddress destination;
    /**
     * The message sans header.
     */
    protected ByteBuffer msg;
    protected MessageCallback<InetSocketAddress, ByteBuffer> continuation;
    Map<String, Integer> options;

    /**
     * Constructor for Envelope.
     *
     * @param adr DESCRIBE THE PARAMETER
     * @param m DESCRIBE THE PARAMETER
     */
    public Envelope(InetSocketAddress destination, 
        ByteBuffer msg,
        MessageCallback<InetSocketAddress, ByteBuffer> deliverAckToMe, 
        Map<String, Integer> options) {
      this.destination = destination;
      this.msg = msg;
      this.continuation = deliverAckToMe;
      this.options = options;
    }

    public boolean cancel() {
      if (pendingMsgs.remove(this)) {
//      continuation.receiveResult(msg); // do we want to do this?
        return true;
      }      
      return false;
    }

    public InetSocketAddress getIdentifier() {
      return destination;
    }

    public ByteBuffer getMessage() {
      return msg;
    }

    public Map<String, Integer> getOptions() {
      return options;
    }
  }

  public void destroy() {
    if (logger.level <= Logger.INFO) logger.log("destroy()");
    Runnable r = new Runnable(){    
      public void run() { 
        try {
          if (key != null) {
            if (key.channel() != null)
              key.channel().close();
            key.cancel();
            key.attach(null);
          }
        } catch (IOException ioe) {
          if (logger.level <= Logger.WARNING) logger.logException("Error destroying UDPLayer", ioe); 
        }
      }
    };
    
    // thread safety
    if (wire.environment.getSelectorManager().isSelectorThread()) {
      r.run();
    } else {
      wire.environment.getSelectorManager().invoke(r);
    }    
  }

  public void acceptMessages(final boolean b) {
    Runnable r = new Runnable(){    
      public void run() {
        if (b) {
          key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        } else {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        }
      }    
    };
    
    // thread safety
    if (wire.environment.getSelectorManager().isSelectorThread()) {
      r.run();
    } else {
      wire.environment.getSelectorManager().invoke(r);
    }
  }  
}
