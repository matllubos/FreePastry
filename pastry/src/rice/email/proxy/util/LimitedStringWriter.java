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

public class LimitedStringWriter extends StringWriter {
  
  /**
   * The capacity of the writer
   */
  protected int capacity;
  
  /**
   * Constructor which takes a maximum capacity
   * 
   * @param capacity The capacity
   */
  public LimitedStringWriter(int capacity) {
    this.capacity = capacity;
  }
  
  /**
   * First checks to see if this will put us over capacity, and if 
   * so, throws a StringWriterOverflowException. Otherwise, proceeds
   * as normal
   *
   * @param c The character
   */
  public void write(int c) {
    if (getBuffer().length() + 1 > capacity) 
      throw new StringWriterOverflowException();
    
    super.write(c);
  }
  
  /**
   * First checks to see if this will put us over capacity, and if 
   * so, throws a StringWriterOverflowException. Otherwise, proceeds
   * as normal
   *
   * @param c The array
   * @param off The offset
   * @param len The length
   */
  public void write(char[] c, int off, int len) {
    if (getBuffer().length() + len > capacity) 
      throw new StringWriterOverflowException();
    
    super.write(c, off, len);
  }
  
  /**
   * First checks to see if this will put us over capacity, and if 
   * so, throws a StringWriterOverflowException. Otherwise, proceeds
   * as normal
   *
   * @param str The string
   */
  public void write(String str) {
    if (getBuffer().length() + str.length() > capacity) 
      throw new StringWriterOverflowException();
    
    super.write(str);
  }
  
  /**
   * First checks to see if this will put us over capacity, and if 
   * so, throws a StringWriterOverflowException. Otherwise, proceeds
   * as normal
   *
   * @param str The string
   * @param off The offset
   * @param len The length
   */
  public void write(String str, int off, int len) {
    if (getBuffer().length() + len > capacity) 
      throw new StringWriterOverflowException();
    
    super.write(str, off, len);
  }
}