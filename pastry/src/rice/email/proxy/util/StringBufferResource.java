/*************************************************************************

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
package rice.email.proxy.util;

import java.io.*;

public class StringBufferResource implements Resource {
  
  public static int MAXIMUM_LENGTH = 7000000;
  
  /**
   * The buffers used in the resource 
   */
  protected StringWriter writer;
  protected StringBuffer buffer;
  
  public StringBufferResource() {
  }
  
  public StringBufferResource(String initalValue) {
    buffer = new StringBuffer(initalValue);
  }
  
  public Writer getWriter() throws IOException {
//    writer = new LimitedStringWriter(MAXIMUM_LENGTH);
    writer = new StringWriter();
    
    return writer;
  }
  
  public InputStream getInputStream() throws IOException {
    closeInput();
    
    return new InputStream() {
      int pos = 0;
      
      public int read() {
        return (pos < buffer.length() ? (int) buffer.charAt(pos++) & 0xFF : -1);
      }
      
      public int read(byte[] buf, int off, int len) {
        if (pos >= buffer.length())
          return -1;
        
        if (len > buffer.length() - pos)
          len = buffer.length() - pos;
        
        for (int i=0; i<len; i++)
          buf[off+i] = (byte) buffer.charAt(pos++);
        
        return len;
      }
    };
  }
  
  public Reader getReader() throws IOException {
    closeInput();
    
    return new InputStreamReader(getInputStream());
  }
  
  private void closeInput() throws IOException {
    if (writer != null) {
      buffer = writer.getBuffer();
      writer = null;
    }
    
    if (buffer == null)
      throw new IOException("No content has been written");
  }
  
  public long getSize() {       
    try {
      closeInput();
    } catch (IOException e) {
      return 0;
    }
    
    return buffer.length();
  }
  
  public void delete() {
    buffer = null;
    writer = null;
  }
}