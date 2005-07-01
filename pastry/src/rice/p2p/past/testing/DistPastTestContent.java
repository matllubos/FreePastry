/*
 * Created on Jun 27, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package rice.p2p.past.testing;

import rice.environment.Environment;
import rice.p2p.commonapi.IdFactory;
import rice.p2p.past.ContentHashPastContent;

/**
 * @author jstewart
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class DistPastTestContent extends ContentHashPastContent {
  String content;
  long timestamp;
  
  public DistPastTestContent(Environment env, IdFactory idf, String content) {
    this.content = content;
    this.myId = idf.buildId(this.content);
    this.timestamp = env.getTimeSource().currentTimeMillis();
  }
  
  public String getContent() { return content; } 
  
  public long getTimestamp() { return timestamp; }
}
