package rice.p2p.splitstream;

import java.io.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * This represents data sent through scribe for splitstream
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SplitStreamContent implements ScribeContent {

  /**
   * The internal data - just the bytes
   */
  protected byte[] data;

  /**
   * Constructor taking in a byte[]
   *
   * @param data The data for this content
   */
  public SplitStreamContent(byte[] data) {
    this.data = data;
  }

  /**
   * Returns the data for this content
   *
   * @return The data for this content
   */
  public byte[] getData() {
    return data;
  }
}

