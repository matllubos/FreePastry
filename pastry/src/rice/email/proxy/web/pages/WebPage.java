package rice.email.proxy.web.pages;

import java.io.*;
import rice.email.proxy.web.*;

public abstract class WebPage {
  
  public abstract boolean authenticationRequired();
	public abstract void execute(WebConnection conn, WebState state) throws WebException, IOException;
  
  public void writeHeader(WebConnection conn) {
    conn.print("<HTML><HEAD><TITLE>ePOST Webmail</TITLE></HEAD><script>function setURL(indx) {top.location=indx;}</script><BODY>");
  }
  
  public void writeFooter(WebConnection conn) throws IOException {
    conn.print("</BODY></HTML>");
    conn.send();
  }
}
