package rice.email.proxy.imap;

/**
 * A Phoenix-accessable interface to the
 * IMAP server.
 */
public interface ImapServer {
  
  public int getPort();
  
  public void start();
  
}