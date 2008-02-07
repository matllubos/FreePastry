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

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.processing.Processor;
import rice.environment.processing.WorkRequest;
import rice.p2p.commonapi.appsocket.AppSocket;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.p2p.util.MathUtils;
import rice.p2p.util.SortedLinkedList;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.selector.SelectorManager;

/**
 * TODO: implement read, write
 * 
 * @author Jeff Hoye
 *
 */
public class FileTransferImpl implements FileTransfer, AppSocketReceiver {
  /**
   * Contains int UID, long offset, long length, int nameLength, UTF name
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

  protected Processor processor;
  protected Environment environment;
  
  public static final byte MAX_PRIORITY = -15;
  public static final byte HIGH_PRIORITY = -10;
  public static final byte MEDIUM_HIGH_PRIORITY = -5;
  public static final byte MEDIUM_PRIORITY = 0;
  public static final byte MEDIUM_LOW_PRIORITY = 5;
  public static final byte LOW_PRIORITY = 10;
  public static final byte LOWEST_PRIORITY = 15;
  public static final byte DEFAULT_PRIORITY = MEDIUM_PRIORITY;
  public static final byte CANCEL_PRIORITY = -20;
  
  public FileTransferImpl(AppSocket socket, FileTransferCallback callback, FileAllocationStrategy tempFileStrategy, Environment env) {
    this.socket = socket;
    this.callback = callback;
    this.tempFileStrategy = tempFileStrategy;
    this.queue = new SortedLinkedList<MessageWrapper>();
    this.selectorManager = env.getSelectorManager();
    this.logger = env.getLogManager().getLogger(FileTransferImpl.class, null);
    this.processor = env.getProcessor();
    this.environment = env;
    
    socket.register(true, false, -1, this);
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
    
    if (selectorManager.isSelectorThread()) {
      scheduleToWriteIfNeeded();
    } else {
      selectorManager.invoke(new Runnable() { public void run() {scheduleToWriteIfNeeded();}});
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
  
  public Iterable<FileTransferListener> getListeners() {
    synchronized(listeners) {
      return new ArrayList<FileTransferListener>(listeners);
    }
  }
  
  protected void notifyListenersSendMsgProgress(BBReceipt receipt,
      int bytesSent, int bytesTotal) {
    if (logger.level <= Logger.FINE) logger.log("notifyListenersSendMsgProgress("+receipt+","+bytesSent+","+bytesTotal+")");
    for (FileTransferListener l : getListeners()) {
      l.msgTransferred(receipt, bytesSent, bytesTotal, false);
    }
  }

  protected void notifyListenersSendMsgComplete(BBReceipt receipt) {
    if (logger.level <= Logger.FINE) logger.log("notifyListenersSendMsgComplete("+receipt+")");
    for (FileTransferListener l : getListeners()) {
      l.msgTransferred(receipt, (int)receipt.getSize(), (int)receipt.getSize(), false);
    }
  }

  protected void notifyListenersReceiveMsgProgress(BBReceipt receipt,
      int bytesReceived, int bytesTotal) {
    if (logger.level <= Logger.FINE) logger.log("notifyListenersReceiveMsgProgress("+receipt+","+bytesReceived+","+bytesTotal+")");
    for (FileTransferListener l : getListeners()) {
      l.msgTransferred(receipt, bytesReceived, bytesTotal, true);
    }
  }

  protected void notifyListenersReceiveMsgComplete(BBReceipt receipt) {
    if (logger.level <= Logger.FINE) logger.log("notifyListenersReceiveMsgComplete("+receipt+")");
    callback.messageReceived(ByteBuffer.wrap(receipt.getBytes()));
    for (FileTransferListener l : getListeners()) {
      l.msgTransferred(receipt, (int)receipt.getSize(), (int)receipt.getSize(), true);
    }
  }

  protected void notifyListenersSendFileProgress(FileReceipt receipt,
      long bytesSent, long bytesTotal) {
    if (logger.level <= Logger.FINE) logger.log("notifyListenersSendFileProgress("+receipt+","+bytesSent+","+bytesTotal+")");
    for (FileTransferListener l : getListeners()) {
      l.fileTransferred(receipt, bytesSent, bytesTotal, false);
    }
  }
  
  protected void notifyListenersSendFileComplete(FileReceipt receipt) {
    if (logger.level <= Logger.FINE) logger.log("notifyListenersSendFileComplete("+receipt+")");
    for (FileTransferListener l : getListeners()) {
      l.fileTransferred(receipt, receipt.getSize(), receipt.getSize(), false);
    }
  }

  protected void notifyListenersReceiveFileProgress(FileReceipt receipt,
      long bytesReceived, long bytesTotal) {
    if (logger.level <= Logger.FINE) logger.log("notifyListenersReceiveFileProgress("+receipt+","+bytesReceived+","+bytesTotal+")");
    for (FileTransferListener l : getListeners()) {
      l.fileTransferred(receipt, bytesReceived, bytesTotal, true);
    }
  }

  protected void notifyListenersReceiveFileComplete(FileReceipt receipt) {
    if (logger.level <= Logger.FINE) logger.log("notifyListenersReceiveFileComplete("+receipt+"):"+receipt.getFile());    
    callback.fileReceived(receipt.getFile(), receipt.getName());
    for (FileTransferListener l : getListeners()) {
      l.fileTransferred(receipt, receipt.getSize(), receipt.getSize(), true);
    }
  }

  protected void notifyListenersSenderCancelled(DataReader receipt) {
    if (logger.level <= Logger.FINE) logger.log("notifyListenersSenderCancelled("+receipt+")");    
    for (FileTransferListener l : getListeners()) {
      l.transferCancelled(receipt, true);
    }
  }

  protected void notifyListenersReceiverCancelled(ReceiptImpl receipt) {
    if (logger.level <= Logger.FINE) logger.log("notifyListenersReceiverCancelled("+receipt+")");    
    for (FileTransferListener l : getListeners()) {
      l.transferCancelled(receipt, false);
    }
  }
  public FileReceipt sendFile(File f, String s, byte priority,
      Continuation<FileReceipt, Exception> c) throws IOException  {
    return sendFile(f,s,priority,0,f.length(),c);
  }

  public FileReceipt sendFile(File f, String s, byte priority, long offset, long length,
      Continuation<FileReceipt, Exception> c) throws IOException {
    FileReceiptImpl ret = new FileReceiptImpl(f,s,priority,offset,length,getUid(),c);
    return ret;
  }

  public BBReceipt sendMsg(ByteBuffer bb, byte priority,
      Continuation<BBReceipt, Exception> c) {
    BBReceiptImpl ret = new BBReceiptImpl(bb,priority,getUid(),c);
    return ret;
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
    boolean completed = false;
    
    public ReceiptImpl(byte priority, int uid) {
      this.priority = priority;
      this.uid = uid;
      outgoingData.put(uid, this);
    }
    
    public byte getPriority() {
      return priority;
    }

    public int getUID() {
      return uid;
    }

    
    public boolean isCancelled() {
      return cancelled;
    }
    
    abstract void complete(MessageWrapper wrapper);
    
    public boolean cancel() {
      cancelled = true;
      // TODO: remove from table
      outgoingData.remove(uid);
      // TODO: send cancel msg
      sendCancel(uid);
      return !completed;
    }

    abstract public void notifyReceiverCancelled();
  }
  
  Map<Integer, ReceiptImpl> outgoingData = new HashMap<Integer, ReceiptImpl>();

  

  
  /**
   * Each time complete is called, schedule the next one chunk.
   * 
   * @author Jeff Hoye
   * 
   * TODO: make not abstract, this is just so it compiles
   */
  class BBReceiptImpl extends ReceiptImpl implements BBReceipt {
    /**
     * The whole message to send.
     */
    ByteBuffer msg;
    byte[] msgBytes;
    LinkedList<ByteBuffer> msgList; 
    ByteBuffer header;
    MessageWrapperImpl outstanding; // = new MessageWrapper
    int wrapperSeq = Integer.MIN_VALUE+10; // to give cancel priority    
    final ByteBuffer chunkBuffer;
    int initialPosition;
    Continuation<BBReceipt, Exception> deliverAckToMe;
    
