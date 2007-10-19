package org.mpisws.p2p.transport.peerreview.replay;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.mpisws.p2p.transport.peerreview.PeerReviewEvents;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactoryImpl;
import org.mpisws.p2p.transport.peerreview.history.reader.EntryDeserializer;
import org.mpisws.p2p.transport.peerreview.history.reader.LogReader;
import org.mpisws.p2p.transport.peerreview.history.stub.NullHashProvider;

import rice.environment.Environment;
import rice.p2p.util.rawserialization.SimpleInputBuffer;

public class BasicEntryDeserializer implements PeerReviewEvents, EntryDeserializer {

  public String entryId(short id) {
    switch (id) {
    case EVT_SEND: return "Send";
    case EVT_RECV: return "Receive";
    case EVT_SIGN: return "Sign";
    case EVT_ACK: return "Ack";
    case EVT_CHECKPOINT: return "Checkpoint";
    case EVT_INIT: return "Init";
    case EVT_SENDSIGN: return "Send_sign";
    
    case EVT_SOCKET_OPEN_INCOMING: return "Socket_open_incoming";
    case EVT_SOCKET_OPEN_OUTGOING: return "Socket_open_outgoing";
    case EVT_SOCKET_OPENED_OUTGOING: return "Socket_opened_outgoing";
    case EVT_SOCKET_EXCEPTION: return "Socket_exception";
    case EVT_SOCKET_CLOSE: return "Socket_close";
    case EVT_SOCKET_CLOSED: return "Socket_closed";
    case EVT_SOCKET_CAN_READ: return "Socket_can_R";
    case EVT_SOCKET_CAN_WRITE: return "Socket_can_W";
    case EVT_SOCKET_CAN_RW: return "Socket_can_RW"; 
    case EVT_SOCKET_READ: return "Socket_R";
    case EVT_SOCKET_WRITE: return "Socket_W";
    case EVT_SOCKET_SHUTDOWN_OUTPUT: return "Socket_shutdown_output";

    default: return null;
    }
  }

  public String read(IndexEntry ie, SecureHistory history) throws IOException {
    if (ie.getType() >= EVT_MIN_SOCKET_EVT && ie.getType() <= EVT_MAX_SOCKET_EVT) {
      return entryId(ie.getType())+" n:"+ie.getSeq()+" i:"+ie.getFileIndex()+" sock:"+new SimpleInputBuffer(history.getEntry(ie, 4)).readInt();
    }
    
    return entryId(ie.getType())+" n:"+ie.getSeq()+" s:"+ie.getSizeInFile()+" i:"+ie.getFileIndex();
  }

  public static void printLog(String name, EntryDeserializer deserializer, Environment env) throws IOException {
    System.out.println("printLog("+name+")");
    String line;
    
    HashProvider hashProv = new NullHashProvider();
    
    int ctr = 0;
    LogReader reader = new LogReader(name, new SecureHistoryFactoryImpl(hashProv, env), deserializer);
    while ((line = reader.readEntry()) != null) {
      System.out.println("#"+ctr+" "+line);
      ctr++;
    }    
  }
  
  public static final void main(String[] args) throws IOException {
    printLog(args[0], new BasicEntryDeserializer(), new Environment());
  }
  
}
