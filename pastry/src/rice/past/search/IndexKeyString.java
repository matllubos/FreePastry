package rice.past.search;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import rice.pastry.NodeId;
import java.io.Serializable;
import java.util.Arrays;

/**
 * @(#) IndexKeyString.java
 *
 * Class which represents a search term for the Search Service.
 * It can also represent multiple search terms anded together.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class IndexKeyString implements Serializable {

  private String[] _strings;
  public static String PAST_PREFIX = "SearchService:";
  public static String TERM_SEPERATOR = "\n";

  /**
   * Constructor which creates an IndexKeyString given a
   * String.
   *
   * @param string The String to create this IndexKeyString from.
   * @throws IllegalArgumentException If the length of the string
   *          is less than MIN_STRING_LENGTH.
   */
  public IndexKeyString(String string) {
    _strings = new String[1];
    _strings[0] = string.toLowerCase().trim();
  }

  /**
   * Constructor which creates an IndexKeyString given a
   * String[].
   *
   * @param string The String[] to create this IndexKeyString from.
   * @throws IllegalArgumentException If the length of any of the strings
   *          is less than MIN_STRING_LENGTH.
   */
  public IndexKeyString(String[] strings) {
    _strings = new String[strings.length];

    for (int i=0; i<strings.length; i++) {
      _strings[i] = strings[i].toLowerCase().trim();
    }

    Arrays.sort(_strings);
  }

  /**
   * Constructor which builds an IndexKeyString from an
   * array of other IndexKeyStrings.  It simply appends all
   * of the arrays together in order to get the array of
   * strings for the new IndexKeyStrings.
   *
   * @param keys The source IndexKeyStrings for the new one.
   */
  public IndexKeyString(IndexKeyString[] keys) {
    int total_size = 0;
    for (int i=0; i<keys.length; i++) {
      total_size += keys[i].getStringArray().length;
    }

    _strings = new String[total_size];
    int curr = 0;

    for (int i=0; i<keys.length; i++) {
      String[] thisArray = keys[i].getStringArray();
      System.arraycopy(thisArray, 0, _strings, curr, thisArray.length);

      curr += thisArray.length;
    }

    Arrays.sort(_strings);
  }

  /**
   * Returns the Pastry NodeID of this IndexKeyString.
   *
   * @return This IndexKeyString's NodeID.
   * @throws NoSuchAlgorithmException If there is no SHA algorithm
   *          support
   */
  public NodeId getNodeId() {

    MessageDigest md = null;

    try {
      md = MessageDigest.getInstance("SHA");
    } catch ( NoSuchAlgorithmException e ) {
      System.err.println( "No SHA support!" );
    }

    md.update(generateMessage().getBytes());

    byte[] array = md.digest();
    NodeId nodeId = new NodeId(array);

    return nodeId;
  }

  /**
   * Method which creates the String to be hashed for this
   * IndexKeyString.
   *
   * @return the String message to be hashed for this IndexKeyString.
   */
  private String generateMessage() {
    String result = PAST_PREFIX;

    for (int i=0; i<_strings.length; i++) {
      result += _strings[i];

      if (i < _strings.length-1)
        result += TERM_SEPERATOR;
    }

    return result;
}

  /**
   * Returns this IndexKeyString in String format.
   *
   * @return A String representation of this IndexKeyString.
   */
  public String toString() {
    String result = "";

    for (int i=0; i<_strings.length; i++) {
      result += _strings[i];

      if (i < _strings.length-1)
        result += "&";
    }

    return result;
  }

  /**
   * Returns the internal array of Strings.
   *
   * @return The internal array of Strings for this IndexKeyString.
   */
  public String[] getStringArray() {
    return _strings;
  }

  /**
   * Determines equality with another IndexKeyString.
   *
   * @param obj The object to compare this to.
   * @return Whether or not this and obj are equal
   */
  public boolean equals(Object obj) {
    if (obj instanceof IndexKeyString) {
      return ((IndexKeyString)obj).toString().equals(toString());
    }

    return false;
  }
}