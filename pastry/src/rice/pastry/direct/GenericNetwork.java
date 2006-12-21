/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.pastry.direct;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
// import rice.pastry.mytesting.*;
// import rice.p2p.lala.testing.Tracker;

import java.util.*;
import java.lang.*;
import java.io.*;

// This topology will read in a topology-distance matrix and the corresponding
// coordinates from input files
public class GenericNetwork extends BasicNetworkSimulator 
{
  
  float MIN_DIST = 2.0f;
  
  // This stores the matrix
  private float distance[][];

  // This stores the coordinates
  public Hashtable nodePos = new Hashtable();

//  private Vector transit = new Vector();

  /**
   * The number of stubs.
   */
  public static int MAXOVERLAYSIZE = 2000;

  // This keeps track of the indices that have already been assigned
  // index -> ctr
  public HashMap<Integer, Integer> assignedIndices = new HashMap<Integer, Integer>();

  public File inFile_Matrix;// = "GNPINPUT";

//  public File inFile_Coord;// = "COORD";

//  public File outFile_RawGNPError;// = "RawGNPError";

  public NodeRecord generateNodeRecord() {
    return new GNNodeRecord(); 
  }
  
  private class GNNodeRecord implements NodeRecord {
    public int index; // index in the symmetric inter-host latency matrix

    public GNNodeRecord() {
      if (numNodes >= MAXOVERLAYSIZE*nodesPerStub) throw new RuntimeException("No more nodes int he network.");
      // index = countIndex++;
      index = random.nextInt(MAXOVERLAYSIZE);
      while (stubIsFull(index)) {
        index = random.nextInt(MAXOVERLAYSIZE);
      }
      
      incrementStub(index);
      // System.out.println("index= " + index);
    }

    public float proximity(NodeRecord that) {
      return Math.round(networkDelay(that)+that.networkDelay(this));
    }
    
    public float networkDelay(NodeRecord that) {
      GNNodeRecord nr = (GNNodeRecord)that;
      float res = distance[index][nr.index];
      if (res < 0)
        return Float.MAX_VALUE;

      if ((res < MIN_DIST) && !this.equals(that)) return MIN_DIST;

      return res;
    }

    // this return the index in the matrix/CoordinateArray that this NodeRecord
    // cooresponds to
    public int getIndex() {
      return index;
    }

    public void markDead() {
//      System.out.println("a"+assignedIndices.size());
      decrementStub(index);
//      System.out.println("b"+assignedIndices.size());
    }
  }

  // The static variable MAXOVERLAYSIZE should be set to the n, where its input
  // is a N*N matrix
  public GenericNetwork(Environment env, String inFile) {
    this(env, new File(inFile));  
  }
  
  public GenericNetwork(Environment env) {
    this(env, (File)null);
  }
  
  public GenericNetwork(Environment env, File inFile) {
    super(env);
    
    MAXOVERLAYSIZE = env.getParameters().getInt("pastry_direct_gtitm_max_overlay_size");
    MIN_DIST = env.getParameters().getFloat("pastry_direct_min_delay");
    float delayFactor = env.getParameters().getFloat("pastry_direct_gtitm_delay_factor");// 1.0
    
    inFile_Matrix = inFile;
    if (inFile_Matrix == null) {
      inFile_Matrix = new File(env.getParameters().getString("pastry_direct_gtitm_matrix_file"));
    }
  
    setNodesPerStub(env.getParameters().getInt("pastry_direct_gtitm_nodes_per_stub"));// 1
    
//    System.out.println("TOPOLOGY : Generic toplogy");
    // rng = new Random(PastrySeed.getSeed());
    readOverlayMatrix(delayFactor);
    // readOverlayPos();
    // computeRawGNPError();
    // System.exit(1);

  }
  
  int nodesPerStub = 1;

  public void setNodesPerStub(int numPerStub) {
    this.nodesPerStub = numPerStub; 
  }
  
  public void readOverlayMatrix(float delayFactor) {
    FileReader fr = null;
    try {
      fr = new FileReader(inFile_Matrix);
    } catch (Exception e) {
      System.out
          .println("ERROR: The required inter-host distance matrix for Generic Network not found:"+inFile_Matrix.getAbsolutePath());
      System.exit(1);
    }
    BufferedReader in = new BufferedReader(fr);

    int lineCount = 0;
    String line = null;
    try {
      while ((line = in.readLine()) != null) {
        String[] words;
        words = line.split("[ \t]+");
        if (distance == null) {
          if (words.length < MAXOVERLAYSIZE)
            MAXOVERLAYSIZE = words.length;
          distance = new float[MAXOVERLAYSIZE][MAXOVERLAYSIZE]; 
        }
        int nodeCount = 0;
//        for (int i = 0; i < words.length; i++) {
        for (int i = 0; i < MAXOVERLAYSIZE; i++) {
          if (words[i].length() > 0) {
//            if ((nodeCount >= MAXOVERLAYSIZE) || (lineCount >= MAXOVERLAYSIZE)) {
//              System.out
//                  .println("ERROR: the matrix has more entries than MAXOVERLAYSIZE which is a static variable set in main()");
//              System.exit(1);
//            }
            distance[lineCount][nodeCount] = delayFactor*Float.parseFloat(words[i]);
            nodeCount++;
            if (nodeCount == MAXOVERLAYSIZE) break;
          }
        }
        lineCount++;
        if (lineCount == MAXOVERLAYSIZE) break;
      }
      System.out.println("Size of Generic Network matrix= " + lineCount);
    } catch (IOException e) {
      System.out.println("Exception" + e);
    }
  }
  
