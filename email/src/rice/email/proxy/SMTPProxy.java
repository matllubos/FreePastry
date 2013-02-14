package rice.email.proxy;

import java.net.BindException;
import java.net.InetAddress;

import rice.post.Post;

/**
 * This class defines an SMTP service that is layered on top of the POST system.  Since both
 * sending and receiving are actions in POST that must be associated with a specific user, 
 * this SMTP proxy requires password authentication from clients trying to send through it. 
 * 
 * @author Derek Ruths
 */
public class SMTPProxy {

  /**
   * This constructor creates an SMTP service running on a specified address/port pair.
   * 
   * @param address is the local address to which the service will bind.
   * @param port is the local port to which the service will bind.
   * 
   * @throws BindException if the service could not bind to the address/port pair.
   */
  public SMTPProxy(InetAddress address, int port) throws BindException {
    return;
  }

  // methods
  /**
   * This method binds this SMTP proxy to a post account.  In order to send through this 
   * account, the smtp client must provide the specified username and password.
   * 
   * @param post is the Post this SMTP proxy should begin servicing.
   * @param smtpUsername is the username that a SMTP client must provide to access the
   * client.
   * @param smtpPassword is the password that a SMTP client must provide to access the
   * client.
   */
  public void attach(Post post, String smtpUsername, String smtpPassword) {
    return;
  }
}
