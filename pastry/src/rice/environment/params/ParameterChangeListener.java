/*
 * Created on Jun 27, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package rice.environment.params;

/**
 *	 Listener interface for changes to a parameters object
 *
 * @author jstewart
 *
 */
public interface ParameterChangeListener {
  public void parameterChange(String paramName, String newVal);
}
