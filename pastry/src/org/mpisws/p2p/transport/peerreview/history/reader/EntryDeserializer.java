package org.mpisws.p2p.transport.peerreview.history.reader;

import java.io.IOException;

import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;

public interface EntryDeserializer {
  public String entryId(short id);

  public String read(IndexEntry ie, SecureHistory history) throws IOException;
}
