package rice.email.proxy.util;

import java.io.File;
import java.io.IOException;


public interface Workspace
{
    String ROLE = Workspace.class.getName();

    Resource getTmpFile()
                          throws IOException;

    void release(Resource tmpFile);
}