package rice.email.proxy.mail;

import javax.activation.*;
import java.io.*;

public class MailDataHandler implements DataContentHandler {

  /** Creates a new instance of MailDataHandler */
  public MailDataHandler() {
  }

  /** This is the key, it just returns the data uninterpreted. */
  public Object getContent(javax.activation.DataSource dataSource) throws java.io.IOException {
    System.out.println("BinaryDataHandler: getContent called with: " + dataSource);
    return dataSource.getInputStream();
  }

  public Object getTransferData(java.awt.datatransfer.DataFlavor dataFlavor,
                                javax.activation.DataSource dataSource)
  throws java.awt.datatransfer.UnsupportedFlavorException,
  java.io.IOException {
    System.out.println("BinaryDataHandler: getTransferData called with: " + dataFlavor + " " + dataSource);
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
