package rice.p2p.libra;

import rice.replay.*;
import rice.pastry.PastryNode;
import java.io.*;
import java.text.*;
import java.lang.Double;                                                                               
                                                                                
public class GNPCoordinate implements Serializable {
    public static byte FALSE = 0;
    public static byte TRUE = 1;
    
    public static int MAXDIMENSIONS = 8;
    public int numDim = 0; // the current value of the number of dimensions
    public double[] pos = new double[MAXDIMENSIONS];
    public boolean isStable = false;
    public static double GNPCHANGETHRESHOLD = 8; // one shift in each dimension


    public void dump(ReplayBuffer buffer, PastryNode pn) {
	buffer.appendInt(numDim);
	for(int i=0; i < numDim; i++) {
	    int val = (int) pos[i]*1000; // For precision purposes since we do not write doubles
            buffer.appendInt(val);
        }
	if(isStable) {
	    buffer.appendByte(TRUE);
	} else {
	    buffer.appendByte(FALSE);
	}
	
    }

    public GNPCoordinate(ReplayBuffer buffer, PastryNode pn) {
	numDim = buffer.getInteger();
	for(int i=0; i < numDim; i++) {
	    int val = buffer.getInteger(); // For precision purposes since we do not write doubles
            pos[i] = ((double)val)/ 1000.0;
        }
	if(buffer.getByte() == TRUE) {
	    isStable = true;
	} else {
	    isStable = false;
	}
    }

    public GNPCoordinate(GNPCoordinate o) {
	if(o != null) {
	    this.numDim = o.numDim;
	    this.isStable = o.isStable;
	    for(int i=0; i< o.numDim; i++) {
		pos[i] = o.pos[i];
	    }
	    for(int i=o.numDim; i< MAXDIMENSIONS; i++) {
		pos[i] = 0;
	    }
	} else {
	    System.out.println("WARNING: GNPCoordinate() constructor called with a null GNPCoordinate as parameter");
	}


    }
                                       
    public GNPCoordinate(boolean isStable, int numDim, double[] arr) {
	if((numDim >0) && (numDim <= MAXDIMENSIONS)) {
	    this.numDim = numDim;
	    this.isStable = isStable;
	    for(int i=0; i<numDim; i++) {
		pos[i] = arr[i];
	    }
	    for(int i=numDim; i< MAXDIMENSIONS; i++) {
		pos[i] = 0;
	    }
	}
    }
         

    public boolean negligibleChange(GNPCoordinate o) {
	if(numDim != o.numDim) {
	    return false;
	} else {
	    // We compute the distance between the two coordinates
	    double myDist  = distance(o);
	    if(myDist > GNPCHANGETHRESHOLD) {
		return false;
	    } else {
		return true;
	    }
	}

    }

    public boolean isStable() {
	return isStable;
    }
                                                                                
    public double distance(GNPCoordinate o) {
	if((o == null) || (o.numDim != numDim)) {
	    return Double.MAX_VALUE;
	} else {
	    double dpos[] = new double[numDim];
	    double sumDist = 0;
	    for(int i=0; i<numDim; i++) {
		dpos[i] = Math.abs(o.pos[i] - pos[i]);
		sumDist = sumDist + (dpos[i]*dpos[i]);
	    }
	    double dist = Math.sqrt(sumDist);
	    return dist;
	}
    }
                                                                                
    public String toString() {
	DecimalFormat df = new DecimalFormat("0.000");

        String s = "GNPCOORD:(";
	s =s + numDim + "," + isStable + ",["; 
        for(int i=0; i < numDim; i++) {
            s = s + df.format(pos[i]) + ",";
        }
        s = s + "])";
        return s;
    }

}
