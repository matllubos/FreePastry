/*
 * Created on Feb 3, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.pastry.wire.testing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class WireFileProcessor {
  public static final String WNH_PREFIX = "WNH";
  public static final String DM_PREFIX = "DM";

  public static final int PREFIX_LENGTH = 4;

  public static final String enquePrefix = "ENQ:";
  public static final String sentPrefix = "SEN:";
  public static final String receivedPrefix = "REC:";
  public static final String dropPrefix = "DRP:";

  public static final int TYPE_ENQ = 0;
  public static final int TYPE_SEN = 1;
  public static final int TYPE_REC = 2;
  public static final int TYPE_DRP = 3;

  public static final int ID_LENGTH = 10;

  public static final int NUM_TABLES = 4;
  /**
   * String nodeid -> HashTable of MessageDigest -> ObjPair
   */
  public Hashtable enqueued = new Hashtable();
  public Hashtable sent = new Hashtable();
  public Hashtable received = new Hashtable();
  public Hashtable dropped = new Hashtable();
  
  public Hashtable[] tables = new Hashtable[NUM_TABLES];
  
  private MessageDigestFactory mdf = new MessageDigestFactory();
  
  public WireFileProcessor() {
    tables[TYPE_ENQ] = enqueued;
    tables[TYPE_SEN] = sent;
    tables[TYPE_REC] = received;
    tables[TYPE_DRP] = dropped;
  }
  
  
  public void processFiles() throws IOException {
    // hack to get working directory, I'm sure there is a better way
    File foo = new File("temp.blah.moo");
    foo.delete();
    foo.createNewFile();    
    File workingDirectory = foo.getAbsoluteFile().getParentFile();

    if (!workingDirectory.isDirectory()) {
      throw new RuntimeException(workingDirectory+" is not a directory.");
    }

    processFiles(workingDirectory);
  }

  public void processFile(String fname) throws IOException {
    File f = new File(fname);
    if (!f.exists()) {
      throw new RuntimeException("File Not Found");
    }
    if (fname.startsWith(WNH_PREFIX)) {
      processWNHFile(f,true);      
    }
    if (fname.startsWith(DM_PREFIX)) {
      processDMFile(f,true);      
    }
  }

  public void processFiles(File workingDirectory) throws IOException {
    WireFileFilter wnhFilter = new WireFileFilter(WNH_PREFIX);
    File[] wnhFiles = workingDirectory.listFiles(wnhFilter); 
    for (int i = 0; i < wnhFiles.length; i++) {
      processWNHFile(wnhFiles[i],false);
    }


    WireFileFilter dmFilter = new WireFileFilter(DM_PREFIX);
    File[] dmFiles = workingDirectory.listFiles(dmFilter); 
    for (int i = 0; i < dmFiles.length; i++) {
      processDMFile(dmFiles[i],false);
    }
  }
  
  public void processWNHFile(File f, boolean log) throws IOException {    
    int NOISE_AFTER_PREFIX = 2;
    int NOISE_AFTER_SENDER = 4;

   
    String fname = f.getName();
    int begin = WNH_PREFIX.length()+NOISE_AFTER_PREFIX;
    int end = begin+ID_LENGTH;
    String local = fname.substring(begin,end);

    begin = end+NOISE_AFTER_SENDER;
    end = begin + ID_LENGTH;
    String remote = fname.substring(begin,end);

    System.out.println("WNH File:"+f.getName()+" : "+local+" to "+remote); 

    BufferedReader br = getReader(f);
    
    String messageLine;
    int lineNumber = 0;
    while((messageLine = br.readLine()) != null) {
      lineNumber++;
      if (messageLine.length() < PREFIX_LENGTH) {
        // skip garbage
        continue;
      }
      String prefix = messageLine.substring(0,PREFIX_LENGTH);
      int type = getType(prefix);
      if (type == -1) {
        // skip garbage
        continue;
      }

      messageLine = messageLine.substring(PREFIX_LENGTH,messageLine.length());
      MessageDigest msg = mdf.getMessageDigest(lineNumber,local,remote,messageLine,type,false);
      int num = addToTable(type,msg);
      if (log) {
        System.out.println(lineNumber+":"+num+":"+msg);      
      }
    }       
  }

  public void processDMFile(File f, boolean log) throws IOException {
    int NOISE_AFTER_PREFIX = 2;
   
    String fname = f.getName();
    int begin = DM_PREFIX.length()+NOISE_AFTER_PREFIX;
    int end = begin+ID_LENGTH;
    String local = fname.substring(begin,end);

    System.out.println("DM File:"+f.getName()+" : "+local);        
    BufferedReader br = getReader(f);
    
    int GARBAGE_PREFIX_LN = 1;
    int GARBAGE_PREFIX_LN2 = 2;
    int totalHeadLength = GARBAGE_PREFIX_LN+ID_LENGTH+GARBAGE_PREFIX_LN2+PREFIX_LENGTH;
    
    String messageLine;
    int lineNumber = 0;
    while((messageLine = br.readLine()) != null) {
      lineNumber++;
      if (messageLine.length() < totalHeadLength) {
        // skip garbage
        continue;
      }
      begin = GARBAGE_PREFIX_LN;
      end = begin+ID_LENGTH;
      String remote = messageLine.substring(begin,end);
      begin = end+GARBAGE_PREFIX_LN2;
      end = begin+PREFIX_LENGTH;
      String prefix = messageLine.substring(begin,end);
      int type = getType(prefix);
      if (type == -1) {
        // skip garbage
        continue;
      }

      messageLine = messageLine.substring(totalHeadLength,messageLine.length());
      MessageDigest msg = mdf.getMessageDigest(lineNumber,local,remote,messageLine,type,true);
      
      int num = addToTable(type,msg);
      if (log) {
        System.out.println(lineNumber+":"+num+":"+msg);      
      }
    }       
  }
  

  public int addToTable(int type, MessageDigest msg) {    
    //System.out.println("addToTable("+type+","+msg+")");
    Hashtable t = tables[type];  
    Hashtable set = (Hashtable)t.get(msg.local);
    if (set == null) {
      set = new Hashtable();
      t.put(msg.local,set);
    }
    ObjPair i = (ObjPair)set.get(msg);
    if (i == null) {
      set.put(msg,new ObjPair(msg));
      return 1;
    } else {
      i.add(msg);
      return i.getCount();
    }
  }
  
  public static int getType(String prefix) {
    if (prefix.equals(enquePrefix)) return TYPE_ENQ;
    if (prefix.equals(sentPrefix)) return TYPE_SEN;
    if (prefix.equals(receivedPrefix)) return TYPE_REC;
    if (prefix.equals(dropPrefix)) return TYPE_DRP;

    return -1;
  }
  
  public static String getType(int type) {
    switch(type) {
      case TYPE_ENQ:
        return enquePrefix;
      case TYPE_SEN:
        return sentPrefix;
      case TYPE_REC:
        return receivedPrefix;
      case TYPE_DRP:
        return dropPrefix;
    }
    return "UNKNOWN";
  }
  
  public static BufferedReader getReader(File f) throws IOException {
    FileInputStream fis = new FileInputStream(f);    
    InputStreamReader in = new InputStreamReader(fis);
    BufferedReader br = new BufferedReader((Reader)in);
    return br;
  }

  public Pair countItems(Hashtable t) {
    Pair numMsgs = new Pair(0,0);
    Collection sets = t.keySet();
    Iterator setIter = sets.iterator();
    while (setIter.hasNext()) {
      Object o = setIter.next();
      Hashtable set = (Hashtable)t.get(o);
      Pair num = countItems2(set);
      numMsgs.add(num);
      //System.out.println(o.toString()+":"+num+"  "+numMsgs);
    }    
    return numMsgs;
  }

  public class ObjPair {
    public ArrayList udp;
    public ArrayList tcp;
    MessageDigest canonMd;
    
    public ObjPair(MessageDigest md) {
      udp = new ArrayList();
      tcp = new ArrayList();
      canonMd = md;
      add(md);
    }
    
    public void add(MessageDigest md) {
      if (!canonMd.equals(md)) {
        throw new RuntimeException("Message Digests Not Equal");
      }
      if (md.udp) {
        udp.add(md);        
      } else {
        tcp.add(md);
      }
    }
    
    public int getCount() {
      return udp.size()+tcp.size();
    }

    /**
     * 
     */
    public void print() {
//      System.out.println("begin print");
      printList(udp);
      printList(tcp);
//      System.out.println("end print");
    }

    private void printList(ArrayList al) {
      Iterator i = al.iterator();
      while (i.hasNext()) {
        MessageDigest md = (MessageDigest)i.next();
        System.out.println(md);
      }      
    }

    /**
     * @param that
     */
    public void printDiffa(ObjPair that) {
      
      
    }

//    private void printDiffList(ArrayList a1, ArrayList a2 ) {
//      if (a1)
//      Iterator i = a1.iterator();
//      while (i.hasNext()) {
//        MessageDigest md1 = (MessageDigest)i.next();
//        
//        System.out.println(md);
//      }      
//    }
    
  }

  public class Pair {
    public int udp;
    public int tcp;
    public Pair(int udp, int tcp) {
      this.udp = udp;
      this.tcp = tcp;
    }
    
    public void add(Pair p) {
      udp+=p.udp;
      tcp+=p.tcp;
    }
    
    public String toString(){
      int sum = udp+tcp;
      return "U:"+udp+",T:"+tcp+",sum:"+sum;
    }
  }

  public Pair countItems2(Hashtable set) {
    int numMsgsU = 0;
    int numMsgsT = 0;
    Iterator digIter = set.keySet().iterator();
    while (digIter.hasNext()) {      
      MessageDigest msg = (MessageDigest)digIter.next();        
      if (msg instanceof PublishMessageDigest) {
        ObjPair i = (ObjPair)set.get(msg);
        numMsgsU += i.udp.size();
        numMsgsT += i.tcp.size();
      }
    }    
    return new Pair(numMsgsU,numMsgsT);
  }

  public void printTableTotals() {
    for (int i = 0; i<NUM_TABLES; i++) {
      System.out.println("\n"+getType(i));
      Pair numItems = countItems(tables[i]);
      System.out.println("Total:"+numItems);
    }    
  }

  public void compareEnqSnd() {
    compareTables(tables[TYPE_ENQ],tables[TYPE_SEN]);
  }
  
  public void compareEnqRec() {
    compareTables(tables[TYPE_ENQ],tables[TYPE_REC]);
  }
  
  public void compareTables(Hashtable ta, Hashtable tb) {
    Iterator i = ta.keySet().iterator();
    while(i.hasNext()) {
      Object key = i.next();
      Hashtable sa = (Hashtable)ta.get(key);
      Hashtable sb = (Hashtable)tb.get(key);
      System.out.println("\nMissing Msgs for "+key);
      int totalMissing = compareSets(sa,sb);
      System.out.println("Total:"+totalMissing);
    }
  }
  
  public int compareSets(Hashtable sa, Hashtable sb) {    
    int totalMissing = 0;
    Iterator i = sa.keySet().iterator();
    while(i.hasNext()) {
      Object oa = i.next();
      if (oa instanceof PublishMessageDigest) {
        ObjPair i1 = (ObjPair)sa.get(oa);
        if (sb.containsKey(oa)) {
          ObjPair i2 = (ObjPair)sb.get(oa);
          int diff = i1.getCount()-i2.getCount();
          if (diff > 0) {
            i1.print();
            i2.print();
            System.out.println();
            totalMissing+=(diff);
          }
        } else {
          i1.print();
          System.out.println();
          totalMissing+=i1.getCount();
        }
      }
    }
    return totalMissing;
  }
  
  /**
   * 
   */
  private void analyze() {
    compareEnqSnd();
    compareEnqRec();
    
    printTableTotals();
  }

  public static void main(String[] args) throws Exception {
    WireFileProcessor wfp = new WireFileProcessor();
    if (args.length == 0) {
      wfp.processFiles();
      wfp.analyze();
    } else {
      wfp.processFile(args[0]);          
    }
    
    
  }

}