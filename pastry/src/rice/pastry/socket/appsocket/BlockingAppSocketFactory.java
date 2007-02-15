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
/*
 * Created on Nov 22, 2006
 */
package rice.pastry.socket.appsocket;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import rice.p2p.commonapi.exception.*;
import rice.p2p.util.MathUtils;
import rice.pastry.socket.*;

public class BlockingAppSocketFactory {

  /**
   * 12 bytes, contains the magic number, the version number, and the HEADER_DIRECT
   */
  public static final byte[] magic_version_direct;
  
  static {
    int mvdLength = 
      SocketCollectionManager.PASTRY_MAGIC_NUMBER.length+
      4+ // version
      SocketCollectionManager.HEADER_DIRECT.length;

    // allocate it
    magic_version_direct = new byte[mvdLength];

    // copy in magic number
    System.arraycopy(SocketCollectionManager.PASTRY_MAGIC_NUMBER,0,magic_version_direct,0,SocketCollectionManager.PASTRY_MAGIC_NUMBER.length);
    
    // handle the version number
    // TODO: do this if you use a different version number
    
    // copy in HEADER_DIRECT
    System.arraycopy(SocketCollectionManager.HEADER_DIRECT,0,magic_version_direct,8,SocketCollectionManager.HEADER_DIRECT.length);    
  }
  
  // the size of the buffers for the socket
  private int SOCKET_BUFFER_SIZE = 32768;

  public BlockingAppSocketFactory() {
    
  }
  
  public SocketChannel connect(InetSocketAddress addr, int appId) throws IOException, AppSocketException {
    SocketChannel channel;
    channel = SocketChannel.open();
    channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
    channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
    
    channel.connect(addr);
/*
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    +                   PASTRY_MAGIC_NUMBER                         + 
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    +                   version number 0                            + 
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ =}
    +                   HEADER_SOURCE_ROUTE                         +   }
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+   }
    +            Next Hop (EpochInetSocketAddress)                  +    > // zero or more 
    +                                                               +    >           
    +                                                               +   }           
    +                                                               +   }           
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ =}
    +                      HEADER_DIRECT                            +   
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+   
    +                          AppId                                +   
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+   
*/
    ByteBuffer bb[] = new ByteBuffer[2];
    bb[0] = ByteBuffer.wrap(magic_version_direct);
    bb[1] = ByteBuffer.wrap(MathUtils.intToByteArray(appId));
    channel.write(bb);

    // read result
    ByteBuffer answer = ByteBuffer.allocate(1);
    channel.read(answer);
    answer.clear();
    byte connectResult = answer.get();
    
    //System.out.println(this+"Read "+connectResult);
    switch(connectResult) {
      case SocketAppSocket.CONNECTION_OK:
        break;
      case SocketAppSocket.CONNECTION_NO_APP:
        throw new AppNotRegisteredException(appId);
      case SocketAppSocket.CONNECTION_NO_ACCEPTOR:
        throw new NoReceiverAvailableException();            
      default:
        throw new AppSocketException("Unknown error "+connectResult);
    }
    
    
    return channel;
  }  
}
