package org.mpisws.p2p.transport.util;

import java.util.HashMap;
import java.util.Map;

public class OptionsFactory {
  public static Map<String, Integer> copyOptions(Map<String, Integer> existing) {
    if (existing == null) return new HashMap<String, Integer>();
    return new HashMap<String, Integer>(existing);
  }
}
