/*
 * Created on Mar 11, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.visualization.client;

import java.io.Serializable;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class UpdateJarResponse implements Serializable {

  Exception e;
  public static final int OK=0;
  public static final int EXCEPTION=1;
  public static final int FILE_COPY_NOT_ALLOWED=2;
  public static final int NEW_EXEC_NOT_ALLOWED=3;
  
  int response = 0;

  
  public UpdateJarResponse() {
    this(OK);
  }

  public UpdateJarResponse(int reason) {
    this(null,reason);
  }

  public UpdateJarResponse(Exception e) {
    this(e,OK);
    if (e != null) {
      response = EXCEPTION;
    }
  }
  
  public UpdateJarResponse(Exception e, int response) {
    this.e = e;
    this.response = response;
  }
  
  public boolean success() {
    return e == null;
  }

  public Exception getException() {
    return e;
  }
  
  public int getResponse() {
    return response;
  }
}
