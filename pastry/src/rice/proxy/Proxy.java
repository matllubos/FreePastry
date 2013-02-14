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
import java.util.*;

import java.awt.*;
import javax.swing.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.p2p.util.*;

/**
 * This class represents a generic Java process launching program (wrapper process) which reads in
 * preferences from a preferences file and then invokes another JVM using those
 * prefs.  If the launched JVM dies, this process can be configured to restart
 * the JVM any number of times before giving up.  This process can also be configured
 * to launch the second JVM with a specified memory allocation, etc...
 *
 * To use with your own configuration (ePost is the default), create a main() method as follows:
 * 
 *  public static final String[] MY_PARAM_FILES = {"freepastry","myapp"};
 *  public static final String MY_USER_PARAM = "proxy";
 *  
 *  public static void main(String[] args) throws IOException, InterruptedException {    
 *    Proxy proxy = new Proxy(new Environment(MY_PARAM_FILES,MY_USER_PARAM),"My Application");
 *    proxy.run();
 *  }
 *  
 *  parameters: (TODO: fill these in)
 *    proxy_doc_image: a png to show up in the macos doc
 *  
 * @author Alan Mislove
 */
public class Proxy {
  
  public static String[] DEFAULT_PARAM_FILES = {"freepastry","epost"};
  
  public static String PROXY_PARAMETERS_NAME = "proxy";
  
  protected Process process;
  
  protected LivenessMonitor lm;
    
  protected Environment environment;
  
  protected Logger logger;

  protected String appString;
  
  public Proxy(Environment env) {
    this(env,"ePOST");
  }

  /**
   * The name of the enclosed application.
   * 
   * @param env
   * @param appString
   */
  public Proxy(Environment env, String appString) {
    environment = env;
    this.appString = appString;
    logger = environment.getLogManager().getLogger(Proxy.class, null);
  }
  
  public void run() throws IOException, InterruptedException {
    Parameters parameters = environment.getParameters();
    int count = 0;
    
    if (parameters.getBoolean("proxy_automatic_update_enable")) {
      AutomaticUpdater au = new AutomaticUpdater(parameters);
      au.start();
    }
    
    if (parameters.getBoolean("proxy_sleep_monitor_enable")) {
      SleepMonitor sm = new SleepMonitor(parameters);
      sm.start();
    }
    
    while (true) {
      String command = buildJavaCommand(parameters);
      String[] environment = buildJavaEnvironment(parameters);

      if (logger.level <= Logger.INFO) logger.log("[Loader       ]: Launching command " + command);
      
      process = (environment.length > 0 ? Runtime.getRuntime().exec(command, environment) : Runtime.getRuntime().exec(command));
      lm = new LivenessMonitor(parameters, process);

      if (parameters.getBoolean("proxy_liveness_monitor_enable"))
        lm.start();
      
      Printer error = new Printer(process.getErrorStream(), "[Error Stream ]: ");
      
      // have to do the byte cast below because java 5 only returns low 8 bits
      // probably we should change all our status codes to be 0..255 some day
      int exit = (byte)process.waitFor();    
      lm.die();
      
      // re-initialize parameters for debugging purposes
      parameters = new SimpleParameters(DEFAULT_PARAM_FILES, PROXY_PARAMETERS_NAME);
      
      if (exit != -1) { 
        if (logger.level <= Logger.INFO) logger.log("[Loader       ]: Child process exited with value " + exit + " - restarting client");
        count++;
        
        if (count < parameters.getInt("restart_max")) {
          if (logger.level <= Logger.INFO) logger.log("[Loader       ]: Waiting for " + parameters.getInt("restart_delay") + " milliseconds");   
          Thread.sleep(parameters.getInt("restart_delay"));
        } else {
          if (logger.level <= Logger.INFO) logger.log("[Loader       ]: Child process exited with value " + exit + " - exiting loader");   
          break;
        }
      } else {
        if (logger.level <= Logger.INFO) logger.log("[Loader       ]: Child process exited with value " + exit + " - exiting loader");   
        break;
      }
    }
    
    System.exit(0);
  }

