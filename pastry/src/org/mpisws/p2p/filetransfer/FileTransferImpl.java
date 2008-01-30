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
package org.mpisws.p2p.filetransfer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.appsocket.AppSocket;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.p2p.util.MathUtils;
import rice.p2p.util.SortedLinkedList;
import rice.selector.SelectorManager;

/**
 * TODO: implement read, write
 * 
 * @author Jeff Hoye
 *
 */
public class FileTransferImpl implements FileTransfer, AppSocketReceiver {
  /**
   * Contains int UID, long length, short name_length, String name
   */
  public static final byte MSG_FILE_HEADER = 1;
  
  /**
   * Contains int UID, int length
   */
  public static final byte MSG_BB_HEADER = 2;
  
  /**
   * Contains int UID, int chunk_length, byte[chunk_length] msg
   */
  public static final byte MSG_CHUNK = 3;
  
  /**
   * Contains int UID
   */
  public static final byte MSG_CANCEL = 4;
  
  /**
   * Contains int UID
   */
  public static final byte MSG_CANCEL_REQUEST = 5;  

  
  protected FileAllocationStrategy tempFileStrategy;
  protected AppSocket socket;
  protected FileTransferCallback callback;
  
  int MAX_QUEUE_SIZE = 1024;
  
  /**
   * Max size of a message, (the size that can't be preempted)
   */
  protected int CHUNK_SIZE = 8192;
  
  /**
   * Number of chunks to keep in memory per file.
   * 
   * If this is too big, takes more memory, if this is too small, we are always blocking on file I/O when there is available network I/O...
   * 
   * Is there a way to make this adaptive?  Think about it...
   */
  protected int FILE_CACHE = 10;
  
  ByteBuffer byteBuffer;
  protected SelectorManager selectorManager;
  protected Logger logger;
  
  public FileTransferImpl(AppSocket socket, FileTransferCallback callback, FileAllocationStrategy tempFileStrategy, Environment env) {
    this.socket = socket;
    this.callback = callback;
    this.tempFileStrategy = tempFileStrategy;
    this.queue = new SortedLinkedList<MessageWrapper>();
    this.selectorManager = env.getSelectorManager();
    this.logger = env.getLogManager().getLogger(FileTransferImpl.class, null);
  }

  public FileTransferImpl(AppSocket socket, FileTransferCallback callback, Environment env) {
    this(socket, callback, new TempFileAllocationStrategy(), env);      
  }

  public void receiveException(AppSocket socket, Exception e) {
    // warn user
    throw new RuntimeException("Not Implemented "+e);
  }

  protected void socketClosed() {
    // warn user
    throw new RuntimeException("Todo: implement.");
  }
  
  int seq = Integer.MIN_VALUE;
  SortedLinkedList<MessageWrapper> queue; // messages we want to send
  MessageWrapper messageThatIsBeingWritten = null;
  boolean registered = false;
  
  private void enqueue(MessageWrapper ret) {
//  logger.log("enqueue("+ret+")");
    synchronized(queue) {
      queue.add(ret);       
      
      // drop the lowest priority message if the queue is overflowing        
      while (queue.size() > MAX_QUEUE_SIZE) {
        MessageWrapper w = queue.removeLast();
        if (logger.level <= Logger.CONFIG) logger.log("Dropping "+w+" because queue is full. MAX_QUEUE_SIZE:"+MAX_QUEUE_SIZE);
        w.drop();
      }
    }
  }
  
  /**
   * Must be called on selectorManager.
   *
   * A) finds a writingSocket if possible
   *   opens one if needed
   */
  protected void scheduleToWriteIfNeeded() {
    if (!selectorManager.isSelectorThread()) throw new IllegalStateException("Must be called on the selector");

    // register on the writingSocket if needed
    if (!registered) {
      if (haveMessageToSend()) {
        //logger.log(this+" registering on "+writingSocket);
        // maybe we should remember if we were registered, and don't reregister, but for now it doesn't hurt
        registered = true;  // may fail in this call and set registered back to false, so make sure to do this before calling register          
        if (logger.level <= Logger.FINEST) logger.log(this+".scheduleToWriteIfNeeded() registering to write");
        socket.register(false, true, 300000, this);
      }
    }      
  }

  /**
   * Returns the messageThatIsBeingWritten, or the first in the queue, w/o setting messageThatIsBeingWritten
   * @return
   */
  private MessageWrapper peek() {
    if (messageThatIsBeingWritten == null) {
      return queue.peek();
    }
    return messageThatIsBeingWritten;
  }
  
