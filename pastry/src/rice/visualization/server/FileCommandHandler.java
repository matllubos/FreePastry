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
/*
 * Created on Aug 2, 2004
 */
package rice.visualization.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.util.DebugCommandHandler;
import rice.post.proxy.PostProxy;

/**
 * @author Jeff Hoye
 */
public class FileCommandHandler implements DebugCommandHandler {

  public static final String GET_PROPS_COMMAND = "pastry.getproperties";
  public static final String GET_MANIFEST_COMMAND = "pastry.getmanifest";

  Environment environment;
  
  /**
   * Lazilly constructed.
   */
  protected Logger logger;
  /**
   * @param environment
   */
  public FileCommandHandler(Environment env) {
    this.environment = env;
  }

  public String handleDebugCommand(String command) {
    
    if (command.startsWith(GET_PROPS_COMMAND)) {
      return handlePropsCmd(command);
    }
    if (command.startsWith(GET_MANIFEST_COMMAND)) {
      return handleManifestCmd(command);
    }
    
    return null;
  }

  protected String handleManifestCmd(String command) {
    final String filename = System.getProperty("java.class.path");

    try {  
      File f = new File(filename);
      if (!f.exists()) {
        return "Could not find file of name \""+filename+"\"";
      }
      JarFile jf = new JarFile(f);
      Manifest m = jf.getManifest();
      Map map = m.getMainAttributes();
      Iterator i = map.keySet().iterator();
      String ret = "Jar "+filename+":\n";
      while (i.hasNext()) {
        Object key = i.next();
        Object val = map.get(key);
        ret+=key+" = " +val+"\n";
      }
      ret+="FileSize:"+f.length()+" Last Modified:"+new Date(f.lastModified());
      return ret;
    } catch (Throwable t) {     
      if (logger == null) logger = environment.getLogManager().getLogger(FileCommandHandler.class, null); 
      if (logger.level <= Logger.WARNING) logger.logException(
          "ERROR: opening \""+filename+"\":"+t.getMessage(), t);
      return "ERROR: opening \""+filename+"\":"+t.getMessage();
    }      
  }
  
  protected String handlePropsCmd(String command) {
    final String filename = PostProxy.PROXY_PARAMETERS_NAME + ".params";

    try {  
      File f = new File(filename);
      if (!f.exists()) {
        return "Could not find file of name \""+filename+"\"";
      }
      FileInputStream fis = new FileInputStream(f);
      InputStreamReader isr = new InputStreamReader(fis);
      BufferedReader br = new BufferedReader(isr);
      String ret = "";
      String s = br.readLine();
      while (s != null) {
        ret+=s+"\n";
        s = br.readLine();
      } 
      return ret;
    } catch (Throwable t) {
      if (logger == null) logger = environment.getLogManager().getLogger(FileCommandHandler.class, null); 
      if (logger.level <= Logger.WARNING) logger.logException(
          "ERROR: opening \""+filename+"\":"+t.getMessage(),
          t);
      return "ERROR: opening \""+filename+"\":"+t.getMessage();
    }
  }

  private String getArg(String command, int l) {
    int i = command.indexOf(" ");
    if (i >= l) {
      String sub = command.substring(i,command.length());
      // strip off leading space
      while (sub.startsWith(" ")) {
        sub = sub.substring(1);
      }
      
      if (sub.length() >= 1)
        return sub;
    }    
    return null;
  }
}
