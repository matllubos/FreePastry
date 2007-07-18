package org.mpisws.p2p.transport.security;

import org.mpisws.p2p.transport.commonapi.CommonAPITransportLayer;

import rice.p2p.commonapi.NodeHandle;

public interface CertificateTransportLayer extends CommonAPITransportLayer {

  public Certificate getCertificate(NodeHandle handle);
}