    public BBReceiptImpl(ByteBuffer bb, byte priority, int uid, Continuation<BBReceipt, Exception> c) {
      super(priority, uid);
      this.deliverAckToMe = c;
      this.msg = bb;
      initialPosition = msg.position();
      this.msgBytes = bb.array();
      msgList = new LinkedList<ByteBuffer>(); 
      chunkBuffer = ByteBuffer.wrap(msgBytes);
      
      // construct header
      // byte MSG_BB_HEADER, int UID, int length      
      header = ByteBuffer.allocate(9);
      header.put(MSG_BB_HEADER);
      header.put(MathUtils.intToByteArray(uid));
      header.put(MathUtils.intToByteArray(bb.remaining()));
      header.clear();
      msgList.add(header);
            
      outstanding = new MessageWrapperImpl(this,wrapperSeq++,msgList);
      enqueue(outstanding);
    }
    
    public String toString() {
      return "Outgoing msg<"+uid+"> size:"+getSize()+" priority:"+priority;
    }
    
    void complete(MessageWrapper wrapper) {
      // advance msg's pointer as necessary
      msg.position(msg.position()+chunkBuffer.position());
      chunkBuffer.clear();
      
      // notify listener
      notifyListenersSendMsgProgress(this,msg.position()-initialPosition, msg.limit()-initialPosition);
      
      // if need to send more:
      if (msg.hasRemaining()) {
        // Construct a chunk (note that we reuse all the objects...)
        ByteBuffer chunk = msg;
        // Construct a BB of the right size
        if (msg.remaining() > CHUNK_SIZE) {
          // don't send the whole message
          chunk = chunkBuffer;
          chunk.position(msg.position());
          chunk.limit(msg.position()+CHUNK_SIZE);          
        }
        
        header.clear();
        header.put(MSG_CHUNK);
        header.put(MathUtils.intToByteArray(uid));
        header.put(MathUtils.intToByteArray(chunk.remaining()));
        header.clear();
        msgList.add(header);
        msgList.add(chunk);

        outstanding.clear(msgList, wrapperSeq++);
        
        // schedule it
        enqueue(outstanding);
        return;        
      }
  
      // we've sent the whole message
      if (deliverAckToMe != null) deliverAckToMe.receiveResult(this);
      completed = true;
      notifyListenersSendMsgComplete(this);
    }
    
