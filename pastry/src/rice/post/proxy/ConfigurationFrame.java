package rice.post.proxy;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.text.*;

import rice.proxy.*;

public class ConfigurationFrame extends JFrame {
  
  public static final int FRAME_WIDTH=400;
  public static final int FRAME_HEIGHT=600;
  public static final int DEFAULT_HEIGHT=32;
  
  public static final Dimension ENABLE_BOX_SIZE = new Dimension(FRAME_WIDTH/2, DEFAULT_HEIGHT);
  public static final Dimension TEXT_BOX_SIZE = new Dimension(FRAME_WIDTH, DEFAULT_HEIGHT);
  public static final Dimension NUMERIC_BOX_SIZE = new Dimension(FRAME_WIDTH/2, DEFAULT_HEIGHT);
 
  protected Parameters parameters;
  
  protected ControlPanel[] panels;
    
  public ConfigurationFrame(Parameters parameters) {
    super("ePOST Configuration");
    
    this.parameters = parameters;
    this.panels = new ControlPanel[5];
    this.panels[0] = new JavaConfiguration();
    this.panels[1] = new PostConfiguration();
    this.panels[2] = new EmailConfiguration();
    this.panels[3] = new ProxyConfiguration();
    this.panels[4] = new OtherConfiguration();
    
    GridBagLayout layout = new GridBagLayout();
    getContentPane().setLayout(layout);
    
    JTabbedPane pane = new JTabbedPane();
    
    pane.addTab("Java", null, panels[0], "Java Configuration Pane");
    pane.addTab("POST", null, panels[1], "POST Configuration Pane");
    pane.addTab("Email", null, panels[2], "Email Configuration Pane");
    pane.addTab("Proxy", null, panels[3], "Proxy Configuration Pane");
    pane.addTab("Other", null, panels[4], "Other Configuration Pane");

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
  }
  
  protected class ButtonPane extends JPanel {
    
    protected JButton cancel;
    protected JButton save;
    
    protected ButtonPane(GridBagLayout layout) {
      super(layout);
     
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
      
      GridBagConstraints gbc = new GridBagConstraints();
      layout.setConstraints(cancel, gbc);      
      add(cancel); 
      
      GridBagConstraints gbd = new GridBagConstraints();
      gbd.gridx = 1;
      layout.setConstraints(save, gbd);      
      add(save);
    }
    
  }
  
  protected class JavaConfiguration extends ControlPanel {
    public JavaConfiguration() {
      super(new GridBagLayout(), 
            new TitledPanel[] { new GeneralJavaConfiguration(new GridBagLayout()),
                                new MemoryConfiguration(new GridBagLayout()),
                                new DebugConfiguration(new GridBagLayout()),
                                new ProfilingConfiguration(new GridBagLayout()),
                                new OtherJavaConfiguration(new GridBagLayout())});
    }
  }
  
  protected class PostConfiguration extends ControlPanel {
    public PostConfiguration() {
      super(new GridBagLayout(), 
            new TitledPanel[] { new GeneralPostConfiguration(new GridBagLayout()),
              new SecurityConfiguration(new GridBagLayout()),
              new LogConfiguration(new GridBagLayout()),
              new RefreshConfiguration(new GridBagLayout()) });
    }
  }
  
  protected class EmailConfiguration extends ControlPanel {
    public EmailConfiguration() {
      super(new GridBagLayout(), 
            new TitledPanel[] { new GeneralEmailConfiguration(new GridBagLayout()),
                                new SmtpConfiguration(new GridBagLayout()), 
                                new ImapConfiguration(new GridBagLayout()),
                                new Pop3Configuration(new GridBagLayout()),
                                new SSLConfiguration(new GridBagLayout())});
    }
  }
  
  protected class ProxyConfiguration extends ControlPanel {
    public ProxyConfiguration() {
      super(new GridBagLayout(), 
            new TitledPanel[] { new GeneralProxyConfiguration(new GridBagLayout()),
              new UpdateConfiguration(new GridBagLayout()), 
              new LivenessConfiguration(new GridBagLayout()),
              new SleepConfiguration(new GridBagLayout()),
              new RestartConfiguration(new GridBagLayout())});
    }
  }
  
