package rice.p2p.splitstream;

import java.io.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * This represents data sent through scribe for splitstream during a
 * subscribe
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SplitStreamSubscribeContent implements ScribeContent {

  /**
   * The first stage of the join process
   */
  public static int STAGE_NON_FINAL = -10;

  /**
   * The final stage of the join process
   */
  public static int STAGE_FINAL = -9;
  
  /**
   * The stage that the client attempting to join is in
   */
  protected int stage;

  /**
   * Constructor taking in a byte[]
   *
   * @param data The data for this content
   */
  public SplitStreamSubscribeContent(int stage) {
    this.stage = stage;
  }

  /**
    * Returns the data for this content
   *
   * @return The data for this content
   */
  public int getStage() {
    return stage;
  }
}