    public byte[] getBytes() {
      return msgBytes;
    }


    public long getSize() {
      return msg.limit()-initialPosition;
    }
    
    @Override
    public boolean cancel() {
      outstanding.cancel();
      return super.cancel();
    }

    @Override
    public void notifyReceiverCancelled() {
      if (deliverAckToMe != null) deliverAckToMe.receiveException(new OperationCancelledException(this));
    }
  }
  
  /**
   * Keep up to FILE_CACHE of chunks scheduled, and every time one finishes, read some more to schedule more.
   * 
   * TODO: Make more than 1 outstanding chunk
   * 
   * @author Jeff Hoye
   * 
   */
  class FileReceiptImpl extends ReceiptImpl implements FileReceipt {
    /**
     * The whole message to send.
     */
    FileInputStream file;
    
    File f;
    String name;
    Continuation<FileReceipt, Exception> deliverAckToMe;
    
    LinkedList<ByteBuffer> msgList; 
    MessageWrapperImpl outstanding; // = new MessageWrapper
    int wrapperSeq = Integer.MIN_VALUE;    
    final ByteBuffer chunk;
    byte[] chunkBytes;
    long lastByte;
    long ptr;
    long length;
    long initialPosition;
    ByteBuffer header;
    
    public FileReceiptImpl(File f, String name, byte priority, long offset, long length, int uid, Continuation<FileReceipt, Exception> c) throws IOException {
      super(priority, uid);
      if (offset+length > f.length()) throw new IllegalArgumentException("File is only "+f.length()+" but you are trying to send "+length+" bytes starting at "+offset);
      this.f = f;
      this.name = name;
      this.file = new FileInputStream(f);
      file.skip(offset);
      lastByte = offset+length;
      
      this.deliverAckToMe = c;
      this.initialPosition = offset;
      this.ptr = offset;
      this.length = length;
      
      msgList = new LinkedList<ByteBuffer>(); 
      long chunkSize = length;
      if (length > CHUNK_SIZE) chunkSize = CHUNK_SIZE;
      chunkBytes = new byte[(int)chunkSize]; 
      chunk = ByteBuffer.wrap(chunkBytes);

      // used to send the chunks
      header = ByteBuffer.allocate(9);
      
      // serialize string
      SimpleOutputBuffer sob = new SimpleOutputBuffer();
      sob.writeUTF(name);      
      byte[] serializedString = sob.getBytes();
      
      // construct header
      // byte MSG_BB_HEADER, int UID, long offset, long length, int nameLength, UTF name
      ByteBuffer hdr = ByteBuffer.allocate(25);
      hdr.put(MSG_FILE_HEADER);
      hdr.put(MathUtils.intToByteArray(uid));
      hdr.put(MathUtils.longToByteArray(offset));
      hdr.put(MathUtils.longToByteArray(length));
      hdr.put(MathUtils.intToByteArray(serializedString.length));
      hdr.clear();
      msgList.add(hdr);
      msgList.add(ByteBuffer.wrap(serializedString));
      
      outstanding = new MessageWrapperImpl(this,wrapperSeq++,msgList);
      enqueue(outstanding);
    }
    
