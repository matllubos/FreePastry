package rice.splitstream.testing;

import java.io.*;
import java.util.*;

public class AbsoluteDelay extends ProcessLog{
    
    private static int absoluteDelayMax[] = new int[MAX_SEQ];
    private static int absoluteDelayMin[] = new int[MAX_SEQ];
    private static int absoluteDelayAvg[] = new int[MAX_SEQ];

    protected static int TIME_SLOTS = 20;
    protected static int TIME_RANGE = 100; // denotes 50 msec delay

    protected static int timeSlots[] = new int[TIME_SLOTS];

    private static int count[] = new int[MAX_SEQ];

    private int num = 0;

    private int num_packets = 0;
    public static void initializeArrays(){
	for(int i = 0; i < MAX_SEQ; i++){
	    absoluteDelayMax[i] = 0;
	    absoluteDelayMin[i] = 0; 
	    absoluteDelayAvg[i] = 0; 
	    count[i] = 0;
	}

	for(int i = 0; i < TIME_SLOTS; i++)
	    timeSlots[i] = 0;
    }

    public void findAbsoluteDelay(){
	FileInputStream fis = null;
	int i =0;
	String filename = "Node";
	while(true){

	    try{
		fis = new FileInputStream(filename+ i + ".ad");
		System.out.println("Reading file num "+i);
	    }catch(FileNotFoundException e){
		System.out.println("Max sequ number "+num+" total packets "+num_packets);
		dump();
		//e.printStackTrace();
		System.exit(1);
	    }

	    num = 0;
	    i++;
	    InputStreamReader in = new InputStreamReader(fis);
	    BufferedReader br = new BufferedReader((Reader)in);
	    
	    String line = null;
	    int minimum = 0;
	    try{
		line = br.readLine();
	    }catch(IOException ioe){
		ioe.printStackTrace();
	    }
	    while(line != null){
	    StringTokenizer tk = new StringTokenizer(line);
	    int index1 = Integer.valueOf(tk.nextToken()).intValue();
	    int value = Integer.valueOf(tk.nextToken()).intValue();
	    
	    if(absoluteDelayMin[index1] == 0)
		absoluteDelayMin[index1] = value;
	    if(absoluteDelayMax[index1] == 0)
		absoluteDelayMax[index1] = value;
	    
	    if(value < absoluteDelayMin[index1] && value > 0)
		absoluteDelayMin[index1] = value;
	    if(value > absoluteDelayMax[index1] && value > 0)
		absoluteDelayMax[index1] = value;
	    
	    absoluteDelayAvg[index1] = (absoluteDelayAvg[index1] * count[index1]) + value;
	    count[index1] ++;
	    num_packets ++;
	    int k = value / TIME_RANGE;
	    if( k < TIME_SLOTS)
		timeSlots[k] ++;
	    if(index1 > num)
		num = index1;
	    absoluteDelayAvg[index1] /= count[index1];
	    try{
		line = br.readLine();
		
	    }catch(IOException ei){
		ei.printStackTrace();
	    }
	    }
	}
	
    }

    public void dump(){
	String name = "Overall";
	File logMin = new File(name + ".DelayMin");
	File logAvg = new File(name + ".DelayAvg");
	File logMax = new File(name + ".DelayMax");
	File logCdf = new File(name + ".DelayCdf");

	FileOutputStream fosMin = null;
	FileOutputStream fosAvg = null;
	FileOutputStream fosMax = null;
	FileOutputStream fosCdf = null;


	try{
	    if(!logMin.createNewFile())
		System.out.println("Error.. could not create log file for file ");
	    if(!logAvg.createNewFile())
		System.out.println("Error.. could not create log file for file ");
	    if(!logMax.createNewFile())
		System.out.println("Error.. could not create log file for file ");
	    if(!logCdf.createNewFile())
		System.out.println("Error.. could not create log file for file ");

	}catch(Exception ec){
	    ec.printStackTrace();
	    System.exit(1);
	}

	try{
	    fosMin = new FileOutputStream(logMin);

	    fosAvg = new FileOutputStream(logAvg);

	    fosMax = new FileOutputStream(logMax);

	    fosCdf = new FileOutputStream(logCdf);
	}catch(FileNotFoundException e){
	    e.printStackTrace();
	}


	OutputStreamWriter outMin = new OutputStreamWriter(fosMin);
	OutputStreamWriter outAvg = new OutputStreamWriter(fosAvg);
	OutputStreamWriter outMax = new OutputStreamWriter(fosMax);
	OutputStreamWriter outCdf = new OutputStreamWriter(fosCdf);


	BufferedWriter bwMin = new BufferedWriter((Writer) outMin);
	BufferedWriter bwAvg = new BufferedWriter((Writer) outAvg);
	BufferedWriter bwMax = new BufferedWriter((Writer) outMax);
	BufferedWriter bwCdf = new BufferedWriter((Writer) outCdf);

	// now dump this info
	String stringMin;
	String stringAvg;
	String stringMax;
	String stringCdf;

	for(int j = 0; j < num; j++){
	    Integer intg = new Integer(j);
	    stringMin = stringAvg = stringMax = intg.toString();

	    stringMin += "\t" + absoluteDelayMin[j];
	    stringAvg += "\t" + absoluteDelayAvg[j];
	    stringMax += "\t" + absoluteDelayMax[j];

	    try{
		bwMin.write(stringMin, 0, stringMin.length());
		bwMin.newLine();
		bwMin.flush();

		bwAvg.write(stringAvg, 0, stringAvg.length());
		bwAvg.newLine();
		bwAvg.flush();

		bwMax.write(stringMax, 0, stringMax.length());
		bwMax.newLine();
		bwMax.flush();
	    }catch(IOException i){
		i.printStackTrace();
	    } 
	}

	for(int j = 0; j < TIME_SLOTS - 1; j++)
	    timeSlots[j+1] += timeSlots[j];

	for(int j = 0; j < TIME_SLOTS; j++){
	    Integer num = new Integer(j*100);
	    stringCdf = num.toString();
	    stringCdf += "\t" + ((timeSlots[j] * 100)/num_packets);
	    try{
		bwCdf.write(stringCdf, 0, stringCdf.length());
		bwCdf.newLine();
		bwCdf.flush();
	    }catch(IOException i){
		i.printStackTrace();
	    } 
	}
    }

    public static void main(String args[]){
	//AbsoluteDelay.initializeArrays();
	AbsoluteDelay ad = new AbsoluteDelay();
	
	ad.findAbsoluteDelay();
    }    
}
