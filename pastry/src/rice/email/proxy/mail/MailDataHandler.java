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
package rice.email.proxy.mail;

import javax.activation.*;
import java.io.*;

public class MailDataHandler implements DataContentHandler {

  /** Creates a new instance of MailDataHandler */
  public MailDataHandler() {
  }

  /** This is the key, it just returns the data uninterpreted. */
  public Object getContent(javax.activation.DataSource dataSource) throws java.io.IOException {
    return dataSource.getInputStream();
  }

  public Object getTransferData(java.awt.datatransfer.DataFlavor dataFlavor,
                                javax.activation.DataSource dataSource)
  throws java.awt.datatransfer.UnsupportedFlavorException,
  java.io.IOException {
    return null;
  }

  public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
    return new java.awt.datatransfer.DataFlavor[0];
  }

  public void writeTo(Object obj, String str, java.io.OutputStream outputStream) throws java.io.IOException {
    // You would need to implement this to have
    // the conversion done automatically based on
    // mime type on the client side.
    System.out.println("I WAS TOLD TO WRITE " + obj.getClass().getName() + " OF TYPE " + str + " TO OUTPUT STREAM " + outputStream.getClass().getName());
    outputStream.write(obj.toString().getBytes());
    outputStream.flush();
  }
}