  protected String[] buildJavaEnvironment(Parameters parameters) {
    HashSet<String> set = new HashSet<String>();
    
    if (parameters.getBoolean("java_profiling_enable") ||
        parameters.getBoolean("java_thread_debugger_enable"))  {
      if (System.getProperty("os.name").toLowerCase().indexOf("windows") < 0) {
        set.add("LD_LIBRARY_PATH=" + parameters.getString("java_profiling_native_library_directory"));
      } 
    }
    
    return (String[]) set.toArray(new String[0]);
  }   
  
  protected String buildJavaCommand(Parameters parameters) {
    StringBuffer result = new StringBuffer();
    
    if (! ("".equals(parameters.getString("java_wrapper_command")))) {
      result.append(parameters.getString("java_wrapper_command"));
      result.append(" \"");
    }
    
    if ((parameters.getString("java_home") != null && !("".equals(parameters.getString("java_home"))))
        && !(new File(parameters.getString("java_home"))).exists()) {
    parameters.remove("java_home");
    try {
      parameters.store();
    } catch (IOException e) {
      if (logger.level <= Logger.FINE)
        logger.logException(
            "Got error storing java_home parameter -- is your proxy.params file writable?", e);
    }
    }  

    if (((parameters.getString("java_home") == null) ||
         (parameters.getString("java_home").equals(""))) && 
        (System.getProperty("os.name").toLowerCase().indexOf("windows") < 0)) {
      parameters.setString("java_home", System.getProperty("java.home"));
      try {
        parameters.store();
      } catch (IOException e) {
        if (logger.level <= Logger.FINE) 
            logger.logException("Got error storing java_home parameter -- is your proxy.params file writable?",e);
      }
    }
    
    if ((parameters.getString("java_home") != null) && (! ("".equals(parameters.getString("java_home"))))) {
      result.append(parameters.getString("java_home"));
      result.append(System.getProperty("file.separator"));
      result.append("bin");
      result.append(System.getProperty("file.separator"));
    }
    
    result.append(parameters.getString("java_command"));
    result.append(" -Xmx");
    result.append(parameters.getString("java_maximum_memory"));
    result.append(" -Xss");
    result.append(parameters.getString("java_stack_size"));
    if (System.getProperty("RECOVER") != null)
      result.append(" -DRECOVER=\""+System.getProperty("RECOVER")+"\"");
    
    if (parameters.getBoolean("java_memory_free_enable")) {
      result.append(" -Xmaxf" + parameters.getDouble("java_memory_free_maximum"));
    }
    
    if (parameters.getBoolean("java_use_server_vm")) {
      result.append(" -server");
    }
    
    if (parameters.getBoolean("java_interpreted_mode")) {
      result.append(" -Xint");
    }
    
    if (parameters.getBoolean("java_prefer_select") ||
        (parameters.getBoolean("java_prefer_select_automatic_osx") && 
         System.getProperty("os.name").toLowerCase().indexOf("mac os x") >= 0)) {
//      Moved from the FAQ to the source:
//      For the curious, using the <tt>java.nio</tt> package on dual
//      G5 machines with Java 1.4.2 is known to cause kernel panics.  However,
//      by forcing the JVM to use the <tt>select()</tt> system call instead
//      of the <tt>kqueue</tt> call, the panics can be avoided.  For more information,
//      click <a href="http://sourceforge.net/forum/message.php?msg_id=2837986">here</a>.
      result.append(" -Djava.nio.preferSelect=true");
    }
    
    if (System.getProperty("os.name").toLowerCase().indexOf("mac os x") >= 0) {
      if (parameters.contains("proxy_doc_image")) {
        result.append(" -Xdock:name="+appString+" -Xdock:icon="+parameters.getString("proxy_doc_image"));
      } else {
        result.append(" -Xdock:name="+appString);        
      }
    }
    
    if (parameters.getBoolean("java_debug_enable")) {
      result.append(" -Xdebug -Djava.compiler=NONE -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=");
      result.append(parameters.getString("java_debug_port"));
    }
    
    if (parameters.getBoolean("java_hprof_enable")) {
      result.append(" -Xrunhprof");
    }
    
    if (parameters.getString("java_other_options") != null) {
      result.append(" " + parameters.getString("java_other_options"));
    }
    
    if (parameters.getBoolean("java_profiling_enable")) {
      if (System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0) {
        result.append(" -Djava.library.path=");
        result.append(parameters.getString("java_profiling_native_library_directory"));
      }
      
      result.append(" -Xrunpri");
      if (! parameters.getBoolean("java_profiling_memory_enable"))
          result.append(":dmp=1");
          
      result.append(" -Xbootclasspath/a:");
      result.append(parameters.getString("java_profiling_library_directory"));
      result.append(System.getProperty("file.separator"));
      result.append("oibcp.jar -cp ");
      result.append(parameters.getString("java_profiling_library_directory"));
      result.append(System.getProperty("file.separator"));
      result.append("optit.jar");
      result.append(System.getProperty("path.separator"));
      
      DynamicClasspath dc = new DynamicClasspath(new File("."), parameters.getStringArray("java_classpath"));
      result.append(dc.getClasspath());
      
      result.append(" intuitive.audit.Audit -port ");
      result.append(parameters.getString("java_profiling_port"));
    } else if (parameters.getBoolean("java_thread_debugger_enable"))  {
      result.append(" -Xruntdi:port=");
      result.append(parameters.getInt("java_thread_debugger_port"));
      result.append(",analyzer=t");
      
      result.append("-Xint -Xbootclasspath/a:");
      result.append(parameters.getString("java_thread_debugger_library_directory"));
      result.append(System.getProperty("file.separator"));
      result.append("oibcp.jar -cp ");
      result.append(parameters.getString("java_thread_debugger_library_directory"));
      result.append(System.getProperty("file.separator"));
      result.append("optit.jar");
      result.append(System.getProperty("path.separator"));

      DynamicClasspath dc = new DynamicClasspath(new File("."), parameters.getStringArray("java_classpath"));
      result.append(dc.getClasspath());
    } else {    
      result.append(" -cp ");
        
      DynamicClasspath dc = new DynamicClasspath(new File("."), parameters.getStringArray("java_classpath"));
      result.append(dc.getClasspath());
    }
    
    result.append(" ");
    result.append(parameters.getString("java_main_class"));
    result.append(" ");
    result.append(parameters.getString("java_main_class_parameters"));
    
    if (! ("".equals(parameters.getString("java_wrapper_command")))) {
      result.append(" \"");
    }
    
    return result.toString();
  }
  
