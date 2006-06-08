/*
 * Created on Jun 8, 2006
 */
package rice.pastry.socket.nat;

import java.io.IOException;
import java.net.InetAddress;

public interface NATHandler {

  InetAddress getFireWallExternalAddress();

  public InetAddress findFireWall(InetAddress bindAddress) throws IOException;
  public int findAvailableFireWallPort(int internal, int external) throws IOException;
  public void openFireWallPort(int local, int external) throws IOException;

}
