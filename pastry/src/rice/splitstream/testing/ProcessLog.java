package rice.splitstream.testing;

import java.io.*;
import java.util.*;

/**
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */
public class ProcessLog{
    protected static Vector file_names = new Vector();
    protected static int MAX_SEQ = 10240;
    protected static int NUM_STRIPE = 16;
    protected static int TIME_SLOTS = 20;
    protected static int TIME_RANGE = 50; // denotes 50 msec delay

    public ProcessLog(){
    }

    public void process(){
	for(int i = 0; i < file_names.size(); i++){
	    processFile((String)file_names.elementAt(i));
	}
    }
    
    
    public void processFile(String filename){
	File log = new File(filename + ".Processed");
	File log2 = new File(filename + ".Total");
	File log3 = new File(filename + ".Jitter");
	File log4 = new File(filename + ".AbsoluteDelay");

	File[] slot = new File[TIME_SLOTS];
	
	FileInputStream fis = null;
	FileOutputStream fos = null;
	FileOutputStream fos2 = null;
	FileOutputStream fos3 = null;
	FileOutputStream fos4 = null;

	FileOutputStream[] fos5 = new FileOutputStream[TIME_SLOTS];
	int dump[][] = new int[MAX_SEQ][];
	int timeSlots[] = new int[TIME_SLOTS];
	try{
	    if(!log.createNewFile())
		System.out.println("Error.. could not create log file for file "+filename);
	    if(!log2.createNewFile())
		System.out.println("Error.. could not create log file for file "+filename);
	    if(!log3.createNewFile())
		System.out.println("Error.. could not create log file for file "+filename);
	    if(!log4.createNewFile())
		System.out.println("Error.. could not create log file for file "+filename);

	    /*
	    for(int i = 0; i < TIME_SLOTS; i++){
		slot[i] = new File(filename + ".Slot" + i);
		if(!slot[i].createNewFile())
		    System.out.println("Error.. could not create log file for file "+filename);
	    }
	    */
	}catch(Exception ec){
	    ec.printStackTrace();
	    System.exit(1);
	}

	for(int s = 0; s < TIME_SLOTS; s++)
	    timeSlots[s] = 0;


	try{
	    fis = new FileInputStream(filename);
	    fos = new FileOutputStream(log);
	    fos2 = new FileOutputStream(log2);
	    fos3 = new FileOutputStream(log3);
	    fos4 = new FileOutputStream(log4);
	    //for(int i = 0; i < TIME_SLOTS; i++)
	    //fos5[i] = new FileOutputStream(slot[i]);
	}catch(FileNotFoundException e){
	    e.printStackTrace();
	}
	InputStreamReader in = new InputStreamReader(fis);
	OutputStreamWriter out = new OutputStreamWriter(fos);
	OutputStreamWriter out2 = new OutputStreamWriter(fos2);
	OutputStreamWriter out3 = new OutputStreamWriter(fos3);
	OutputStreamWriter out4 = new OutputStreamWriter(fos4);
	BufferedReader br = new BufferedReader((Reader)in);
	BufferedWriter bw = new BufferedWriter((Writer) out);
	BufferedWriter bw2 = new BufferedWriter((Writer) out2);
	BufferedWriter bw3 = new BufferedWriter((Writer) out3);
	BufferedWriter bw4 = new BufferedWriter((Writer) out4);

	/*
	OutputStreamWriter[] out5 = new OutputStreamWriter[TIME_SLOTS];
	BufferedWriter[] bw5 = new BufferedWriter[TIME_SLOTS];
	
	for(int j = 0; j < TIME_SLOTS; j++){
	    out5[j] = new OutputStreamWriter(fos5[j]);
	    bw5[j] = new BufferedWriter((Writer)out5[j]);
	}
	*/

	String line = null;
	int minimum = 0;
	try{
	    line = br.readLine();
	}catch(IOException i){
	    i.printStackTrace();
	}
	while(line != null){
	    //System.out.println("Read "+line);
	    StringTokenizer tk = new StringTokenizer(line);
	    String isDebug = null; 
	    try{
		isDebug = tk.nextToken();
	    }catch(NoSuchElementException noe){
		try{
		    line = br.readLine();
		    continue;
		}catch(IOException e){
		    e.printStackTrace();
		}
	    }
	    if(isDebug.startsWith("DEBUG") || isDebug.startsWith(" DEBUG") ||
	       isDebug.startsWith("Pastry") ||
	       isDebug.startsWith("command") ||
	       isDebug.startsWith("Initing") ||
	       isDebug.startsWith("\n") ||
	       isDebug.startsWith("<0x") ||
	       isDebug.startsWith("Rece") ||
	       isDebug.startsWith("ERROR")){
		try{
		    line = br.readLine();
		    continue;
		}catch(IOException e){
		    e.printStackTrace();
		}
	    }
	    int index1 = Integer.valueOf(isDebug).intValue();
	    int index2 = Integer.valueOf(tk.nextToken()).intValue();
	    int value = Integer.valueOf(tk.nextToken()).intValue();
	    int absoluteDelay = -1;
	    String lastToken = tk.nextToken();
	    if(lastToken != null)
		absoluteDelay = (value - Integer.valueOf(lastToken).intValue());
	    //System.out.println("index1 "+index1+" index2 "+index2+" value "+value);
	    if(dump[index2] == null){
		//System.out.println("nullllllll for "+index1+ ", index 2 "+index2 +" value "+value);
		dump[index2] = new int[NUM_STRIPE];
	    }
	    dump[index2][index1] = value;
	    if(minimum == 0)
		minimum = value;
	    if(minimum > value)
		minimum = value;
	    try{
		Integer i1 = new Integer(index2);
		String output = i1.toString();
		if(absoluteDelay > 0 ){
		    output += "\t" + absoluteDelay;
		    bw4.write(output, 0, output.length());
		    bw4.newLine();
		    bw4.flush();
		}
		line = br.readLine();
	    }catch(IOException i){
		i.printStackTrace();
	    } 
	}

	// now dump this info
	String string;
	String string2;
	String string3;
	int diff = 0;
	int max =0;
	int min = 0;
	for(int j = 0; j < MAX_SEQ; j++){
	    Integer intg = new Integer(j);
	    int count = 0;
	    string2 = intg.toString();
	    string3 = intg.toString();	    
	    if(dump[j] != null){
		for(int k = 0; k < 16; k++){
		    string = intg.toString();
		    if(dump[j][k] > 0){
			string += "\t"+(dump[j][k] - minimum);
			if(max == 0 && min == 0)
			    max = min = dump[j][k];
			if(max < dump[j][k])
			    max = dump[j][k];
			if(min > dump[j][k])
			    min = dump[j][k];
			count++;
		    }
		    else
			string += "\t0";
		    try{
			bw.write(string, 0, string.length());
			bw.newLine();
			bw.flush();
		    }catch(IOException i){
			i.printStackTrace();
		    } 
		  

		}

		for(int m = 0; m < 16; m++){
		    if(dump[j][m] > 0){
			int delay = dump[j][m] - min;
			int index = delay / TIME_RANGE;
			if(index < TIME_SLOTS)
			    timeSlots[index] ++;
		    }
		}
		
		    /*
		    for(int m = 0; m < 15; m++)
			timeSlots[m+1] += timeSlots[m];
		    
		    String temp = string3;

		    for(int m1 = 0; m1 < 16; m1++){
			temp = string3;
			Integer l3 = new Integer(timeSlots[m1]);
			temp += "\t" + l3.toString();
			bw5[m1].write(temp, 0, temp.length());
			bw5[m1].newLine();
			bw5[m1].flush();
			timeSlots[m1] = 0;
			}*/
		//string += "\t"+(max - min);
		max = min = 0;
		try{
		    Integer l = new Integer(count);
		    string2 += "\t" + l.toString();
		    bw2.write(string2, 0, string2.length());
		    bw2.newLine();
		    bw2.flush();
		    
		    

		}catch(IOException i){
		    i.printStackTrace();
		} 
		
	    }
	}
    }

    /**
     * process command line args,
     */
    private static void doInitstuff(String args[]) {
	// process command line arguments
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-help")) {
		System.out.println("Usage: ProcessLog <file_name> <file_name> ...");
		System.exit(1);
	    }
	}
	
	for (int i = 0; i < args.length; i++) {
	    String str = args[i];
	    file_names.addElement(str);
	}
    }
    

    public static void main(String args[]){
	doInitstuff(args);
	ProcessLog processor = new ProcessLog();

	processor.process();
    }
}



