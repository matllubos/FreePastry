package rice.proxy;

import java.io.*;
import java.net.*;

public class HttpFetcher {
  
  protected URL url;
  protected OutputStream out;
  
  public HttpFetcher(URL url, OutputStream out) {
    this.url = url;
    this.out = new BufferedOutputStream(out); 
  }
  
  public void fetch() throws IOException {
    readHttpURL((HttpURLConnection) url.openConnection());
  }
  
  public final void readHttpURL(HttpURLConnection url) throws IOException {
    url.connect();
    
    try {
      int i = 0;
      InputStream in = new BufferedInputStream(url.getInputStream());
      
      if (url.getResponseCode() != HttpURLConnection.HTTP_OK) 
        throw new IOException("Unexpected HTTP response code: " + url.getResponseCode());
      else 
        while (i >= 0) {
          i = in.read();
          if (i >= 0) out.write(i);
        }
    } finally {
      url.disconnect();
      close();
    }
  }
  
  protected void close() throws IOException {
    out.close();
  }
  
  public static void main(String[] args) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    HttpFetcher f = new HttpFetcher(new URL("http://www.cnn.com"), baos);
    
    f.fetch();
    
    System.out.println("MONKEYS!" + new String(baos.toByteArray()));
  }
}

