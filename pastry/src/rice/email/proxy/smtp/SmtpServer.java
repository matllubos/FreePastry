package rice.email.proxy.smtp;

public interface SmtpServer {
  
  public int getPort();
  
  public void start();
}