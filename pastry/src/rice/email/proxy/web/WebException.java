package rice.email.proxy.web;

public class WebException extends Exception {
  
  protected String status;
  protected String message;
  
  public WebException(String status, String message) {
    this.status = status;
    this.message = message;
  }
  
  public String getStatus() {
    return status;
  } 
  
  public String getMessage() {
    return message;
  }
}