    public String toString() {
      return "Outgoing file<"+uid+"> "+name+" size:"+getSize()+" priority:"+priority+" "+file;
    }

    void complete(MessageWrapper wrapper) {
      // notify listener
      notifyListenersSendFileProgress(this,ptr-initialPosition, length);
      
      if (cancelled) return;
      
      // if need to send more:
      if (ptr < lastByte) {
        // Construct a chunk (note that we reuse all the objects...)
        try {
          long ret = file.read(chunkBytes);
          if (ret < 0) {
            throw new EOFException("Unexpected EOF... cancelling "+name+" "+f+".");
          }
          ptr += ret;
          chunk.clear();
          chunk.limit((int)ret);
                      
          header.clear();
          header.put(MSG_CHUNK);
          header.put(MathUtils.intToByteArray(uid));
          header.put(MathUtils.intToByteArray(chunk.remaining()));
          header.clear();
          msgList.add(header);
          msgList.add(chunk);
  
          outstanding.clear(msgList, wrapperSeq++);
          
          // schedule it
          enqueue(outstanding);
          return;        
        } catch (IOException ioe) {
          if (deliverAckToMe != null) deliverAckToMe.receiveException(ioe);          
          FileTransferImpl.this.sendCancel(uid);
          return;
        }
      }
  
      // we've sent the whole message
      try {
        file.close();
      } catch (IOException ioe) {
        logger.logException("Error closing file <"+uid+"> "+file+" "+name, ioe);
      }
      if (deliverAckToMe != null) deliverAckToMe.receiveResult(this);
      notifyListenersSendFileComplete(this);
    }
    
    public long getSize() {
      return length;
    }

    public File getFile() {
      return f;
    }

    public long getLength() {
      return length;
    }

    public String getName() {
      return name;
    }

    public long getOffset() {
      return initialPosition;
    }
    
    @Override
    public boolean cancel() {
      try {
        file.close();
      } catch (IOException ioe) {
        logger.logException("Error closing file <"+uid+"> "+file+" "+name, ioe);
      }
      outstanding.cancel();
      return super.cancel();
    }
    
    @Override
    public void notifyReceiverCancelled() {
      if (deliverAckToMe != null) deliverAckToMe.receiveException(new OperationCancelledException(this));
    }

  }
  
  
  interface MessageWrapper extends Comparable<MessageWrapper> {
    byte getPriority();
    void complete();
    void drop();
    int getUid();
    long getSeq();
    boolean receiveSelectResult(AppSocket socket) throws IOException;
  }
  
  class MessageWrapperImpl implements MessageWrapper {
    boolean started = false;
    ReceiptImpl receipt;
    LinkedList<ByteBuffer> message;
    long seq;  // the sequence inside of the ReceiptImpl

    public MessageWrapperImpl(ReceiptImpl receipt, long seq, LinkedList<ByteBuffer> message) {
      this.receipt = receipt;
      this.seq = seq;
      this.message = message;
    }
    
    public String toString() {
      return "Part "+seq+" of "+receipt;
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
      started = false;
      this.message = message;
      this.seq = seq;
    }
    
