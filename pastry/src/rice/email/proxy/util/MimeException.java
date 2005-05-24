package rice.email.proxy.util;

import java.io.IOException;

/**
 * Exception which represents an exception in the MIME parsing
 */
public class MimeException extends IOException {
  
  public MimeException(String s) {
    super(s);
  } 
  
  public MimeException(Exception e) {
    super("Caused by " + e);
  }
  
}











