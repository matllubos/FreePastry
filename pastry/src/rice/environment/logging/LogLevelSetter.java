/*
 * Created on Jun 28, 2005
 *
 */
package rice.environment.logging;

/**
 * @author jstewart
 *
 */
public interface LogLevelSetter {
  void setMinLogLevel(int logLevel);
  int getMinLogLevel();
}
