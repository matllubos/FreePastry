/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.pastry.socket.nat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;

import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.networkinfo.ConnectivityResult;
import org.mpisws.p2p.transport.networkinfo.InetSocketAddressLookup;

import rice.Continuation;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.util.AttachableCancellable;
import rice.pastry.Id;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.transport.TLPastryNode;

public class ConnectivityVerifierImpl implements ConnectivityVerifier {
  SocketPastryNodeFactory spnf;
  
  public ConnectivityVerifierImpl(SocketPastryNodeFactory spnf) {
    this.spnf = spnf;
  }
  
  public Cancellable findExternalAddress(final InetSocketAddress local,
      Collection<InetSocketAddress> probeAddresses,
      final Continuation<InetSocketAddress, Exception> deliverResultToMe) {
    // TODO: make sure the addresses are Internet routable?
    // TODO: Timeout (can be in parallel, so timeout ~every second, and try the next one, then cancel everything if one comes through)

    final ArrayList<InetSocketAddress> probeList = new ArrayList<InetSocketAddress>(probeAddresses);
    final AttachableCancellable ret = new AttachableCancellable();

    // getInetSocketAddressLookup verifyies that we are on the selector
    ret.attach(getInetSocketAddressLookup(local, new Continuation<InetSocketAddressLookup, Exception>() {
    
      public void receiveResult(InetSocketAddressLookup lookup) {
        // we're on the selector now, and we have our TL
        
        // pull a random node off the list, and try it, we do this so the recursion works
        InetSocketAddress target = probeList.remove(spnf.getEnvironment().getRandomSource().nextInt(probeList.size())); 
        
        ret.attach(lookup.getMyInetAddress(target, new Continuation<InetSocketAddress, Exception>() {          
          public void receiveResult(InetSocketAddress result) {              
            // success!
            ret.cancel(); // kill any recursive tries
            deliverResultToMe.receiveResult(result);
          }
        
          public void receiveException(Exception exception) {
            // see if we can try anyone else
            if (probeList.isEmpty()) deliverResultToMe.receiveException(exception);
            
            // retry (recursive)
            ret.attach(findExternalAddress(local, probeList, deliverResultToMe));
          }      
        }, null));
      }
      
      public void receiveException(Exception exception) {
        // we couldn't even get a transport layer, DOA        
        deliverResultToMe.receiveException(exception);
      }      
    }));
    
    return ret;
  }

  protected Cancellable getInetSocketAddressLookup(final InetSocketAddress bindAddress, final Continuation<InetSocketAddressLookup, Exception> deliverResultToMe) {
    final AttachableCancellable ret = new AttachableCancellable();
    
    Runnable r = new Runnable() {
      public void run() {
        if (ret.isCancelled()) return;
        TLPastryNode pn = new TLPastryNode(Id.build(), spnf.getEnvironment());
    
        try {
          InetSocketAddressLookup lookup = (InetSocketAddressLookup)spnf.getBottomLayers(pn, new MultiInetSocketAddress(bindAddress));      
          deliverResultToMe.receiveResult(lookup);
        } catch (IOException ioe) {
          deliverResultToMe.receiveException(ioe);
        }
      }
    };
    // only do this on the selector thread, b/c binding on the non-selector makes java cranky on some versions of linux
    if (spnf.getEnvironment().getSelectorManager().isSelectorThread()) {
      r.run();
    } else {
      spnf.getEnvironment().getSelectorManager().invoke(r);
    }
    return ret;    
  }
  
  public Cancellable verifyConnectivity(MultiInetSocketAddress local,
      Collection<InetSocketAddress> probeAddresses,
      ConnectivityResult deliverResultToMe) {
    throw new RuntimeException("TODO: Implement.");
  }

}
