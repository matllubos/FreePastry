/*
 * Created on Jan 30, 2006
 */
package rice.pastry.direct;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.*;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.appsocket.*;
import rice.p2p.commonapi.exception.*;
import rice.pastry.client.PastryAppl;

public class DirectAppSocket {
  public static final byte[] EOF = new byte[0];
  
  /**
   * The sum the simulated read/write buffers for one direction of a socket
   */
  private static final int MAX_BYTES_IN_FLIGHT = 10000;
  
  DirectNodeHandle acceptorNodeHandle;
  
  PastryAppl acceptorAppl;
  
  AppSocketReceiver connectorReceiver;
  PastryAppl connectorAppl;
  
  NetworkSimulator simulator;
  
  DirectAppSocketEndpoint acceptorEndpoint;
  DirectAppSocketEndpoint connectorEndpoint;
  
  Logger logger;
  
  DirectAppSocket(DirectNodeHandle acceptor, AppSocketReceiver connector, PastryAppl connectorAppl, NetworkSimulator simulator) {
    this.acceptorNodeHandle = acceptor;
    DirectPastryNode acceptorNode = acceptor.getRemote();    
    this.connectorReceiver = connector;
    this.connectorAppl = connectorAppl;
    this.simulator = simulator;
    acceptorAppl = acceptorNode.getMessageDispatch().getDestinationByAddress(connectorAppl.getAddress());
    logger = simulator.getEnvironment().getLogManager().getLogger(DirectAppSocket.class,"");
    
    
    acceptorEndpoint = new DirectAppSocketEndpoint(acceptor);
    connectorEndpoint = new DirectAppSocketEndpoint((DirectNodeHandle)connectorAppl.getNodeHandle());
    acceptorEndpoint.setCounterpart(connectorEndpoint);
    connectorEndpoint.setCounterpart(acceptorEndpoint);
  }
  
  class DirectAppSocketEndpoint implements AppSocket {
    DirectAppSocketEndpoint counterpart;
    
    AppSocketReceiver reader;
    AppSocketReceiver writer;
    DirectNodeHandle localNodeHandle;
    int seq = 0;
    
    boolean inputClosed;
    boolean outputClosed;
    
    // these three are tightly related, and should only be modified in synchronized methods on DirectAppSocketEndpoint.this
    // bytes that are either in deliveries, or in the local buffer
    int bytesInFlight = 0;
    /**
     * of byte[]
     */
    LinkedList byteDeliveries = new LinkedList();
    /**
     * The offset of the first delivery, in case the reader didn't have enough space to read everything available.
     */
    int firstOffset = 0;
 
    
    public DirectAppSocketEndpoint(DirectNodeHandle localNodeHandle) {
      this.localNodeHandle = localNodeHandle;
    }
    
    public void setCounterpart(DirectAppSocketEndpoint counterpart) {
      this.counterpart = counterpart;
    }

    public DirectNodeHandle getRemoteNodeHandle() {
      return counterpart.localNodeHandle;
    }
  
    public long read(ByteBuffer[] dsts, int offset, int length) {
      int lengthRead = 0;
      
      synchronized(this) {
        if (byteDeliveries.getFirst() == EOF) {
          inputClosed = true;
          return -1;          
        }
        Iterator i = byteDeliveries.iterator();
        // loop over all messages to be delivered
        while(i.hasNext()) {
          byte[] msg = (byte[])i.next();          
          
          // loop through all the dsts, and fill them with the current message if possible
          for (int dstCtr = offset; dstCtr < offset+length;dstCtr++) {
            ByteBuffer curBuffer = dsts[dstCtr];
            int lengthToPut = curBuffer.remaining();
            if (lengthToPut > (msg.length-firstOffset)) {
              lengthToPut = msg.length-firstOffset;
            }
            
            curBuffer.put(msg,firstOffset,lengthToPut);
            firstOffset+=lengthToPut;
            lengthRead+=lengthToPut;
            
            // we finished a message
            if (firstOffset == msg.length)
              break; // for distCtr loop
            
            // optimization: if we are here then there must be no more remaining in curBuffer
            offset=dstCtr+1;
          }
          
          // see if we finished a message
          if (firstOffset == msg.length) {
            i.remove();
            firstOffset = 0;
          } else {
            break; // i.hasNext() loop
          }
        }   
      } // synchronized(this)

      bytesInFlight-=lengthRead;
      simulator.enqueueDelivery(new Delivery() {              
        public void deliver() {
          notifyCanWrite();            
        }            
        public int getSeq() {
          return 0;            
        }            
      }, 0);            
      return lengthRead;
    }
  
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
      if (outputClosed) throw new ClosedChannelException();
      int availableToWrite = 0;
      for (int i = offset; i < offset+length; i++) {
        availableToWrite+=srcs[i].remaining(); 
      }
      
