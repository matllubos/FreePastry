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
package org.mpisws.p2p.transport.peerreview.audit;

import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import rice.p2p.util.tuples.Tuple;

public class EvidenceToolImpl<Handle, Identifier> implements EvidenceTool<Handle, Identifier> {

  Logger logger;
  
  public EvidenceToolImpl(LogManager manager) {
    this.logger = manager.getLogger(EvidenceToolImpl.class, null);
  }
  
  /**
   * 1) is the log snippet well-formed, i.e. are the entries of the correct length, and do they have the correct format?
   * 2) do we locally have the node certificate for each signature that occurs in the snippet?
   * 
   * if the former doesn't hold, it returns INVALID
   * if the latter doesn't hold, it returns CERT_MISSING, and returns the nodeID of the node whose certificate we need to request
   */
  public Tuple<Integer, Identifier> checkSnippet(LogSnippet snippet) {
    for (SnippetEntry entry : snippet.entries) {
      if (entry.isHash && entry.type != EVT_CHECKPOINT && entry.type != EVT_SENDSIGN && entry.type != EVT_SEND) {
        if (logger.level <= Logger.WARNING) logger.log("Malformed statement: Entry of type #"+entry.type+" is hashed");
        return new Tuple<Integer, Identifier>(INVALID,null);
      }
      
      
    }
    
    throw new RuntimeException("implement");
  }

}
