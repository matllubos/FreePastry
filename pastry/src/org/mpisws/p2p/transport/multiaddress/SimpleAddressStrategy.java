package org.mpisws.p2p.transport.multiaddress;

import java.net.InetSocketAddress;

public class SimpleAddressStrategy implements AddressStrategy {

  MultiInetSocketAddress local;
  
  public SimpleAddressStrategy(MultiInetSocketAddress local) {
    this.local = local; 
  }
  
  /**
   * Method which returns the address of this address
   *
   * @return The address
   */
  public InetSocketAddress getAddress(MultiInetSocketAddress remote) {   
    // start from the outside address, and return the first one not equal to the local address (sans port)
    
    try {
      for (int ctr = 0; ctr < remote.address.length; ctr++) {
        if (!remote.address[ctr].getAddress().equals(local.address[ctr].getAddress())) {
          return remote.address[ctr];
        }
      }
    } catch (ArrayIndexOutOfBoundsException aioobe) {
      throw new RuntimeException("ArrayIndexOutOfBoundsException in "+this+".getAddress("+local+")",aioobe);
    }
    return remote.address[remote.address.length-1]; // the last address if we are on the same computer
  }
  
  /**
   * This is for hairpinning support.  The Node is advertising many different 
   * InetSocketAddresses that it could be contacted on.  In a typical NAT situation 
   * this will be 2: the NAT's external address, and the Node's non-routable 
   * address on the Lan.  
   * 
   * The algorithm sees if the external address matches its own external 
   * address.  If it doesn't then the node is on a different lan, use the external.
   * If the external address matches then both nodes are on the same Lan, and 
   * it uses the internal address because the NAT may not support hairpinning.  
   * 
   * @param local my sorted list of InetAddress
   * @return the address I should use to contact the node
   */
//  public InetSocketAddress getAddress(InetAddress[] local) {   
//    // start from the outside address, and return the first one not equal to the local address (sans port)
//    try {
//      for (int ctr = 0; ctr < remote.address.length; ctr++) {
//        if (!remote.address[ctr].getAddress().equals(local[ctr])) {
//          return remote.address[ctr];
//        }
//      }
//    } catch (ArrayIndexOutOfBoundsException aioobe) {
//      String s = "";
//      for (int ctr = 0; ctr < local.length; ctr++) {
//        s+=local[ctr];
//        if (ctr < local.length-1) s+=":";  
//      }
//      throw new RuntimeException("ArrayIndexOutOfBoundsException in "+this+".getAddress("+local.length+")",aioobe);
//    }
//    return remote.address[remote.address.length-1]; // the last address if we are on the same computer
//  }

}
