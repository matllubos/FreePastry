package rice.email;

import java.io.*;
import java.io.ObjectInputStream.*;
import java.util.*;

import rice.*;
import rice.email.*;
import rice.post.log.*;

/**
 * Flags object to store the flags of an email
 *
 * @version $Id$
 * @author
 */

public class Flags implements java.io.Serializable, Cloneable {
  
  /**
   * NOTE:  This class has changed significantly, and thus the
   * readObject() method is complicated, in order to support the translation
   * from the old version to the new.
   */

  /**
   * HashSet containing all of the set flags. 
   */
  HashSet flags = new HashSet();

  /**
   * Serial version for compatibility
   */
  static final long serialVersionUID = -3351853213516773179L;

  /**
   * Constructor for email Flags
   */
  public Flags() {
  }
  
  protected Flags(HashSet flags) {
    this.flags = (HashSet) flags.clone();
  }
  
  /**
    * Get the attribute for the specified Flag object
   *
   * @return the Flag's value
   */
  public boolean isSet(String flag) {      
    return flags.contains(flag);
  }
  
  /** 
    * Sets the specified attribute of the Flags object
    *
    * @param string The attribute to be set
    * @param value The new value
    *
    */
  public void setFlag(String flag, boolean value) {
    if (value)
      flags.add(flag);
    else
      flags.remove(flag);
  }

  /**
   * Returns a Vector representation of the flagList
   *
   * @return the Vector of the set flags
   */
  public Vector flagList() {
    Vector flaglist = new Vector();
    Iterator i = flags.iterator();
    
    while (i.hasNext()) 
      flaglist.add(i.next());
    
    return flaglist;
  }
  
  public boolean equals(Object o) {
    return flags.equals(((Flags) o).flags);
  }
    
  /**
   * ReadObject overridden in order to support translation from
   * old -> new style flags.  
   *
   * @param ois Object Input Stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    GetField gf = ois.readFields();
    
    if (! gf.defaulted("flags")) {
      flags = (HashSet) gf.get("flags", null);
    } else {
      flags = new HashSet();
      
      if (gf.get("_deleted", false))
        flags.add("\\Deleted");
      if (gf.get("_answered", false))
        flags.add("\\Answered");
      if (gf.get("_draft", false))
        flags.add("\\Draft");
      if (gf.get("_flagged", false))
        flags.add("\\Flagged");
      if (gf.get("_seen", false))
        flags.add("\\Seen");
      
      Hashtable list = (Hashtable) gf.get("_flagList", null);
      Enumeration e = list.keys();
      
      while (e.hasMoreElements()) {
        String flag = (String) e.nextElement();
        
        if (((Boolean) list.get(flag)).booleanValue())
          flags.add(flag);
      }
    }
  }
  
  public String toString() {
    return flags.toString();
  }
  
  public Object clone() {
    return new Flags(flags);
  }
}