  /**
   * Returns the messageThatIsBeingWritten, polls the queue if it is null
   * @return
   */
  private MessageWrapper poll() {
    if (messageThatIsBeingWritten == null) {
      messageThatIsBeingWritten = queue.poll();
      if (logger.level <= Logger.FINEST) logger.log(this+".poll() set messageThatIsBeingWritten = "+messageThatIsBeingWritten);
    }
    if (queue.size() >= (MAX_QUEUE_SIZE-1) && logger.level <= Logger.INFO) {
      logger.log(this+"polling from full queue (this is a good thing) "+messageThatIsBeingWritten);
    }      
    return messageThatIsBeingWritten;
  }
  
  /**
   * True if we have a message to send
   * @return
   */
  private boolean haveMessageToSend() {
    if (messageThatIsBeingWritten == null && queue.isEmpty()) return false; 
    return true;
  }

  protected boolean complete(MessageWrapper wrapper) {
    if (logger.level <= Logger.FINEST) logger.log(this+".complete("+wrapper+")");
    if (wrapper != messageThatIsBeingWritten) throw new IllegalArgumentException("Wrapper:"+wrapper+" messageThatIsBeingWritten:"+messageThatIsBeingWritten);
    
    messageThatIsBeingWritten = null;
      
    // notify deliverAckToMe
    wrapper.complete();
    
    // close the socket if we need to 
    return true;
  }

  ArrayList<FileTransferListener> listeners = new ArrayList<FileTransferListener>();
  public void addListener(FileTransferListener listener) {
    synchronized(listeners) {
      listeners.add(listener);
    }
  }

  public void removeListener(FileTransferListener listener) {
    synchronized(listeners) {
      listeners.remove(listener);
    }
  }

  public FileReceipt sendFile(File f, String s, byte priority,
      Continuation<FileReceipt, Exception> c) {
    return sendFile(f,s,priority,0,f.getTotalSpace(),c);
  }

  public FileReceipt sendFile(File f, String s, byte priority, long offset, long length,
      Continuation<FileReceipt, Exception> c) {
    // TODO Auto-generated method stub
    return null;
  }

  public BBReceipt sendMsg(ByteBuffer bb, byte priority,
      Continuation<BBReceipt, Exception> c) {
    BBReceiptImpl ret = new BBReceiptImpl(bb,priority,getUid());
    // TODO Auto-generated method stub
    return null;
  }
  
  protected synchronized int getUid() {
    return seq++;
  }
  
  /**
   * Will Schedule MessageWrappers as needed 
   * 
   * So, a File or BB corresponds 1 to 1 for ReceiptImpls, but a ReceiptImpl will schedule 
   * several MessageWrappers for writing.
   * 
   * @author Jeff Hoye
   *
   */
  abstract class ReceiptImpl implements Receipt {
    byte priority;
    int uid;
    boolean cancelled = false;    
    
    public ReceiptImpl(byte priority, int uid) {
      this.priority = priority;
      this.uid = uid;
    }
    
    public byte getPriority() {
      return priority;
    }

    public int getUID() {
      return uid;
    }

    
    public boolean cancel() {
      cancelled = true;
      return true;
    }    
    public boolean isCancelled() {
      return cancelled;
    }
    
    abstract void complete(MessageWrapper wrapper);
  }
  
  /**
   * Each time complete is called, schedule the next one chunk.
   * 
   * @author Jeff Hoye
   * 
   * TODO: make not abstract, this is just so it compiles
   */
  class BBReceiptImpl extends ReceiptImpl implements BBReceipt {
    ByteBuffer msg;
    ArrayList<ByteBuffer> msgAndHeader;
    MessageWrapper outstanding; // = new MessageWrapper
    
    public BBReceiptImpl(ByteBuffer bb, byte priority, int uid) {
      super(priority, uid);
      // construct header, first chunk
      // byte MSG_BB_HEADER, int UID, int length

      ByteBuffer header = ByteBuffer.allocate(9);
      header.put(MSG_BB_HEADER);
      header.put(MathUtils.intToByteArray(uid));
      header.put(MathUtils.intToByteArray(bb.remaining()));
      
      LinkedList<ByteBuffer> msgList = new LinkedList<ByteBuffer>(); 
      
      outstanding = new MessageWrapper(this,0,msgList);
      
    }
    
    void complete(MessageWrapper wrapper) {
      // if need to send more:
      //   construct/schedule next chunk 
//      wrapper.clear(new LinkedList<ByteBuffer>(msg));
      // else
      //   notify listeners
    }
    
    @Override
    public boolean cancel() {
      super.cancel();
      return outstanding.cancel();
    }

