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
import java.util.*;

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
                                                                {"java_use_server_vm", "true"}, 
                                                                {"java_classpath", "pastry.jar"},
                                                                {"java_main_class", "rice.visualization.proxy.VisualizationEmailProxy"},
                                                                {"java_main_class_parameters", ""}, 
                                                                {"restart_return_value_min", "0"},
                                                                {"restart_return_value_max", "1000"},
                                                                {"restart_max", "5"},
                                                                {"restart_delay", "5000"}};
    
  public Proxy() {
  }
  
  public void run(String params) throws IOException, InterruptedException {
    Parameters parameters = initializeParameters(params);
    String command = buildJavaCommand(parameters);
    String[] environment = buildJavaEnvironment(parameters);
    int count = 0;
    
    while (count < parameters.getIntParameter("restart_max")) {
      System.out.println("[Loader       ]: Launching command " + command);
      
      Process process = (environment.length > 0 ? Runtime.getRuntime().exec(command, environment) : Runtime.getRuntime().exec(command));
      Printer error = new Printer(process.getErrorStream(), "[Error Stream ]: ");
      Printer standard = new Printer(process.getInputStream(), "[Output Stream]: ");
      
      int exit = process.waitFor();    
      
      if ((exit >= parameters.getIntParameter("restart_return_value_min")) && (exit <= parameters.getIntParameter("restart_return_value_max"))) {
        System.out.println("[Loader       ]: Child process exited with value " + exit + " - restarting client");
        count++;
        
        if (count < parameters.getIntParameter("restart_max")) {
          System.out.println("[Loader       ]: Waiting for " + parameters.getIntParameter("restart_delay") + " milliseconds");   
          Thread.sleep(parameters.getIntParameter("restart_delay"));
        }
      } else {
        System.out.println("[Loader       ]: Child process exited with value " + exit + " - exiting loader");   
        return;
      }
    }
    
    System.out.println("[Loader       ]: Restarted child process " + count + " times - exiting loader");   
  }
  
  protected Parameters initializeParameters(String params) {
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
    
}