/*
 * Created on Jun 27, 2005
 */
package rice.tutorial.lesson7;

import rice.p2p.commonapi.Id;
import rice.p2p.past.ContentHashPastContent;

/**
 * @author Jeff Hoye
 */
public class MyPastContent extends ContentHashPastContent {
  
  /**
   * Store the content.
   * 
   * Note that this class is Serializable, so any non-transient field will 
   * automatically be stored to to disk.
   */
  String content;
    
  /**
   * Takes an environment for the timestamp
   * An IdFactory to generate the hash
   * The content to be stored.
   * 
   * @param idf to generate a hash of the content
   * @param content to be stored
   */
  public MyPastContent(Id id, String content) {
    super(id);
    this.content = content;
  }
  
  /**
   * A descriptive toString()
   */
  public String toString() {
    return "MyPastContent ["+content+"]";
  }
}