  private boolean stubIsFull(int index) {
    if (!assignedIndices.containsKey(index)) return false;
    return assignedIndices.get(index)>=nodesPerStub; 
  }

  /**
   * The number of nodes assigned.
   */
  int numNodes = 0;
  
  private void incrementStub(int index) {
    numNodes++;

    int val = 0;
    if (assignedIndices.containsKey(index)) {
      val = assignedIndices.get(index);
    }
    assignedIndices.put(index, val+1);
  }
  
  private void decrementStub(int index) {
    numNodes--;

    int val = assignedIndices.get(index);
    assignedIndices.put(index, val-1);
  }
  

//  public void readOverlayPos() {
//    BufferedReader fin = null;
//    try {
//      fin = new BufferedReader(new FileReader(inFile_Coord));
//      String line;
//      line = fin.readLine();
//      while (line != null) {
//        String[] words;
//        words = line.split("[ \t]+");
//        // System.out.println("words.length= " + words.length);
//        // for(int i=0; i< words.length; i++) {
//        // System.out.println("word[" + i + "]= " + words[i]);
//        // }
//        if (words[0].equals("Done")) {
//          // This means that we are done
//          break;
//        }
//        if (words[0].equals("##index=")) {
//          int index;
//          double pos[] = new double[Coordinate.GNPDIMENSIONS];
//          index = Integer.parseInt(words[1]);
//          for (int i = 0; i < Coordinate.GNPDIMENSIONS; i++) {
//            pos[i] = Double.parseDouble(words[2 + i]);
//          }
//          Coordinate state = new Coordinate(index, pos);
//          nodePos.put(new Integer(index), state);
//          // System.out.println("inputfile coord[" + index + "]= " + state);
//        }
//        line = fin.readLine();
//      }
//      fin.close();
//
//    } catch (IOException e) {
//      System.out.println("ERROR: In opening input/output files");
//    }
//
//  }

  // This evaluates the GNP error (predicted - actual)/min(predicted,actual)
  // function
//  public void computeRawGNPError() {
//    BufferedWriter fout = null;
//    try {
//      String s = "";
//      fout = new BufferedWriter(new FileWriter(outFile_RawGNPError));
//
//      for (int i = 0; i < MAXOVERLAYSIZE; i++) {
//        for (int j = 0; j < MAXOVERLAYSIZE; j++) {
//          double actual = (double) distance[i][j];
//          Coordinate state_i = (Coordinate) nodePos.get(new Integer(i));
//          Coordinate state_j = (Coordinate) nodePos.get(new Integer(j));
//          double predicted = state_i.distance(state_j);
//          double min;
//          if (actual != -1) {
//            if (actual < predicted)
//              min = actual;
//            else
//              min = predicted;
//            if (min > 0) {
//              double gnpError = 0;
//              gnpError = (Math.abs(predicted - actual)) / min;
//              s = "" + gnpError;
//              fout.write(s, 0, s.length());
//              fout.newLine();
//              fout.flush();
//            }
//          }
//
//        }
//      }
//
//    } catch (IOException e) {
//      System.out.println("ERROR: In opening input/output files");
//    }
//  }  
  
//  public static class Coordinate implements Serializable {
//
//    public static int GNPDIMENSIONS = 8;
//    public int index;
//    public double[] pos = new double[GNPDIMENSIONS];
//    
//    public Coordinate(int _index, double[] arr) {
//    for(int i=0; i<GNPDIMENSIONS; i++) {
//        pos[i] = arr[i];
//    }
//    }
//
//    
//    public double distance(Coordinate o) {
//    double dpos[] = new double[GNPDIMENSIONS];
//    double sumDist = 0;
//    for(int i=0; i<GNPDIMENSIONS; i++) {
//        dpos[i] = Math.abs(o.pos[i] - pos[i]);
//        sumDist = sumDist + (dpos[i]*dpos[i]);
//    }
//    double dist = Math.sqrt(sumDist);
//    return dist;
//    
//    }
//
//    public String toString() {
//    String s = "(";
//    for(int i=0; i < GNPDIMENSIONS; i++) {
//        s = s + pos[i] + ",";
//    }
//    s = s + ")";
//    return s;
//    }
//
//}
}
