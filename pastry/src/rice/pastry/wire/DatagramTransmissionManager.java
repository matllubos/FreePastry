/**************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.pastry.wire;

import rice.pastry.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.wire.exception.*;
import rice.pastry.wire.messaging.datagram.*;
import rice.pastry.wire.messaging.socket.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.net.*;

/**
 * Class which controls which object should be transmitted in the UPD
 * version of the pastry protocol.  This class is responsible for ensuring
 * reliable and ordered delivery of packets to their destinations. This
 * implementation is thread-safe, so both the client and pastry thread can
 * access it at once.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class DatagramTransmissionManager {

  // maps address -> TransmissionEntry
  private HashMap map;

  // the key the datagrammanager uses
  private SelectionKey key;

  // the pastry node this transmission manager serves
  private WirePastryNode pastryNode;

  // the first 'ack' number to use
  public static int BEGIN_ACK_NUM = Integer.MIN_VALUE;

  // a random number generator used for retransmission delays
  private Random random;

  /**
   * Builds a transmission manager for a given pastry node using a
   * given key.
   *
   * @param spn The pastry node this manager serves.
   * @param key The key used by the datagram manager.
   */
  public DatagramTransmissionManager(WirePastryNode spn, SelectionKey key) {
    map = new HashMap();
    this.key = key;
    pastryNode = spn;

    random = new Random();
  }

  /**
   * Adds a pending write to the queue.
   *
   * @param write The pending write to add.
   */
  public void add(PendingWrite write) {
    synchronized (map) {
      TransmissionEntry entry = (TransmissionEntry) map.get(write.getDestination());

      if (entry == null) {
        entry = new TransmissionEntry(write.getDestination(), write.getAddress());
        map.put(write.getDestination(), entry);
      }

      entry.add(write);

      if (entry.getState() == entry.STATE_READY) {
        if (pastryNode.inThread()) {
          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        } else {
          pastryNode.getSelectorManager().getSelector().wakeup();
        }
      }
    }
  }

  /**
   * Returns an iterator over the pending writes ready to be written.
   *
   * @return An Iterator over PendingWrites, all ready to be written.
   */
  public Iterator getReady() {
    synchronized (map) {
      LinkedList list = new LinkedList();
      Object[] array = getEntries();

      for (int i=0; i<array.length; i++) {
        TransmissionEntry entry = (TransmissionEntry) array[i];

        if (entry.getState() == entry.STATE_READY) {
          list.addLast(entry.get());
        }
      }

      return list.iterator();
    }
  }

  /**
   * Callback mechanism designed to be called when an ack packet
   * is received. If there are more messasges in the queue, the
   * appropriate entry will be set 'ready', and the next message will
   * be sent across the wire on the next getReady() call.
   *
   * @param address The address the ack came from.
   * @param num The number of the ack
   */
  public void receivedAck(AcknowledgementMessage message) {
    TransmissionEntry entry = null;

    synchronized (map) {
      entry = (TransmissionEntry) map.get(message.getSource());
    }

    if (entry != null) {
      entry.ackReceived(message.getNum());
    } else {
      debug("PANIC: Ack received from unknown nodeId " + message.getSource());
    }
  }

  /**
   * Designed to be called periodically in order for the Transmission
   * Manager to decide if a packet has been lost.
   */
  public void wakeup() {
    synchronized (map) {
      Object[] array = getEntries();
      boolean ready = false;

      for (int i=0; i<array.length; i++) {
        TransmissionEntry entry = (TransmissionEntry) array[i];
        entry.wakeup();

        if (entry.getState() == entry.STATE_READY)
          ready = true;
      }

      if (ready)
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
      else
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
    }
  }

  /**
   * Resets the sequence number for the specified node
   *
   * @param node The node to reset
   */
  public void resetAckNumber(NodeId node) {
    TransmissionEntry entry = null;

    synchronized (map) {
      entry = (TransmissionEntry) map.get(node);
    }

    if (entry != null) {
      entry.resetAckNumber();
    } else {
      debug("PANIC: Reset request received for unknown nodeId " + node);
    }
  }

  /**
   * Private method which returns a iterator over all of the entries in
   * the TransmissionManager.
   *
   * @return An Iterator over the TransmissionEntries in the Transmission
   *         Manager.
   */
  private Object[] getEntries() {
    return map.values().toArray();
  }

  private void debug(String s) {
    if (Log.ifp(6))
      System.out.println(pastryNode.getNodeId() + " (T): " + s);
  }

  /**
   * Interal class which handles the transmission for a single destination address.
   * It has an iternal queue of messages waiting to be sent. It is also resposiable
   * for determining when a message has been lost, and it then resends the message.
   *
   * This class also implements a policy of exponential trials in order to get it's
   * message sent.  If a message fails, it first waits INITIAL_RESEND_WAIT_TIME seconds,
   * and then tries again. If it fails again, it waits between 1.5 and 2.5 times the
   * previous wait time, and tries again.  This repeats until MAX_NUM_RETRIES, at which
   * point the remote node is declared dead.
   *
   * This class also implements a policy of dynamic timeout.  If a message takes
   * X ms to receive an ack, it will wait up to TIMEOUT_FACTOR * X ms before the
   * next message is declared dead.  This will improve performance when the amount
   * of traffic on the network is changing.
   */
  private class TransmissionEntry {

    // PRIVATE FIELDS
    // the destination address this entry is sending to
    private InetSocketAddress address;

    // the node ID for this TE
    private NodeId nodeId;

    // the node handle for this TE
    private WireNodeHandle handle;

    // the queue of pending writes
    private LinkedList queue;

    // the ack number we are currently waiting for
    private int ackExpected;

    // time last time at which we sent a message
    private long sendTime;

    // the time at which we began waiting to resent
    private long resendWaitBeginTime;

    // how long we should wait until we resent
    private long resendWaitTime;

    // the state of this TransmissionEntry
    private int state;

    // how long we should wait before declaring a packet lost
    private long sendTimeoutTime;

    // how me retries we have done so far
    private int numRetries;


    // STATIC FIELDS
    public int STATE_READY = -1;
    public int STATE_WAITING_FOR_ACK = -2;
    public int STATE_NO_DATA = -3;
    public int STATE_WAITING_FOR_RESEND = -4;
    public int STATE_WAITING_TO_SEND = -5;

    // the default wait-time for a lost packet
    public long SEND_TIMEOUT_DEFAULT = 1000;

    // the minimum wait time for a lost packet
    public long SEND_TIMEOUT_MIN = 500;

    // the initial amount of time to wait before resending
    public long INITIAL_RESEND_WAIT_TIME = 100;

    // the factor by which to multiply the last send time
    // to determine the next timeout time
    public double TIMEOUT_FACTOR = 2;

    // the maximum number to retries before dropping the message
    // on the floor
    public int MAX_NUM_RETRIES = 5;

    // the maximum number of retries before declaring the node to
    // be dead and attampting to open a socket
    public int NUM_RETRIES_BEFORE_OPENING_SOCKET = 3;

    // the maximum number of objects in the UDP queue before we
    // open a socket
    public int MAX_UDP_QUEUE_SIZE = 4;


    /**
     * Builds a TransmissionEntry for a specified address.
     *
     * @param address The destination address of this entry.
     */
    public TransmissionEntry(NodeId nodeId, InetSocketAddress address) {
      queue = new LinkedList();
      ackExpected = BEGIN_ACK_NUM;
      state = STATE_NO_DATA;
      resendWaitTime = (long) (INITIAL_RESEND_WAIT_TIME * (1 + random.nextDouble()));
      sendTimeoutTime = SEND_TIMEOUT_DEFAULT;
      numRetries = 0;

      this.nodeId = nodeId;
      this.address = address;

      handle = ((WireNodeHandlePool) pastryNode.getNodeHandlePool()).get(nodeId);

      if (handle == null) {
        handle = new WireNodeHandle(address, nodeId, pastryNode);
        handle = (WireNodeHandle) pastryNode.getNodeHandlePool().coalesce(handle);
      }
    }

    /**
     * Resets this entry's ack number
     */
    public void resetAckNumber() {
      ackExpected = BEGIN_ACK_NUM;
    }

    /**
     * Adds a pending write to this entry's queue.
     *
     * @param write The write to add.
     */
    public void add(PendingWrite write) {
      queue.addLast(write);

      debug("Added write for object " + write.getObject());

      if ((queue.size() > MAX_UDP_QUEUE_SIZE) && (! (write.getObject() instanceof DatagramMessage)) &&
          (handle.getState() == WireNodeHandle.STATE_USING_UDP)) {
        LinkedList list = new LinkedList();

        debug("Queue has exceed maximum length - moving to TCP.");

        Iterator i = queue.iterator();

        while (i.hasNext()) {
          PendingWrite pw = (PendingWrite) i.next();

          if (! (pw.getObject() instanceof DatagramMessage)) {
            debug("Moving message " + pw.getObject() + " to TCP queue.");
            list.addLast(new SocketTransportMessage(pw.getObject()));
            i.remove();
          }
        }

        if (queue.size() > 0)
          state = STATE_READY;
        else
          state = STATE_NO_DATA;

        handle.connectToRemoteNode(list);
      } else {
        if (state == STATE_NO_DATA)
          state = STATE_READY;
      }
    }

    /**
     * Returns a the current PendingWrite, if the entry is in the STATE_READY
     * state.  If not, it thows an IllegalArgumentException. If a write is returned,
     * the state is changed to the STATE_WAITING_FOR_ACK state.
     *
     * Also checks to see if there are too many objects in the queue, and a socket needs
     * to be opened.
     *
     * @return The current PendingWrite.
     */
    public PendingWrite get() {
      if (state == STATE_READY) {
        state = STATE_WAITING_FOR_ACK;
        sendTime = System.currentTimeMillis();
        PendingWrite write = (PendingWrite) queue.getFirst();

        debug("Returning write for object " + write.getObject());

        if (write.getObject() instanceof DatagramMessage) {
          DatagramMessage msg = (DatagramMessage) write.getObject();
          msg.setNum(ackExpected);

          return new PendingWrite(nodeId, address, msg);
        } else {
          return new PendingWrite(nodeId, address, new DatagramTransportMessage(pastryNode.getNodeId(), nodeId, ackExpected, write.getObject()));
        }
      } else {
        throw new IllegalArgumentException("get() called on non-ready TransmissionEntry.");
      }
    }

    /**
     * Callback mechanism designed to be called when an ack comes in from this
     * address.
     *
     * @param num The number of the ack
     */
    public void ackReceived(int num) {
      if (state != STATE_NO_DATA) {
        if (ackExpected == num) {
          handle.markAlive();

          PendingWrite pw = (PendingWrite) queue.removeFirst();

          if (pw.getObject() instanceof PingMessage)
            handle.pingResponse();

          long elapsedTime = System.currentTimeMillis() - sendTime;

          if ((elapsedTime * TIMEOUT_FACTOR) > SEND_TIMEOUT_MIN)
            sendTimeoutTime = (long) (TIMEOUT_FACTOR * elapsedTime);
          else
            sendTimeoutTime = SEND_TIMEOUT_MIN;

          resendWaitTime = (long) (resendWaitTime / 2);

          if (queue.size() > 0) {
            state = STATE_WAITING_TO_SEND;
            resendWaitBeginTime = System.currentTimeMillis();
          } else {
            state = STATE_NO_DATA;
          }

          numRetries = 0;
          ackExpected++;
        } else {
          debug("WARNING: Got wrong ack - got " + num + " expected " + ackExpected);
        }
      } else {
        debug("WARNING: ackReceived() called on non-active TransmissionEntry. num " + num + " ackExpected " + ackExpected);
      }
    }

    /**
     * Returns the current state of this entry.
     *
     * @return The current state.
     */
    public int getState() {
      return state;
    }

    /**
     * Callback designed to be called on a regular basis to that the entry can
     * determine if a packet has been lost, or, if we a are waiting to send, if
     * it is time.
     */
    public void wakeup() {
      if (state == STATE_WAITING_FOR_ACK) {
        long timeout = System.currentTimeMillis() - sendTime;

        if (timeout > sendTimeoutTime) {
          debug("WARNING: It has been too long (" + timeout + ") - packet lost. Resending in " + resendWaitTime + " milliseconds. (" + numRetries + " try)");

          state = STATE_WAITING_FOR_RESEND;
          resendWaitBeginTime = System.currentTimeMillis();
          numRetries++;

          if (numRetries == NUM_RETRIES_BEFORE_OPENING_SOCKET) {
            PendingWrite write = (PendingWrite) queue.getFirst();

            if (! (write.getObject() instanceof DatagramMessage)) {
              debug("Attempting to open a socket... (" + numRetries + " try)");

              LinkedList list = new LinkedList();

              Iterator i = queue.iterator();

              while (i.hasNext()) {
                PendingWrite pw = (PendingWrite) i.next();
                if (! (pw.getObject() instanceof DatagramMessage)) {
                  debug("Moving message " + pw.getObject() + " to TCP queue.");
                  list.addLast(new SocketTransportMessage(pw.getObject()));
                  i.remove();
                }
              }

              //handle.markDead();

              if (queue.size() > 0)
                state = STATE_READY;
              else
                state = STATE_NO_DATA;

              handle.connectToRemoteNode(list);
            }
          }
        }

        if (numRetries >= MAX_NUM_RETRIES) {
          debug(pastryNode.getNodeId() + " found " + nodeId + " to be non-responsive - cancelling message " + queue.getFirst());
          queue.removeFirst();
          state = STATE_NO_DATA;
	  //handle.markDead();
        }
      } else if (state == STATE_WAITING_FOR_RESEND) {
        long timeout = System.currentTimeMillis() - resendWaitBeginTime;

        if (timeout > resendWaitTime) {
          debug("WARNING: Timeout has completed - resending.");

          state = STATE_READY;
          resendWaitTime = (long) (resendWaitTime * TIMEOUT_FACTOR);// (1.5 + random.nextDouble()));
          sendTimeoutTime = (long) (sendTimeoutTime * TIMEOUT_FACTOR);
        }
      } else if (state == STATE_WAITING_TO_SEND) {
        long timeout = System.currentTimeMillis() - resendWaitBeginTime;

        if (timeout > resendWaitTime) {
          state = STATE_READY;
        }
      }
    }

    private void debug(String s) {
      if (Log.ifp(7))
        System.out.println(pastryNode.getNodeId() + " (" + nodeId + ") (TE): " + s);
    }
  }
}
