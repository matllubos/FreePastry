package rice.splitstream.testing;

import java.io.*;
import java.util.Vector;
import java.util.StringTokenizer;

public class ProcessLog{
    private static Vector file_names = new Vector();
    private static int MAX_SEQ = 512;
    private static int NUM_STRIPE = 16;
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
	FileInputStream fis = null;
	FileOutputStream fos = null;
	FileOutputStream fos2 = null;
	int dump[][] = new int[MAX_SEQ][];

	try{
	    if(!log.createNewFile())
		System.out.println("Error.. could not create log file for file "+filename);
	    if(!log2.createNewFile())
		System.out.println("Error.. could not create log file for file "+filename);
	    
	}catch(Exception ec){
	    ec.printStackTrace();
	}

	try{
	    fis = new FileInputStream(filename);
	    fos = new FileOutputStream(log);
	    fos2 = new FileOutputStream(log2);
	}catch(FileNotFoundException e){
	    e.printStackTrace();
	}
	InputStreamReader in = new InputStreamReader(fis);
	OutputStreamWriter out = new OutputStreamWriter(fos);
	OutputStreamWriter out2 = new OutputStreamWriter(fos2);
	BufferedReader br = new BufferedReader((Reader)in);
	BufferedWriter bw = new BufferedWriter((Writer) out);
	BufferedWriter bw2 = new BufferedWriter((Writer) out2);


	String line = null;
	int minimum = 0;
	try{
	    line = br.readLine();
	}catch(IOException i){
	    i.printStackTrace();
	}
	while(line != null){
	    System.out.println("Read "+line);
	    StringTokenizer tk = new StringTokenizer(line);
	    int index1 = Integer.valueOf(tk.nextToken()).intValue();
	    int index2 = Integer.valueOf(tk.nextToken()).intValue();
	    int value = Integer.valueOf(tk.nextToken()).intValue();
	    System.out.println("index1 "+index1+" index2 "+index2+" value "+value);
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
		//bw.write(line, 0, line.length());
		//bw.flush();
		//bw.newLine();
		line = br.readLine();
	    }catch(IOException i){
		i.printStackTrace();
	    } 
	}

	// now dump this info
	String string;
	String string2;
	int diff = 0;
	int max =0;
	int min = 0;
	for(int j = 0; j < MAX_SEQ; j++){
	    Integer intg = new Integer(j);
	    int count = 0;
	    string2 = intg.toString();
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