    public byte[] getBytes() {
      // TODO Auto-generated method stub
      return null;
    }


    public long getSize() {
      // TODO Auto-generated method stub
      return 0;
    }

    public int compareTo(Receipt o) {
      // TODO Auto-generated method stub
      return 0;
    }
  }
  
  /**
   * Keep up to FILE_CACHE of chunks scheduled, and every time one finishes, read some more to schedule more.
   * @author Jeff Hoye
   * 
   * TODO: make not abstract, this is just so it compiles
   */
  abstract class FileReceiptImpl extends ReceiptImpl {

    public FileReceiptImpl(byte priority, int uid) {
      super(priority, uid);
    }
    
  }
  
  class MessageWrapper implements Comparable<MessageWrapper> {
    boolean started = false;
    ReceiptImpl receipt;
    LinkedList<ByteBuffer> message;
    long seq;  // the sequence inside of the ReceiptImpl

    public MessageWrapper(ReceiptImpl receipt, long seq, LinkedList<ByteBuffer> message) {
      this.receipt = receipt;
      this.seq = seq;
      this.message = message;
    }
    
    /**
     * Called due to a queue overflow.
     */
    public void drop() {
      throw new RuntimeException("Implement.");
    }

    public boolean cancel() {
      if (this.equals(messageThatIsBeingWritten)) {
        if (!started) {
          // TODO: can still cancel the message, but have to have special behavior when the socket calls us back 
          return true;
        } else {
          return false;
        }
      }
      synchronized(queue) {
        return queue.remove(this);
      }
    }

    /**
     * To make this reusable
     * @param message
     * @param seq
     */
    public void clear(LinkedList<ByteBuffer> message, long seq) {
      if (!message.isEmpty()) throw new IllegalStateException(this+".clear() message is not empty!");
      started = false;
      this.message = message;
      this.seq = seq;
    }
    
    /**
     * Defines the ordering in the queue
     */
    public int compareTo(MessageWrapper that) {
      if (this.receipt.priority == that.receipt.priority) {
        if (this.receipt.uid == that.receipt.uid) {
          return (int)(this.seq-that.seq);
        }
        return this.receipt.uid-that.receipt.uid;
      }
      return this.receipt.priority-that.receipt.priority;
    }
    
    /**
     * @return true if should keep writing
     */
    public boolean receiveSelectResult(AppSocket socket) throws IOException {
      if (logger.level <= Logger.FINEST) logger.log(this+".receiveSelectResult("+socket+")");
//      if (socket == null) logger.log("Starting to write "+this+" on "+socket);
      
      if (receipt.isCancelled() && !started) {
        if (logger.level <= Logger.FINEST) logger.log(this+".rsr("+socket+") cancelled"); 
        // cancel, don't do anything
        return true;
      } else {
        started = true;
        long bytesWritten;
        if ((bytesWritten = socket.write(message.getFirst())) == -1) {
          // socket was closed, panic
          socketClosed();
          return false;
        }
//        if (logger.level <= Logger.FINER) logger.log(this+" wrote "+bytesWritten+" bytes of "+message.capacity()+" remaining:"+message.remaining());

        if (message.getFirst().hasRemaining()) {
          if (logger.level <= Logger.FINEST) logger.log(this+".rsr("+socket+") has remaining"); 
          return false;
        } else {
          // write the next BB
          message.removeFirst();
          if (!message.isEmpty())
            return receiveSelectResult(socket);
        }
      }
              
      return FileTransferImpl.this.complete(this); 
    }
    
    public void complete() {
      receipt.complete(this);
    }
  }

  public void receiveSelectResult(AppSocket socket, boolean canRead, boolean canWrite) {
//    if (canRead || !canWrite) throw new IllegalStateException(this+" Expected only to write. canRead:"+canRead+" canWrite:"+canWrite+" socket:"+socket);
    
    if (canWrite) {  
      try {
        registered = false;
        if (logger.level <= Logger.FINEST) logger.log("receivedSelectResult("+socket+","+canRead+","+canWrite);
        MessageWrapper current = poll();
        while (current != null && current.receiveSelectResult(socket)) {
          current = poll();
        }
        scheduleToWriteIfNeeded();
      } catch (IOException ioe) {
        // note, clearAndEnqueue() gets called later by the writer when the stack unravels again
        if (logger.level <= Logger.FINEST) logger.logException(this+".rsr("+socket+")", ioe);
        receiveException(socket, ioe);
        return;
      }
    }
    if (canRead) {
      try {
        // keep reading
        while(reader.read(socket));
        // always be reading
        socket.register(true, false, -1, this);
      } catch (IOException ioe) {
        receiveException(socket, ioe);
      }
    }
  }
  