  public void restart() {
    if (process != null) 
      process.destroy();
    
    if (lm != null)
      lm.die();
  }
  
  public boolean verifyJar(String filename, byte[] hash, String md5) {
   /* String[] cp = new DynamicClasspath().getClasspath().split(System.getProperty("path.separator"));
    JarFile newJar = new JarFile(filename);
    int i=0;
    
    while ((oldJar == null) && (i < cp.length)) 
      if (! cp[i].equals(filename))
        JarFile oldJar = new JarFile(cp[i]);
      
    Certificate cert = oldJar */
    
    return MathUtils.toHex(hash).trim().equalsIgnoreCase(md5.trim());
  }
  
  public static void main(String[] args) throws IOException, InterruptedException {
    Proxy proxy = new Proxy(new Environment(DEFAULT_PARAM_FILES,PROXY_PARAMETERS_NAME));
    proxy.run();
  }
  
  private class Printer extends Thread {
    
    protected BufferedReader reader;
    protected String prefix;
    
    public Printer(InputStream input, String prefix) {
      super("Printer Thread");
      this.reader = new BufferedReader(new InputStreamReader(input));
      this.prefix = prefix;
      
      start();
    }
    
    public void run() {
      try {
        while (true) {
          String line = reader.readLine();

          if (line != null) {
            if (logger.level <= Logger.INFO) logger.log(prefix + line);
          } else {
            break;
          }
        }
      } catch (IOException e) {
        if (logger.level <= Logger.WARNING) logger.logException("",e);
      }
    }
  }
  
  private class LivenessMonitor extends Thread {
   
    protected Process process;
    
    protected int sleep;
    
    protected int timeout;
    
    protected boolean alive;
    
    protected boolean answered;
    
    public LivenessMonitor(Parameters parameters, Process process) {
      super("LivenessMonitor");
      this.alive = true;
      this.process = process;
      this.sleep = parameters.getInt("proxy_liveness_monitor_sleep");
      this.timeout = parameters.getInt("proxy_liveness_monitor_timeout");
    }
    
