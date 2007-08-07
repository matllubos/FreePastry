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
package org.mpisws.p2p.transport.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.ClosedChannelException;

import org.mpisws.p2p.transport.P2PSocket;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.util.rawserialization.SimpleInputBuffer;

/**
 * Allows reading in from a socket, throws exception when there is not enough data, but 
 * caches the data so that it can be read when there is enough.
 * 
 * Not thread safe!
 * 
 * The read operations will either:
 *   a) succeed
 *   b) throw a InsufficientBytesException, which you should probably retry later when there are more bytes
 *   c) throw a ClosedChannelException, which means the socket was closed, 
 *   d) other IOException
 *   
 * Note that the bytesRemaining() field always returns UNKNOWN because Java's socket api doesn't give us this information
 * 
 * To find the size of the cache call size();
 * 
 * @author Jeff Hoye
 *
 */
public class SocketInputBuffer implements InputBuffer {
  P2PSocket socket;
  ByteBuffer readBB, writeBB;  
  byte[] cache;
  ByteBuffer one, two, four, eight; // to reduce object allocation, reuse common sizes.  Lazilly constructed
  int initialSize;
  
  DataInputStream dis;
  
  public SocketInputBuffer(P2PSocket socket, int size) {
    this.socket = socket;
    initialSize = size;
    cache = new byte[size];
    readBB = ByteBuffer.wrap(cache);
    writeBB = ByteBuffer.wrap(cache);
    dis = new DataInputStream(new InputStream() {
    
      @Override
      public int read(byte[] b) throws IOException {
        return readInternal(b);
      }
    
      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        return readInternal(b, off, len);
      }
    
      @Override
      public int read() throws IOException {
        return readInternal();
      }    
    });
  }
  
  public int bytesRemaining() {
    return UNKNOWN;
  }

  public void reset() {
    readBB.clear(); 
  }  
    
  public int size() {
    return writeBB.position(); 
  }

  public int readInternal(byte[] b, int off, int len) throws IOException {
    int bytesToRead = needBytes(len, false);
    readBB.get(b, off, bytesToRead);
    return bytesToRead;
  }

  public int readInternal(byte[] b) throws IOException {
    int bytesToRead = needBytes(b.length, false);
    readBB.get(b, 0, bytesToRead);
    return bytesToRead;
  }

  public int readInternal() throws IOException {
    needBytes(1, true);
    return (readBB.get() & 0xFF);
  }


  public int read(byte[] b, int off, int len) throws IOException {
    return dis.read(b, off, len);
  }

  public int read(byte[] b) throws IOException {
    return dis.read(b);
  }

  public byte readByte() throws IOException {
    return dis.readByte();
  }  
  
  public boolean readBoolean() throws IOException {
    return dis.readBoolean();
  }

  public char readChar() throws IOException {
    return dis.readChar();
  }

  public double readDouble() throws IOException {
    return dis.readDouble();
  }

  public float readFloat() throws IOException {
    return dis.readFloat();    
  }

  public int readInt() throws IOException {
    return dis.readInt();
  }

  public long readLong() throws IOException {
    return dis.readLong();
  }

  public short readShort() throws IOException {
    return dis.readShort();
  }

  public String readUTF() throws IOException {
    return dis.readUTF();
  }

  /**
   * Returns the number of bytes available in the cache,
   * reads bytes from socket into cache if need be
   * 
   * @param num the number of bytes you need
   * @param fail true if you want it to throw an exception if there aren't enough bytes
   * @return
   * @throws IOException
   */
  private int needBytes(int num, boolean fail) throws IOException {
    int bytesToReadIntoCache = num - (writeBB.position() - readBB.position());
    if (bytesToReadIntoCache > 0) {
      readBytesIntoCache(bytesToReadIntoCache);
    }
    int ret = readBB.remaining();
    if (ret > num) ret = num;
    if (fail && ret < num) throw new InsufficientBytesException(num, ret);
    return ret;
  }
  
  /**
   * Reads this many bytes into the cache, grows the cache if needed
   * 
   * @param num
   * @return
   * @throws IOException
   */
  private int readBytesIntoCache(int num) throws IOException {
    ByteBuffer in;
    switch(num) {
    case 0: 
      return 0;
    case 1:
      if (one == null) one = ByteBuffer.allocate(num);
      one.clear();      
      in = one;
      break;
    case 2:      
      if (two == null) two = ByteBuffer.allocate(num);
      two.clear();      
      in = two;
      break;
    case 4:      
      if (four == null) four = ByteBuffer.allocate(num);
      four.clear();      
      in = four;
      break;
    case 8:      
      if (eight == null) eight = ByteBuffer.allocate(num);
      eight.clear();      
      in = eight;
      break;
    default:
      in = ByteBuffer.allocate(num);      
    }
    
    int ret = (int)socket.read(in);
    if (ret == -1) throw new ClosedChannelException();
    in.flip();
    while (writeBB.remaining() < ret) {
      grow(); 
    }
    writeBB.put(in);
    return ret;
  }
  
  /**
   * Clears the cache from memory, resetting it to the initial size.  This is a 
   * good thing to do after you read an object.  
   */
  public void clear() throws IOException {
    if (cache.length > initialSize)
      cache = new byte[initialSize];
    readBB = ByteBuffer.wrap(cache);
    writeBB = ByteBuffer.wrap(cache);
    dis.reset();
  }  
  
  private void grow() {
    byte[] newCache = new byte[cache.length];
    System.arraycopy(cache, 0, newCache, 0, cache.length);
    ByteBuffer newReadBB = ByteBuffer.wrap(newCache);
    ByteBuffer newWriteBB = ByteBuffer.wrap(newCache);
    
    newReadBB.position(readBB.position()); 
    newWriteBB.position(writeBB.position()); 
    
    cache = newCache;
    readBB = newReadBB;
    writeBB = newWriteBB;
  }
}
