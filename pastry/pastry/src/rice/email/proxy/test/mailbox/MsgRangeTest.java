package rice.email.proxy.test.mailbox;

import rice.email.proxy.mailbox.*;
import junit.framework.TestCase;

/**
 * This test is temporarly unusable due to
 * interface changes.
 */
public class MsgRangeTest
    extends TestCase
{
    public MsgRangeTest(String s)
    {
        super(s);
    }
    
    public void testNullTest() {
    }
/*
    public void testSingleSequence()
    {
        MsgRange range = new MsgRange("11", false);
        assertTrue(range.includes(11, -1));
        assertTrue(range.includes(11, 11));
        assertTrue(range.includes(11, 20));
        assertTrue(!range.includes(2, 11));
        try
        {
            range = new MsgRange("2.0", false);
            fail("Hmmm.");
        }
        catch (RuntimeException expected)
        {
        }
    }

    public void testSingleUID()
    {
        MsgRange range = new MsgRange("11", true);
        assertTrue(range.includes(-1, 11));
        assertTrue(range.includes(11, 11));
        assertTrue(range.includes(20, 11));
        assertTrue(!range.includes(11, 2));
        try
        {
            range = new MsgRange("2.0", true);
            fail("Hmmm.");
        }
        catch (RuntimeException expected)
        {
        }
    }

    public void testDoubleUID()
    {
        MsgRange range = new MsgRange("11:15", true);
        assertTrue(range.includes(-1, 11));
        assertTrue(range.includes(23, 15));
        assertTrue(range.includes(15, 13));
        assertTrue(!range.includes(14, 16));
        try
        {
            range = new MsgRange("2.0:3", true);
            fail("Hmmm.");
        }
        catch (RuntimeException expected)
        {
        }

        range = new MsgRange("5:*", true);
        assertTrue(range.includes(-1, 5));
        assertTrue(range.includes(-1, 6));
        assertTrue(range.includes(-1, 30));
        assertTrue(range.includes(-1, Integer.MAX_VALUE));

        assertTrue(!range.includes(-1, 4));
    }
    */
}