    public void run() {
      try {
        // sleep to let JVM boot up
        Thread.sleep(2*timeout);
        
        while (alive) {
          this.answered = false;
          LivenessMonitorTest test = new LivenessMonitorTest(this, process);
          test.start();
          
          long start = environment.getTimeSource().currentTimeMillis();
          
          Thread.sleep(timeout);
          
          if (! answered) {
            System.err.println("SERIOUS ERROR: Process did not respond to liveness check - started at " + start + " now " + environment.getTimeSource().currentTimeMillis() + " - killing process");
            process.destroy();
            die();
          }
          
          Thread.sleep(sleep);
        }
      } catch (InterruptedException e) {
        die();
      }
    }
    
    public void die() {
      this.alive = false;
    }
    
    public void answered() {
      answered = true;
    }
  }
   
  protected class LivenessMonitorTest extends Thread {

    protected Process process;
    
    protected LivenessMonitor monitor;
    
    public LivenessMonitorTest(LivenessMonitor monitor, Process process) {
      super("LivenessMonitorTest");
      this.monitor = monitor;
      this.process = process;
    }
    
    public void run() {
      try {
        int i = 27;
        
        if (logger.level <= Logger.FINEST) logger.log("writing "+i+" to output stream");
        process.getOutputStream().write(i);
        process.getOutputStream().flush();
        int j = 0;
        
        while ((j != i) && (j >= 0)) { 
          j = process.getInputStream().read();
          if (logger.level <= Logger.FINEST) logger.log("read "+j+" from input stream");
        }
        
        if (j >= 0)
          monitor.answered();
      } catch (IOException e) {
        if (logger.level <= Logger.SEVERE) logger.logException( 
            "ERROR: Got IOException while checking liveness!" + e + " This is usually an unrecoverable JVM crash - we're going to exit now.",
            e);
        System.exit(-1);
      } catch (NullPointerException e) {
        if (logger.level <= Logger.SEVERE) logger.logException( "Liveness test ended in NullPointerException " , e);
      }
    }
  }
  
  protected class AutomaticUpdater extends Thread {
       
    protected int interval;
    
    protected String root;
    protected String url;
    
    protected Parameters parameters;
    
    public AutomaticUpdater(Parameters parameters) {
      super("AutomaticUpdater");
      this.interval = parameters.getInt("proxy_automatic_update_interval");
      this.root = parameters.getString("proxy_automatic_update_root");
      this.url = root + parameters.getString("proxy_automatic_update_latest_filename");
      this.parameters = parameters;
    }
    
