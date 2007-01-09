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

package rice.pastry;

import java.net.*;

/**
 * Represents a listener to pastry network activity
 *
 * @version $Id$
 *
 * @author Peter Druschel
 */
public interface NetworkListener {
  
  public static int TYPE_TCP    = 0x00;
  public static int TYPE_UDP    = 0x01;
  public static int TYPE_SR_TCP = 0x10;
  public static int TYPE_SR_UDP = 0x11;
  
  public static int REASON_NORMAL = 0;
  public static int REASON_SR = 1;
  public static int REASON_BOOTSTRAP = 2;
  
  public static int REASON_ACC_NORMAL = 3;
  public static int REASON_ACC_SR = 4;
  public static int REASON_ACC_BOOTSTRAP = 5;
  public static int REASON_APP_SOCKET_NORMAL = 6;
  
  public void channelOpened(InetSocketAddress addr, int reason);
  public void channelClosed(InetSocketAddress addr);
  public void dataSent(int msgAddress, short msgType, InetSocketAddress socketAddress, int size, int wireType);
  public void dataReceived(int msgAddress, short msgType, InetSocketAddress socketAddress, int size, int wireType);
   
}


