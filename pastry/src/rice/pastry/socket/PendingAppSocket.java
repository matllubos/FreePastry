/*
 * Created on Feb 14, 2006
 */
package rice.pastry.socket;

import rice.p2p.commonapi.appsocket.AppSocketReceiver;

public class PendingAppSocket {
  int appAddress;
  AppSocketReceiver receiver;
  
  public PendingAppSocket(int appAddress, AppSocketReceiver receiver) {
    this.appAddress = appAddress;
    this.receiver = receiver;
  }

}
