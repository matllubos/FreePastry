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

package rice.proxy;

import java.io.*;
import java.net.*;
import java.util.*;

import java.awt.*;
import javax.swing.*;

/**
 * This class represents a generic Java process launching program which reads in
 * preferences from a preferences file and then invokes another JVM using those
 * prefs.  If the launched JVM dies, this process can be configured to restart
 * the JVM any number of times before giving up.  This process can also be configured
 * to launch the second JVM with a specified memory allocation, etc...
 *
 * @author Alan Mislove
 */
public class Proxy {
  
  public static String PROXY_PARAMETERS_NAME = "proxy";
  public static String[][] DEFAULT_PARAMETERS = new String[][] {{"java_home", System.getProperty("java.home")},
                                                                {"java_command", "java"},
                                                                {"java_wrapper_command", ""},
                                                                {"java_maximum_memory", "128M"},
                                                                {"java_memory_free_enable", "true"},
                                                                {"java_memory_free_maximum", "0.50"},
                                                                {"java_debug_enable", "false"},
                                                                {"java_debug_port", "8000"},
                                                                {"java_stack_size", "1024K"},
                                                                {"java_hprof_enable", "false"},
                                                                {"java_interpreted_mode", "false"},
                                                                {"java_profiling_enable", "false"},
                                                                {"java_profiling_port", "31000"},
                                                                {"java_profiling_memory_enable", "false"},
                                                                {"java_profiling_library_directory", "lib"},
                                                                {"java_profiling_native_library_directory", "."},
                                                                {"java_thread_debugger_enable", "false"},
                                                                {"java_thread_debugger_port", "31000"},
                                                                {"java_thread_debugger_library_directory", "lib"},
                                                                {"java_use_server_vm", "false"}, 
                                                                {"java_classpath", "pastry.jar"},
                                                                {"java_main_class", "rice.visualization.proxy.VisualizationEmailProxy"},
                                                                {"java_main_class_parameters", ""}, 
                                                                {"restart_return_value_min", "0"},
                                                                {"restart_return_value_max", "1000"},
                                                                {"restart_max", "5"},
                                                                {"restart_delay", "5000"},
                                                                {"proxy_liveness_monitor_enable", "true"},
                                                                {"proxy_liveness_monitor_sleep", "30000"}, 
                                                                {"proxy_liveness_monitor_timeout", "15000"},
                                                                {"proxy_automatic_update_enable", "true"},
                                                                {"proxy_automatic_update_root", "http://www.epostmail.org/code/"},
                                                                {"proxy_automatic_update_latest_filename", "latest.txt"},
                                                                {"proxy_automatic_update_interval", "86400000"}
  };
  
  protected Process process;
    
  public Proxy() {
  }
  
  public void run(String params) throws IOException, InterruptedException {
    Parameters parameters = initializeParameters(params);
    int count = 0;
    
    if (parameters.getBooleanParameter("proxy_automatic_update_enable")) {
      AutomaticUpdater au = new AutomaticUpdater(parameters);
      au.start();
    }
    
//    while (count < parameters.getIntParameter("restart_max")) {
    while (true) {
      String command = buildJavaCommand(parameters);
      String[] environment = buildJavaEnvironment(parameters);

      System.out.println("[Loader       ]: Launching command " + command);
      
      process = (environment.length > 0 ? Runtime.getRuntime().exec(command, environment) : Runtime.getRuntime().exec(command));
      LivenessMonitor lm = new LivenessMonitor(parameters, process);

      if (parameters.getBooleanParameter("proxy_liveness_monitor_enable"))
        lm.start();
      
      Printer error = new Printer(process.getErrorStream(), "[Error Stream ]: ");
      
      int exit = process.waitFor();    
      lm.die();
      
      if (exit != -1) { //((exit >= parameters.getIntParameter("restart_return_value_min")) && (exit <= parameters.getIntParameter("restart_return_value_max"))) {
        System.out.println("[Loader       ]: Child process exited with value " + exit + " - restarting client");
        count++;
        
        if (count < parameters.getIntParameter("restart_max")) {
          System.out.println("[Loader       ]: Waiting for " + parameters.getIntParameter("restart_delay") + " milliseconds");   
          Thread.sleep(parameters.getIntParameter("restart_delay"));
        }
      } else {
        System.out.println("[Loader       ]: Child process exited with value " + exit + " - exiting loader");   
        break;
      }
    }
    
    System.exit(0);
  }
  
