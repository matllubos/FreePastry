package rice.email.proxy;

import java.net.BindException;
import java.net.InetAddress;

import rice.email.EmailService;

/**
 * This class defines an SMTP service that is layered on top of the
 * POST system. It accepts SMTP connections on the given port, parses
 * the messages it receives, and sends them using the provided
 * EmailService.
 *
 * Note that most of the actual functionality is in SMTPDaemon.
 * @author Dave Price
 */
public class SMTPProxy {

    InetAddress address;
    int port;
    SMTPDaemon daemon;

  /**
   * This constructor creates an SMTP service running on a specified address/port pair.
   * 
   * @param address is the local address to which the service will bind.
   * @param port is the local port to which the service will bind.
   * 
   * @throws BindException if the service could not bind to the address/port pair.
   */
  public SMTPProxy(InetAddress address, int port) throws BindException {
      this.address = address;
      this.port = port;
  }

  // methods
 /**
   * This method binds this SMTP proxy to a post account.  In order to send through this 
   * account, the smtp client must provide the specified username and password.
   * 
   * @param service is the EmailService this SMTP proxy should begin servicing.
   */
  public void attach(EmailService service) {

      // Launch a new SMTPDaemon at the given address and port.
      // This daemon will be responsible for actually receiving SMTP requests
      // and delivering messages.
      daemon = new SMTPDaemon(address, port, service);
      Thread t = new Thread(daemon);
      t.setDaemon(true);
      t.start();
  }
}

