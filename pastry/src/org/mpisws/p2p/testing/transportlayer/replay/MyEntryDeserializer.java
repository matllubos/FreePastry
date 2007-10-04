package org.mpisws.p2p.testing.transportlayer.replay;

import org.mpisws.p2p.transport.peerreview.replay.BasicEntryDeserializer;

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

}
