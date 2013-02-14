/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.proxy;

import java.io.*;
import java.net.*;
import java.security.*;

import rice.p2p.util.*;

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

    System.out.println("HASH: " + MathUtils.toHex(bytes));
  }
}

