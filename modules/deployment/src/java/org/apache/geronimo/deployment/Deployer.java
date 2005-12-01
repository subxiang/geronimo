/**
 *
 * Copyright 2003-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.geronimo.deployment;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.geronimo.common.DeploymentException;
import org.apache.geronimo.deployment.util.DeploymentUtil;
import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.kernel.GBeanNotFoundException;
import org.apache.geronimo.kernel.Kernel;
import org.apache.geronimo.kernel.config.Configuration;
import org.apache.geronimo.kernel.config.ConfigurationData;
import org.apache.geronimo.kernel.config.ConfigurationStore;
import org.apache.geronimo.kernel.config.InvalidConfigException;
import org.apache.geronimo.system.configuration.ExecutableConfigurationUtil;
import org.apache.geronimo.system.main.CommandLineManifest;

/**
 * GBean that knows how to deploy modules (by consulting available module builders)
 *
 * @version $Rev$ $Date$
 */
public class Deployer {
    private static final Log log = LogFactory.getLog(Deployer.class);
    private final Collection builders;
    private final ConfigurationStore store;
    private final Kernel kernel;

    public Deployer(Collection builders, ConfigurationStore store, Kernel kernel) {
        this.builders = builders;
        this.store = store;
        this.kernel = kernel;
    }

    public List deploy(File moduleFile, File planFile) throws DeploymentException {
        File originalModuleFile = moduleFile;
        File tmpDir = null;
        if (moduleFile != null && !moduleFile.isDirectory()) {
            // todo jar url handling with Sun's VM on Windows leaves a lock on the module file preventing rebuilds
            // to address this we use a gross hack and copy the file to a temporary directory
            // unfortunately the lock on the file will prevent that being deleted properly
            // we need to rewrite deployment so that it does not use jar: urls
            try {
                tmpDir = File.createTempFile("deployer", ".tmpdir");
                tmpDir.delete();
                tmpDir.mkdir();
                File tmpFile = new File(tmpDir, moduleFile.getName());
                DeploymentUtil.copyFile(moduleFile, tmpFile);
                moduleFile = tmpFile;
            } catch (IOException e) {
                throw new DeploymentException(e);
            }
        }

        try {
            return deploy(planFile, moduleFile, null, true, null, null, null, null);
        } catch (DeploymentException e) {
            log.debug("Deployment failed: plan=" + planFile +", module=" + originalModuleFile, e);
            throw e.cleanse();
        } finally {
            if (tmpDir != null) {
                DeploymentUtil.recursiveDelete(tmpDir);
            }
        }
    }

