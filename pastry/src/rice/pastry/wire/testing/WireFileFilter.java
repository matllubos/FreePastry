/*
 * Created on Feb 3, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.pastry.wire.testing;

import java.io.File;
import java.io.FileFilter;


/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class WireFileFilter implements FileFilter {
  private String prefix = null;

  public WireFileFilter(String prefix) {
    this.prefix = prefix;
  }

  /* (non-Javadoc)
   * @see javax.swing.filechooser.FileFilter#accept(java.io.File)
   */
  public boolean accept(File arg0) {
    String fname = arg0.getName();
    return fname.startsWith(prefix);
  }

}