      int lengthToWrite;
      synchronized(counterpart) {
        lengthToWrite = MAX_BYTES_IN_FLIGHT - counterpart.bytesInFlight;
        if (lengthToWrite > availableToWrite) lengthToWrite = availableToWrite;
        counterpart.bytesInFlight+=lengthToWrite;
      }

      final byte[] msg = new byte[lengthToWrite]; 
      int remaining = lengthToWrite;
      int i = offset;
      while(remaining > 0) {
        int lengthToReadFromBuffer = srcs[i].remaining();
        if (remaining < lengthToReadFromBuffer) lengthToReadFromBuffer = remaining;
        srcs[i].get(msg,lengthToWrite-remaining,lengthToReadFromBuffer);
        remaining-=lengthToReadFromBuffer;
        i++;
      }
      
      if (logger.level <= Logger.FINER) logger.log(this+".write("+lengthToWrite+")");
      simulator.enqueueDelivery(new Delivery() {      
        int mySeq = seq++;
        public void deliver() {
          counterpart.addToReadQueue(msg);      
        }
        public int getSeq() {
          return mySeq; 
        }
      }, (int)Math.round(simulator.networkDelay(localNodeHandle, counterpart.localNodeHandle)));      
      return lengthToWrite;
    }
  
    /**
     * only called on simulator thread
     * @param bb
     */
    protected void addToReadQueue(byte[] msg) {
      synchronized(this) {
        if (logger.level <= Logger.FINE) {
          if (msg == EOF) {
            logger.log(this+": addToReadQueue(EOF)");
          } else {
            logger.log(this+": addToReadQueue("+msg.length+")");            
          }
        }
        byteDeliveries.addLast(msg);
      }
      notifyCanRead();
    }    
    
    /**
     * must be called on the simulator thread
     */
    protected void notifyCanWrite() {
      if (writer == null) return;
      if (counterpart.bytesInFlight < MAX_BYTES_IN_FLIGHT) {
        AppSocketReceiver temp = writer;
        writer = null;
        temp.receiveSelectResult(this, false, true);
      }
    }

    /**
     * must be called on the simulator thread
     */
    protected void notifyCanRead() {
      if (byteDeliveries.isEmpty()) return;
      if (reader != null) {
        AppSocketReceiver temp = reader;
        reader = null;
        temp.receiveSelectResult(this, true, false);
      }
    }    

    /**
     * Can be called on any thread
     */
    public void register(boolean wantToRead, boolean wantToWrite, int timeout,
        AppSocketReceiver receiver) {
      if (wantToWrite) {
        writer = receiver; 
        
        simulator.enqueueDelivery(new Delivery() {              
          public void deliver() {
            notifyCanWrite(); // only actually notifies if proper at the time
          }
          // I don't think this needs a sequence number, but I may be wrong
          public int getSeq() {
            return 0;
          }
        }, 0); // I dont think this needs a delay, but I could be wrong            
      }
      
      if (wantToRead) {
        reader = receiver;
        
        simulator.enqueueDelivery(new Delivery() {              
          public void deliver() {
            notifyCanRead(); // only actually notifies if proper at the time           
          }            
          // I don't think this needs a sequence number, but I may be wrong
          public int getSeq() {
            return 0;
          }
        }, 0); // I dont think this needs a delay, but I could be wrong            
      }        
    }
  
