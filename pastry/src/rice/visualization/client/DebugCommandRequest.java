package rice.visualization.client;

import java.io.*;

public class DebugCommandRequest implements Serializable {
  public String command;
  
  public DebugCommandRequest(String command) {
    this.command = command;
  }
}
