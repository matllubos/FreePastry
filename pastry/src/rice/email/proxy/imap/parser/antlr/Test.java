/*
 * Created on Aug 31, 2005
 */
package rice.email.proxy.imap.parser.antlr;

public class Test {

  /**
   * @param args
   */
  public static void main(String[] args) {
    System.out.println("Hello world");
    
    Object foo = new ImapLineParser().parseCommand("001 LOGIN foobar somepasswd");
    System.out.println("foo:"+foo);
    foo = new ImapLineParser().parseCommand("001 LOGIN foo.bar somepasswd");
    System.out.println("foo:"+foo);
  }

}
