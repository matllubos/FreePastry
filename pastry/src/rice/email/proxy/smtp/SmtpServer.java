package rice.email.proxy.smtp;

public interface SmtpServer {
  
  public int getPort();
  
  public void start();
  public int getConnections();
  public int getSuccess();
  public int getFail();
  
  public void incrementSuccess();
  public void incrementFail();
}