    public void run() {
      while (true) {
        try {
          Thread.sleep(environment.getRandomSource().nextInt(interval));
          
          try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            HttpFetcher hf = new HttpFetcher(new URL(url), baos);
            
            hf.fetch();
            
            String filename = new String(baos.toByteArray()).trim();
            
            if (filename.indexOf("\t") > 0)
              filename = filename.substring(0, filename.indexOf("\t"));
            
            if (! new File(".", filename).exists()) {              
              if (parameters.getBoolean("proxy_show_dialog") && parameters.getBoolean("proxy_automatic_update_ask_user")) {
                String message = "A new version of the "+appString+" software has been detected.\n\n" +
                "Would you like to automatically upgrade to '" + filename + "' and restart your proxy?";
                int i = JOptionPane.showOptionDialog(null, message, "Updated Software Detected", 
                                                     0, JOptionPane.INFORMATION_MESSAGE, null, 
                                                     new Object[] {"Disable Automatic Updating", "Later", "Yes"}, "Yes");
                
                if (i == 0) {
                  String message2 = "Are your sure you wish to disable automatic updating?\n\n" +
                  "You can re-enable it by changing the field 'proxy_automatic_update_enable'\n" +
                  "in your proxy.params file.";
                  int j = JOptionPane.showConfirmDialog(null, message2, "Confirm Automatic Update Disable", 
                                                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                  
                  if (j == JOptionPane.YES_OPTION) {
                    parameters.setString("proxy_automatic_update_enable", "false");
                    parameters.store();
                    return;
                  }
                } else if (i == 2) {
                  ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
                  hf = new HttpFetcher(new URL(root + filename + ".md5sum"), baos1);
                  
                  hf.fetch();
                  
                  String md5 = new String(baos1.toByteArray()).trim();                  
                  
                  hf = new HttpFetcher(new URL(root + filename), new FileOutputStream(new File(".", filename)));
                  byte[] bytes = hf.fetch();
                  
                  if (verifyJar(filename, bytes, md5)) {
                    restart();
                  } else {
                    System.err.println("ERROR - Corrupted download detected on file " + filename + " - hash " + MathUtils.toHex(bytes) + " required " + md5);

                    JOptionPane.showMessageDialog(null, "It appears that your update download was corrupted - "+appString+" will try \n" + 
                                                        "again at the next update interval.\n\n" +
                                                  "Hash: " + MathUtils.toHex(bytes) + " Required: " + md5,
                                                  "Corrupted Download Detected", JOptionPane.WARNING_MESSAGE, null);
                    
                    new File(".", filename).delete();
                  }
                }
              } else {
                ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
                hf = new HttpFetcher(new URL(root + filename + ".md5sum"), baos1);
                
                hf.fetch();
                
                String md5 = new String(baos1.toByteArray()).trim();                
                
                hf = new HttpFetcher(new URL(root + filename), new FileOutputStream(new File(".", filename)));
                byte[] bytes = hf.fetch();
                
                if (verifyJar(filename, bytes, md5)) {
                  restart();   
                } else {
                  System.err.println("ERROR - Corrupted download detected on file " + filename + " - hash " + MathUtils.toHex(bytes) + " required " + md5);
                  new File(".", filename).delete();
                }
              }
            }
          } catch (Exception e) {
            if (logger.level <= Logger.SEVERE) logger.logException( 
                "ERROR: Got exception " + e + " while running automatic update - ignoring",
                e);
          }
        } catch (InterruptedException e) {
        }
      }
    }
  }
  
  protected class DynamicClasspath {
    
    protected File dir;
    
    protected File[] files;
    
    protected String[] other;
    
    public DynamicClasspath(File dir, String[] other) {
      this.dir = dir;
      this.other = other;
      
      this.files = dir.listFiles(new FilenameFilter() {
        public boolean accept(File dir, String filename) {
          return filename.endsWith(".jar");
        }
      });
      
      Arrays.sort(this.files, new Comparator<File>() {
        public int compare(File a, File b) {
          long am = ((File) a).lastModified();
          long bm = ((File) b).lastModified();
          
          if (am < bm) return 1;
          else if (am > bm) return -1;
          else return 0;
        }
        
        public boolean equals(Object o) {
          return false;
        }
      });
    }
    
    public String getClasspath() {
      String seperator = System.getProperty("path.separator");
      StringBuffer buf = new StringBuffer();
      
      for (int i=0; i<files.length; i++) {
        buf.append(files[i].getName());
        
        if ((i < files.length-1) || ((other != null) && (other.length > 0)))
          buf.append(seperator);
      }
      
      if (other != null) {
        for (int i=0; i<other.length; i++) {
          buf.append(other[i]);
          
          if (i < other.length-1)
            buf.append(seperator);
        }
      }
      
      return buf.toString();
    }
  }
  
  private class SleepMonitor extends Thread {
    
    protected int sleep;
    
    protected int timeout;
    
    protected long last;
        
    public SleepMonitor(Parameters parameters) {
      super("SleepMonitor");
      this.sleep = parameters.getInt("proxy_sleep_monitor_sleep");
      this.timeout = parameters.getInt("proxy_sleep_monitor_timeout");
    }
    
    public void run() {
      this.last = environment.getTimeSource().currentTimeMillis();
      
      while (true) {
        try {          
          Thread.sleep(sleep);
          
          if (environment.getTimeSource().currentTimeMillis() - last > timeout) {
            if (logger.level <= Logger.INFO) logger.log( "INFO: Sleep detected - " + (environment.getTimeSource().currentTimeMillis() - last) + " millis elapsed - restarting "+appString+"!");
            restart();
          }
          
          last = environment.getTimeSource().currentTimeMillis();
        } catch (InterruptedException e) {}
      }
    }
  }   
}
