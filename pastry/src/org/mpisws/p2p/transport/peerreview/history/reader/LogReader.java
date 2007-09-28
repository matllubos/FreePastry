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
  
  public LogReader(String name, SecureHistoryFactory factory, HashProvider hashProv, EntryDeserializer deserializer) throws IOException {
    history = factory.open(name, "r", hashProv);
    entryIndex = 0;
  }    
  
  protected String readEntry() throws IOException {
    if (entryIndex >= history.getNumEntries()) return null;
    IndexEntry ie = history.statEntry(entryIndex);
    return deserializer.read(ie, history);    
  }
}
