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

import java.io.IOException;

public interface SecureHistory {
  public int getNumEntries();
  public long getBaseSeq();
  public long getLastSeq();
  
  /**
   *  Returns the node hash and the sequence number of the most recent log entry 
   */
  public HashSeq getTopLevelEntry();
  
  /**
   * Appends a new entry to the log. If 'storeFullEntry' is false, only the hash of the
   * entry is stored. If 'header' is not NULL, the log entry is formed by concatenating
   * 'header' and 'entry'; otherwise, only 'entry' is used. 
   * @throws IOException 
   */
  public void appendEntry(byte type, boolean storeFullEntry, byte[] entry, byte[] header) throws IOException;
  
  /**
   * Append a new hashed entry to the log. Unlike appendEntry(), this only keeps
   * the content type, sequence number, and hash values. No entry is made in
   * the data file. 
   */
  public void appendHash(byte type, Hash hash) throws IOException;

  /**
   * Sets the next sequence number to be used. The PeerReview library typically
   * uses the format <xxxx...xxxyyyy>, where X is a timestamp in microseconds
   * and Y a sequence number. The sequence numbers need not be contigious
   * (and usually aren't) 
   */
  public boolean setNextSeq(long nextSeq); 

  /**
   * The destructor.  Closes the file handles.
   * @throws IOException 
   */
  public void close() throws IOException;
  
}