    /**
     * Defines the ordering in the queue
     */
    public int compareTo(MessageWrapper that) {
      if (this.receipt.priority == that.getPriority()) {
        if (this.receipt.uid == that.getUid()) {
          return (int)(this.seq-that.getSeq());
        }
        return this.receipt.uid-that.getUid();
      }
      return this.receipt.priority-that.getPriority();
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

    public byte getPriority() {
      return receipt.getPriority();
    }

    public long getSeq() {
      return seq;
    }

    public int getUid() {
      return receipt.getUID();
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
  final FileHeaderReader fileHeaderReader = new FileHeaderReader();
  final FileNameReader fileNameReader = new FileNameReader();
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
      
      
      buf.clear();      
      byte msgType = bytes[0];
      int uid = MathUtils.byteArrayToInt(bytes,1);
      buf.clear();
      
      switch(msgType) {
      case MSG_BB_HEADER:
        bbHeaderReader.setUID(uid);
        reader = bbHeaderReader;
        break;
      case MSG_FILE_HEADER:
        fileHeaderReader.setUID(uid);
        reader = fileHeaderReader;
        break;
      case MSG_CHUNK:
        chunkReader.setUID(uid);
        reader = chunkReader;
        break;
      case MSG_CANCEL:
        senderCancelled(uid);
        break;
      case MSG_CANCEL_REQUEST:
        receiverCancelled(uid);
        break;
      }
      return true;      
    }
  }
  
  protected void receiverCancelled(int uid) {
    ReceiptImpl writer = outgoingData.remove(uid);
    if (writer != null) {
      writer.cancel();
      writer.notifyReceiverCancelled();
      notifyListenersReceiverCancelled(writer);
    } else {
      logger.log("receiverCanclled("+uid+") no record of the uid.");
    }
  }

  protected void senderCancelled(int uid) {
    DataReader reader = incomingData.remove(uid);
    if (reader != null) {
      reader.cancelled();
      notifyListenersSenderCancelled(reader);
    } else {
      logger.log("senderCanclled("+uid+") no record of the uid.");
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
            
      buf.clear();      
      int size = MathUtils.byteArrayToInt(bytes);
      buf.clear();
      
      addIncomingMessage(uid,size);
      
      reader = msgTypeReader;
      return true;      
    }
  }
  
  class FileHeaderReader implements Reader {
    byte[] bytes = new byte[20];
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
            
      buf.clear();      
      long offset = MathUtils.byteArrayToLong(bytes,0);
      long length = MathUtils.byteArrayToLong(bytes,8);
      int nameSize = MathUtils.byteArrayToInt(bytes,16);
      buf.clear();
      
      
      fileNameReader.initialize(uid,offset,length,nameSize);
      reader = fileNameReader;
      return true;      
    }
  }

  class FileNameReader implements Reader {
    byte[] bytes = new byte[20];
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    int uid;
    long offset;
    long length;
    
    public void initialize(int uid, long offset, long length, int nameSize) {
      this.uid = uid;
      this.offset = offset;
      this.length = length;
      if (bytes.length<nameSize) {
        bytes = new byte[nameSize];
        buf = ByteBuffer.wrap(bytes);
      }
      buf.clear();
      buf.limit(nameSize);      
    }
    
    public boolean read(AppSocket socket) throws IOException {
      // read the 
      long bytesRead = socket.read(buf);
      if (bytesRead < 0) {
        socketClosed();
        return false;
      }
      if (buf.hasRemaining()) return false;

      SimpleInputBuffer sib = new SimpleInputBuffer(bytes);
      String name = sib.readUTF();      
      buf.clear();
      
      addIncomingFile(uid,name,offset,length);
      
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
            
      buf.clear();      
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
    BBDataReader bbdr = new BBDataReader(uid, size);
    incomingData.put(uid,bbdr);
    notifyListenersReceiveMsgProgress(bbdr, 0, size);
  }