  final MsgTypeReader msgTypeReader = new MsgTypeReader();
  final BBHeaderReader bbHeaderReader = new BBHeaderReader();
  final ChunkReader chunkReader = new ChunkReader();

  Reader reader = msgTypeReader;
  
  // Reading.
  interface Reader {

    /**
     * @param socket
     * @return true if should keep reading
     */
    boolean read(AppSocket socket) throws IOException;
  }
  
  class MsgTypeReader implements Reader {
    byte[] bytes = new byte[5];
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    
    public boolean read(AppSocket socket) throws IOException {
      // read the 
      long bytesRead = socket.read(buf);
      if (bytesRead < 0) {
        socketClosed();
        return false;
      }
      if (buf.hasRemaining()) return false;
      
      
      buf.reset();      
      byte msgType = bytes[0];
      int uid = MathUtils.byteArrayToInt(bytes,1);
      buf.clear();
      
      switch(msgType) {
      case MSG_BB_HEADER:
        bbHeaderReader.setUID(uid);
        reader = bbHeaderReader;
        break;
      case MSG_CHUNK:
        chunkReader.setUID(uid);
        reader = chunkReader;
        break;
      }
      return true;      
    }
  }

  class BBHeaderReader implements Reader {
    byte[] bytes = new byte[4];
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    int uid;
    
    public void setUID(int uid) {
      this.uid = uid;
    }
    
    public boolean read(AppSocket socket) throws IOException {
      // read the 
      long bytesRead = socket.read(buf);
      if (bytesRead < 0) {
        socketClosed();
        return false;
      }
      if (buf.hasRemaining()) return false;
            
      buf.reset();      
      int size = MathUtils.byteArrayToInt(bytes);
      buf.clear();
      
      addIncomingMessage(uid,size);
      
      reader = msgTypeReader;
      return true;      
    }
  }

  class ChunkReader implements Reader {
    byte[] bytes = new byte[4];
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    int uid;
    
    public void setUID(int uid) {
      this.uid = uid;
    }
    
    public boolean read(AppSocket socket) throws IOException {
      // read the 
      long bytesRead = socket.read(buf);
      if (bytesRead < 0) {
        socketClosed();
        return false;
      }
      if (buf.hasRemaining()) return false;
            
      buf.reset();      
      int size = MathUtils.byteArrayToInt(bytes);
      buf.clear();
      
      DataReader dataReader = incomingData.get(uid);
      if (dataReader ==  null) throw new IllegalStateException("No record of uid "+uid);
      return dataReader.read(socket, size);
    }
  }

  
  Map<Integer, DataReader> incomingData = new HashMap<Integer, DataReader>();
  public void addIncomingMessage(int uid, int size) {
    if (incomingData.containsKey(uid)) throw new IllegalArgumentException("DataReader with uid "+uid+" already exists! "+incomingData.get(uid)+" "+size);
    incomingData.put(uid,new BBDataReader(uid, size));
  }

  interface DataReader extends Reader {
    public boolean read(AppSocket socket, int size) throws IOException;
  }
  
  class BBDataReader implements DataReader {
    int uid;
    byte[] bytes;
    ByteBuffer curReader;
        
    public BBDataReader(int uid, int size) {
      this.uid = uid;
      bytes = new byte[size];
      curReader.wrap(bytes);      
    }

    public boolean read(AppSocket socket, int numToRead) throws IOException {
      if (curReader.hasRemaining()) throw new IllegalStateException("curReader has "+curReader.remaining()+" bytes remaining. "+numToRead);
      curReader.limit(curReader.position()+numToRead);
      reader = this;
      return read(socket);
    }

    public boolean read(AppSocket socket) throws IOException {
      long ret = socket.read(curReader);
      if (ret < 0) {
        socketClosed();
        return false;
      }
      if (curReader.hasRemaining()) {
        return false;
      }
      completeChunk();
      return true;
    }
    
    public void completeChunk() {
      // notify listeners
      System.out.println(FileTransferImpl.this+" read chunk.");
      
      if (curReader.position() == bytes.length) {
        complete();
      }
      reader = msgTypeReader;
    }
    
    public void complete() {
      incomingData.remove(uid);
      
      // notify callback
      System.out.println(FileTransferImpl.this+" read msg. "+bytes);
    }
  }
  
  public void receiveSocket(AppSocket socket) {
    throw new RuntimeException("Not Implemented, shouldn't be called.");
  }
  
}
