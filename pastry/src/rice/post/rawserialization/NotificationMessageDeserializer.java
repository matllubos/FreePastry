/*
 * Created on Mar 31, 2006
 */
package rice.post.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.post.messaging.NotificationMessage;

public interface NotificationMessageDeserializer {
  
  NotificationMessage deserializeNotificationMessage(InputBuffer buf, Endpoint endpoint, short contentType) throws IOException;
}
