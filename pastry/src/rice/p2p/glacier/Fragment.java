package rice.p2p.glacier;

import java.io.Serializable;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class Fragment implements Serializable {
  public byte payload[];

  public Fragment(int _size) {
    payload = new byte[_size];
  }
}

