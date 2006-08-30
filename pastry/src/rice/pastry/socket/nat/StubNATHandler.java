/*
 * Created on Jun 8, 2006
 */
package rice.pastry.socket.nat;

import java.io.IOException;
import java.net.InetAddress;

import rice.environment.Environment;

public class StubNATHandler implements NATHandler {

  String errorString = "Stub implementation.  Plese refer to http://freepastry.org/FreePastry/nat.html to configure FreePastry for your environment.";
//  String errorString = "Stub implementation.  You should use a real implementation such as rice.pastry.socket.nat.sbbi.SBBINatHandler.  You can do this by setting \"nat_handler_class = rice.pastry.socket.nat.sbbi.SBBINatHandler\" in the params file, or disable firewall checking by setting \"nat_search_policy\" = \"never\".";
  
  public StubNATHandler(Environment environment, InetAddress address) {
    // TODO Auto-generated constructor stub
  }

  public InetAddress getFireWallExternalAddress() {
    return null;
  }

  public InetAddress findFireWall(InetAddress bindAddress) throws IOException {
    throw new IOException(errorString);
  }

  public int findAvailableFireWallPort(int internal, int external, int tries, String appName) throws IOException {
    throw new IOException(errorString);
  }

  public void openFireWallPort(int local, int external, String appName) throws IOException {
    throw new IOException(errorString);
  }
}