  public void addIncomingFile(int uid, String name, long offset, long length) throws IOException {
    File f = tempFileStrategy.getFile(name, offset, length);
    if (incomingData.containsKey(uid)) throw new IllegalArgumentException("DataReader with uid "+uid+" already exists! "+incomingData.get(uid)+" "+name);
    FileDataReader fdr = new FileDataReader(uid, name, f, offset, length);
    incomingData.put(uid,fdr);
    notifyListenersReceiveFileProgress(fdr, 0, length);
  }

  interface DataReader extends Reader, Receipt {
    public boolean read(AppSocket socket, int size) throws IOException;
    public void cancelled();
  }
  
  class BBDataReader implements DataReader, BBReceipt {
    int uid;
    byte[] bytes;
    ByteBuffer curReader;
    boolean requestedCancel = false;    
    
    
    public BBDataReader(int uid, int size) {
      this.uid = uid;
      bytes = new byte[size];
      curReader = ByteBuffer.wrap(bytes);   
      curReader.limit(0);
    }

    public String toString() {
      return "Incoming msg<"+uid+"> of length"+bytes.length;
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
      notifyListenersReceiveMsgProgress(this, curReader.position(), bytes.length);
      
      if (curReader.position() == bytes.length) {
        complete();
      }
      reader = msgTypeReader;
    }
    
    public void complete() {
      incomingData.remove(uid);
      
      // notify callback
      notifyListenersReceiveMsgComplete(this);
    }

    public byte[] getBytes() {
      return bytes;
    }

    public byte getPriority() {
      throw new RuntimeException("Unknown priority.  Don't call this on the receiving side.");
    }

    public long getSize() {
      return bytes.length;
    }

    public int getUID() {
      return uid;
    }

    public boolean cancel() {
      if (requestedCancel) return false;
      requestedCancel = true;
      return requestCancel(uid);
    }
    
    // called when actually cancelled
    public void cancelled() {
      
    }
  }
  
  class FileDataReader implements DataReader, FileReceipt {
    int uid;
    byte[] bytes;
    ByteBuffer curReader;
    RandomAccessFile file;
    
    File f;    
    String name;
    long offset;
    long length;
    
    long ptr;
    
    boolean requestedCancel = false;
    boolean cancelled = false;
    
    public FileDataReader(int uid, String name, File f, long offset, long length) throws IOException {
      this.uid = uid;
      this.f = f;
      this.ptr = offset;
      this.offset = offset;
      this.length = length;
      file = new RandomAccessFile(f, "rw");
      this.name = name;
      file.seek(offset);
      bytes = new byte[CHUNK_SIZE];
      curReader = ByteBuffer.wrap(bytes);   
    }

    public String toString() {
      return "Incoming file<"+uid+"> "+name+" off:"+offset+" length:"+length+" "+f;
    }
    
    public boolean read(AppSocket socket, int numToRead) throws IOException {
      if (curReader.position() != 0) throw new IllegalStateException("curReader has "+curReader.remaining()+" bytes remaining. "+numToRead);
      curReader.limit(numToRead);
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
      curReader.flip();
      
      // copy the bytes so we can reuse them
      final byte[] writeMe = new byte[curReader.remaining()];
      curReader.get(writeMe);
      curReader.clear();
      
      // schedule them to be written, then notified on the blockingIOThread
      
      // note, that it is required that these are in order
      WorkRequest<FileDataReader> wr = new WorkRequest<FileDataReader>(new Continuation<FileDataReader, Exception>() {
      
        public void receiveResult(FileDataReader result) {
          if (cancelled) return;
          ptr+=writeMe.length;
          // notify listeners
          notifyListenersReceiveFileProgress(result, ptr-offset, length);
          if (ptr == offset+length) FileDataReader.this.complete();
        }
      
        public void receiveException(Exception exception) {
          logger.logException("Error writing file "+f+" "+name, exception);
          FileDataReader.this.cancel();
        }      
      },environment.getSelectorManager()) {
      
        @Override
        public FileDataReader doWork() throws Exception {
          if (cancelled) return FileDataReader.this;
          file.write(writeMe);
          return FileDataReader.this;
        }      
      };        
      processor.processBlockingIO(wr);      
      reader = msgTypeReader;
    }
    
    public void complete() {
      incomingData.remove(uid);
      try {
        file.close();
      } catch (IOException ioe) {
        logger.logException("Error closing file "+file, ioe);
      }
        
      // notify callback
      notifyListenersReceiveFileComplete(this);
    }