  protected class OtherConfiguration extends ControlPanel {
    public OtherConfiguration() {
      super(new GridBagLayout(), 
            new TitledPanel[] { new StorageConfiguration(new GridBagLayout()),
              new LoggingConfiguration(new GridBagLayout()), 
              new GlobalConfiguration(new GridBagLayout()), 
              new PastConfiguration(new GridBagLayout())});
    }
  }
  
  protected class GeneralJavaConfiguration extends TitledPanel {
    public GeneralJavaConfiguration(GridBagLayout layout) {      
      super("General", layout, 
            new ConfigurationPanel[][] { 
            { new TextBox("java_home", "Java Home", new GridBagLayout(), "The location of the Java installation on your computer") },
            { new TextBox("java_command", "Java Command", new GridBagLayout(), "The command to run when starting Java (this should not change)") },
            { new TextBox("java_classpath", "Extra Classpath", new GridBagLayout(), "Any extra arguments that should go on the ePOST classpath") }});
    }
  }
  
  protected class MemoryConfiguration extends TitledPanel {
    public MemoryConfiguration(GridBagLayout layout) {      
      super("Memory", layout, 
            new ConfigurationPanel[][] { 
            { new TextBox("java_maximum_memory", "Max JVM Memory", new GridBagLayout(), "The maximum amount of memory to allocate to Java (should look like ###M)") },
            { new EnableBox("java_memory_free_enable", "Free JVM Memory", new GridBagLayout(), "Whether or not to request the JVM periodically release unused memory"), new FloatBox("java_memory_free_maximum", "Free Threshold", new GridBagLayout(), "The threshold of free memory before Java will release it") }});
    }
  }
  