  protected Parameters initializeParameters(String params) throws IOException {
    Parameters result = new Parameters(params); 
    
    for (int i=0; i<DEFAULT_PARAMETERS.length; i++)
      result.registerStringParameter(DEFAULT_PARAMETERS[i][0], DEFAULT_PARAMETERS[i][1]);
    
    return result;
  }

  protected String[] buildJavaEnvironment(Parameters parameters) {
    HashSet set = new HashSet();
    
    if (parameters.getBooleanParameter("java_profiling_enable") ||
        parameters.getBooleanParameter("java_thread_debugger_enable"))  {
      if (System.getProperty("os.name").toLowerCase().indexOf("windows") < 0) {
        set.add("LD_LIBRARY_PATH=" + parameters.getStringParameter("java_profiling_native_library_directory"));
      } 
    }
    
    return (String[]) set.toArray(new String[0]);
  }   
  
  protected String buildJavaCommand(Parameters parameters) {
    StringBuffer result = new StringBuffer();
    
    if (! ("".equals(parameters.getStringParameter("java_wrapper_command")))) {
      result.append(parameters.getStringParameter("java_wrapper_command"));
      result.append(" \"");
    }
    
    if (! ("".equals(parameters.getStringParameter("java_home")))) {
      result.append(parameters.getStringParameter("java_home"));
      result.append(System.getProperty("file.separator"));
      result.append("bin");
      result.append(System.getProperty("file.separator"));
    }
    
    result.append(parameters.getStringParameter("java_command"));
    result.append(" -Xmx");
    result.append(parameters.getStringParameter("java_maximum_memory"));
    result.append(" -Xss");
    result.append(parameters.getStringParameter("java_stack_size"));
    
    if (parameters.getBooleanParameter("java_memory_free_enable")) {
      result.append(" -Xmaxf" + parameters.getDoubleParameter("java_memory_free_maximum"));
    }
    
    if (parameters.getBooleanParameter("java_use_server_vm")) {
      result.append(" -server");
    }
    
    if (parameters.getBooleanParameter("java_interpreted_mode")) {
      result.append(" -Xint");
    }
    
    if (parameters.getBooleanParameter("java_debug_enable")) {
      result.append(" -Xdebug -Djava.compiler=NONE -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=");
      result.append(parameters.getStringParameter("java_debug_port"));
    }
    
    if (parameters.getBooleanParameter("java_hprof_enable")) {
      result.append(" -Xrunhprof");
    }
    
    if (parameters.getStringParameter("java_other_options") != null) {
      result.append(" " + parameters.getStringParameter("java_other_options"));
    }
    
    if (parameters.getBooleanParameter("java_profiling_enable")) {
      if (System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0) {
        result.append(" -Djava.library.path=");
        result.append(parameters.getStringParameter("java_profiling_native_library_directory"));
      }
      
      result.append(" -Xrunpri");
      if (! parameters.getBooleanParameter("java_profiling_memory_enable"))
          result.append(":dmp=1");
          
      result.append(" -Xbootclasspath/a:");
      result.append(parameters.getStringParameter("java_profiling_library_directory"));
      result.append(System.getProperty("file.separator"));
      result.append("oibcp.jar -cp ");
      result.append(parameters.getStringParameter("java_profiling_library_directory"));
      result.append(System.getProperty("file.separator"));
      result.append("optit.jar");
      
      String[] classpath = parameters.getStringArrayParameter("java_classpath");
      for (int i=0; i<classpath.length; i++) {
        result.append(System.getProperty("path.separator"));
        result.append(classpath[i]); 
      }
      
      result.append(" intuitive.audit.Audit -port ");
      result.append(parameters.getStringParameter("java_profiling_port"));
    } else if (parameters.getBooleanParameter("java_thread_debugger_enable"))  {
      result.append(" -Xruntdi:port=");
      result.append(parameters.getIntParameter("java_thread_debugger_port"));
      result.append(",analyzer=t");
      
      result.append("-Xint -Xbootclasspath/a:");
      result.append(parameters.getStringParameter("java_thread_debugger_library_directory"));
      result.append(System.getProperty("file.separator"));
      result.append("oibcp.jar -cp ");
      result.append(parameters.getStringParameter("java_thread_debugger_library_directory"));
      result.append(System.getProperty("file.separator"));
      result.append("optit.jar");
      
      String[] classpath = parameters.getStringArrayParameter("java_classpath");
      for (int i=0; i<classpath.length; i++) {
        result.append(System.getProperty("path.separator"));
        result.append(classpath[i]); 
      }
    } else {    
      String[] classpath = parameters.getStringArrayParameter("java_classpath");
      if (classpath.length > 0) {
        result.append(" -cp ");
        
        for (int i=0; i<classpath.length; i++) {
          result.append(classpath[i]); 
          if (i < classpath.length-1)
            result.append(System.getProperty("path.separator"));
        }
      }
    }
    
    result.append(" ");
    result.append(parameters.getStringParameter("java_main_class"));
    result.append(" ");
    result.append(parameters.getStringParameter("java_main_class_parameters"));
    
    if (! ("".equals(parameters.getStringParameter("java_wrapper_command")))) {
      result.append(" \"");
    }
    
    return result.toString();
  }
  
