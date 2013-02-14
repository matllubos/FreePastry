import java.io.* ;
import java.util.* ;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.DirectoryScanner ;
import org.apache.tools.ant.types.FileSet;


/**
 * This class uses the 'append task', used to append a header to the beginning
 * of each file in a system. It reads in the text from a source text file and
 * adds that text to a series of files. Here is a sample invocation:
 * <append srcfile="copyright.txt" />
 * 
 * @author Abhishek Ray
 * 
 **/
public class Append extends Task
{
  
  // Attributes
  
  /**
   * The source file containing the stream to be appended to each file. 
   */
  private File sourcefile = null;
  
  /**
   * The set of directories of files to which the stream will be appended.
   */
  private Vector filesets = new Vector() ;
  
  // Constructors.
  
  /**
   * Public, no-argument constructor. Required by Ant.
   */
  public Append()
  {}
  
  // Attribute setters
  
  /**
   * Sets the source file for the stream.
   */
  public void setSrcfile(File sourcefile)
  {
    this.sourcefile = sourcefile;
  }
    
  /**
   * Adds a set of files (nested fileset attribute).
   */
  public void addFileset(FileSet set)
  {
    filesets.addElement(set);
  }
  
  // methods
  
 /**
  * This method performs the appending.
  */
  public void execute() throws BuildException
  {
    if (sourcefile == null)
      throw new BuildException("At least one source file must be prpvided");
    try
    { 
      for (int i = 0 ; i < filesets.size() ; i++)
      {
        FileSet fs = (FileSet)filesets.elementAt(i) ;
        DirectoryScanner ds = fs.getDirectoryScanner(project) ;
        File fromdir = fs.getDir(project) ;
        String[] destfiles = ds.getIncludedFiles();
        
        ByteArrayInputStream instream ;
        ByteArrayOutputStream outstream ;
        ByteArrayOutputStream outstreamcheck ;
        FileReader in ;
        FileWriter out ;
        int c ;
        
        for (int j = 0; j < destfiles.length;j++)  
        {
          File destfile = new File(fromdir + File.separator + destfiles[j]);
          
          // reads the java code from the destination file and inserts it into a
          // stream buffer for temporary storage
          in = new FileReader(destfile) ;
          outstream = new ByteArrayOutputStream() ;
          while ((c=in.read()) != -1)
            outstream.write(c) ;
          in.close();
          outstream.close() ;
          
          // checks if stream of source file hasn't already been apppended to
          // destination file and then does the appending process
          in = new FileReader(sourcefile);
          outstreamcheck = new ByteArrayOutputStream() ;
          while ((c=in.read()) != -1)
            outstreamcheck.write(c) ;
          in.close();
          outstreamcheck.close();
          if (!outstream.toString().startsWith(outstreamcheck.toString()))
          {
            System.out.println("appending " + sourcefile + " to " + destfile) ;
            
            // reads the text from the sourcefile and overwrites it over the java code
            // in the destination file
            in = new FileReader(sourcefile);
            out = new FileWriter(destfile, false);
            while ((c=in.read()) != -1)
              out.write(c) ;
            in.close();
            out.close();
            
            // reads the java code from the stream buffer and appends it to the text
            // in the destionation file
            instream = new ByteArrayInputStream(outstream.toByteArray()) ;
            out = new FileWriter(destfile, true) ;
            while ((c=instream.read()) != -1)
              out.write(c) ;
            out.close();
          }
        }
      }
    }
    catch (IOException ioex)
    {
      throw new BuildException("Error while appending text") ;
    }
  }
}