  protected class DebugConfiguration extends TitledPanel {
    public DebugConfiguration(GridBagLayout layout) {      
      super("Debug", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("java_debug_enable", "Enable Debug", new GridBagLayout(), "Whether or not to enable the Java debugger (available using JDB)"), new NumericBox("java_debug_port", "Debug Port", new GridBagLayout(), "#####", "The port number of accept debugger connections on") }});
    }
  }
  
  protected class ProfilingConfiguration extends TitledPanel {
    public ProfilingConfiguration(GridBagLayout layout) {      
      super("Profiling", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("java_profiling_enable", "Enable Profiling", new GridBagLayout(), "Whether or not to enable Borland OptimizeIt profiling (this is unavailable in the default installation)"), new NumericBox("java_profiling_port", "Profiling Port", new GridBagLayout(), "#####", "The port number of accept profiling connections on") }});
    }
  }
  
  protected class OtherJavaConfiguration extends TitledPanel {
    public OtherJavaConfiguration(GridBagLayout layout) {      
      super("Advanced", layout, 
            new ConfigurationPanel[][] { 
            { new TextBox("java_stack_size", "Max JVM Stack", new GridBagLayout(), "The maximum stack size to allow the JVM to have (should be in the form ###K)") },
            { new EnableBox("java_use_server_vm", "Use Server VM", new GridBagLayout(), "Whether or not to use the server VM (this is unavailable on most Windows installations)"), new EnableBox("java_interpreted_mode", "Interpreted Mode", new GridBagLayout(), "Whether or not to run in interpreted mode only (this is generally much slower)") }});
    }
  }
  
  protected class GeneralPostConfiguration extends TitledPanel {
    public GeneralPostConfiguration(GridBagLayout layout) {      
      super("General", layout, 
            new ConfigurationPanel[][] { 
            { new TextBox("post_username", "Post Username", new GridBagLayout(), "The username that POST should run with - a file with this name and the extension .epost must be in the local directory") },
            { new PasswordBox("post_password", "Post Password", new GridBagLayout(), "The password corresponding to the user's certificate") }, 
            { new TextBox("post_synchronize_interval", "Publish Interval", new GridBagLayout(), "The interval with which POST should request any pending email messages (in milliseconds)") }});
    }
  }
  
  protected class SecurityConfiguration extends TitledPanel {
    public SecurityConfiguration(GridBagLayout layout) {      
      super("Security", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("post_certificate_verification_enable", "Verify Certificate", new GridBagLayout(), "Whether or not POST should verify the certifate upon startup"), new EnableBox("post_keypair_verification_enable", "Verify Keypair", new GridBagLayout(), "Whether or not POST should compare the provided keypair to the ceriticate on startup") } });
    }
  }
    
  protected class LogConfiguration extends TitledPanel {
    public LogConfiguration(GridBagLayout layout) {      
      super("Logs", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("post_allow_log_insert", "Allow Reinsert", new GridBagLayout(), "Whether or not POST is able to reinsert a log if it cannot be found - this should ONLY be enabled on the very first boot.\n It has the potential to erase you email folders!"), new EnableBox("post_allow_log_insert_reset", "Auto Reset", new GridBagLayout(), "Whether or not to automatically reset the 'Allow Reinsert' flag to a safe position") },
            { new EnableBox("post_fetch_log", "Fetch Log", new GridBagLayout(), "Whether or not POST should fetch your log on startup - this is required to use email"), new NumericBox("post_fetch_log_retries", "Fetch Retries", new GridBagLayout(), "##", "The number of log fetch tries before giving up") },
            { new EnableBox("post_force_log_reinsert", "Erase Log", new GridBagLayout(), "Whethere or not POST should erase your log on the next bootup - WARNING: This will PERMANENTLY erase your email folders!") }
            });
    }
  }
  
  protected class RefreshConfiguration extends TitledPanel {
    public RefreshConfiguration(GridBagLayout layout) {      
      super("Refresh", layout, 
            new ConfigurationPanel[][] { 
            { new TextBox("post_object_refresh_interval", "Refresh Interval", new GridBagLayout(), "The interval with which all live objects should be refreshed (in milliseconds)") },
            { new TextBox("post_object_timeout_interval", "Refresh Lease", new GridBagLayout(), "The lifetime extension of all refreshed objects (in milliseconds)") },
            });
    }
  }
  
  protected class GeneralEmailConfiguration extends TitledPanel {
    public GeneralEmailConfiguration(GridBagLayout layout) {      
      super("General", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("email_gateway", "Serve As Gateway", new GridBagLayout(), "Whether or not this machine should serve as a gateway - this should only be done by site administrators"), new EnableBox("email_accept_nonlocal", "Non-Local Connections", new GridBagLayout(), "Whether or not the email servers should accept connections which are not from the local box") } });
    }
  }
  
  protected class SmtpConfiguration extends TitledPanel {
    public SmtpConfiguration(GridBagLayout layout) {      
      super("SMTP Server", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("email_smtp_enable", "SMTP Enable", new GridBagLayout(), "Whether or not the SMTP service should be enabled - if so, there must be a default server"), new NumericBox("email_smtp_port", "SMTP Port", new GridBagLayout(), "#####", "The port which the local SMTP server should run on") },
            { new EnableBox("email_smtp_ssl", "Use SSL", new GridBagLayout(), "Whether or not the SMTP server should run as an SSL server"), new EnableBox("email_smtp_authenticate", "Require Authentication", new GridBagLayout(), "Whether or not the SMTP server should require authenication before sending (via CRAM-MD5 or AUTH LOGIN)") },
            { new TextBox("email_smtp_server", "Default SMTP Server", new GridBagLayout(), "The default SMTP server to use if an email is sent to a non-ePOST recipient - this is generally the same as your normal SMTP server") } });
    }
  }
  
  protected class ImapConfiguration extends TitledPanel {
    public ImapConfiguration(GridBagLayout layout) {      
      super("IMAP Server", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("email_imap_enable", "IMAP Enable", new GridBagLayout(), "Whether or not the IMAP service should be enabled"), new NumericBox("email_imap_port", "SMTP Port", new GridBagLayout(), "#####", "The port which the local IMAP server should run on") },
            { new EnableBox("email_imap_ssl", "Use SSL", new GridBagLayout(), "Whether or not the IMAP server should run as an SSL server") } });
    }
  }

  protected class Pop3Configuration extends TitledPanel {
    public Pop3Configuration(GridBagLayout layout) {      
      super("POP3 Server", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("email_pop3_enable", "POP3 Enable", new GridBagLayout(), "Whether or not the POP3 service should be enabled"), new NumericBox("email_pop3_port", "POP3 Port", new GridBagLayout(), "#####", "The port which the local POP3 server should run on") },
            { new EnableBox("email_pop3_ssl", "Use SSL", new GridBagLayout(), "Whether or not the POP3 server should run as an SSL server") } });
    }
  }
  
  protected class SSLConfiguration extends TitledPanel {
    public SSLConfiguration(GridBagLayout layout) {      
      super("SSL Settings", layout, 
            new ConfigurationPanel[][] { 
            { new TextBox("email_ssl_keystore_filename", "Keystore Filename", new GridBagLayout(), "The filename of the SSL key which the SSL servers should use") },
            { new PasswordBox("email_ssl_keystore_password", "Keystore Password", new GridBagLayout(), "The password to the keystore holding the SSL key") } });
    }
  }
  
  protected class GeneralProxyConfiguration extends TitledPanel {
    public GeneralProxyConfiguration(GridBagLayout layout) {      
      super("General", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("proxy_compatibility_check_enable", "Check Compatibility", new GridBagLayout(), "Whether or not the proxy should check ePOST compatibility on startup"), new EnableBox("proxy_show_dialog", "Show Window", new GridBagLayout(), "Whether or not the proxy should show the 'Welcome to ePOST' dialog on startup") }});
    }
  }
  
  protected class UpdateConfiguration extends TitledPanel {
    public UpdateConfiguration(GridBagLayout layout) {      
      super("Automatic Updating", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("proxy_automatic_update_enable", "Enable Updating", new GridBagLayout(), "Whether or not the proxy should periodically check for updated ePOST software") },
            { new TextBox("proxy_automatic_update_interval", "Updating Interval", new GridBagLayout(), "The interval, in milliseconds, with which ePOST should check for updates") } });
    }
  }
  
  protected class LivenessConfiguration extends TitledPanel {
    public LivenessConfiguration(GridBagLayout layout) {      
      super("Liveness Monitor", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("proxy_liveness_monitor_enable", "Enable Monitor", new GridBagLayout(), "Whether or not the proxy should periodically ping the client JVM to make sure it's not hung") },
            { new TextBox("proxy_liveness_monitor_timeout", "Monitor Timeout", new GridBagLayout(), "The maximum response time to a ping for the client JVM to be considered alive") } });
    }
  }
  
  protected class SleepConfiguration extends TitledPanel {
    public SleepConfiguration(GridBagLayout layout) {      
      super("Sleep Monitor", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("proxy_sleep_monitor_enable", "Enable Monitor", new GridBagLayout(), "Whether or not the proxy should detect the computer going to sleep, and automatically reboot the proxy") },
            { new TextBox("proxy_sleep_monitor_timeout", "Monitor Timeout", new GridBagLayout(), "The maximal timeout before the proxy decides to reboot the proxy") } });
    }
  }
  
  protected class RestartConfiguration extends TitledPanel {
    public RestartConfiguration(GridBagLayout layout) {      
      super("Auto-Restart", layout, 
            new ConfigurationPanel[][] { 
            { new TextBox("restart_delay", "Restart Delay", new GridBagLayout(), "The amount of time to wait, in milliseconds, before restart the client JVM") },
            { new TextBox("restart_max", "Maximum Restarts", new GridBagLayout(), "The maximum number of times to restart the client before exiting the server JVM") } });
    }
  }
  
  protected class StorageConfiguration extends TitledPanel {
    public StorageConfiguration(GridBagLayout layout) {      
      super("Disk Storage", layout, 
            new ConfigurationPanel[][] { 
            { new TextBox("storage_root_location", "Storage Location", new GridBagLayout(), "The path where the FreePastry-Storage-Root directory (containing DHT data) should be stored") },
            { new TextBox("storage_cache_limit", "Cache Limit", new GridBagLayout(), "The size limit, in bytes, of the disk cache") },
            { new TextBox("storage_disk_limit", "Disk Limit", new GridBagLayout(), "The size limit, in bytes, of the on-disk storage roots") } });
    }
  }
  
  protected class LoggingConfiguration extends TitledPanel {
    public LoggingConfiguration(GridBagLayout layout) {      
      super("Logging", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("standard_output_redirect_enable", "Enable Logging", new GridBagLayout(), "Whether or not the proxy should log statistics to disk (please don't disable)"), new EnableBox("standard_output_network_enable", "Upload Logs", new GridBagLayout(), "Whether or not the proxy should automatically upload the statistics to the Rice ePOST team") },
            { new TextBox("standard_output_network_interval", "Upload Interval", new GridBagLayout(), "The interval, in milliseconds, with which the statistics should be uploaded") } });
    }
  }
  
  protected class GlobalConfiguration extends TitledPanel {
    public GlobalConfiguration(GridBagLayout layout) {      
      super("Global Ring", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("multiring_enable", "Enable Multiple Rings", new GridBagLayout(), "Whether or not the proxy should use the multiring protocol (do not disable)"), new EnableBox("multiring_global_enable", "Join Global Ring", new GridBagLayout(), "Whether or not the proxy should attempt to join the global ring (should only be activated if your have a routable IP address)") } });
    }
  }
  
  protected class PastConfiguration extends TitledPanel {
    public PastConfiguration(GridBagLayout layout) {      
      super("PAST", layout, 
            new ConfigurationPanel[][] { 
            { new EnableBox("past_backup_cache_enable", "Enable Backup Cache", new GridBagLayout(), "Whether or not PAST should cache over-replicated objects for better performacne") }, 
            { new TextBox("past_backup_cache_limit", "Backup Cache Limit", new GridBagLayout(), "The limit to the amount of over-replicated objects to cache, in bytes") },
            { new TextBox("past_garbage_collection_interval", "GC Interval", new GridBagLayout(), "The interval, in milliseconds, with which PAST should collect expired objects in the local storage") } });
    }
  }
  
  protected abstract class ControlPanel extends JPanel {
    
    protected TitledPanel[] panels;
    
    public ControlPanel(GridBagLayout layout, TitledPanel[] panels) {
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

  protected abstract class TitledPanel extends JPanel {
    
    protected ConfigurationPanel[][] panels;
    
    public TitledPanel(String title, GridBagLayout layout, ConfigurationPanel[][] panels) {
      super(layout);
      
      this.panels = panels;
      
      setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
                                                   BorderFactory.createEmptyBorder(5,5,5,5)));
      
      for (int i=0; i<panels.length; i++) {
        ConfigurationPanel[] row = panels[i];
        
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
  
  protected abstract class ConfigurationPanel extends JPanel {
    
    protected String parameter;
    
    public ConfigurationPanel(String parameter, LayoutManager layout) {
      super(layout);
      this.parameter = parameter;
    }
    
    protected void save() {
      parameters.setStringParameter(parameter, getValue());
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
      
      box.setSelected(parameters.getBooleanParameter(parameter));
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
      
      field.setText(parameters.getStringParameter(parameter));
    }
    
    protected String getValue() {
      return field.getText(); 
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
    new ConfigurationFrame(new Parameters("test"));
  }
  
  public static JTextField buildMaskedField(String mask) {
    try {
      MaskFormatter format = new MaskFormatter(mask);
      format.setPlaceholder(" ");
      format.setValidCharacters(" 0123456789M");
      //format.setAllowsInvalid(false);
      JTextField result =  new JFormattedTextField(format);
      result.setColumns(5);
      return result;
    } catch (Exception e) { System.out.println("ERROR: " + e); }
    
    return null;
  }
  
}