  public void restart() {
    if (process != null)
      process.destroy();
  }
  
  public static void main(String[] args) throws IOException, InterruptedException {
    Proxy proxy = new Proxy();
    proxy.run(PROXY_PARAMETERS_NAME);
  }
  
  private class Printer extends Thread {
    
    protected BufferedReader reader;
    protected String prefix;
    
    public Printer(InputStream input, String prefix) {
      this.reader = new BufferedReader(new InputStreamReader(input));
      this.prefix = prefix;
      
      start();
    }
    
    public void run() {
      try {
        boolean done = false;
       
        while (! done) {
          String line = reader.readLine();

          if (line != null) 
            System.out.println(prefix + line);
          else
            done = true;
        }
      } catch (IOException e) {
        System.err.println(e);
        e.printStackTrace();
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
      this.alive = true;
      this.process = process;
      this.sleep = parameters.getIntParameter("proxy_liveness_monitor_sleep");
      this.timeout = parameters.getIntParameter("proxy_liveness_monitor_timeout");
    }
    
    public void run() {
      try {
        while (alive) {
          this.answered = false;
          LivenessMonitorTest test = new LivenessMonitorTest(this, process);
          test.start();
          
          Thread.sleep(timeout);
          if (! answered) {
            System.err.println("SERIOUS ERROR: Process did not respond to liveness check - KILLING!");
            process.destroy();
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
      this.monitor = monitor;
      this.process = process;
    }
    
    public void run() {
      try {
        int i = 27;
        
        process.getOutputStream().write(i);
        process.getOutputStream().flush();
        int j = 0;
        
        while ((j != i) && (j >= 0)) 
          j = process.getInputStream().read();
        
        if (j >= 0)
          monitor.answered();
      } catch (IOException e) {
        System.err.println("ERROR: Got IOException while checking liveness!" + e);
        e.printStackTrace();
      }
    }
  }
  
  protected class AutomaticUpdater extends Thread {
   
    protected Random rng = new Random();
    
    protected int interval;
    
    protected String root;
    protected String url;
    
    protected Parameters parameters;
    
    public AutomaticUpdater(Parameters parameters) {
      this.interval = parameters.getIntParameter("proxy_automatic_update_interval");
      this.root = parameters.getStringParameter("proxy_automatic_update_root");
      this.url = root + parameters.getStringParameter("proxy_automatic_update_latest_filename");
      this.parameters = parameters;
    }
    
    public void run() {
      while (true) {
        try {
          Thread.sleep(rng.nextInt(interval));
          
          try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            HttpFetcher hf = new HttpFetcher(new URL(url), baos);
            
            hf.fetch();
            
            String filename = new String(baos.toByteArray()).trim();
            
            if (parameters.getStringParameter("java_classpath").indexOf(filename) < 0) {
              if (parameters.getBooleanParameter("proxy_show_dialog")) {
                String message = "A new version of the ePOST software has been detected.\n\n" +
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
                    parameters.setStringParameter("proxy_automatic_update_enable", "false");
                    parameters.writeFile();
                    return;
                  }
                } else if (i == 2) {
                  hf = new HttpFetcher(new URL(root + filename), new FileOutputStream(new File(".", filename)));
                  hf.fetch();
                  
                  parameters.setStringParameter("java_classpath", filename + System.getProperty("path.separator") + parameters.getStringParameter("java_classpath"));
                  parameters.writeFile();
                  
                  restart();
                }
              }
            } else {
              hf = new HttpFetcher(new URL(root + filename), new FileOutputStream(new File(".", filename)));
              hf.fetch();
              
              parameters.setStringParameter("java_classpath", filename + System.getProperty("path.separator") + parameters.getStringParameter("java_classpath"));
              parameters.writeFile();
              
              restart();     
            }
          } catch (Exception e) {
            System.out.println("ERROR: Got exception " + e + " while running automatic update - ignoring");
            e.printStackTrace();
          }
        } catch (InterruptedException e) {
        }
      }
    }
    
  }
}