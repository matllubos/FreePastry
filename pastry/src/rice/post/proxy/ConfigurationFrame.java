package rice.post.proxy;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import javax.swing.*;
import javax.swing.text.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.proxy.*;
import rice.Continuation;

public class ConfigurationFrame extends JFrame {
  
  public static final int FRAME_WIDTH=400;
  public static final int FRAME_HEIGHT=600;
  public static final int DEFAULT_HEIGHT=32;
  
  public static final Dimension ENABLE_BOX_SIZE = new Dimension(FRAME_WIDTH/2, DEFAULT_HEIGHT);
  public static final Dimension TEXT_BOX_SIZE = new Dimension(FRAME_WIDTH, DEFAULT_HEIGHT);
  public static final Dimension INFO_BOX_SIZE = new Dimension(FRAME_WIDTH/2, DEFAULT_HEIGHT);
  public static final Dimension LIST_SIZE = new Dimension(FRAME_WIDTH/2, DEFAULT_HEIGHT*4);
  public static final Dimension LIST_BOX_SIZE = new Dimension(FRAME_WIDTH, DEFAULT_HEIGHT*6);
  public static final Dimension LIST_BUTTONS_SIZE = new Dimension(FRAME_WIDTH, DEFAULT_HEIGHT);
  public static final Dimension NUMERIC_BOX_SIZE = new Dimension(FRAME_WIDTH/2, DEFAULT_HEIGHT);
  public static final Dimension SLIDER_BOX_SIZE = new Dimension(FRAME_WIDTH, (int)(DEFAULT_HEIGHT*1.5));
  public static final Dimension SLIDER_BOX_MIN = new Dimension((int)(FRAME_WIDTH*0.75), (int)(DEFAULT_HEIGHT*1.5));
  // SLIDER_BOX_* must be at least 46 tall to show up properly in MacOSX java 1.4
 
  protected Parameters parameters;
  
  protected Environment environment;
  
  protected PostProxy proxy;
  
  protected SaveablePanel[] panels;
    
  public ConfigurationFrame(Environment env, PostProxy proxy) {
    super("ePOST Configuration");
    this.proxy = proxy;
    this.environment = env;
    addWindowListener(new WindowListener() {
      public void windowActivated(WindowEvent e) {}      
      public void windowClosed(WindowEvent e) {
        synchronized (ConfigurationFrame.this) {
          ConfigurationFrame.this.notify();
        }
      }      
      public void windowClosing(WindowEvent e) {
        synchronized (ConfigurationFrame.this) {
          ConfigurationFrame.this.notify();
        }
      }      
      public void windowDeactivated(WindowEvent e) {}      
      public void windowDeiconified(WindowEvent e) {}      
      public void windowIconified(WindowEvent e) {}      
      public void windowOpened(WindowEvent e) {}
    });
    
    this.parameters = environment.getParameters();
    this.panels = new ControlPanel[7];
    this.panels[0] = new EmailConfiguration();
    this.panels[1] = new ForwardingConfiguration();
    this.panels[2] = new JavaConfiguration();
    this.panels[3] = new PostConfiguration();
    this.panels[4] = new ProxyConfiguration();
    this.panels[5] = new GlacierConfiguration();
    this.panels[6] = new OtherConfiguration();
    
    GridBagLayout layout = new GridBagLayout();
    getContentPane().setLayout(layout);
    
    JTabbedPane pane = new JTabbedPane();
    
    pane.addTab("Email", null, panels[0], "Email Configuration Pane");
    pane.addTab("Forward", null, panels[1], "Email Forwarding Configuration Pane");
    pane.addTab("Java", null, panels[2], "Java Configuration Pane");
    pane.addTab("POST", null, panels[3], "POST Configuration Pane");
    pane.addTab("Proxy", null, panels[4], "Proxy Configuration Pane");
    if (parameters.getBoolean("glacier_enable") && (proxy != null))
      pane.addTab("Glacier", null, panels[5], "Glacier Configuration Pane");
    pane.addTab("Other", null, panels[6], "Other Configuration Pane");

    ButtonPane button = new ButtonPane(new GridBagLayout());
    
    GridBagConstraints gbc = new GridBagConstraints();
    layout.setConstraints(pane, gbc);      
    getContentPane().add(pane);
    
    GridBagConstraints gbd = new GridBagConstraints();
    gbd.gridy = 1;
    layout.setConstraints(button, gbd);      
    getContentPane().add(button);
    
    pack();
    show();
  }
  
  protected void save() {
    for (int i=0; i<panels.length; i++) 
      panels[i].save();
    try {
      parameters.store();
    } catch (IOException e) {
      JOptionPane.showMessageDialog(this, "Could not save configuration due to an error: "+e);
      environment.getLogManager().getLogger(this.getClass(), "").logException(Logger.WARNING, "could not save user configuration", e);
    }
  }
  
  protected class ButtonPane extends JPanel {
    
    protected JButton cancel;
    protected JButton save;
    protected JButton password;
    
