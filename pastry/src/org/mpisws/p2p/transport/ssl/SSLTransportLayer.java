package org.mpisws.p2p.transport.ssl;

import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.multiaddress.MultiInetAddressTransportLayer;

import rice.pastry.socket.EpochInetSocketAddress;

/**
 * Optionally Encrypts or Authenticates sockets.
 * @author Jeff Hoye
 *
 */
public interface SSLTransportLayer extends MultiInetAddressTransportLayer {
  public static final String OPTION_ENCRYPTION = "encrypt";
  public static final String OPTION_AUTHENTICATION = "authenticate";

  public static final int NO = 0;
  public static final int YES = 1;
}
