package rice.pastry.boot;

import java.util.Collection;

public interface Bootstrapper<Identifier> {

  // TODO: figure out where to report errors
  void boot(Collection<Identifier> bootaddresses);

}