    public List deploy(File planFile, File moduleFile, File targetFile, boolean install, String mainClass, String classPath, String endorsedDirs, String extensionDirs) throws DeploymentException {
        if (planFile == null && moduleFile == null) {
            throw new DeploymentException("No plan or module specified");
        }

        if (planFile != null) {
            if (!planFile.exists()) {
                throw new DeploymentException("Plan file does not exist: " + planFile.getAbsolutePath());
            }
            if (!planFile.isFile()) {
                throw new DeploymentException("Plan file is not a regular file: " + planFile.getAbsolutePath());
            }
        }

        JarFile module = null;
        if (moduleFile != null) {
            if (!moduleFile.exists()) {
                throw new DeploymentException("Module file does not exist: " + moduleFile.getAbsolutePath());
            }
            try {
                module = DeploymentUtil.createJarFile(moduleFile);
            } catch (IOException e) {
                throw new DeploymentException("Cound not open module file: " + moduleFile.getAbsolutePath(), e);
            }
        }

        File configurationDir = null;
        try {
            Object plan = null;
            ConfigurationBuilder builder = null;
            for (Iterator i = builders.iterator(); i.hasNext();) {
                ConfigurationBuilder candidate = (ConfigurationBuilder) i.next();
                plan = candidate.getDeploymentPlan(planFile, module);
                if (plan != null) {
                    builder = candidate;
                    break;
                }
            }
            if (builder == null) {
                throw new DeploymentException("Syntax error in deployment plan or no deployer service available (currently I can't tell the difference):" +
                        (planFile == null ? "" : " planFile=" + planFile.getAbsolutePath()) +
                        (moduleFile == null ? "" : ", moduleFile" + moduleFile.getAbsolutePath()));
            }

            // Make sure this configuration doesn't already exist
            URI configID = builder.getConfigurationID(plan, module);
            try {
                kernel.getGBeanState(Configuration.getConfigurationObjectName(configID));
                throw new DeploymentException("Module "+configID+" already exists in the server.  Try to undeploy it first or use the redeploy command.");
            } catch (GBeanNotFoundException e) {
                // this is good
            }

            // create a configuration dir to write the configuration during the building proces
            configurationDir = store.createNewConfigurationDir();

            // create te meta-inf dir
            File metaInf = new File(configurationDir, "META-INF");
            metaInf.mkdirs();

            // create the manifest
            Manifest manifest;
            if (mainClass != null) {
                manifest = new Manifest();
                Attributes mainAttributes = manifest.getMainAttributes();
                mainAttributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
                if (mainClass != null) {
                    mainAttributes.putValue(Attributes.Name.MAIN_CLASS.toString(), mainClass);
                }
                if (classPath != null) {
                    mainAttributes.putValue(Attributes.Name.CLASS_PATH.toString(), classPath);
                }
                if (endorsedDirs != null) {
                    mainAttributes.putValue(CommandLineManifest.ENDORSED_DIRS.toString(), endorsedDirs);
                }
                if (extensionDirs != null) {
                    mainAttributes.putValue(CommandLineManifest.EXTENSION_DIRS.toString(), extensionDirs);
                }
            } else {
                manifest = null;
            }

            ConfigurationData configurationData = builder.buildConfiguration(plan, module, configurationDir);
            try {
                if (targetFile != null) {
                    ExecutableConfigurationUtil.createExecutableConfiguration(configurationData, manifest, configurationDir, targetFile);
                }
                if (install) {
                    store.install(configurationData, configurationDir);
                    List deployedURIs = new ArrayList();
                    deployedURIs.add(configurationData.getId().toString());
                    // todo this should support a tree structure since configurations could be nested to any depth
                    for (Iterator iterator = configurationData.getChildConfigurations().iterator(); iterator.hasNext();) {
                        ConfigurationData childConfiguration = (ConfigurationData) iterator.next();
                        deployedURIs.add(childConfiguration.getId().toString());
                        // todo install the child conifgurations here
                    }
                    return deployedURIs;
                } else {
                    DeploymentUtil.recursiveDelete(configurationDir);
                    return Collections.EMPTY_LIST;
                }
            } catch (InvalidConfigException e) {
                // unlikely as we just built this
                throw new DeploymentException(e);
            }
        } catch(Throwable e) {
            DeploymentUtil.recursiveDelete(configurationDir);
            if (targetFile != null) {
                targetFile.delete();
            }

            if (e instanceof Error) {
                log.error("Deployment failed due to ", e);
                throw (Error)e;
            } else if (e instanceof DeploymentException) {
                throw (DeploymentException)e;
            } else if (e instanceof Exception) {
                log.error("Deployment failed due to ", e);
                throw new DeploymentException(e);
            }
            throw new Error(e);
        } finally {
            DeploymentUtil.close(module);
        }
    }

    public static final GBeanInfo GBEAN_INFO;

    private static final String DEPLOYER = "Deployer";

    static {
        GBeanInfoBuilder infoFactory = GBeanInfoBuilder.createStatic(Deployer.class, DEPLOYER);

        infoFactory.addAttribute("kernel", Kernel.class, false);
        infoFactory.addOperation("deploy", new Class[]{File.class, File.class});
        infoFactory.addOperation("deploy", new Class[]{File.class, File.class, File.class, boolean.class, String.class, String.class, String.class, String.class});

        infoFactory.addReference("Builders", ConfigurationBuilder.class, "ConfigBuilder");
        infoFactory.addReference("Store", ConfigurationStore.class, "ConfigurationStore");

        infoFactory.setConstructor(new String[]{"Builders", "Store", "kernel"});

        GBEAN_INFO = infoFactory.getBeanInfo();
    }

    public static GBeanInfo getGBeanInfo() {
        return GBEAN_INFO;
    }
}
