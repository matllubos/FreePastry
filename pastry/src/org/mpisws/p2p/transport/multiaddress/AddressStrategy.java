package org.mpisws.p2p.transport.multiaddress;

import java.net.InetSocketAddress;

/**
 * Return the InetSocketAddress to use for this EpochInetSocketAddress
 * 
 * @author Jeff Hoye
 *
 */
public interface AddressStrategy {
  public InetSocketAddress getAddress(MultiInetSocketAddress remote);
}
