package rice.email.proxy.util;

import java.io.IOException;

public class InMemoryWorkspace implements Workspace {
  public Resource getTmpFile() throws IOException {
    return new StringBufferResource();
  }
  
  public void release(Resource tmpFile) {
  }
}