    public byte[] getBytes() {
      return bytes;
    }

    public byte getPriority() {
      throw new RuntimeException("Unknown priority.  Don't call this on the receiving side.");
    }

    public long getSize() {
      return bytes.length;
    }

    public int getUID() {
      return uid;
    }

    public boolean cancel() {
      if (requestedCancel) return false;
      requestedCancel = true;
      return requestCancel(uid);
    }

    public File getFile() {
      return f;
    }

    public long getLength() {
      return length;
    }

    public String getName() {
      return name;
    }

    public long getOffset() {
      return offset;
    }
    
    // called when actually cancelled
    public void cancelled() {
      cancelled = true;
      
      // Don't close the file on the wrong thread.
      WorkRequest<RandomAccessFile> wr = new WorkRequest<RandomAccessFile>(new Continuation<RandomAccessFile, Exception>() {
      
        public void receiveResult(RandomAccessFile result) {
        }
      
        public void receiveException(Exception exception) {
          logger.logException("Error closing file "+file, exception);
        }      
      },environment.getSelectorManager()) {
      
        @Override
        public RandomAccessFile doWork() throws Exception {
          file.close();
          return file;
        }      
      };        
      processor.processBlockingIO(wr);      

    }
  }
  
  public void receiveSocket(AppSocket socket) {
    throw new RuntimeException("Not Implemented, shouldn't be called.");
  }

  /**
   * Only call this on the receiver side, b/c uid is simplex
   * @param uid
   * @return if we can guarantee the request was cancelled
   */
  protected boolean requestCancel(int uid) {
    enqueue(new SimpleMessageWrapper(MSG_CANCEL_REQUEST, uid));
    return false;
  }
  
  /**
   * Only call this on the sender side, b/c uid is simplex
   * @param uid
   * @return
   */
  protected boolean sendCancel(int uid) {
    enqueue(new SimpleMessageWrapper(MSG_CANCEL, uid));
    return false;
  }
  
  class SimpleMessageWrapper implements MessageWrapper {
    ByteBuffer msg;
    int uid;
    byte msgType;
    public SimpleMessageWrapper(byte msgType, int uid) {
      this.uid = uid;
      this.msgType = msgType;
      msg = ByteBuffer.allocate(5);
      msg.put(msgType);
      msg.put(MathUtils.intToByteArray(uid));
      msg.clear();
    }
    
    public String toString() {
      if (msgType == MSG_CANCEL) return "Cancel msg <"+uid+">";
      if (msgType == MSG_CANCEL_REQUEST) return "Cancel request <"+uid+">";
      return "Unknown message";
    }
    
    public void complete() {
      // TODO Auto-generated method stub      
    }
    public void drop() {
      // TODO Auto-generated method stub      
    }
    public byte getPriority() {
      return CANCEL_PRIORITY;
    }
    public long getSeq() {
      return Integer.MIN_VALUE;
    }
    public int getUid() {
      return uid;
    }
    public int compareTo(MessageWrapper that) {
      if (this.getPriority() == that.getPriority()) {
        if (this.uid == that.getUid()) {
          return (int)(this.getSeq()-that.getSeq());
        }
        return this.uid-that.getUid();
      }
      return this.getPriority()-that.getPriority();
    }
    
    /**
     * @return true if should keep writing
     */
    public boolean receiveSelectResult(AppSocket socket) throws IOException {
      if (logger.level <= Logger.FINEST) logger.log(this+".receiveSelectResult("+socket+")");
//      if (socket == null) logger.log("Starting to write "+this+" on "+socket);
      
      long bytesWritten;
      if ((bytesWritten = socket.write(msg)) == -1) {
        // socket was closed, panic
        socketClosed();
        return false;
      }
//        if (logger.level <= Logger.FINER) logger.log(this+" wrote "+bytesWritten+" bytes of "+message.capacity()+" remaining:"+message.remaining());

      if (msg.hasRemaining()) {
        if (logger.level <= Logger.FINEST) logger.log(this+".rsr("+socket+") has remaining"); 
        return false;
      }
      return FileTransferImpl.this.complete(this);
    }
    
    
  }
  
}
