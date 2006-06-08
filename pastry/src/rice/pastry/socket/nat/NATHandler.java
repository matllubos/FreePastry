/*
 * Created on Jun 8, 2006
 */
package rice.pastry.socket.nat;

import java.io.IOException;
import java.net.InetAddress;

/**
 * This is supposed to open a hole in the Firewall, usually using UPnP.
 * 
 * @author Jeff Hoye
 */
public interface NATHandler {

  /**
   * Search for the firewall on the NIC specified by the bindAddress
   * 
   * @param bindAddress the network to find the firewall on
   * @return
   * @throws IOException
   */
  public InetAddress findFireWall(InetAddress bindAddress) throws IOException;
  
  /**
   * The neame of the firewall's external address.  null if there is no firewall.
   * 
   * @return
   */
  InetAddress getFireWallExternalAddress();

  /**
   * Search for an available port forwarding, starting with the external address specified.  The internal 
   * one is given so you can detect that the rule was already in place.
   * 
   * @param internal
   * @param external
   * @return
   * @throws IOException
   */
  public int findAvailableFireWallPort(int internal, int external, int tries, String appName) throws IOException;
  public void openFireWallPort(int local, int external, String appName) throws IOException;

}
