package rice.proxy;

import java.io.*;
import java.net.*;
import java.security.*;

public class HttpFetcher {
  
  protected URL url;
  protected OutputStream out;
  
  public HttpFetcher(URL url, OutputStream out) {
    this.url = url;
    this.out = new BufferedOutputStream(out); 
  }
  
  public byte[] fetch() throws IOException {
    return readHttpURL((HttpURLConnection) url.openConnection());
  }
  
  public final byte[] readHttpURL(HttpURLConnection url) throws IOException {
    url.connect();
    
    MessageDigest md = null;
    
    try {
      md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("Hash algorithm not found.");
    }
    
    byte[] tmp = new byte[32000];
    
    try {
      int i = 0;
      InputStream in = new BufferedInputStream(url.getInputStream());
      
      if (url.getResponseCode() != HttpURLConnection.HTTP_OK) {
        throw new IOException("Unexpected HTTP response code: " + url.getResponseCode());
      } else {  
        while (i >= 0) {
          i = in.read(tmp);
          
          if (i > 0) {
            out.write(tmp, 0, i);
            md.update(tmp, 0, i);
          }
        }
      }
    } finally {
      url.disconnect();
      close();
    }
      
    return md.digest();
  }
  
  protected void close() throws IOException {
    out.close();
  }
  
  public static void main(String[] args) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    HttpFetcher f = new HttpFetcher(new URL("http://www.epostmail.org/code/epost-2.1.3.jar"), baos);
    
    byte[] bytes = f.fetch();

    System.out.println("HASH: " + rice.post.security.SecurityUtils.toHex(bytes));
  }
}

