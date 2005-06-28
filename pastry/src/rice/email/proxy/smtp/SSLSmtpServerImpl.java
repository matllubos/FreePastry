package rice.email.proxy.smtp;

import rice.post.*;

import rice.email.*;
import rice.email.proxy.smtp.manager.*;
import rice.email.proxy.util.*;
import rice.email.proxy.user.*;
import rice.email.proxy.smtp.commands.*;
import rice.environment.Environment;

import java.io.*;
import java.net.*;
import java.security.*;
import javax.security.cert.*;
import javax.net.ssl.*;

public class SSLSmtpServerImpl extends SmtpServerImpl {
  
  protected String keystore;
  
  protected String password;
  
  public SSLSmtpServerImpl(int port, EmailService email, boolean gateway, PostEntityAddress address, boolean acceptNonLocal, boolean authenticate, UserManager userManager, String keystore, String password, String server, Environment env) throws Exception {
    super(port, email, gateway, address, acceptNonLocal, authenticate, userManager, server, env);    
    this.keystore = keystore;
    this.password = password;
    initializeSSL();
  }
  
  public void initialize() {
  }
  
  public void initializeSSL() throws IOException {
    try {
      //Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
      SSLContext con = SSLContext.getInstance("TLS");
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
