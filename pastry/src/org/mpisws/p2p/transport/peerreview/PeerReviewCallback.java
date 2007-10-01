package org.mpisws.p2p.transport.peerreview;

import rice.Destructable;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;

/**
 * Callback interface that all PeerReview-enabled applications must implement. 
 * During normal operation, PeerReview uses this interface to checkpoint the
 * application, and to inquire about the witness set of another node. 
 */
public interface PeerReviewCallback extends Destructable /*: public IdentityTransportCallback*/ {
  // PeerReviewCallback() : IdentityTransportCallback() {};
  public void init();
  void storeCheckpoint(OutputBuffer buffer);
  boolean loadCheckpoint(InputBuffer buffer);
//  void getWitnesses(Identifier subject, WitnessListener callback);
//  int getMyWitnessedNodes(NodeHandle **nodes, int maxResults);
//  PeerReviewCallback getReplayInstance(ReplayWrapper replayWrapper);
}
