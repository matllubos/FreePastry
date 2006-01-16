package rice.p2p.libra;
import java.util.*;
import java.io.*;
import java.net.*;

public class MyTest {

    int val;

    public MyTest() {

	try {
	    for(int i=0; i< 100; i++) {
		System.out.println("I am running");
		Thread.sleep(10000);
	    }
	}
	catch(Exception e) {
	    System.out.println("ERROR: " + e);
	}

    }
    
    


    public static void main(String[] args) throws Exception {
      new MyTest();

    }
      

}
