/*
 * Created on Jun 29, 2005
 */
package rice.post.proxy;

import java.io.*;
import java.io.PrintStream;

import rice.environment.Environment;
import rice.environment.logging.Logger;

/**
 * @author Jeff Hoye
 */
public class PSCatcher extends PrintStream {

  PrintStream out;
  Environment environment;
  Logger logger;
  
  /**
   * @param arg0
   * @throws FileNotFoundException
   */
  public PSCatcher(Environment env, PrintStream out) throws FileNotFoundException {
    super(new FileOutputStream("out.txt", true), true);
    this.environment = env;
    logger = environment.getLogManager().getLogger(PSCatcher.class, null);
    this.out = out;
  }

  public void println(String arg0) {
//    out.println(arg0);
    if (logger.level <= Logger.INFO) logger.logException( arg0, 
      new Exception("System.x.println() called"));
  }

  
  public void print(String arg0) {
    println(arg0);
  }

  public void println() {
    println("");
  }
  
  public static void main(String[] args) throws Exception {
    System.out.println("this goes only to out");
    System.err.println("this goes only to err");
    PrintStream ps = new PSCatcher(new Environment(), System.out);
    System.out.println("here");
    System.setOut(ps);
    System.out.println("here2");
    System.setErr(ps);
    System.out.println("This out goes to bof");
    System.out.println("This err goes to bof");
    Thread.dumpStack();
    new Exception("regular exception").printStackTrace();
    new Exception("out exception").printStackTrace(System.out);
    new Exception("err exception").printStackTrace(System.err);
    System.out.println(2); // goes only to out.txt
  }
}
