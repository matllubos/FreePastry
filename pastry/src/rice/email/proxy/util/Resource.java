package rice.email.proxy.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;


public interface Resource
{
    public InputStream getInputStream()
                               throws IOException;

    public Writer getWriter()
                     throws IOException;

    public Reader getReader()
                     throws IOException;

    public long getSize()
                 throws IOException;

    public void delete()
                throws IOException;
}