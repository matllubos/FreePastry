package rice.visualization.client;

import java.io.Serializable;

public class DebugCommandResponse implements Serializable {

  protected String request;
  protected String response;
  protected int responseCode;
  
  public DebugCommandResponse(String request, String response, int responseCode) {
    this.request = request;
    this.response = response;
    this.responseCode = responseCode;
  }
  
  public String getResponse() {
    return response;
  }
  
  public String getRequest() {
    return request;
  }
  
  public int getResponseCode() {
    return responseCode;
  }
}
