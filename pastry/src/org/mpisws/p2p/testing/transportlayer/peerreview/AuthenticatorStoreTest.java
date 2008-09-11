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
package org.mpisws.p2p.testing.transportlayer.peerreview;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.SortedSet;

import org.mpisws.p2p.transport.peerreview.PeerReview;
import org.mpisws.p2p.transport.peerreview.commitment.Authenticator;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorSerializer;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorSerializerImpl;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorStore;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorStoreImpl;
import org.mpisws.p2p.transport.peerreview.replay.IdentifierSerializer;
import org.mpisws.p2p.transport.peerreview.replay.inetsocketaddress.ISASerializer;

import rice.environment.Environment;


public class AuthenticatorStoreTest {
  public static final int HASH_LEN = 20;
  public static final int SIGN_LEN = 28;
  
  
  
  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {
    Environment env = new Environment();
    PeerReview<InetSocketAddress> pr = new TestPeerReview(env, new AuthenticatorSerializerImpl(HASH_LEN, SIGN_LEN));
    TestAuthenticatorStore store = new TestAuthenticatorStore(pr,false);
    
    InetSocketAddress id = new InetSocketAddress(InetAddress.getLocalHost(), 6789);
    
    byte[] h1 = new byte[HASH_LEN];
    h1[2] = 5;
    byte[] s1 = new byte[SIGN_LEN];
    s1[2] = 17;
    Authenticator a1 = new Authenticator(42,h1,s1);

    // same
    byte[] h2 = new byte[HASH_LEN];
    h2[2] = 5;
    byte[] s2 = new byte[SIGN_LEN];
    s2[2] = 17;
    Authenticator a2 = new Authenticator(42,h2,s2);

    // dif hash
    byte[] h3 = new byte[HASH_LEN];
    h3[2] = 8;
    byte[] s3 = new byte[SIGN_LEN];
    s3[2] = 17;
    Authenticator a3 = new Authenticator(42,h3,s3);

    store.addAuthenticatorToMemory(id, new Authenticator(4,h1,s1));
    store.addAuthenticatorToMemory(id, new Authenticator(7,h1,s1));
    store.addAuthenticatorToMemory(id, new Authenticator(8,h1,s1));
    store.addAuthenticatorToMemory(id, new Authenticator(9,h1,s1));
    store.addAuthenticatorToMemory(id, new Authenticator(41,h1,s1));
    store.addAuthenticatorToMemory(id, new Authenticator(43,h1,s1));
    store.addAuthenticatorToMemory(id, new Authenticator(53,h1,s1));
    
    store.addAuthenticatorToMemory(id, a1);
    store.addAuthenticatorToMemory(id, a2); // should not crash here
    
    boolean fail = true;
    try {
      store.addAuthenticatorToMemory(id, a3); // should crash here
      fail = false;
    } catch (RuntimeException re) {
      fail = true;
    }
    if (!fail) {
      System.out.println("Allowed Duplicates, BAD!!!!");
      return;
    }
    
    store.flushAuthenticatorsFromMemory(id, 8, 42);
    if (store.findSubject(id).size() != 4) {
      System.out.println("flush failed! "+store.findSubject(id));
    }

    store.flushAuthenticatorsFromMemory(id, 43, 43);
    if (store.findSubject(id).size() != 3) {
      System.out.println("flush failed! "+store.findSubject(id));
    }

    System.out.println("success");
    env.destroy();
  }
}
  class TestPeerReview implements PeerReview<InetSocketAddress> {
    
    Environment env;
    AuthenticatorSerializer aSer;
    public TestPeerReview(Environment env, AuthenticatorSerializer aSer) {
      this.env = env;
      this.aSer = aSer;
    }

    public IdentifierSerializer<InetSocketAddress> getIdSerializer() {
      return new ISASerializer();
    }
  
    public Environment getEnvironment() {
      return env;
    }
  
    public AuthenticatorSerializer getAuthenticatorSerializer() {
      return aSer;
    }

    public long getTime() {
      return env.getTimeSource().currentTimeMillis();
    }
  }

  class TestAuthenticatorStore extends AuthenticatorStoreImpl<InetSocketAddress> {

    public TestAuthenticatorStore(PeerReview<InetSocketAddress> peerreview,
        boolean allowDuplicateSeqs) {
      super(peerreview, allowDuplicateSeqs);
    }
    
    @Override
    public void addAuthenticatorToMemory(InetSocketAddress id,
        Authenticator authenticator) {
      super.addAuthenticatorToMemory(id, authenticator);
    }

    @Override
    public void flushAuthenticatorsFromMemory(InetSocketAddress id,
        long minseq, long maxseq) {
      super.flushAuthenticatorsFromMemory(id, minseq, maxseq);
    }

    @Override
    public SortedSet<Authenticator> findSubject(InetSocketAddress id) {
      return super.findSubject(id);
    }

    
  }
