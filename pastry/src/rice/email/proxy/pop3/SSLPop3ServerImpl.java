/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.email.proxy.pop3;

import rice.email.*;
import rice.email.proxy.pop3.commands.*;
import rice.email.proxy.user.*;
import rice.environment.Environment;

import java.io.*;

import java.net.*;
import java.security.*;
import javax.security.cert.*;
import javax.net.ssl.*;

public class SSLPop3ServerImpl extends Pop3ServerImpl {
  
  protected String keystore;
  
  protected String password;
  
  public SSLPop3ServerImpl(InetAddress localHost, int port, EmailService email, UserManager manager, boolean gateway, boolean acceptNonLocal, String keystore, String password, Environment env) throws IOException {
    super(localHost, port, email, manager, gateway, acceptNonLocal, env);
    this.keystore = keystore;
    this.password = password;
    initializeSSL();
  }
  
  public void initialize() {
  }
  
  public void initializeSSL() throws IOException {
    try {
      //Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
      SSLContext con =SSLContext.getInstance("TLS");
      SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
      
      // Change this to whatever the password is for the key
      char[] pass = password.toCharArray();
      
      // load the key
      KeyStore ks = KeyStore.getInstance("JKS");
      ks.load(new FileInputStream(keystore), null);
      
      KeyManagerFactory km = KeyManagerFactory.getInstance("SunX509");
      km.init(ks, pass);
      
      // Now get the key managers
      KeyManager[] keymanage = km.getKeyManagers();
      
      // Now get the Trust Manager stuff
      TrustManagerFactory tmFactory = TrustManagerFactory.getInstance("SunX509");
      tmFactory.init(ks);
      TrustManager[] tmArray = tmFactory.getTrustManagers();
      
      // Now intialize the keymanagers
      con.init(keymanage, tmArray, random);
      
      // finally we can create a socket factory
      SSLServerSocketFactory  sf=con.getServerSocketFactory();
      server = sf.createServerSocket(port);
      
      // We don't want the  client to authenticate themselves
      ((SSLServerSocket) server).setNeedClientAuth(false);
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(e.getMessage());
    } catch (KeyStoreException e) {
      throw new IOException(e.getMessage());
    } catch (NoSuchProviderException e) {
      throw new IOException(e.getMessage());
    } catch (UnrecoverableKeyException e) {
      throw new IOException(e.getMessage());
    } catch (java.security.cert.CertificateException e) {
      throw new IOException(e.getMessage());
    } catch (KeyManagementException e) {
      throw new IOException(e.getMessage());
    } 
  }
}