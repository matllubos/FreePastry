package org.mpisws.p2p.transport.peerreview.history.reader;

import java.io.IOException;
import java.util.Iterator;

import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.history.HashSeq;
import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactory;

public class LogReader {
  EntryDeserializer deserializer;
  SecureHistory history;
  long entryIndex;
  
  public LogReader(String name, SecureHistoryFactory factory, EntryDeserializer deserializer) throws IOException {
    this.deserializer = deserializer;
    history = factory.open(name, "r");
    entryIndex = 0;
  }    
  
  public String readEntry() throws IOException {
    if (entryIndex >= history.getNumEntries()) return null;
    IndexEntry ie = history.statEntry(entryIndex);
    String ret = deserializer.read(ie, history);    
    entryIndex++;
    return ret;
  }
}
