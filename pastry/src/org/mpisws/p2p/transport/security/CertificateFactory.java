package org.mpisws.p2p.transport.security;

import rice.Continuation;
import rice.p2p.commonapi.NodeHandle;

/**
 * Specifies the method to acquire a Node's certificate
 * @author Jeff Hoye
 *
 */
public interface CertificateFactory {
  void getCertificate(NodeHandle handle, Continuation<Certificate, Exception> response);
}
