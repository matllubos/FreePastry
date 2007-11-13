package rice.testing.routeconsistent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rice.pastry.Id;


public class Replayer {
  public static void main(String[] args) throws Exception {
    String idString = "7B2C2EC47F036DADB5B998571D483EA8033F0FA4";
    String addrString = "planetlab01.mpi-sws.mpg.de/139.19.142.1:21854";
    String startTimeString = "1193145369648";
    String randSeedString = "-3326724064924085497";
    String bootAddrCString = "planetlab02.mpi-sws.mpg.de/139.19.142.2:21854";
    
//    String bootAddressString = ConsistencyPLTest.ALT_BOOTNODE;
//    int bootPort = ConsistencyPLTest.startPort;

    Id id = Id.build(idString);
    InetSocketAddress addr = getAddr(addrString).get(0);
    long startTime = Long.parseLong(startTimeString);
    long randSeed = Long.parseLong(randSeedString);
    
    
    List<InetSocketAddress> bootAddrCandidates = getAddr(bootAddrCString);

    System.out.println(bootAddrCandidates);
    
//    ConsistencyPLTest.replayNode(id, addr, bootAddrCandidates, startTime, randSeed);
  }
  
  public static List<InetSocketAddress> getAddr(String s) throws UnknownHostException {
    ArrayList<InetSocketAddress> ret = new ArrayList<InetSocketAddress>();    
    Pattern inetSAddrPattern = Pattern.compile("/([0-9.]+):([0-9]+)");
    Matcher matcher = inetSAddrPattern.matcher(s);    
    while(matcher.find()) {
      ret.add(new InetSocketAddress(InetAddress.getByName(matcher.group(1)), Integer.parseInt(matcher.group(2))));
    }
    return ret;
  }
}
