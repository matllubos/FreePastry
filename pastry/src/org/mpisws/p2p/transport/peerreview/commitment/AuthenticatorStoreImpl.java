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
package org.mpisws.p2p.transport.peerreview.commitment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.mpisws.p2p.transport.peerreview.PeerReview;
import org.mpisws.p2p.transport.peerreview.replay.IdentifierSerializer;

import rice.environment.logging.Logger;
import rice.p2p.util.RandomAccessFileIOBuffer;

public class AuthenticatorStoreImpl<Identifier> implements AuthenticatorStore<Identifier> {
  
//  protected int authenticatorSizeBytes;
  protected boolean allowDuplicateSeqs;
  PeerReview<Identifier> peerreview;
  int numSubjects;
  RandomAccessFileIOBuffer authFile;

  Map<Identifier,SortedSet<Authenticator>> authenticators;
  
  Logger logger;
  IdentifierSerializer<Identifier> idSerializer;
  AuthenticatorSerializer authenticatorSerializer;

  public AuthenticatorStoreImpl(PeerReview<Identifier> peerreview, boolean allowDuplicateSeqs) {
    this.allowDuplicateSeqs = allowDuplicateSeqs;
    this.authenticators = new HashMap<Identifier, SortedSet<Authenticator>>();
    this.authFile = null;
    this.numSubjects = 0;
    this.peerreview = peerreview;
    this.authenticatorSerializer = peerreview.getAuthenticatorSerializer();
    this.idSerializer = peerreview.getIdSerializer();
    
    logger = peerreview.getEnvironment().getLogManager().getLogger(AuthenticatorStoreImpl.class, null);
  }
  
  public void destroy() {
    authenticators.clear();
    
    if (authFile != null) {
      try {
        authFile.close();
      } catch (IOException ioe) {
        logger.logException("Couldn't close authFile "+authFile,ioe);
      } finally {
        authFile = null;
      }
    }
  }
  
  /**
   *  Discard the authenticators in a certain sequence range (presumably because we just checked 
   *  them against the corresponding log segment, and they were okay) 
   */
  protected void flushAuthenticatorsFromMemory(Identifier id, long minseq, long maxseq) {
    
    SortedSet<Authenticator> list = authenticators.get(id);    

    if (list != null) {
      SortedSet<Authenticator> subList = list.subSet(new Authenticator(minseq,null,null), new Authenticator(maxseq+1,null,null));      
      list.removeAll(new ArrayList<Authenticator>(subList));
    }    
  }
  
  /**
   *  Each instance of this class has just a single file in which to store authenticators.
   *  The file format is (<id> <auth>)*; authenticators from different peers can be
   *  mixed. This method sets the name of the file and reads its current contents
   *  into memory. 
   */
  boolean setFilename(String filename) throws IOException {
    if (authFile != null) {
      authFile.close();
      authFile = null;
    }
    
    authFile = new RandomAccessFileIOBuffer(filename, "rw"); //O_RDWR | O_CREAT, 0644);
    
    // read in authenticators
    int authenticatorsRead = 0;
    int bytesRead = 0;
    while (authFile.bytesRemaining() > 0) {
      
      long pos = authFile.getFilePointer();
      try {
        Identifier id = idSerializer.deserialize(authFile); //idbuf, &pos, sizeof(idbuf));
        Authenticator authenticator = authenticatorSerializer.deserialize(authFile);
        addAuthenticatorToMemory(id, authenticator);
        authenticatorsRead++;
      } catch (IOException ioe) {
        // clobber anything in the file after the ioexception
        authFile.seek(pos);
        break;
      }
    }
     
    authFile.seek(authFile.length());
    
    return true;
  }

  /**
   * Add a new authenticator. Note that in memory, the authenticators are sorted by nodeID
   * and by sequence number, whereas on disk, they are not sorted at all. 
   */

  protected void addAuthenticatorToMemory(Identifier id, Authenticator authenticator) {
    SortedSet<Authenticator> list = authenticators.get(id);
    if (list == null) {
      list = new TreeSet<Authenticator>();
      authenticators.put(id, list);
    }
    if (!allowDuplicateSeqs) {
      SortedSet<Authenticator> sub = list.subSet(
          new Authenticator(authenticator.getSeq(),null,null),
          new Authenticator(authenticator.getSeq()+1, null, null));
      if (!sub.isEmpty()) {
        if (!sub.contains(authenticator)) {
          throw new RuntimeException("Adding duplicate auths for the same sequence number is not allowed for this store old:"+sub.first()+" new:"+authenticator);
        }
      }
    }
    list.add(authenticator);
  }
  
  protected SortedSet<Authenticator> findSubject(Identifier id) {
    return authenticators.get(id);
  }

}
