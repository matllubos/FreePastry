//-----------------------------------------------------------------------------
// $RCSfile$
// $Revision$
// $Author$
// $Date$
//-----------------------------------------------------------------------------

package rice.im.io;

/** Primitive debug logging facility. */
public class Debug {
   private static boolean _debug = false;

   //--------------------------------------------------------------------
   /** Turn on debug message output. */
   public static void setDebug(boolean flag) {
      _debug = flag;
      if (_debug) println("Debug is ON");
   }
   //--------------------------------------------------------------------
   /** Wap, wap -- is this thing turn on? */
   public static boolean isDebug() {
      return _debug;
   }
   //--------------------------------------------------------------------
   /** Write to debug message output destination. */
   public static void println(String msg) {
      if (_debug) {
         System.out.println(msg);
      }
   }
   //--------------------------------------------------------------------
   /** Write exception text to message output destination. */
   public static void printStackTrace(Exception e) {
      e.printStackTrace();
   }
}
