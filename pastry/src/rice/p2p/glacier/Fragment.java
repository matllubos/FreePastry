package rice.p2p.glacier;

import java.io.Serializable;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class Fragment implements Serializable {
  /**
   * DESCRIBE THE FIELD
   */
  public int fragmentID;
  /**
   * DESCRIBE THE FIELD
   */
  public byte payload[];

  /**
   * Constructor for Fragment.
   *
   * @param _fragmentID DESCRIBE THE PARAMETER
   * @param _size DESCRIBE THE PARAMETER
   */
  public Fragment(int _fragmentID, int _size) {
    fragmentID = _fragmentID;
    payload = new byte[_size];
  }
}

