package rice.email.proxy.smtp;

import java.net.InetAddress;

import rice.environment.Environment;

public interface SmtpServer {
  
  public int getPort();
  
  public void start();
  public int getConnections();
  public int getSuccess();
  public int getFail();
  
  public void incrementSuccess();
  public void incrementFail();

  public Environment getEnvironment();
  
  public InetAddress getLocalHost();
}