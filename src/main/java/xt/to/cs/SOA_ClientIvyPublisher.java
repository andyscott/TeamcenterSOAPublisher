package xt.to.cs;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.publish.PublishOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.core.settings.IvyVariableContainer;
import org.apache.ivy.core.settings.IvyVariableContainerImpl;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.apache.ivy.plugins.version.ExactVersionMatcher;

import com.googlecode.bushel.core.BundleInfo;
import com.googlecode.bushel.core.ManifestParser;
import com.googlecode.bushel.ivy.RemapInfo;
import com.googlecode.bushel.repo.BundleInfoAdapter;

/**
 * Reads the OSGI bundle jars from Teamcenter and publishes them to a local ivy repository at ~/.ivy2/local/
 * 
 * @author Andy Scott
 */
public class SOA_ClientIvyPublisher {

  static Ivy                ivy;
  static FileSystemResolver toResolver;
  static FileSystemResolver fromResolver;
  
  static Map<String, RemapInfo> remaps = new HashMap<String, RemapInfo>();
  
  static {
    remaps.put("system.bundle", new RemapInfo(null, null, null));
    //remaps.put("org.apache.axis", new RemapInfo(null, null, null));
    //remaps.put("org.apache.xalan", new RemapInfo(null, null, null));
    // Hijack axis and xalan to inject two other dependencies!
    remaps.put("org.apache.axis", new RemapInfo("commons-logging", "commons-logging", "1.1.1"));
    remaps.put("org.apache.xalan", new RemapInfo("commons-codec", "commons-codec", "1.6"));
    
    remaps.put("org.apache.xerces", new RemapInfo("xercesImpl", "xerces", "2.10.0"));
    remaps.put("org.apache.commons.httpclient", new RemapInfo("commons-httpclient", "commons-httpclient", "3.1"));
    remaps.put("org.apache.log4j", new RemapInfo("log4j-over-slf4j", "org.slf4j", "1.6.4"));
  }

  public static void main(String arg[]) {

    // The directory that stores the Teamcenter API libs
    String fromDir = "C:/bin/soa_client/java/libs";

    // Begin setting up Ivy
    IvyVariableContainer ivyAntVariableContainer = new IvyVariableContainerImpl();
    IvySettings settings = new IvySettings(ivyAntVariableContainer);
    settings.setBaseDir(new File(fromDir));
    settings.addVersionMatcher(new ExactVersionMatcher());

    // Resolver for the local repo where we publish the jars
    toResolver = new FileSystemResolver();
    toResolver.setName("to");
    String pattern = new File(System.getProperty("user.home"), ".ivy2/local/").getAbsolutePath() + "/[organisation]/[module]/[revision]/[type]s/[artifact].[ext]";
    toResolver.addIvyPattern(pattern);
    toResolver.addArtifactPattern(pattern);

    // Resolver to find the jars in the original lib directory
    fromResolver = new FileSystemResolver();
    fromResolver.setName("from");
    addFromFileSystemResolver(new File(fromDir));

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
  
  public static void addFromFileSystemResolver(File directory) {
    String fromDir = directory.getAbsolutePath();
    fromResolver.addArtifactPattern(fromDir + "/[artifact]_[revision].[ext]");
    fromResolver.addArtifactPattern(fromDir + "/[artifact].[ext]");
  }

  public static void walk(String path) {
    File root = new File(path);
    File[] list = root.listFiles();
    for (File f : list) {
      if (f.isDirectory()) {
        addFromFileSystemResolver(f);
        File mf = new File(f, "META-INF/MANIFEST.MF");
        if (mf.exists())
          process(mf); // Process expanded bundles
        else
          walk(f.getAbsolutePath()); // Walk other dirs and continue searching
      } else {
        // Process encountered file
        process(f);
      }
    }
  }

  public static void process(File file) {
    try {
      BundleInfo info;
      if (file.getName().endsWith(".MF"))
        info = ManifestParser.parseManifest(file);
      else
        info = ManifestParser.parseJarManifest(new FileInputStream(file));

      if (info != null) {
        // Success. Be sure to set the Uri so that the outputted ivy.xml includes an artifact (the jar)!
        info.setUri(file.getPath());

        // Create a temporary ivy.xml file from the bundle info
        DefaultModuleDescriptor moduleDescriptor = BundleInfoAdapter.toModuleDescriptor("com.teamcenter", info, null, remaps);
        File ivyFile = File.createTempFile("ivy", "xml");
        ivyFile.deleteOnExit();
        moduleDescriptor.toIvyFile(ivyFile);
        
        //System.out.println(readFile(ivyFile.getAbsolutePath()));
     
        
        // Resolve -> deliver -> publish! BOOM
        ivy.resolve(ivyFile);
        ivy.deliver(moduleDescriptor.getModuleRevisionId(), moduleDescriptor.getRevision(), (String) toResolver.getArtifactPatterns().get(0));
        ivy.publish(moduleDescriptor.getModuleRevisionId(), fromResolver.getArtifactPatterns(), "to", new PublishOptions());
      }
    } catch (Exception e) {
      //e.printStackTrace();
    }
  }
//  private static String readFile(String path) throws IOException {
//    FileInputStream stream = new FileInputStream(new File(path));
//    try {
//      FileChannel fc = stream.getChannel();
//      MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
//      /* Instead of using default, pass in a decoder. */
//      return Charset.defaultCharset().decode(bb).toString();
//    }
//    finally {
//      stream.close();
//    }
//  }
}