    public void shutdownOutput() {
      if (logger.level <= Logger.FINER) logger.log(this+".shutdownOutput()");
      outputClosed = true;
      simulator.enqueueDelivery(new Delivery() {      
        int mySeq = seq++;
        public void deliver() {
          counterpart.addToReadQueue(EOF);      
        }
        public int getSeq() {
          return mySeq;
        }
      }, (int)Math.round(simulator.networkDelay(localNodeHandle, counterpart.localNodeHandle))); // I dont think this needs a delay, but I could be wrong            
    }
  
    public void shutdownInput() {
      inputClosed = true;
    }
  
    public void close() {
      shutdownOutput();
      shutdownInput();
    }
    
    public String toString() {
      return "DAS{"+localNodeHandle+":"+writer+"->"+counterpart.localNodeHandle+":"+reader+"}"; 
    }
  }  

  
  /**
   * This is how the Acceptor Responds, success is the ConnectorDelivery, failure is the ConnectorExceptionDelivery.
   * 
   * When connect() this is sent to the Acceptor, then it responds with a ConnectorDelivery
   * 
   * @author Jeff Hoye
   */
  class AcceptorDelivery implements Delivery {
    public void deliver() {
      if (acceptorNodeHandle.isAlive()) {
        if (acceptorAppl == null) {
          simulator.enqueueDelivery(new ConnectorExceptionDelivery(new AppNotRegisteredException()),
              (int)Math.round(simulator.networkDelay(acceptorNodeHandle, (DirectNodeHandle)connectorAppl.getNodeHandle()))); 
        } else {
          if (acceptorAppl.receiveSocket(acceptorEndpoint)) {
            simulator.enqueueDelivery(new ConnectorDelivery(),
                (int)Math.round(simulator.networkDelay(acceptorNodeHandle, (DirectNodeHandle)connectorAppl.getNodeHandle()))); 
          } else {
            simulator.enqueueDelivery(new ConnectorExceptionDelivery(new NoReceiverAvailableException()),
                (int)Math.round(simulator.networkDelay(acceptorNodeHandle, (DirectNodeHandle)connectorAppl.getNodeHandle()))); 
          }
        }
      } else {
        simulator.enqueueDelivery(new ConnectorExceptionDelivery(new NodeIsDeadException()),
            (int)Math.round(simulator.networkDelay(acceptorNodeHandle, (DirectNodeHandle)connectorAppl.getNodeHandle()))); 
      }
    }
    public int getSeq() {
      return -1; 
    }
  }
  
  class ConnectorDelivery implements Delivery {
    public void deliver() {      
      if (connectorAppl.getNodeHandle().isAlive()) {
        connectorReceiver.receiveSocket(connectorEndpoint);
      } else {
        System.out.println("NOT IMPLEMENTED: Connector died during application socket initiation.");
//        simulator.enqueueDelivery(new ConnectorExceptionDelivery(new NodeIsDeadException(acceptorNodeHandle))); 
      }
    }
    // out of band, needs to get in front of any other message
    public int getSeq() {
      return -1; 
    }
  }
  
  class ConnectorExceptionDelivery implements Delivery {
    Exception e;
    public ConnectorExceptionDelivery(Exception e) {
      this.e = e; 
    }
    public void deliver() {
      connectorReceiver.receiveException(null, e);      
    }
    // out of band, needs to get in front of any other message
    public int getSeq() {
      return -1; 
    }
  }

  public Delivery getAcceptorDelivery() {
    return new AcceptorDelivery();
  }
  
  public String toString() {
    return "DAS{"+connectorAppl+"->"+acceptorAppl+"}"; 
  }
}
