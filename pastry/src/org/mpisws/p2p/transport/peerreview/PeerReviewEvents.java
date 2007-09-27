package org.mpisws.p2p.transport.peerreview;

public interface PeerReviewEvents {

  public static final short EVT_SEND = 0; // Outgoing message (followed by SENDSIGN entry)
  public static final short EVT_RECV = 1; // Incoming message (followed by SIGN entry)
  public static final short EVT_SIGN = 2;                   /* Signature on incoming message */
  public static final short EVT_ACK = 3;                                  /* Acknowledgment */
  public static final short EVT_CHECKPOINT = 4;                 /* Checkpoint of the state machine */
  public static final short EVT_INIT = 5;                    /* State machine is (re)started */
  public static final short EVT_SENDSIGN = 6;                   /* Signature on outgoing message */
  
  public static final short EVT_SOCKET_OPEN_INCOMING = 9; 
  public static final short EVT_SOCKET_OPEN_OUTGOING = 10; 
  public static final short EVT_SOCKET_OPENED_OUTGOING = 18; 
  public static final short EVT_SOCKET_EXCEPTION = 19; 
  public static final short EVT_SOCKET_CLOSE = 11; 
  public static final short EVT_SOCKET_CLOSED = 12; 
  public static final short EVT_SOCKET_CAN_READ = 13; 
  public static final short EVT_SOCKET_CAN_WRITE = 14; 
  public static final short EVT_SOCKET_CAN_RW = 15; 
  public static final short EVT_SOCKET_READ = 16; 
  public static final short EVT_SOCKET_WRITE = 17;   
}
