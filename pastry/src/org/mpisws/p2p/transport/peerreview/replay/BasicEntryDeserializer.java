package org.mpisws.p2p.transport.peerreview.replay;

import java.io.IOException;

import org.mpisws.p2p.transport.peerreview.PeerReviewEvents;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactoryImpl;
import org.mpisws.p2p.transport.peerreview.history.reader.EntryDeserializer;
import org.mpisws.p2p.transport.peerreview.history.reader.LogReader;
import org.mpisws.p2p.transport.peerreview.history.stub.NullHashProvider;

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

    default: return null;
    }
  }

  public String read(IndexEntry ie, SecureHistory history) {
    return entryId(ie.getType())+" "+ie.getSeq()+" "+ie.getSizeInFile();
  }

  public static void printLog(String name, EntryDeserializer deserializer) throws IOException {
    String line;
    
    HashProvider hashProv = new NullHashProvider();
    
    LogReader reader = new LogReader(name, new SecureHistoryFactoryImpl(hashProv), deserializer);
    while ((line = reader.readEntry()) != null) {
      System.out.println(line);
    }    
  }
  
  public static final void main(String[] args) throws IOException {
    printLog(args[0], new BasicEntryDeserializer());
  }
  
}