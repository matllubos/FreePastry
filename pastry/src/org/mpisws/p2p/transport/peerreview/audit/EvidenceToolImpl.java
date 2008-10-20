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

import java.io.IOException;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.peerreview.PeerReview;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtAck;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtInit;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtRecv;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtSend;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtSign;
import org.mpisws.p2p.transport.util.Serializer;

import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.rawserialization.RawSerializable;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.tuples.Tuple;

public class EvidenceToolImpl<Handle extends RawSerializable, Identifier extends RawSerializable> implements EvidenceTool<Handle, Identifier> {

  Logger logger;
  Serializer<Identifier> idSerializer;
  Serializer<Handle> handleSerializer;
  
  int hashSize;
  int signatureSize;
  private PeerReview<Handle, Identifier> peerreview;
  
  public EvidenceToolImpl(PeerReview<Handle, Identifier> peerreview, Serializer<Handle> handleSerializer, Serializer<Identifier> idSerializer, int hashSize, int signatureSize) {
    this.peerreview = peerreview;
    this.logger = peerreview.getEnvironment().getLogManager().getLogger(EvidenceToolImpl.class, null);
    this.handleSerializer = handleSerializer;
    this.idSerializer = idSerializer;
    this.hashSize = hashSize;
    this.signatureSize = signatureSize;
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
      
      /* Further processing depends on the entry type */
      if (logger.level <= Logger.FINER) logger.log("Entry type "+entry.type+", size="+entry.content.length+" "+(entry.isHash ? " (hashed)" : ""));

      try {
        SimpleInputBuffer sib = new SimpleInputBuffer(entry.content);
        switch (entry.type) {
          case EVT_SEND : /* No certificates needed; just do syntax checking */
            if (!entry.isHash) {
              new EvtSend<Identifier>(sib, idSerializer, hashSize);
            }
            break;
          case EVT_RECV : {/* We may need the certificate for the sender */          
            EvtRecv<Handle> recv = new EvtRecv<Handle>(sib,handleSerializer,hashSize);
            Identifier id = peerreview.getIdentifierExtractor().extractIdentifier(recv.getSenderHandle());
            if (!peerreview.hasCertificate(id)) {
              if (logger.level <= Logger.FINE) logger.log("AUDIT RESPONSE contains RECV from "+id+"; certificate needed");
              return new Tuple<Integer, Identifier>(CERT_MISSING, id);
            }
            break;
          }
          case EVT_SIGN : /* No certificates needed */
            new EvtSign(sib, signatureSize, hashSize);
            break;
          case EVT_ACK : /* We may need the certificate for the sender */
            EvtAck<Identifier> evtAck = new EvtAck<Identifier>(sib,idSerializer,hashSize,signatureSize);
            Identifier id = evtAck.getRemoteId();
            if (!peerreview.hasCertificate(id)) {
              if (logger.level <= Logger.FINE) logger.log("AUDIT RESPONSE contains RECV from "+id+"; certificate needed");
              return new Tuple<Integer, Identifier>(CERT_MISSING, id);
            }
            break;
          case EVT_CHECKPOINT : /* No certificates needed */
          case EVT_VRF :
          case EVT_CHOOSE_Q :
          case EVT_CHOOSE_RAND :
            break;
          case EVT_INIT: {/* No certificates needed */          
            new EvtInit(sib,handleSerializer);
            break;
          }
          case EVT_SENDSIGN : /* No certificates needed */
            break;
          default : /* No certificates needed */
            assert(entry.type > EVT_MAX_RESERVED); 
            break;
        }
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.log("Malformed entry:"+entry);
        return new Tuple<Integer, Identifier>(INVALID,null);
      }
    }
    
    return new Tuple<Integer, Identifier>(VALID,null);
  }

}
