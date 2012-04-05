/*
 * Copyright 2009 Bushel Project Members (http://bushel.googlecode.com/)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.bushel.repo;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.Configuration.Visibility;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.matcher.ExactOrRegexpPatternMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.StringUtils;

import com.googlecode.bushel.core.BundleInfo;
import com.googlecode.bushel.core.BundleRequirement;
import com.googlecode.bushel.core.ExportPackage;
import com.googlecode.bushel.repo.osgi.ExecutionEnvironmentProfileProvider;
import com.googlecode.bushel.util.Version;
import com.googlecode.bushel.util.VersionRange;

public class BundleInfoAdapter {

    public static final String CONF_NAME_DEFAULT = "default";

    public static final Configuration CONF_DEFAULT = new Configuration(CONF_NAME_DEFAULT);

    public static final String CONF_NAME_OPTIONAL = "optional";

    public static final Configuration CONF_OPTIONAL = new Configuration(CONF_NAME_OPTIONAL, Visibility.PUBLIC,
            "Optional dependencies", new String[] { CONF_NAME_DEFAULT }, true, null);

    public static final String CONF_NAME_TRANSITIVE_OPTIONAL = "transitive-optional";

    public static final Configuration CONF_TRANSITIVE_OPTIONAL = new Configuration(CONF_NAME_TRANSITIVE_OPTIONAL,
            Visibility.PUBLIC, "Optional dependencies", new String[] { CONF_NAME_OPTIONAL }, true, null);

    public static final String CONF_USE_PREFIX = "use_";

    public static final String EXTRA_ATTRIBUTE_NAME = "osgi";

    public static final Map<String, String> OSGI_BUNDLE = Collections.singletonMap(EXTRA_ATTRIBUTE_NAME,
            BundleInfo.BUNDLE_TYPE);

    public static final Map<String, String> OSGI_PACKAGE = Collections.singletonMap(EXTRA_ATTRIBUTE_NAME,
            BundleInfo.PACKAGE_TYPE);

    public static final Map<String, String> OSGI_SERVICE = Collections.singletonMap(EXTRA_ATTRIBUTE_NAME,
            BundleInfo.SERVICE_TYPE);

    public static DefaultModuleDescriptor toModuleDescriptor(String org, BundleInfo bundle,
            ExecutionEnvironmentProfileProvider profileProvider) throws ProfileNotFoundException {
        DefaultModuleDescriptor md = new DefaultModuleDescriptor(null, null);
        ModuleRevisionId mrid = asMrid(org, bundle.getSymbolicName(), bundle.getVersion(), null);
        md.setResolvedPublicationDate(new Date());
        md.setModuleRevisionId(mrid);

        md.addConfiguration(CONF_DEFAULT);
        md.addConfiguration(CONF_OPTIONAL);
        md.addConfiguration(CONF_TRANSITIVE_OPTIONAL);

        Set<String> exportedPkgNames = new HashSet<String>(bundle.getExports().size());
        for (ExportPackage exportPackage : bundle.getExports()) {
            exportedPkgNames.add(exportPackage.getName());
            String[] confDependencies = new String[exportPackage.getUses().size() + 1];
            int i = 0;
            for (String use : exportPackage.getUses()) {
                confDependencies[i++] = CONF_USE_PREFIX + use;
            }
            confDependencies[i] = CONF_NAME_DEFAULT;
            md.addConfiguration(new Configuration(CONF_USE_PREFIX + exportPackage.getName(), Visibility.PUBLIC,
                    "Exported package " + exportPackage.getName(), confDependencies, true, null));
        }

        requirementAsDependency(org, md, bundle, exportedPkgNames);

        if (bundle.getUri() != null) {
            DefaultArtifact artifact = null;
            String uri = bundle.getUri();
            if (uri.startsWith("ivy://")) {
                artifact = decodeIvyLocation(uri);
            } else {
                URL url = null;
                try {
                    url = new URL("file:" + uri);
                } catch (MalformedURLException e) {
                    Message.error("BUG IN BUSHEL, please report: " + e.getMessage() + "\n"
                            + StringUtils.getStackTrace(e));
                }
                if (url != null) {
                    artifact = new DefaultArtifact(mrid, null, bundle.getSymbolicName(), "jar", "jar", url, null);
                }
            }
            if (artifact != null) {
                md.addArtifact(CONF_NAME_DEFAULT, artifact);
            }
        }

        if (profileProvider != null) {
            for (String env : bundle.getExecutionEnvironments()) {
                ExecutionEnvironmentProfile profile = profileProvider.getProfile(env);
                if (profile == null) {
                    throw new ProfileNotFoundException("Execution environment profile " + env + " not found");
                }
                for (String pkg : profile.getPkgNames()) {
                    ArtifactId id = new ArtifactId(ModuleId.newInstance("", pkg), PatternMatcher.ANY_EXPRESSION,
                            PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION);
                    DefaultExcludeRule rule = new DefaultExcludeRule(id, ExactOrRegexpPatternMatcher.INSTANCE,
                            OSGI_PACKAGE);
                    String[] confs = md.getConfigurationsNames();
                    for (int i = 0; i < confs.length; i++) {
                        rule.addConfiguration(confs[i]);
                    }
                    md.addExcludeRule(rule);
                }
            }
        }

        return md;
    }

    public static String encodeIvyLocation(Artifact artifact) {
        ModuleRevisionId mrid = artifact.getModuleRevisionId();
        return encodeIvyLocation(mrid.getOrganisation(), mrid.getName(), mrid.getBranch(), mrid.getRevision(), artifact
                .getType(), artifact.getName(), artifact.getExt());
    }

    private static String encodeIvyLocation(String org, String name, String branch, String rev, String type,
            String art, String ext) {
        StringBuilder builder = new StringBuilder();
        builder.append("ivy://");
        builder.append(org);
        builder.append('/');
        builder.append(name);
        builder.append('?');
        if (branch != null) {
            builder.append("branch=");
            builder.append(branch);
        }
        if (rev != null) {
            builder.append("&rev=");
            builder.append(rev);
        }
        if (type != null) {
            builder.append("&type=");
            builder.append(type);
        }
        if (art != null) {
            builder.append("&art=");
            builder.append(art);
        }
        if (ext != null) {
            builder.append("&ext=");
            builder.append(ext);
        }
        return builder.toString();
    }

    private static DefaultArtifact decodeIvyLocation(String uri) {
        String org = null;
        String name = null;
        String branch = null;
        String rev = null;
        String art = null;
        String type = null;
        String ext = null;

        uri = uri.substring(6);
        int i = uri.indexOf('/');
        if (i < 0) {
            throw new IllegalArgumentException("Expecting an organisation in the ivy uri: " + uri);
        }
        org = uri.substring(0, i);
        uri = uri.substring(i + 1);

        i = uri.indexOf('?');
        if (i < 0) {
            throw new IllegalArgumentException("Expecting an module name in the ivy uri: " + uri);
        }
        name = uri.substring(0, i);
        uri = uri.substring(i + 1);

        String[] parameters = uri.split("&");
        for (String parameter : parameters) {
            String[] nameAndValue = parameter.split("=");
            if (nameAndValue.length != 2) {
                throw new IllegalArgumentException("Malformed query string in the ivy uri: " + uri);
            } else if (nameAndValue[0].equals("branch")) {
                branch = nameAndValue[1];
            } else if (nameAndValue[0].equals("rev")) {
                rev = nameAndValue[1];
            } else if (nameAndValue[0].equals("art")) {
                art = nameAndValue[1];
            } else if (nameAndValue[0].equals("type")) {
                type = nameAndValue[1];
            } else if (nameAndValue[0].equals("ext")) {
                ext = nameAndValue[1];
            } else {
                throw new IllegalArgumentException("Unrecognized parameter '" + nameAndValue[0]
                        + " in the query string of the ivy uri: " + uri);
            }
        }

        ModuleRevisionId amrid = ModuleRevisionId.newInstance(org, name, branch, rev);
        DefaultArtifact artifact = new DefaultArtifact(amrid, null, art, type, ext);
        return artifact;
    }

    private static void requirementAsDependency(String org, DefaultModuleDescriptor md, BundleInfo bundleInfo,
            Set<String> exportedPkgNames) {
        for (BundleRequirement requirement : bundleInfo.getRequirements()) {
            String type = requirement.getType();
            String name = requirement.getName();

            if (type.equals(BundleInfo.PACKAGE_TYPE) && exportedPkgNames.contains(name)) {
                // don't declare package exported by the current bundle
                continue;
            }

            //Map<String, String> osgiAtt = Collections.singletonMap(EXTRA_ATTRIBUTE_NAME, type);
            ModuleRevisionId ddmrid = asMrid(org, name, requirement.getVersion(), md.getModuleRevisionId().getRevision(), null);
            DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(ddmrid, false);

            String conf = CONF_NAME_DEFAULT;
            if (type.equals(BundleInfo.PACKAGE_TYPE)) {
                // declare the configuration for the package
                conf = CONF_USE_PREFIX + name;
                md.addConfiguration(new Configuration(CONF_USE_PREFIX + name, Visibility.PUBLIC, "Exported package "
                        + name, new String[] { CONF_NAME_DEFAULT }, true, null));
                dd.addDependencyConfiguration(conf, conf);
            }

            if ("optional".equals(requirement.getResolution())) {
                dd.addDependencyConfiguration(CONF_NAME_OPTIONAL, conf);
                dd.addDependencyConfiguration(CONF_NAME_TRANSITIVE_OPTIONAL, CONF_NAME_TRANSITIVE_OPTIONAL);
            } else {
                dd.addDependencyConfiguration(CONF_NAME_DEFAULT, conf);
            }

            md.addDependency(dd);
        }

    }

    private static ModuleRevisionId asMrid(String org, String symbolicNAme, Version v, Map<String, String> extraAttr) {
        return ModuleRevisionId.newInstance(org, symbolicNAme, v == null ? null : v.toString(), extraAttr);
    }

    private static ModuleRevisionId asMrid(String org, String symbolicNAme, VersionRange v, String defaultV, Map<String, String> extraAttr) {
        String revision;
        if (v == null) {
            revision = defaultV;
        } else {
            revision = v.toIvyRevision();
        }
        return ModuleRevisionId.newInstance(org, symbolicNAme, revision, extraAttr);
    }

    public static class ProfileNotFoundException extends RuntimeException {

        public ProfileNotFoundException(String msg) {
            super(msg);
        }

    }
}
