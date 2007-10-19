package org.mpisws.p2p.testing.transportlayer.replay;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.replay.BasicEntryDeserializer;

import rice.p2p.util.rawserialization.SimpleInputBuffer;

public class MyEntryDeserializer extends BasicEntryDeserializer implements MyEvents {

  @Override
  public String entryId(short id) {
    String ret = super.entryId(id);
    if (ret != null) return ret;
    
    switch (id) {
    case EVT_BOOT: return "Boot";
    case EVT_SUBSCRIBE: return "Subscribe";
    case EVT_PUBLISH: return "Publish";
    default: return null;
    }
  }


  @Override
  public String read(IndexEntry ie, SecureHistory history) throws IOException {
    SimpleInputBuffer nextEvent = null;
    if (ie.getSizeInFile() > 0) nextEvent = new SimpleInputBuffer(history.getEntry(ie, ie.getSizeInFile()));
    switch (ie.getType()) {
    case EVT_SOCKET_OPEN_OUTGOING: {
      int socketId = nextEvent.readInt();      
      byte[] addrBytes = new byte[4];
      nextEvent.read(addrBytes);
      InetSocketAddress addr = new InetSocketAddress(InetAddress.getByAddress(addrBytes), nextEvent.readShort());
      return entryId(ie.getType())+" socketId:"+socketId+" addr:"+addr;
    }
    case EVT_SOCKET_OPENED_OUTGOING: {
      int socketId = nextEvent.readInt();      
      return entryId(ie.getType())+" socketId:"+socketId;
    }
    default:
      return super.read(ie, history);
    }
  }
}