    protected ButtonPane(GridBagLayout layout) {
      super(layout);
     
      this.password = new JButton("Change Password");
      this.password.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          new ChangePasswordFrame(parameters);
        }
      });
      
      this.cancel = new JButton("Cancel");
      this.cancel.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          ConfigurationFrame.this.dispose();
        }
      });
      
      
      this.save = new JButton("Save");
      this.save.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int i = JOptionPane.showConfirmDialog(ConfigurationFrame.this, "Are you sure you wish to save the changes you made?\n\n" +
                                                "Note that any changes will not take effect until the proxy is restarted.", "Save Changes", 
                                                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
          
          if (i == JOptionPane.YES_OPTION) {
            ConfigurationFrame.this.save();
            ConfigurationFrame.this.dispose();
          }
        }
      });
      
      GridBagConstraints gbb = new GridBagConstraints();
      layout.setConstraints(password, gbb);
      add(password); 
      
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 1;
      layout.setConstraints(cancel, gbc);      
      add(cancel); 
      
      GridBagConstraints gbd = new GridBagConstraints();
      gbd.gridx = 2;
      layout.setConstraints(save, gbd);      
      add(save);
    }
    
  }
  
  protected class JavaConfiguration extends ControlPanel {
    public JavaConfiguration() {
      super(new GridBagLayout(), 
            new SaveablePanel[] { new GeneralJavaConfiguration(new GridBagLayout()),
                                new MemoryConfiguration(new GridBagLayout()),
                                new DebugConfiguration(new GridBagLayout()),
                                new ProfilingConfiguration(new GridBagLayout()),
                                new OtherJavaConfiguration(new GridBagLayout())});
    }
  }
  
  protected class PostConfiguration extends ControlPanel {
    public PostConfiguration() {
      super(new GridBagLayout(), 
            new SaveablePanel[] { new GeneralPostConfiguration(new GridBagLayout()),
              new SecurityConfiguration(new GridBagLayout()),
              new LogConfiguration(new GridBagLayout()),
              new RefreshConfiguration(new GridBagLayout()) });
    }
  }
  
  protected class EmailConfiguration extends ControlPanel {
    public EmailConfiguration() {
      super(new GridBagLayout(), 
            new SaveablePanel[] { new GeneralEmailConfiguration(new GridBagLayout()),
                                new SmtpConfiguration(new GridBagLayout()), 
                                new ImapConfiguration(new GridBagLayout()),
                                new Pop3Configuration(new GridBagLayout()),
                                new SSLConfiguration(new GridBagLayout())});
    }
  }
  
  protected class ForwardingConfiguration extends ControlPanel {
    public ForwardingConfiguration() {
      super(new GridBagLayout(), 
            new SaveablePanel[] { new GeneralForwardingConfiguration(new GridBagLayout())});
    }
  }
  
  protected class ProxyConfiguration extends ControlPanel {
    public ProxyConfiguration() {
      super(new GridBagLayout(), 
            new SaveablePanel[] { new GeneralProxyConfiguration(new GridBagLayout()),
              new UpdateConfiguration(new GridBagLayout()), 
              new LivenessConfiguration(new GridBagLayout()),
              new SleepConfiguration(new GridBagLayout()),
              new RestartConfiguration(new GridBagLayout())});
    }
  }

  protected class GlacierConfiguration extends ControlPanel {
    public GlacierConfiguration() {
      super(new GridBagLayout(), 
            new SaveablePanel[] { 
              new GlacierBandwidthConfiguration(new GridBagLayout()),
              new GlacierTrashConfiguration(new GridBagLayout())
            }
      );
    }
  }
  
  protected class OtherConfiguration extends ControlPanel {
    public OtherConfiguration() {
      super(new GridBagLayout(), 
            new SaveablePanel[] { new StorageConfiguration(new GridBagLayout()),
              new LoggingConfiguration(new GridBagLayout()), 
              new GlobalConfiguration(new GridBagLayout()), 
              new PastConfiguration(new GridBagLayout())});
    }
  }
  
  protected class GeneralJavaConfiguration extends TitledPanel {
    public GeneralJavaConfiguration(GridBagLayout layout) {      
      super("General", layout, 
            new SaveablePanel[][] { 
            { new TextBox("java_home", "Java Home", new GridBagLayout(), "The location of the Java installation on your computer") },
            { new TextBox("java_command", "Java Command", new GridBagLayout(), "The command to run when starting Java (this should not change)") },
            { new TextBox("java_classpath", "Extra Classpath", new GridBagLayout(), "Any extra arguments that should go on the ePOST classpath") }});
    }
  }
  
  protected class MemoryConfiguration extends TitledPanel {
    public MemoryConfiguration(GridBagLayout layout) {      
      super("Memory", layout, 
            new SaveablePanel[][] { 
            { new TextBox("java_maximum_memory", "Max JVM Memory", new GridBagLayout(), "The maximum amount of memory to allocate to Java (should look like ###M)") },
            { new EnableBox("java_memory_free_enable", "Free JVM Memory", new GridBagLayout(), "Whether or not to request the JVM periodically release unused memory"), new FloatBox("java_memory_free_maximum", "Free Threshold", new GridBagLayout(), "The threshold of free memory before Java will release it") }});
    }
  }
  
  protected class DebugConfiguration extends TitledPanel {
    public DebugConfiguration(GridBagLayout layout) {      
      super("Debug", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("java_debug_enable", "Enable Debug", new GridBagLayout(), "Whether or not to enable the Java debugger (available using JDB)"), new NumericBox("java_debug_port", "Debug Port", new GridBagLayout(), "#####", "The port number of accept debugger connections on") }});
    }
  }
  
  protected class ProfilingConfiguration extends TitledPanel {
    public ProfilingConfiguration(GridBagLayout layout) {      
      super("Profiling", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("java_profiling_enable", "Enable Profiling", new GridBagLayout(), "Whether or not to enable Borland OptimizeIt profiling (this is unavailable in the default installation)"), new NumericBox("java_profiling_port", "Profiling Port", new GridBagLayout(), "#####", "The port number of accept profiling connections on") }});
    }
  }
  
  protected class OtherJavaConfiguration extends TitledPanel {
    public OtherJavaConfiguration(GridBagLayout layout) {      
      super("Advanced", layout, 
            new SaveablePanel[][] { 
            { new TextBox("java_stack_size", "Max JVM Stack", new GridBagLayout(), "The maximum stack size to allow the JVM to have (should be in the form ###K)") },
            { new EnableBox("java_use_server_vm", "Use Server VM", new GridBagLayout(), "Whether or not to use the server VM (this is unavailable on most Windows installations)"), new EnableBox("java_interpreted_mode", "Interpreted Mode", new GridBagLayout(), "Whether or not to run in interpreted mode only (this is generally much slower)") }});
    }
  }
  
  protected class GeneralPostConfiguration extends TitledPanel {
    public GeneralPostConfiguration(GridBagLayout layout) {      
      super("General", layout, 
            new SaveablePanel[][] { 
            { new TextBox("post_username", "Post Username", new GridBagLayout(), "The username that POST should run with - a file with this name and the extension .epost must be in the local directory") },
            { new PasswordBox("post_password", "Post Password", new GridBagLayout(), "The password corresponding to the user's certificate") }, 
            { new TextBox("post_synchronize_interval", "Publish Interval", new GridBagLayout(), "The interval with which POST should request any pending email messages (in milliseconds)") }});
    }
  }
  
  protected class SecurityConfiguration extends TitledPanel {
    public SecurityConfiguration(GridBagLayout layout) {      
      super("Security", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("post_certificate_verification_enable", "Verify Certificate", new GridBagLayout(), "Whether or not POST should verify the certifate upon startup"), new EnableBox("post_keypair_verification_enable", "Verify Keypair", new GridBagLayout(), "Whether or not POST should compare the provided keypair to the ceriticate on startup") } });
    }
  }
    
  protected class LogConfiguration extends TitledPanel {
    public LogConfiguration(GridBagLayout layout) {      
      super("Logs", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("post_allow_log_insert", "Allow Reinsert", new GridBagLayout(), "Whether or not POST is able to reinsert a log if it cannot be found - this should ONLY be enabled on the very first boot.\n It has the potential to erase you email folders!"), new EnableBox("post_allow_log_insert_reset", "Auto Reset", new GridBagLayout(), "Whether or not to automatically reset the 'Allow Reinsert' flag to a safe position") },
            { new EnableBox("post_fetch_log", "Fetch Log", new GridBagLayout(), "Whether or not POST should fetch your log on startup - this is required to use email"), new NumericBox("post_fetch_log_retries", "Fetch Retries", new GridBagLayout(), "##", "The number of log fetch tries before giving up") },
            { new EnableBox("post_force_log_reinsert", "Erase Log", new GridBagLayout(), "Whethere or not POST should erase your log on the next bootup - WARNING: This will PERMANENTLY erase your email folders!") }
            });
    }
  }
  
  protected class RefreshConfiguration extends TitledPanel {
    public RefreshConfiguration(GridBagLayout layout) {      
      super("Refresh", layout, 
            new SaveablePanel[][] { 
            { new TextBox("post_object_refresh_interval", "Refresh Interval", new GridBagLayout(), "The interval with which all live objects should be refreshed (in milliseconds)") },
            { new TextBox("post_object_timeout_interval", "Refresh Lease", new GridBagLayout(), "The lifetime extension of all refreshed objects (in milliseconds)") },
            });
    }
  }
  
  protected class GeneralEmailConfiguration extends TitledPanel {
    public GeneralEmailConfiguration(GridBagLayout layout) {      
      super("General", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("email_gateway", "Serve As Gateway", new GridBagLayout(), "Whether or not this machine should serve as a gateway - this should only be done by site administrators"), new EnableBox("email_accept_nonlocal", "Non-Local Connections", new GridBagLayout(), "Whether or not the email servers should accept connections which are not from the local box") } });
    }
  }
  
  protected class GeneralForwardingConfiguration extends TitledPanel {
    public GeneralForwardingConfiguration(GridBagLayout layout) {      
      super("Email Forwarding", layout, 
            new SaveablePanel[][] { 
            { new ListBox("post_forward_addresses", "Forwarding Addresses", new GridBagLayout(), "The list of email addresses you would like your mail forwarded to - note that these must be ePOST addresses")} });
    }
  }

  protected class GlacierBandwidthConfiguration extends TitledPanel {
    public GlacierBandwidthConfiguration(GridBagLayout layout) {      
      super("Bandwidth limit", layout, 
            new SaveablePanel[][] { 
            { new SliderBox("glacier_max_kbytes_per_sec", "Max bytes/sec", new GridBagLayout(), new JSlider(50, 2000), "If Glacier consumes too much of your available bandwidth, you can use this slider to slow it down.") } });
    }
  }
  
  protected class GlacierTrashConfiguration extends TitledPanel {
    public GlacierTrashConfiguration(GridBagLayout layout) {      
      super("Trash storage", layout);
      final JTextField currentSizeField = new JTextField(20);
      setPanels(
        new SaveablePanel[][] {
          { new InfoBox("Current size", (proxy==null) ? "???" : ((proxy.getGlacier().getTrashSize()/1024)+" kB"), new GridBagLayout(), currentSizeField, "This is the amount of trash stored in Glacier. Trash is kept around for efficiency but can be deleted to free up storage space."),
            new ButtonBox(null, "Empty", new GridBagLayout(), "Click here to empty Glacier's trash storage") {
              public void action() {
                if (proxy != null) {
                  currentSizeField.setText("Emptying...");
                  proxy.getGlacier().emptyTrash(new Continuation() {
                    public void receiveResult(Object o) {
                      currentSizeField.setText((proxy.getGlacier().getTrashSize()/1024)+" kB");
                    } 
                    public void receiveException(Exception e) {
                      receiveResult(e);
                    }
                  });
                }
              }
            }
          }
        }
      );
    }
  }
  
  protected class SmtpConfiguration extends TitledPanel {
    public SmtpConfiguration(GridBagLayout layout) {      
      super("SMTP Server", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("email_smtp_enable", "SMTP Enable", new GridBagLayout(), "Whether or not the SMTP service should be enabled - if so, there must be a default server"), new NumericBox("email_smtp_port", "SMTP Port", new GridBagLayout(), "#####", "The port which the local SMTP server should run on") },
            { new EnableBox("email_smtp_ssl", "Use SSL", new GridBagLayout(), "Whether or not the SMTP server should run as an SSL server"), new EnableBox("email_smtp_authenticate", "Require Authentication", new GridBagLayout(), "Whether or not the SMTP server should require authenication before sending (via CRAM-MD5 or AUTH LOGIN)") },
            { new TextBox("email_ring_" + ((rice.p2p.multiring.RingId) proxy.address.getAddress()).getRingId().toStringFull() + "_smtp_server", "Default SMTP Server", new GridBagLayout(), "The default SMTP server to use if an email is sent to a non-ePOST recipient - this is generally the same as your normal SMTP server") },
            { new EnableBox("email_smtp_log", "Log Traffic", new GridBagLayout(), "Whether or not the SMTP server should log all traffic") } });
    }
  }
  
  protected class ImapConfiguration extends TitledPanel {
    public ImapConfiguration(GridBagLayout layout) {      
      super("IMAP Server", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("email_imap_enable", "IMAP Enable", new GridBagLayout(), "Whether or not the IMAP service should be enabled"), new NumericBox("email_imap_port", "IMAP Port", new GridBagLayout(), "#####", "The port which the local IMAP server should run on") },
            { new EnableBox("email_imap_ssl", "Use SSL", new GridBagLayout(), "Whether or not the IMAP server should run as an SSL server"), new EnableBox("email_imap_log", "Log Traffic", new GridBagLayout(), "Whether or not the IMAP server should log all traffic") } });
    }
  }

  protected class Pop3Configuration extends TitledPanel {
    public Pop3Configuration(GridBagLayout layout) {      
      super("POP3 Server", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("email_pop3_enable", "POP3 Enable", new GridBagLayout(), "Whether or not the POP3 service should be enabled"), new NumericBox("email_pop3_port", "POP3 Port", new GridBagLayout(), "#####", "The port which the local POP3 server should run on") },
            { new EnableBox("email_pop3_ssl", "Use SSL", new GridBagLayout(), "Whether or not the POP3 server should run as an SSL server"), new EnableBox("email_pop3_log", "Log Traffic", new GridBagLayout(), "Whether or not the POP3 server should log all traffic") } });
    }
  }
  
  protected class SSLConfiguration extends TitledPanel {
    public SSLConfiguration(GridBagLayout layout) {      
      super("SSL Settings", layout, 
            new SaveablePanel[][] { 
            { new TextBox("email_ssl_keystore_filename", "Keystore Filename", new GridBagLayout(), "The filename of the SSL key which the SSL servers should use") },
            { new PasswordBox("email_ssl_keystore_password", "Keystore Password", new GridBagLayout(), "The password to the keystore holding the SSL key") } });
    }
  }
  
  protected class GeneralProxyConfiguration extends TitledPanel {
    public GeneralProxyConfiguration(GridBagLayout layout) {      
      super("General", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("proxy_compatibility_check_enable", "Check Compatibility", new GridBagLayout(), "Whether or not the proxy should check ePOST compatibility on startup"), new EnableBox("proxy_show_dialog", "Show Window", new GridBagLayout(), "Whether or not the proxy should show the 'Welcome to ePOST' dialog on startup") }});
    }
  }
  
  protected class UpdateConfiguration extends TitledPanel {
    public UpdateConfiguration(GridBagLayout layout) {      
      super("Automatic Updating", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("proxy_automatic_update_enable", "Enable Updating", new GridBagLayout(), "Whether or not the proxy should periodically check for updated ePOST software"), new EnableBox("proxy_automatic_update_ask_user", "Ask Before Updating", new GridBagLayout(), "Whether or not you would like ePOST to automatically download and install new updates") },
            { new TextBox("proxy_automatic_update_interval", "Updating Interval", new GridBagLayout(), "The interval, in milliseconds, with which ePOST should check for updates") } });
    }
  }
  
  protected class LivenessConfiguration extends TitledPanel {
    public LivenessConfiguration(GridBagLayout layout) {      
      super("Liveness Monitor", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("proxy_liveness_monitor_enable", "Enable Monitor", new GridBagLayout(), "Whether or not the proxy should periodically ping the client JVM to make sure it's not hung") },
            { new TextBox("proxy_liveness_monitor_timeout", "Monitor Timeout", new GridBagLayout(), "The maximum response time to a ping for the client JVM to be considered alive") } });
    }
  }
  
  protected class SleepConfiguration extends TitledPanel {
    public SleepConfiguration(GridBagLayout layout) {      
      super("Sleep Monitor", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("proxy_sleep_monitor_enable", "Enable Monitor", new GridBagLayout(), "Whether or not the proxy should detect the computer going to sleep, and automatically reboot the proxy") },
            { new TextBox("proxy_sleep_monitor_timeout", "Monitor Timeout", new GridBagLayout(), "The maximal timeout before the proxy decides to reboot the proxy") } });
    }
  }
  
  protected class RestartConfiguration extends TitledPanel {
    public RestartConfiguration(GridBagLayout layout) {      
      super("Auto-Restart", layout, 
            new SaveablePanel[][] { 
            { new TextBox("restart_delay", "Restart Delay", new GridBagLayout(), "The amount of time to wait, in milliseconds, before restart the client JVM") },
            { new TextBox("restart_max", "Maximum Restarts", new GridBagLayout(), "The maximum number of times to restart the client before exiting the server JVM") } });
    }
  }
  
  protected class StorageConfiguration extends TitledPanel {
    public StorageConfiguration(GridBagLayout layout) {      
      super("Disk Storage", layout, 
            new SaveablePanel[][] { 
            { new TextBox("storage_root_location", "Storage Location", new GridBagLayout(), "The path where the FreePastry-Storage-Root directory (containing DHT data) should be stored") },
            { new TextBox("storage_cache_limit", "Cache Limit", new GridBagLayout(), "The size limit, in bytes, of the disk cache") },
            { new TextBox("storage_disk_limit", "Disk Limit", new GridBagLayout(), "The size limit, in bytes, of the on-disk storage roots") } });
    }
  }
  
  protected class LoggingConfiguration extends TitledPanel {
    public LoggingConfiguration(GridBagLayout layout) {      
      super("Logging", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("standard_output_redirect_enable", "Enable Logging", new GridBagLayout(), "Whether or not the proxy should log statistics to disk (please don't disable)"), new EnableBox("standard_output_network_enable", "Upload Logs", new GridBagLayout(), "Whether or not the proxy should automatically upload the statistics to the Rice ePOST team") },
            { new TextBox("standard_output_network_interval", "Upload Interval", new GridBagLayout(), "The interval, in milliseconds, with which the statistics should be uploaded") } });
    }
  }
  
  protected class GlobalConfiguration extends TitledPanel {
    public GlobalConfiguration(GridBagLayout layout) {      
      super("Global Ring", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("multiring_enable", "Enable Multiple Rings", new GridBagLayout(), "Whether or not the proxy should use the multiring protocol (do not disable)"), new EnableBox("multiring_global_enable", "Join Global Ring", new GridBagLayout(), "Whether or not the proxy should attempt to join the global ring (should only be activated if your have a routable IP address)") } });
    }
  }
  
  protected class PastConfiguration extends TitledPanel {
    public PastConfiguration(GridBagLayout layout) {      
      super("PAST", layout, 
            new SaveablePanel[][] { 
            { new EnableBox("past_backup_cache_enable", "Enable Backup Cache", new GridBagLayout(), "Whether or not PAST should cache over-replicated objects for better performacne") }, 
            { new TextBox("past_backup_cache_limit", "Backup Cache Limit", new GridBagLayout(), "The limit to the amount of over-replicated objects to cache, in bytes") },
            { new TextBox("past_garbage_collection_interval", "GC Interval", new GridBagLayout(), "The interval, in milliseconds, with which PAST should collect expired objects in the local storage") } });
    }
  }

  protected abstract class SaveablePanel extends JPanel {
    public SaveablePanel(LayoutManager layout) {
      super(layout);
    }
    
    protected void save() {
    };
  }
  
  protected abstract class ControlPanel extends SaveablePanel {
    
    protected SaveablePanel[] panels;
    
    public ControlPanel(GridBagLayout layout, SaveablePanel[] panels) {
      super(layout);
      
      this.panels = panels;
      
      for (int j=0; j<panels.length; j++) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = j;
        
        layout.setConstraints(panels[j], gbc);      
        add(panels[j]);
      }      
    }
    
    protected void save() {
      for (int i=0; i<panels.length; i++)
        panels[i].save();
    }
  }

  protected abstract class TitledPanel extends SaveablePanel {
    
    protected SaveablePanel[][] panels;
    protected GridBagLayout layout;
    
    public TitledPanel(String title, GridBagLayout layout) {
      super(layout);
      
      this.layout = layout;
      
      setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
                                                   BorderFactory.createEmptyBorder(5,5,5,5)));
    }
      
    public TitledPanel(String title, GridBagLayout layout, SaveablePanel[][] panels) {
      this(title, layout);
      setPanels(panels);
    }
    
    public void setPanels(SaveablePanel[][] panels) {
      this.panels = panels;
      for (int i=0; i<panels.length; i++) {
        SaveablePanel[] row = panels[i];
        
        for (int j=0; j<row.length; j++) {
          GridBagConstraints gbc = new GridBagConstraints();
          gbc.gridx = j;
          gbc.gridy = i;
          
          if (j == row.length - 1)
            gbc.gridwidth = GridBagConstraints.REMAINDER;
          
          layout.setConstraints(row[j], gbc);      
          add(row[j]);
        }
      }      
    }    
    
    protected void save() {
      for (int i=0; i<panels.length; i++)
        for (int j=0; j<panels[i].length; j++)
          panels[i][j].save();
    }
  }
  
  protected abstract class ConfigurationPanel extends SaveablePanel {
    
    protected String parameter;
    
    public ConfigurationPanel(String parameter, LayoutManager layout) {
      super(layout);
      this.parameter = parameter;
    }
    
    protected void save() {
      parameters.setString(parameter, getValue());
    }
    
    protected abstract String getValue();
  }
  
  protected class EnableBox extends ConfigurationPanel {
   
    protected String label;
    
    protected JCheckBox box;
    
    public EnableBox(String parameter, String label, GridBagLayout layout, String tip) {
      this(parameter, label, layout, true, tip);
    }
    
    public EnableBox(String parameter, String label, GridBagLayout layout, boolean small, String tip) {
      super(parameter, layout);
      this.label = label; 
      
      this.box = new JCheckBox();
      JLabel boxLabel = new JLabel(label + ": ", JLabel.TRAILING);
      boxLabel.setLabelFor(box);
      boxLabel.setToolTipText(tip);
      
      if (small)
        setPreferredSize(ENABLE_BOX_SIZE);
      else
        setPreferredSize(TEXT_BOX_SIZE);
      
      GridBagConstraints gbc1 = new GridBagConstraints();
      layout.setConstraints(boxLabel, gbc1);      
      add(boxLabel);
      
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.gridx = 1;
      layout.setConstraints(box, gbc2);      
      add(box);
      
      box.setSelected(parameters.getBoolean(parameter));
    }
    
    protected String getValue() {
      return box.isSelected() + ""; 
    }
  }
  
  protected class TextBox extends ConfigurationPanel {
    
    protected String label;
    
    protected JTextField field;
    
    public TextBox(String parameter, String label, GridBagLayout layout, String tip) {
      this(parameter, label, layout, new JTextField(20), tip);
    }
    
    public TextBox(String parameter, String label, GridBagLayout layout, JTextField field, String tip) {
      super(parameter, layout);
      this.label = label; 
      this.field = field;
      
      JLabel fieldLabel = new JLabel(label + ": ", JLabel.TRAILING);
      fieldLabel.setLabelFor(field);
      fieldLabel.setToolTipText(tip);
      
      setPreferredSize(TEXT_BOX_SIZE);
      
      GridBagConstraints gbc1 = new GridBagConstraints();
      layout.setConstraints(fieldLabel, gbc1);      
      add(fieldLabel);
      
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.gridx = 1;
      layout.setConstraints(field, gbc2);      
      add(field);
      
      field.setText(parameters.getString(parameter));
    }
    
    protected String getValue() {
      return field.getText(); 
    }
  }

  protected class InfoBox extends SaveablePanel {
    
    protected String label;
    
    protected JTextField field;
    
    public InfoBox(String label, String value, GridBagLayout layout, String tip) {
      this(label, value, layout, new JTextField(20), tip);
    }
    
    public InfoBox(String label, String value, GridBagLayout layout, JTextField field, String tip) {
      super(layout);
      this.label = label; 
      this.field = field;
      field.setEditable(false);
      field.setBackground(Color.WHITE);
      
      setMinimumSize(INFO_BOX_SIZE);
      setPreferredSize(INFO_BOX_SIZE);

      JLabel fieldLabel = new JLabel(label + ": ", JLabel.TRAILING);
      fieldLabel.setLabelFor(field);
      fieldLabel.setToolTipText(tip);
      
      GridBagConstraints gbc1 = new GridBagConstraints();
      gbc1.fill = GridBagConstraints.NONE;
      gbc1.weightx = 0;
      layout.setConstraints(fieldLabel, gbc1);      
      add(fieldLabel);
      
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.gridx = 1;
      gbc2.weightx = 1;
      gbc2.fill = GridBagConstraints.HORIZONTAL;
      gbc2.insets = new Insets(0, 5, 0, 20);
      layout.setConstraints(field, gbc2);      
      add(field);
      
      field.setText(value);
    }
    
    protected void save() {
    }
  }
  
  protected abstract class ButtonBox extends SaveablePanel {
    
    protected String label;
    
    protected JButton button;
    
    public ButtonBox(String label, String value, GridBagLayout layout, String tip) {
      this(label, layout, new JButton(value), tip);
    }
    
    public ButtonBox(String label, GridBagLayout layout, JButton button, String tip) {
      super(layout);
      this.label = label; 
      this.button = button;
      
      setPreferredSize(button.getPreferredSize());
      
      if (label != null) {
        JLabel fieldLabel = new JLabel(label + ": ", JLabel.TRAILING);
        fieldLabel.setLabelFor(button);
        fieldLabel.setToolTipText(tip);

        GridBagConstraints gbc1 = new GridBagConstraints();
        layout.setConstraints(fieldLabel, gbc1);      
        add(fieldLabel);
      }
      
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.gridx = (label == null) ? 0 : 1;
      layout.setConstraints(button, gbc2);      
      add(button);
      button.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          action();
        }
      });
    }
    
    public abstract void action();
    
    protected void save() {
    }
  }
  
  
  
  protected class ListBox extends ConfigurationPanel {
    
    protected String label;
    
    protected JList field;
    
    protected DefaultListModel model;
    
    public ListBox(String parameter, String label, GridBagLayout layout, String tip) {
      super(parameter, layout);
      this.label = label; 
      this.model = new DefaultListModel();
      
      String[] array = parameters.getStringArray(parameter);
      for (int i=0; i<array.length; i++)
        model.add(i, array[i]);
      
      this.field = new JList(model);
      this.field.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      this.field.setLayoutOrientation(JList.VERTICAL);
      this.field.setVisibleRowCount(-1);
      
      JScrollPane listScroller = new JScrollPane(this.field);
      listScroller.setPreferredSize(LIST_SIZE);
      
      JLabel fieldLabel = new JLabel(label + ": ", JLabel.TRAILING);
      fieldLabel.setLabelFor(field);
      fieldLabel.setToolTipText(tip);
      
      setPreferredSize(LIST_BOX_SIZE);
      
      GridBagConstraints gbc1 = new GridBagConstraints();
      layout.setConstraints(fieldLabel, gbc1);      
      add(fieldLabel);
      
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.gridx = 1;
      gbc2.gridwidth = 2;
      layout.setConstraints(listScroller, gbc2);      
      add(listScroller);
      
      JButton add = new JButton(" Add ");
      JButton remove = new JButton("Remove");
      
      GridBagConstraints gbc3 = new GridBagConstraints();
      gbc3.gridx = 1;
      gbc3.gridy = 1;
      layout.setConstraints(add, gbc3);      
      add(add);
      
      GridBagConstraints gbc4 = new GridBagConstraints();
      gbc4.gridx = 2;
      gbc4.gridy = 1;
      layout.setConstraints(remove, gbc4);      
      add(remove);
      
      remove.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int selected = field.getSelectedIndex();
          if (selected >= 0) {
            int i = JOptionPane.showConfirmDialog(null, "Are you sure you want to remove the address '" + model.elementAt(selected) + "'?", "Confirm Removal", JOptionPane.YES_NO_OPTION);
            
            if (i == JOptionPane.YES_OPTION) {
              model.removeElementAt(selected); 
            }
          }
        }
      });
      
      add.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          new ListElementAddFrame();
        }
      });
    }
    
    public class ListElementAddFrame extends JFrame {
     
      protected JTextField field;
      
      public ListElementAddFrame() {
        super("Add Element");
        this.field = new JTextField(20);
        GridBagLayout layout = new GridBagLayout();
        
        getContentPane().setLayout(layout);
        
        JLabel fieldLabel = new JLabel("Please enter the element: ", JLabel.TRAILING);
        fieldLabel.setLabelFor(field);
        
        GridBagConstraints gbc1 = new GridBagConstraints();
        layout.setConstraints(fieldLabel, gbc1);      
        getContentPane().add(fieldLabel);
        
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.gridx = 1;
        gbc2.gridwidth = 2;
        layout.setConstraints(field, gbc2);      
        getContentPane().add(field);
        
        JButton cancel = new JButton("Cancel");
        JButton submit = new JButton("Submit");
        
        GridBagConstraints gbc3 = new GridBagConstraints();
        gbc3.gridx = 1;
        gbc3.gridy = 1;
        layout.setConstraints(cancel, gbc3);      
        getContentPane().add(cancel);
        
        GridBagConstraints gbc4 = new GridBagConstraints();
        gbc4.gridx = 2;
        gbc4.gridy = 1;
        layout.setConstraints(submit, gbc4);      
        getContentPane().add(submit);
        
        final JFrame frame = this;
        
        getRootPane().setDefaultButton(submit);
        
        cancel.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            frame.dispose();
          }
        });
        
        submit.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            model.addElement(field.getText());
            frame.dispose();
          }
        });
        
        pack();
        show();
      }
    }
    
    protected String getValue() {
      StringBuffer result = new StringBuffer();
      Object[] array = model.toArray();
      
      for (int i=0; i<array.length; i++) {
        if (! array[i].toString().equals("")) {
          result.append(array[i].toString());
      
          if (i < array.length-1)
            result.append(",");
        }
      }
      
      return result.toString(); 
    }
  }
  
  protected class SliderBox extends ConfigurationPanel {

    protected String label;
    
    protected JSlider slider;
    
    public SliderBox(String parameter, String label, GridBagLayout layout, String tip) {
      this(parameter, label, layout, new JSlider(), tip);
    }
    
    public SliderBox(String parameter, String label, GridBagLayout layout, JSlider slider, String tip) {
      super(parameter, layout);
      this.label = label; 
      this.slider = slider;
      
      JLabel sliderLabel = new JLabel(label + ": ", JLabel.TRAILING);
      sliderLabel.setLabelFor(slider);
      sliderLabel.setToolTipText(tip);
      
      setPreferredSize(SLIDER_BOX_SIZE);
      setMinimumSize(SLIDER_BOX_MIN);
      
      GridBagConstraints gbc1 = new GridBagConstraints();
      layout.setConstraints(sliderLabel, gbc1);      
      add(sliderLabel);
      
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.gridx = 1;
      gbc2.fill = GridBagConstraints.HORIZONTAL;
      layout.setConstraints(slider, gbc2);      
      add(slider);
      
      slider.setValue(parameters.getInt(parameter));

      int tickSpacing = slider.getMaximum() - slider.getMinimum();
      slider.setMajorTickSpacing(tickSpacing);
      slider.setPaintLabels(true);
    }
    
    protected String getValue() {
      return ""+slider.getValue(); 
    }
  }
      
  protected class NumericBox extends TextBox {
    
    protected String mask;
    
    public NumericBox(String parameter, String label, GridBagLayout layout, String mask, String tip) {
      super(parameter, label, layout, buildMaskedField(mask), tip);
      
      setPreferredSize(NUMERIC_BOX_SIZE);
    }
    
    protected String getValue() {
      return field.getText().trim(); 
    }
  }
  
  protected class FloatBox extends TextBox {
    
    protected String mask;
    
    public FloatBox(String parameter, String label, GridBagLayout layout, String tip) {
      super(parameter, label, layout, new JTextField(4), tip);
      
      setPreferredSize(NUMERIC_BOX_SIZE);
    }
    
  }
  
  protected class PasswordBox extends TextBox {
    
    public PasswordBox(String parameter, String label, GridBagLayout layout, String tip) {
      super(parameter, label, layout, new JPasswordField(20), tip);
    }
  }
  
  public static void main(String[] args) throws IOException {
    String[] defaults = new String[2];
    defaults[0] = "freepastry";
    defaults[1] = "epost";
    
    new ConfigurationFrame(new Environment(defaults,"epost"), null);
  }
  
  public JTextField buildMaskedField(String mask) {
    try {
      MaskFormatter format = new MaskFormatter(mask);
      format.setPlaceholder(" ");
      format.setValidCharacters(" 0123456789M");
      //format.setAllowsInvalid(false);
      JTextField result =  new JFormattedTextField(format);
      result.setColumns(5);
      return result;
    } catch (Exception e) { 
      environment.getLogManager().getLogger(ConfigurationFrame.class, null).logException(Logger.WARNING,"ERROR: " , e); 
    }
    
    return null;
  }
  
  public class ChangePasswordFrame extends JFrame {
    
    protected JPasswordField old;
    protected JPasswordField new1;
    protected JPasswordField new2;
    
    protected Parameters parameters;
    
    protected boolean submitted = false;
    
    public ChangePasswordFrame(Parameters p) {
      super("Password");
      this.parameters = p;
      this.old = new JPasswordField(20);
      this.new1 = new JPasswordField(20);
      this.new2 = new JPasswordField(20);
      GridBagLayout layout = new GridBagLayout();
      
      getContentPane().setLayout(layout);
      
      JLabel oldLabel = new JLabel("Current password: ", JLabel.TRAILING);
      oldLabel.setLabelFor(old);
      
      JLabel new1Label = new JLabel("New password: ", JLabel.TRAILING);
      new1Label.setLabelFor(new1);
                           
      JLabel new2Label = new JLabel("Re-enter new password: ", JLabel.TRAILING);
      new2Label.setLabelFor(new2);
                           
      GridBagConstraints gbc1 = new GridBagConstraints();
      layout.setConstraints(oldLabel, gbc1);      
      getContentPane().add(oldLabel);
      
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.gridx = 1;
      gbc2.gridwidth = 2;
      layout.setConstraints(old, gbc2);      
      getContentPane().add(old);
      
      GridBagConstraints gbc3 = new GridBagConstraints();
      gbc3.gridy = 1;
      layout.setConstraints(new1Label, gbc3);      
      getContentPane().add(new1Label);
      
      GridBagConstraints gbc4 = new GridBagConstraints();
      gbc4.gridy = 1;
      gbc4.gridx = 1;
      gbc4.gridwidth = 2;
      layout.setConstraints(new1, gbc4);      
      getContentPane().add(new1);
      
      GridBagConstraints gbc5 = new GridBagConstraints();
      gbc5.gridy = 2;
      layout.setConstraints(new2Label, gbc5);      
      getContentPane().add(new2Label);
      
      GridBagConstraints gbc6 = new GridBagConstraints();
      gbc6.gridy = 2;
      gbc6.gridx = 1;
      gbc6.gridwidth = 2;
      layout.setConstraints(new2, gbc6);      
      getContentPane().add(new2);
      
      JButton submit = new JButton("Submit");
      
      GridBagConstraints gbc7 = new GridBagConstraints();
      gbc7.gridy = 3;
      gbc7.gridx = 2;
      layout.setConstraints(submit, gbc7);      
      getContentPane().add(submit);
      
      JButton cancel = new JButton("Cancel");
      
      GridBagConstraints gbc8 = new GridBagConstraints();
      gbc8.gridy = 3;
      gbc8.gridx = 1;
      layout.setConstraints(cancel, gbc8);      
      getContentPane().add(cancel);
      
      getRootPane().setDefaultButton(submit);
      
      submit.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          String oldP = new String(old.getPassword());
          String newP1 = new String(new1.getPassword());
          String newP2 = new String(new2.getPassword());
          
          if (oldP.equals(parameters.getString("post_password"))) {
            if (newP1.equals(newP2)) {
              if (newP1.length() > 0) {
                try {
                  rice.post.security.ca.CAPasswordChanger.changePassword(parameters.getString("post_username"), oldP, newP1);
                  JOptionPane.showMessageDialog(null, "Your password has been changed - it will not take effect until a reboot.");
                } catch (Exception f) {
                  JOptionPane.showMessageDialog(null, "An error occurred - your password has not been changed.\n\n" + f.toString());   
                }
                
                ChangePasswordFrame.this.dispose();
              } else {
                JOptionPane.showMessageDialog(null, "Please enter a non-null password.");
              }
            } else {
              JOptionPane.showMessageDialog(null, "The passwords do not match - please try again");
            }
          } else {
            JOptionPane.showMessageDialog(null, "The old password was incorrect - please try again."); 
          }
        }
      });
      
      cancel.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          ChangePasswordFrame.this.dispose();
        }
      });
      
      pack();
      show();
    }
  }
  
}
