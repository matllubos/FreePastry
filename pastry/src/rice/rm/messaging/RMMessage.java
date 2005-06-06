
package rice.rm.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.rm.*;

import java.io.*;

/**
 * @(#) RMMessage.java
 * 
 * A RM message. These messages are exchanged between the RM modules.
 * 
 * @version $Id$
 * 
 * @author Animesh Nandi
 */
public abstract class RMMessage extends Message implements Serializable {

  /**
   * The credentials of the author for the object contained in this object
   */
  private Credentials _authorCred;

  /**
   * The ID of the source of this message. Should be serializable.
   */
  protected NodeHandle _source;

  /**
   * for debugging purposes only
   */
  protected int _seqno;

  /**
   * This class will be used by the messaging system. These are entries in the
   * 'rangeSet'field in the RMRequestKeysMsg
   */
  public static class KEEntry implements Serializable {
    private IdRange reqRange;

    private boolean hashEnabled;

    private IdSet keySet;

    private byte[] hash;

    private int numKeys;

    /**
     * This range is got by the intersection of the reqRange and the responsible
     * range on the responder side.
     */
    private IdRange range;

    /*
     * This constructor is to be used by the requestor for keys
     */
    public KEEntry(IdRange _range, boolean _hEnabled) {
      reqRange = _range;
      hashEnabled = _hEnabled;
      // Other values are set to DONTCARE values
      numKeys = 0;
      keySet = new IdSet();
      hash = new byte[] {};
      range = new IdRange();
    }

    /*
     * This constructor is to be used when the responder
     */
    public KEEntry(IdRange _reqRange, IdRange _range, int _numKeys,
        boolean _hashEnabled, byte[] _hash, IdSet _keySet) {
      reqRange = _reqRange;
      range = _range;
      numKeys = _numKeys;
      hashEnabled = _hashEnabled;
      hash = _hash;
      keySet = _keySet;

    }

    public IdRange getReqRange() {
      return reqRange;
    }

    public IdRange getRange() {
      return range;
    }

    public IdSet getKeySet() {
      return keySet;
    }

    public boolean getHashEnabled() {
      return hashEnabled;
    }

    public byte[] getHash() {
      return hash;
    }

    public int getNumKeys() {
      return numKeys;
    }

    public String toString() {
      String s = "KEE(";
      s = s + getReqRange() + ", " + getHashEnabled() + " , " + getNumKeys();
      return s;

    }

  }

  /**
   * Constructor : Builds a new RM Message
   * 
   * @param source the source of the message
   * @param address RM Application address
   * @param authorCred the credentials of the source
   * @param seqno seuence number for debugging purposes only
   */
  public RMMessage(NodeHandle source, Address address, Credentials authorCred,
      int seqno) {
    super(address);
    this._source = source;
    this._authorCred = authorCred;
    this._seqno = seqno;
  }

  /**
   * This method is called whenever the rm node receives a message for itself
   * and wants to process it. The processing is delegated by rm to the message.
   */
  public abstract void handleDeliverMessage(RMImpl rm);

  public int getSeqno() {
    return _seqno;
  }

  public NodeHandle getSource() {
    return _source;
  }

  /**
   * Gets the author's credentials associated with this object
   * 
   * @return credentials
   */
  public Credentials getCredentials() {
    return _authorCred;
  }

}

