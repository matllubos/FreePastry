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
package org.mpisws.p2p.transport.peerreview;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.mpisws.p2p.transport.util.FileInputBuffer;
import org.mpisws.p2p.transport.util.FileOutputBuffer;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.util.RandomAccessFileIOBuffer;

/**
 * The following class implements PeerReview's log. A log entry consists of
 * a sequence number, a type, and a string of bytes. On disk, the log is
 * stored as two files: An index file and a data file.
 * 
 * @author Jeff Hoye
 * @author Andreas Haeberlen
 */
public class SecureHistoryImpl implements SecureHistory {

  Logger logger;
  
  HashProvider hashProv;
  boolean pointerAtEnd;
  IndexEntry topEntry;
  long baseSeq;
  long nextSeq;
  int numEntries;
  RandomAccessFileIOBuffer indexFile;
  RandomAccessFileIOBuffer dataFile;
  boolean readOnly;
  
  FileOutputBuffer indexFileWriter;
  FileOutputBuffer dataFileWriter;
  
  FileInputBuffer indexFileReader;
  FileInputBuffer dataFileReader;
  
  public SecureHistoryImpl(RandomAccessFileIOBuffer indexFile, RandomAccessFileIOBuffer dataFile, boolean readOnly, HashProvider hashProv, IndexEntryFactory indexFactory, Logger logger) throws IOException {
    this.logger = logger;
    
    if (indexFile == null) throw new IllegalArgumentException("indexFile may not be null");
    if (dataFile == null) throw new IllegalArgumentException("dataFile may not be null");

//    if (hashProv.getHashSizeBytes() != HASH_LENGTH) throw new IllegalArgumentException("HashProvider must use the same hashLength");
    
    // read the first entry
    indexFile.seek(0);
    IndexEntry firstEntry = indexFactory.build(indexFile);
    baseSeq = firstEntry.seq;
    
    // read the last entry (topEntry)
    indexFile.seek(indexFile.length()-indexFactory.getSerializedSize());
    topEntry = indexFactory.build(indexFile);
    
    numEntries = (int)(indexFile.length()/indexFactory.getSerializedSize());
    
    dataFile.seek(dataFile.length());
    nextSeq = topEntry.seq+1;
    pointerAtEnd = true;
    
    this.indexFile = indexFile;
    this.dataFile = dataFile;
    this.readOnly = readOnly;
    this.hashProv = hashProv;
  }
  
//  private void writeEntry(IndexEntry entry, OutputBuffer fileWriter) throws IOException {
//    entry.serialize(fileWriter);
//  }
  
  public long getBaseSeq() {
    return baseSeq;
  }

  public long getLastSeq() {
    return topEntry.seq;
  }

  public int getNumEntries() {
    return numEntries;
  }

  /**
   *  Returns the node hash and the sequence number of the most recent log entry 
   */
  public HashSeq getTopLevelEntry() {
    return new HashSeq(topEntry.nodeHash, topEntry.seq);
  }

  /**
   * Appends a new entry to the log. If 'storeFullEntry' is false, only the hash of the
   * entry is stored. If 'header' is not NULL, the log entry is formed by concatenating
   * 'header' and 'entry'; otherwise, only 'entry' is used. 
   */
  public void appendEntry(byte type, boolean storeFullEntry, byte[] entry, byte[] header) throws IOException {
    if (indexFile == null) throw new IllegalStateException("indexFile == null");
    if (dataFile == null) throw new IllegalStateException("dataFile == null");
    
    // Sanity check (for debugging) 

    if (readOnly) throw new IllegalStateException("Cannot append entry to readonly history");
    
    
    // As an optimization, the log 'remembers' the last entry that was read or written.
    // However, this means that the file pointers do not necessarily point to the 
    // end of the index and data files. If they don't, we must reset them. 

    if (!pointerAtEnd) {
      indexFile.seek(indexFile.length()); 
      dataFile.seek(dataFile.length()); 
      pointerAtEnd = true;
    }
    
    IndexEntry e = new IndexEntry(nextSeq++);
    
    // Calculate the content hash */
    
    if (header != null) {
      e.contentHash = hashProv.hash(header, entry);
    } else {
      e.contentHash = hashProv.hash(entry);
    }
    
    // Calculate the node hash. Note that this also covers the sequence number and
    // the entry type.

    e.nodeHash = hashProv.hash(e.seq, type, topEntry.nodeHash, e.contentHash);

    // Write the entry to the data file 

    e.type = type;
    e.fileIndex = (short)dataFile.getFilePointer();
    if (storeFullEntry) {
      e.sizeInFile = (short)entry.length;
      if (header != null) {
        dataFile.write(header);
        e.sizeInFile += header.length;
      }
      dataFile.write(entry);
    } else {
      e.sizeInFile = -1;
    }
    
    // Optionally, the index file entries can be padded to a multiple of 16 bytes,
    // so they're easier to read in a hexdump. 
 
//    #ifdef USE_PADDING
//     for (int i=0; i<sizeof(e.padding); i++)
//       e.padding[i] = 0xEE;
//    #endif

    // Write the entry to the index file 

    topEntry = e;
    topEntry.serialize(indexFile);
    numEntries++;    
  }
  
  /**
   * Append a new hashed entry to the log. Unlike appendEntry(), this only keeps
   * the content type, sequence number, and hash values. No entry is made in
   * the data file. 
   */
  public void appendHash(byte type, Hash hash) throws IOException {
    if (indexFile == null) throw new IllegalStateException("indexFile == null");
    if (dataFile == null) throw new IllegalStateException("dataFile == null");
    
    // Sanity check (for debugging) 

    if (readOnly) throw new IllegalStateException("Cannot append entry to readonly history");
    
    if (!pointerAtEnd) {
      indexFile.seek(indexFile.length()); 
      dataFile.seek(dataFile.length()); 
      pointerAtEnd = true;
    }
    
    IndexEntry e = new IndexEntry(nextSeq++);

    e.contentHash = hash;
    
    e.nodeHash = hashProv.hash(e.seq, type, topEntry.nodeHash, e.contentHash);
    e.type = type;
    e.fileIndex = -1;
    e.sizeInFile = -1;
    
//    #ifdef USE_PADDING
//    for (int i=0; i<sizeof(e.padding); i++)
//      e.padding[i] = 0xEE;
//    #endif

    // Write an entry to the index file 
    topEntry = e;
    topEntry.serialize(indexFile);
    numEntries++;
  }  
  
  /**
   * Sets the next sequence number to be used. The PeerReview library typically
   * uses the format <xxxx...xxxyyyy>, where X is a timestamp in microseconds
   * and Y a sequence number. The sequence numbers need not be contigious
   * (and usually aren't) 
   */
  public boolean setNextSeq(long nextSeq) {
    if (nextSeq < this.nextSeq) return false;
    
    this.nextSeq = nextSeq;
    return true;
  }
  
  /**
   * The destructor.  Closes the file handles.
   */
  public void close() throws IOException {
    if (indexFile == null) throw new IllegalStateException("indexFile == null");
    if (dataFile == null) throw new IllegalStateException("dataFile == null");

    indexFile.close();
    dataFile.close();
    
    indexFile = null;
    dataFile = null;
  }
}
