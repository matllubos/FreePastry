package rice.pastry.wire.testing;

import java.io.File;
import java.io.FileFilter;


/**
 * Helper for WireFileProcessor to search for specific files.
 * 
 * @author Jeff Hoye
 *
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
