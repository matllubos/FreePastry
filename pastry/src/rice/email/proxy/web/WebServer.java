package rice.email.proxy.web;

/**
 * An interface to the web server
 */
public interface WebServer {
  
  public int getPort();
  
  public void start();
  
}