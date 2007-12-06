package org.mpisws.p2p.transport.rendezvous;

import java.io.IOException;

import org.mpisws.p2p.transport.SocketRequestHandle;

import rice.Continuation;

/**
 * Used by NATted nodes.
 * 
 * Normally this would be notified of all changes to the leafset involving non-NATted nodes.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 */
public interface PilotManager<Identifier> {
  /**
   * Tells the manager to open a pilot to the Identifier
   * 
   * @param i
   * @param deliverAckToMe optional
   * @return
   */
  SocketRequestHandle<Identifier> openPilot(Identifier i, Continuation<SocketRequestHandle<Identifier>, IOException> deliverAckToMe);
  
  /**
   * Tells the manager that the pilot to the Identifier is no longer useful
   * 
   * @param i
   * @param deliverAckToMe optional
   * @return
   */
  void closePilot(Identifier i);

}
