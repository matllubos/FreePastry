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

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Class which serves as an "writer" for all of the messages sent across the
 * wire in Pastry. This class serializes and properly formats all messages, and
 * then waits to be called with an available SocketChannel in order to write the
 * message out. If the messagae could not be written in one go, subsequent calls
 * to the write() method will finish writing out the message. This class also
 * maintains an internal queue of messages waiting to be sent across the wire.
 * Calling isEmpty() will tell clients if it is safe to mark the SelectionKey as
 * not being interested in writing.
 *
 * @version $Id: SocketChannelWriter.java,v 1.5 2004/03/08 19:53:57 amislove Exp
 *      $
 * @author Alan Mislove
 */
public class SocketChannelWriter {
  // the maximum length of the queue
  private final int MAXIMUM_QUEUE_LENGTH;
  
  // the pastry node
  private SocketPastryNode spn;

  // internal buffer for storing the serialized object
  private ByteBuffer buffer;

  // internal list of objects waiting to be written
  private LinkedList queue;
  
  // the address this writer is writing to
  protected SourceRoute path;

  // the environment to use
  protected Environment environment;
  
  protected Logger logger;
  
  static long bytesWritten;
  
  /**
   * Constructor which creates this SocketChannelWriter with a pastry node and
   * an object to write out.
   *
   * @param spn The spn the SocketChannelWriter servers
   */
  public SocketChannelWriter(SocketPastryNode spn, SourceRoute path) {
    this(spn.getEnvironment(),path);
    this.spn = spn;
  }
  
  public SocketChannelWriter(Environment env, SourceRoute path) {
    this.environment = env;
    this.path = path;
    
    MAXIMUM_QUEUE_LENGTH = env.getParameters().getInt("pastry_socket_writer_max_queue_length");
    
    queue = new LinkedList();
    
    logger = environment.getLogManager().getLogger(SocketChannelWriter.class, null);
  }
  
  /**
   * Sets this writer's path
   *
   * @param path The path this writer is using
   */
  protected void setPath(SourceRoute path) {
    this.path = path;
  }

  /**
   * Returns whether or not there are objects in the queue on in writing. If the
   * result is true, it the safe to mark the SelectionKey as not being
   * interested in writing.
   *
   * @return Whether or not there are objects still to be written.
   */
  public boolean isEmpty() {
    return ((buffer == null) && (queue.size() == 0));
  }

  /**
   * Returns the queue of writes for the remote address
   *
   * @return the queue of writes for the remote address
   */
  public LinkedList getQueue() {
    return queue;
  }

  public boolean enqueue(Message msg) throws IOException {
    PRawMessage rm;
    if (msg instanceof PRawMessage) {
      rm = (PRawMessage)msg; 
    } else {
      rm = new PJavaSerializedMessage(msg); 
    }
    // don't put this one in the pool, it doesn't have a proper deserializer
    return enqueue(new SocketBuffer(rm));
  }
  
  public boolean enqueue(byte[] msg) {
    return enqueue(new SocketBuffer(msg)); 
  }
  
  /**
   * Adds an object to this SocketChannelWriter's queue of pending objects to
   * write. This methos is synchronized and therefore safe for use by multiple
   * threads.
   *
   * @param o The object to be written.
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean enqueue(SocketBuffer o) {
    synchronized (queue) {
      addToQueue(o);

      if (queue.size() > MAXIMUM_QUEUE_LENGTH) {
        Object remove = queue.removeLast();
        if (logger.level <= Logger.WARNING) logger.log(
             "(W): Maximum TCP queue length of "+MAXIMUM_QUEUE_LENGTH+" reached to " + path + " - message " + remove + " will be dropped.");
        return false;
      } else if (queue.size() == MAXIMUM_QUEUE_LENGTH/2) {
        if (logger.level <= Logger.WARNING) logger.log(
            "ERROR: Queue to " + path + " has "+queue.size()+" elements - probably a bad sign - enqueue of " + o);
      }        
    }

    return true;
  }

  /**
   * Returns the queue of writes for the remote address
   *
   */
  public void reset() {
    queue = new LinkedList();
    buffer = null;
  }
  
  /**
   * Method which is designed to be called when this writer should write out its
   * data. Returns whether or not the message was completely written. If false
   * is returns, write() will need to be called again when the SocketChannel is
   * ready for data to be written.
   *
   * @param sc The SocketChannel to write to
   * @return true if this output stream is done, false otherwise
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public boolean write(SocketChannel sc) throws IOException {    
    while (true) {
      synchronized (queue) {
        if (buffer == null) {
          if (! queue.isEmpty()) {
            if (logger.level <= Logger.FINER) logger.log(
                "(W) About to serialize object " + queue.getFirst());
            SocketBuffer sbuf = (SocketBuffer)queue.getFirst();
            buffer = sbuf.getBuffer();
            
            if (buffer != null) {
              if (spn != null) {
                spn.broadcastSentListeners(sbuf.getInnermostAddress(),sbuf.getInnermostType(), 
                  (path == null ? 
                      (InetSocketAddress) sc.socket().getRemoteSocketAddress() : 
                      path.getLastHop().getAddress( ((SocketNodeHandle)spn.getLocalHandle()).eaddress )), 
                  buffer.limit(), NetworkListener.TYPE_TCP);
              }
              if (logger.level <= Logger.FINER) logger.log("COUNT: Sent message " + sbuf + " of size " + buffer.limit()+ " to " + path);
            } else {
              queue.removeFirst();
              
              return write(sc);
            }
          } else {
            return true;
          }
        }
        
        int j = buffer.limit();
        int i = sc.write(buffer);
                
        if (logger.level <= Logger.FINER) logger.log("Wrote " + i + " of " + j + " bytes of message " + queue.getFirst() + " of size " + buffer.limit()+ " to " + path);

        if (logger.level <= Logger.FINEST) logger.log(
            "(W) Wrote " + i + " of " + j + " bytes to " + sc.socket().getRemoteSocketAddress());
        
        if (buffer.remaining() != 0) 
          return false;
        
        if (logger.level <= Logger.FINER) logger.log(
            "(W) Finished writing message " + queue.getFirst() + " - queue now contains " + (queue.size() - 1) + " items");
        
        queue.removeFirst();
        buffer = null;
      }
    }
  }

  /**
   * Adds an entry into the queue, taking message prioritization into account
   *
   * @param o The feature to be added to the ToQueue attribute
   */
  private void addToQueue(SocketBuffer buf) {    
    if ((queue.size() > 0)) {
      int i=1;
      
      while (i < queue.size()) {
        SocketBuffer p = (SocketBuffer)queue.get(i);

        if (p.priority > buf.priority)
          break;
        
        i++;
      }
      
      if (logger.level <= Logger.FINER) logger.log(
          "COUNT: Enqueueing message " + buf + " at location " + i + " in the pending queue (priority " + buf.priority + ")");
    
      queue.add(i, buf);
    } else {
      queue.addLast(buf);
    }
  }
}
