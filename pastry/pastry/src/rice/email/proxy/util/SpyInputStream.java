package rice.email.proxy.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class SpyInputStream
    extends FilterInputStream
{
    OutputStream out;
    boolean debug = true;

    public SpyInputStream(InputStream in, OutputStream out)
    {
        super(in);
        this.out = out;
    }

    public void setDebug(boolean value)
    {
        debug = value;
    }

    public int read()
             throws IOException
    {
        int r = super.read();
        if (debug)
        {
            out.write(r);
            out.flush();
        }

        return r;
    }

    public int read(byte[] buf, int start, int len)
             throws IOException
    {
        int r = super.read(buf, start, len);
        if (debug)
        {
            out.write(buf, start, r);
            out.flush();
        }

        return r;
    }

    public int read(byte[] buf)
             throws IOException
    {
        int r = super.read(buf);
        if (debug)
        {
            out.write(buf);
            out.flush();
        }

        return r;
    }
}