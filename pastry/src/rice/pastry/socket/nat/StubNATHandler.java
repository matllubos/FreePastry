/*
 * Created on Jun 8, 2006
 */
package rice.pastry.socket.nat;

import java.io.IOException;
import java.net.InetAddress;

import rice.environment.Environment;

public class StubNATHandler implements NATHandler {

  public StubNATHandler(Environment environment, InetAddress address) {
    // TODO Auto-generated constructor stub
  }

  public InetAddress getFireWallExternalAddress() {
    return null;
  }

  public InetAddress findFireWall(InetAddress bindAddress) throws IOException {
    throw new IOException("Stub implementation.  You should use a real implementation such as rice.pastry.socket.nat.sbbi.SBBINatHandler.  You can do this by setting \"nat_handler_class = rice.pastry.socket.nat.sbbi.SBBINatHandler\" in the params file.");
  }

  public int findAvailableFireWallPort(int internal, int external, int tries, String appName) throws IOException {
    throw new IOException("Stub implementation.  You should use a real implementation such as rice.pastry.socket.nat.sbbi.SBBINatHandler.  You can do this by setting \"nat_handler_class = rice.pastry.socket.nat.sbbi.SBBINatHandler\" in the params file.");
  }

  public void openFireWallPort(int local, int external, String appName) throws IOException {
    throw new IOException("Stub implementation.  You should use a real implementation such as rice.pastry.socket.nat.sbbi.SBBINatHandler.  You can do this by setting \"nat_handler_class = rice.pastry.socket.nat.sbbi.SBBINatHandler\" in the params file.");
  }
}
