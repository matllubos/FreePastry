package rice.p2p.glacier;
import java.io.Serializable;
import rice.p2p.commonapi.Id;

import rice.p2p.glacier.*;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class HistoryEvent implements Serializable {
  /**
   * DESCRIBE THE FIELD
   */
  public int type;
  FragmentKey key;
  Id holder;
  int sequenceNo;

  /**
   * DESCRIBE THE FIELD
   */
  public final static int evtAcquired = 1;
  /**
   * DESCRIBE THE FIELD
   */
  public final static int evtHandedOff = 2;
  /**
   * DESCRIBE THE FIELD
   */
  public final static int evtNewHolder = 3;

  /**
   * Constructor for HistoryEvent.
   *
   * @param type DESCRIBE THE PARAMETER
   * @param key DESCRIBE THE PARAMETER
   * @param holder DESCRIBE THE PARAMETER
   * @param sequenceNo DESCRIBE THE PARAMETER
   */
  public HistoryEvent(int type, FragmentKey key, Id holder, int sequenceNo) {
    this.type = type;
    this.key = key;
    this.holder = holder;
    this.sequenceNo = sequenceNo;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "[S#" + sequenceNo + " " + eventName(type) + " " + key + " - " + holder + "]";
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param eventType DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public static String eventName(int eventType) {
    if (eventType == evtAcquired) {
      return "Acquired";
    }
    if (eventType == evtHandedOff) {
      return "HandedOff";
    }
    if (eventType == evtNewHolder) {
      return "NewHolder";
    }

    return "Unknown (" + eventType + ")";
  }
}
