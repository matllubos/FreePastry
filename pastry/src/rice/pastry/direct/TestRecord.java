
package rice.pastry.direct;

public abstract class TestRecord{
    protected int	nNodes;
    protected int 	nTests;

    /**
     * Constructor.
     *
     * @param n number of nodes
     * @param k number of tests
     */

    public TestRecord( int n, int k ){
	nNodes = n;
	nTests = k;
    }

    /**
     * returns the number of nodes
     *
     * @return the number of nodes 
     */
    
    public int getNodeNumber( ){
	return nNodes;
    }

    /**
     * returns the number of tests
     *
     * @return the number of tests
     */
    
    public int getTestNumber(){
	return nTests;
    }

    public abstract void doneTest( );
}


