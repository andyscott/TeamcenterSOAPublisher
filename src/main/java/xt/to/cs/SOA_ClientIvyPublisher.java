package xt.to.cs;
import java.io.File;
import java.io.FileInputStream;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.publish.PublishOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.core.settings.IvyVariableContainer;
import org.apache.ivy.core.settings.IvyVariableContainerImpl;
import org.apache.ivy.plugins.resolver.FileSystemResolver;

import com.googlecode.bushel.core.BundleInfo;
import com.googlecode.bushel.core.ManifestParser;
import com.googlecode.bushel.repo.BundleInfoAdapter;

/**
 * Reads the OSGI bundle jars from Teamcenter and publishes them to a local ivy repository at ~/.ivy2/local/
 * @author Andy Scott
 *
 */
public class SOA_ClientIvyPublisher {

  static Ivy ivy;
  static FileSystemResolver fromResolver;
  static FileSystemResolver toResolver;

  public static void main(String arg[]) {

    // The directory that stores the Teamcenter API libs
    String fromDir = "C:/bin/soa_client/java/libs";

    // Begin setting up Ivy
    IvyVariableContainer ivyAntVariableContainer = new IvyVariableContainerImpl();
    IvySettings settings = new IvySettings(ivyAntVariableContainer);
    settings.setBaseDir(new File(fromDir));
    
    // Resolver for the local repo where we publish the jars
    toResolver = new FileSystemResolver();
    toResolver.setName("to");
    String pattern = new File(System.getProperty("user.home"), ".ivy2/local/").getAbsolutePath() + "/[organisation]/[module]/[revision]/[type]s/[artifact].[ext]";
    toResolver.addIvyPattern(pattern);
    toResolver.addArtifactPattern(pattern);

    // Resolver to find the jars in the original lib directory
    fromResolver = new FileSystemResolver();
    fromResolver.setName("from");
    fromResolver.addArtifactPattern(fromDir + "/[artifact]_[revision].[ext]");
    fromResolver.addArtifactPattern(fromDir + "/[artifact].[ext]");

    // Finish up ivy settings
    settings.addResolver(toResolver);
    settings.addResolver(fromResolver);
    settings.setDefaultResolver("from");

    // Create the ivy instance
    ivy = Ivy.newInstance(settings);
    try {
      ivy.pushContext();
      // Begin walking the directory tree and processing all files
      walk(fromDir);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      ivy.popContext();
    }

  }

  public static void walk(String path) {
    File root = new File(path);
    File[] list = root.listFiles();
    for (File f : list) {
      if (f.isDirectory()) {
        // Walk subdirs
        walk(f.getAbsolutePath());
      } else {
        // Process encountered file
        process(f);
      }
    }
  }

  public static void process(File file) {
    try {
      // Try to read the OSGI bundle info from the jar manifest
      BundleInfo info = ManifestParser.parseJarManifest(new FileInputStream(file));
      if (info != null) {
        // Success. Be sure to set the Uri so that the outputted ivy.xml includes an artifact (the jar)!
        info.setUri(file.getPath());

        // Create a temporary ivy.xml file from the bundle info
        DefaultModuleDescriptor moduleDescriptor = BundleInfoAdapter.toModuleDescriptor("com.teamcenter", info, null);
        File ivyFile = File.createTempFile("ivy", "xml");
        ivyFile.deleteOnExit();
        moduleDescriptor.toIvyFile(ivyFile);
        
        // Resolve -> deliver -> publish! BOOM
        ivy.resolve(ivyFile);
        ivy.deliver(moduleDescriptor.getModuleRevisionId(), moduleDescriptor.getRevision(), (String) toResolver.getArtifactPatterns().get(0));
        ivy.publish(moduleDescriptor.getModuleRevisionId(), fromResolver.getArtifactPatterns(), "to", new PublishOptions());
      }
    } catch (Exception e) { }
  }

}
