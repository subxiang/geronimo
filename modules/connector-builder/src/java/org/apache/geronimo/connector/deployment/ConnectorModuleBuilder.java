/**
 *
 * Copyright 2004 The Apache Software Foundation
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
package org.apache.geronimo.connector.deployment;

import java.beans.Introspector;
import java.beans.PropertyEditor;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.naming.Reference;

import org.apache.geronimo.common.propertyeditor.PropertyEditors;
import org.apache.geronimo.connector.ResourceAdapterModuleImpl;
import org.apache.geronimo.connector.AdminObjectWrapper;
import org.apache.geronimo.connector.ActivationSpecWrapper;
import org.apache.geronimo.connector.outbound.JCAConnectionFactoryImpl;
import org.apache.geronimo.connector.outbound.ManagedConnectionFactoryWrapper;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.LocalTransactions;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.NoPool;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.NoTransactions;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.PartitionedPool;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.PoolingSupport;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.SinglePool;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.TransactionLog;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.TransactionSupport;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.XATransactions;
import org.apache.geronimo.connector.outbound.security.PasswordCredentialRealm;
import org.apache.geronimo.deployment.DeploymentException;
import org.apache.geronimo.deployment.service.GBeanHelper;
import org.apache.geronimo.deployment.util.DeploymentUtil;
import org.apache.geronimo.gbean.DynamicGAttributeInfo;
import org.apache.geronimo.gbean.GAttributeInfo;
import org.apache.geronimo.gbean.GBeanData;
import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.gbean.InvalidConfigurationException;
import org.apache.geronimo.gbean.jmx.GBeanMBean;
import org.apache.geronimo.j2ee.deployment.ConnectorModule;
import org.apache.geronimo.j2ee.deployment.EARContext;
import org.apache.geronimo.j2ee.deployment.Module;
import org.apache.geronimo.j2ee.deployment.ModuleBuilder;
import org.apache.geronimo.j2ee.deployment.ResourceReferenceBuilder;
import org.apache.geronimo.j2ee.j2eeobjectnames.J2eeContext;
import org.apache.geronimo.j2ee.j2eeobjectnames.J2eeContextImpl;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.kernel.Kernel;
import org.apache.geronimo.naming.reference.GBeanGetResourceRefAddr;
import org.apache.geronimo.naming.reference.RefAddrContentObjectFactory;
import org.apache.geronimo.schema.SchemaConversionUtils;
import org.apache.geronimo.xbeans.geronimo.GerAdminobjectInstanceType;
import org.apache.geronimo.xbeans.geronimo.GerAdminobjectType;
import org.apache.geronimo.xbeans.geronimo.GerConfigPropertySettingType;
import org.apache.geronimo.xbeans.geronimo.GerConnectionDefinitionType;
import org.apache.geronimo.xbeans.geronimo.GerConnectiondefinitionInstanceType;
import org.apache.geronimo.xbeans.geronimo.GerConnectionmanagerType;
import org.apache.geronimo.xbeans.geronimo.GerConnectorDocument;
import org.apache.geronimo.xbeans.geronimo.GerConnectorType;
import org.apache.geronimo.xbeans.geronimo.GerDependencyType;
import org.apache.geronimo.xbeans.geronimo.GerGbeanType;
import org.apache.geronimo.xbeans.geronimo.GerPartitionedpoolType;
import org.apache.geronimo.xbeans.geronimo.GerResourceadapterType;
import org.apache.geronimo.xbeans.geronimo.GerSinglepoolType;
import org.apache.geronimo.xbeans.j2ee.ActivationspecType;
import org.apache.geronimo.xbeans.j2ee.AdminobjectType;
import org.apache.geronimo.xbeans.j2ee.ConfigPropertyType;
import org.apache.geronimo.xbeans.j2ee.ConnectionDefinitionType;
import org.apache.geronimo.xbeans.j2ee.ConnectorDocument;
import org.apache.geronimo.xbeans.j2ee.ConnectorType;
import org.apache.geronimo.xbeans.j2ee.FullyQualifiedClassType;
import org.apache.geronimo.xbeans.j2ee.MessagelistenerType;
import org.apache.geronimo.xbeans.j2ee.ResourceadapterType;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;

/**
 * @version $Rev$ $Date$
 */
public class ConnectorModuleBuilder implements ModuleBuilder, ResourceReferenceBuilder {
    private static final String BASE_REALM_BRIDGE_NAME = "geronimo.security:service=RealmBridge,name=";
    private static final String BASE_PASSWORD_CREDENTIAL_LOGIN_MODULE_NAME = "geronimo.security:service=Realm,type=PasswordCredential,name=";

    private final int defaultMaxSize;
    private final int defaultMinSize;
    private final int defaultBlockingTimeoutMilliseconds;
    private final int defaultIdleTimeoutMinutes;
    private final boolean defaultXATransactionCaching;
    private final boolean defaultXAThreadCaching;
    private final Kernel kernel;
    private final URI defaultParentId;

    public ConnectorModuleBuilder(URI defaultParentId, int defaultMaxSize, int defaultMinSize, int defaultBlockingTimeoutMilliseconds, int defaultIdleTimeoutMinutes, boolean defaultXATransactionCaching, boolean defaultXAThreadCaching, Kernel kernel) {
        this.defaultParentId = defaultParentId;
        this.defaultMaxSize = defaultMaxSize;
        this.defaultMinSize = defaultMinSize;
        this.defaultBlockingTimeoutMilliseconds = defaultBlockingTimeoutMilliseconds;
        this.defaultIdleTimeoutMinutes = defaultIdleTimeoutMinutes;
        this.defaultXATransactionCaching = defaultXATransactionCaching;
        this.defaultXAThreadCaching = defaultXAThreadCaching;
        this.kernel = kernel;
    }

    public Module createModule(File plan, JarFile moduleFile) throws DeploymentException {
        return createModule(plan, moduleFile, "rar", null, true);
    }

    public Module createModule(Object plan, JarFile moduleFile, String targetPath, URL specDDUrl, URI earConfigId) throws DeploymentException {
        return createModule(plan, moduleFile, targetPath, specDDUrl, false);
    }

    private Module createModule(Object plan, JarFile moduleFile, String targetPath, URL specDDUrl, boolean standAlone) throws DeploymentException {
        assert moduleFile != null: "moduleFile is null";
        assert targetPath != null: "targetPath is null";
        assert !targetPath.endsWith("/"): "targetPath must not end with a '/'";

        String specDD;
        XmlObject connector;
        try {
            if (specDDUrl == null) {
                specDDUrl = DeploymentUtil.createJarURL(moduleFile, "META-INF/ra.xml");
            }

            // read in the entire specDD as a string, we need this for getDeploymentDescriptor
            // on the J2ee management object
            specDD = DeploymentUtil.readAll(specDDUrl);

            // parse it
            XmlObject xmlObject = SchemaConversionUtils.parse(specDD);
            ConnectorDocument connectorDoc = SchemaConversionUtils.convertToConnectorSchema(xmlObject);
            connector = connectorDoc.getConnector();
        } catch (Exception e) {
            return null;
        }

        GerConnectorType gerConnector = null;
        try {
            // load the geronimo-application-client.xml from either the supplied plan or from the earFile
            try {
                if (plan instanceof XmlObject) {
                    gerConnector = (GerConnectorType) SchemaConversionUtils.getNestedObjectAsType((XmlObject) plan,
                            "connector",
                            GerConnectorType.type);
                } else {
                    GerConnectorDocument gerConnectorDoc = null;
                    if (plan != null) {
                        gerConnectorDoc = GerConnectorDocument.Factory.parse((File) plan);
                    } else {
                        URL path = DeploymentUtil.createJarURL(moduleFile, "META-INF/geronimo-ra.xml");
                        gerConnectorDoc = GerConnectorDocument.Factory.parse(path);
                    }
                    if (gerConnectorDoc != null) {
                        gerConnector = gerConnectorDoc.getConnector();
                    }
                }
            } catch (IOException e) {
            }

            // if we got one extract the validate it otherwise create a default one
            if (gerConnector == null) {
                throw new DeploymentException("A connector module must be deployed using a plan");
            }
            SchemaConversionUtils.validateDD(gerConnector);
        } catch (XmlException e) {
            throw new DeploymentException(e);
        }

        // get the ids from either the application plan or for a stand alone module from the specific deployer
        URI configId = null;
        try {
            configId = new URI(gerConnector.getConfigId());
        } catch (URISyntaxException e) {
            throw new DeploymentException("Invalid configId " + gerConnector.getConfigId(), e);
        }

        URI parentId = null;
        if (gerConnector.isSetParentId()) {
            try {
                parentId = new URI(gerConnector.getParentId());
            } catch (URISyntaxException e) {
                throw new DeploymentException("Invalid parentId " + gerConnector.getParentId(), e);
            }
        } else {
            parentId = defaultParentId;
        }

        return new ConnectorModule(standAlone, configId, parentId, moduleFile, targetPath, connector, gerConnector, specDD);
    }

    public void installModule(JarFile earFile, EARContext earContext, Module module) throws DeploymentException {
        try {
            JarFile moduleFile = module.getModuleFile();

            // add the manifest classpath entries declared in the connector to the class loader
            // we have to explicitly add these since we are unpacking the connector module
            // and the url class loader will not pick up a manifiest from an unpacked dir
            earContext.addManifestClassPath(moduleFile, URI.create(module.getTargetPath()));

            URI targetURI = URI.create(module.getTargetPath() + "/");
            Enumeration entries = moduleFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                URI target = targetURI.resolve(entry.getName());
                if (entry.getName().endsWith(".jar")) {
                    earContext.addInclude(target, moduleFile, entry);
                } else {
                    earContext.addFile(target, moduleFile, entry);
                }
            }

            GerConnectorType vendorConnector = (GerConnectorType) module.getVendorDD();
            GerDependencyType[] dependencies = vendorConnector.getDependencyArray();
            for (int i = 0; i < dependencies.length; i++) {
                earContext.addDependency(getDependencyURI(dependencies[i]));
            }
        } catch (IOException e) {
            throw new DeploymentException("Problem deploying connector", e);
        }
    }

    public void initContext(EARContext earContext, Module module, ClassLoader cl) throws DeploymentException {
        J2eeContext earJ2eeContext = earContext.getJ2eeContext();
        J2eeContext moduleJ2eeContext = new J2eeContextImpl(earJ2eeContext.getJ2eeDomainName(), earJ2eeContext.getJ2eeServerName(), earJ2eeContext.getJ2eeApplicationName(), module.getName(), null, null);
        XmlObject specDD = module.getSpecDD();

        //set up the metadata for the ResourceAdapterModule
        ObjectName resourceAdapterModuleName = null;
        try {
            resourceAdapterModuleName = NameFactory.getModuleName(null, null, null, null, NameFactory.RESOURCE_ADAPTER_MODULE, moduleJ2eeContext);
        } catch (MalformedObjectNameException e) {
            throw new DeploymentException("Could not construct module name", e);
        }
        GBeanData resourceAdapterModuleData = new GBeanData(resourceAdapterModuleName, ResourceAdapterModuleImpl.GBEAN_INFO);

        // initalize the GBean
        resourceAdapterModuleData.setReferencePattern(NameFactory.J2EE_SERVER, earContext.getServerObjectName());
        if (!earContext.getJ2EEApplicationName().equals(NameFactory.NULL)) {
            resourceAdapterModuleData.setReferencePattern(NameFactory.J2EE_APPLICATION, earContext.getApplicationObjectName());
        }

        try {
            resourceAdapterModuleData.setAttribute("deploymentDescriptor", module.getOriginalSpecDD());
        } catch (Exception e) {
            throw new DeploymentException("Unable to initialize EJBModule GBean", e);
        }

        ResourceadapterType resourceadapter = ((ConnectorType) specDD).getResourceadapter();
        // Create the resource adapter gbean
        if (resourceadapter.isSetResourceadapterClass()) {
            GBeanInfoBuilder resourceAdapterInfoBuilder = new GBeanInfoBuilder("org.apache.geronimo.connector.ResourceAdapterWrapper", cl);
            GBeanData resourceAdapterGBeanData = setUpDynamicGBean(resourceAdapterInfoBuilder, resourceadapter.getConfigPropertyArray(), cl);

            resourceAdapterGBeanData.setAttribute("resourceAdapterClass", resourceadapter.getResourceadapterClass().getStringValue().trim());
            resourceAdapterModuleData.setAttribute("resourceAdapterGBeanData", resourceAdapterGBeanData);
        }

        if (resourceadapter.isSetInboundResourceadapter() && resourceadapter.getInboundResourceadapter().isSetMessageadapter()) {
            Map activationSpecInfoMap = getActivationSpecInfoMap(resourceadapter.getInboundResourceadapter().getMessageadapter().getMessagelistenerArray(), cl);
            resourceAdapterModuleData.setAttribute("activationSpecInfoMap", activationSpecInfoMap);
        }
        Map adminObjectInfoMap = getAdminObjectInfoMap(resourceadapter.getAdminobjectArray(), cl);
        resourceAdapterModuleData.setAttribute("adminObjectInfoMap", adminObjectInfoMap);
        if (resourceadapter.isSetOutboundResourceadapter()) {
            Map managedConnectionFactoryInfoMap = getManagedConnectionFactoryInfoMap(resourceadapter.getOutboundResourceadapter().getConnectionDefinitionArray(), cl);
            resourceAdapterModuleData.setAttribute("managedConnectionFactoryInfoMap", managedConnectionFactoryInfoMap);
        }

        earContext.getRefContext().addResourceAdapterModuleInfo(resourceAdapterModuleName, resourceAdapterModuleData);

        //register the instances we will create later
        GerConnectorType geronimoConnector = (GerConnectorType) module.getVendorDD();
        GerResourceadapterType[] geronimoResourceAdapters = geronimoConnector.getResourceadapterArray();
        for (int k = 0; k < geronimoResourceAdapters.length; k++) {
            GerResourceadapterType geronimoResourceAdapter = geronimoResourceAdapters[k];

            if (resourceadapter.isSetResourceadapterClass()) {
                // set the resource adapter class and activationSpec info map
                try {
                    if (resourceadapter.isSetInboundResourceadapter() && resourceadapter.getInboundResourceadapter().isSetMessageadapter()) {
                        String resourceAdapterName = geronimoResourceAdapter.getResourceadapterInstance().getResourceadapterName();
                        ObjectName resourceAdapterObjectName = NameFactory.getResourceComponentName(null, null, null, null, resourceAdapterName, NameFactory.JCA_RESOURCE_ADAPTER, moduleJ2eeContext);
                        String containerId = resourceAdapterObjectName.getCanonicalName();
                        earContext.getRefContext().addResourceAdapterId(module.getModuleURI(), resourceAdapterName, containerId);
                    }
                } catch (Exception e) {
                    throw new DeploymentException("Could not set ResourceAdapterClass", e);
                }
            }
            if (geronimoResourceAdapter.isSetOutboundResourceadapter()) {
                GerConnectionDefinitionType[] connectionDefinitions = geronimoResourceAdapter.getOutboundResourceadapter().getConnectionDefinitionArray();
                for (int i = 0; i < connectionDefinitions.length; i++) {
                    GerConnectionDefinitionType connectionDefinition = connectionDefinitions[i];
                    GerConnectiondefinitionInstanceType[] connectionDefinitionInstances = connectionDefinition.getConnectiondefinitionInstanceArray();
                    for (int j = 0; j < connectionDefinitionInstances.length; j++) {
                        GerConnectiondefinitionInstanceType connectionDefinitionInstance = connectionDefinitionInstances[j];
                        String containerId = null;
                        try {
                            containerId = NameFactory.getResourceComponentNameString(null, null, null, null, connectionDefinitionInstance.getName(), NameFactory.JCA_MANAGED_CONNECTION_FACTORY, moduleJ2eeContext);
                        } catch (MalformedObjectNameException e) {
                            throw new DeploymentException("Could not construct resource object name", e);
                        }
                        earContext.getRefContext().addConnectionFactoryId(module.getModuleURI(), connectionDefinitionInstance.getName(), containerId);
                    }
                }
            }
        }
        for (int i = 0; i < geronimoConnector.getAdminobjectArray().length; i++) {
            GerAdminobjectType gerAdminObject = geronimoConnector.getAdminobjectArray()[i];
            for (int j = 0; j < gerAdminObject.getAdminobjectInstanceArray().length; j++) {
                GerAdminobjectInstanceType gerAdminObjectInstance = gerAdminObject.getAdminobjectInstanceArray()[j];

                String adminObjectObjectName = null;
                try {
                    adminObjectObjectName = NameFactory.getResourceComponentNameString(null, null, null, null, gerAdminObjectInstance.getMessageDestinationName(), NameFactory.JCA_ADMIN_OBJECT, moduleJ2eeContext);
                } catch (MalformedObjectNameException e) {
                    throw new DeploymentException("Could not construct resource object name", e);
                }
                earContext.getRefContext().addAdminObjectId(module.getModuleURI(), gerAdminObjectInstance.getMessageDestinationName(), adminObjectObjectName);
            }
        }

    }

    public String addGBeans(EARContext earContext, Module module, ClassLoader cl) throws DeploymentException {
        J2eeContext earJ2eeContext = earContext.getJ2eeContext();
        J2eeContext moduleJ2eeContext = new J2eeContextImpl(earJ2eeContext.getJ2eeDomainName(), earJ2eeContext.getJ2eeServerName(), earJ2eeContext.getJ2eeApplicationName(), module.getName(), null, null);

        XmlObject specDD = module.getSpecDD();

        // build the objectName
        ObjectName resourceAdapterModuleName = null;
        try {
            resourceAdapterModuleName = NameFactory.getModuleName(null, null, null, null, NameFactory.RESOURCE_ADAPTER_MODULE, moduleJ2eeContext);
        } catch (MalformedObjectNameException e) {
            throw new DeploymentException("Could not construct module name", e);
        }
        GBeanData resourceAdapterModuleData = earContext.getRefContext().getResourceAdapterModuleData(resourceAdapterModuleName);

        // add it
        earContext.addGBean(resourceAdapterModuleData, cl);

        GerConnectorType geronimoConnector = (GerConnectorType) module.getVendorDD();

        GerGbeanType[] gbeans = geronimoConnector.getGbeanArray();
        for (int i = 0; i < gbeans.length; i++) {
            GBeanHelper.addGbean(new RARGBeanAdapter(gbeans[i]), cl, earContext);
        }

        addConnectorGBeans(earContext, moduleJ2eeContext, resourceAdapterModuleName, (ConnectorType) specDD, geronimoConnector, cl);

        return null;
    }

    private void addConnectorGBeans(EARContext earContext, J2eeContext moduleJ2eeContext, ObjectName resourceAdapterModuleObjectName, ConnectorType connector, GerConnectorType geronimoConnector, ClassLoader cl) throws DeploymentException {
        ResourceadapterType resourceadapter = connector.getResourceadapter();
        String transactionSupport = resourceadapter.getOutboundResourceadapter().getTransactionSupport().getStringValue().trim();
        GerResourceadapterType[] geronimoResourceAdapters = geronimoConnector.getResourceadapterArray();
        for (int k = 0; k < geronimoResourceAdapters.length; k++) {
            GerResourceadapterType geronimoResourceAdapter = geronimoResourceAdapters[k];

            // Resource Adapter
            ObjectName resourceAdapterObjectName = null;
            if (resourceadapter.isSetResourceadapterClass()) {
                GBeanData resourceAdapterGBeanData = earContext.getRefContext().getResourceAdapterGBeanData(resourceAdapterModuleObjectName);
                GBeanData resourceAdapterInstanceGBeanData = new GBeanData(resourceAdapterGBeanData);

                setDynamicGBeanDataAttributes(resourceAdapterInstanceGBeanData, geronimoResourceAdapter.getResourceadapterInstance().getConfigPropertySettingArray(), cl);

                // set the work manager name
                ObjectName workManagerName = null;
                try {
                    workManagerName = NameFactory.getComponentName(null, null, geronimoResourceAdapter.getResourceadapterInstance().getWorkmanagerName().trim(), NameFactory.JCA_WORK_MANAGER, moduleJ2eeContext);
                } catch (MalformedObjectNameException e) {
                    throw new DeploymentException("Could not construct work manager object name", e);
                }
                resourceAdapterInstanceGBeanData.setReferencePattern("WorkManager", workManagerName);

                String resourceAdapterName = geronimoResourceAdapter.getResourceadapterInstance().getResourceadapterName();
                try {
                    resourceAdapterObjectName = NameFactory.getResourceComponentName(null, null, null, null, resourceAdapterName, NameFactory.JCA_RESOURCE_ADAPTER, moduleJ2eeContext);
                } catch (MalformedObjectNameException e) {
                    throw new DeploymentException("Could not construct resource adapter object name", e);
                }
                resourceAdapterInstanceGBeanData.setName(resourceAdapterObjectName);
                earContext.addGBean(resourceAdapterInstanceGBeanData, cl);
            }

            // Outbound Managed Connection Factories (think JDBC data source or JMS connection factory)

            // ManagedConnectionFactory setup
            if (geronimoResourceAdapter.isSetOutboundResourceadapter()) {
                for (int i = 0; i < geronimoResourceAdapter.getOutboundResourceadapter().getConnectionDefinitionArray().length; i++) {
                    GerConnectionDefinitionType geronimoConnectionDefinition = geronimoResourceAdapter.getOutboundResourceadapter().getConnectionDefinitionArray(i);
                    assert geronimoConnectionDefinition != null: "Null GeronimoConnectionDefinition";

                    String connectionFactoryInterfaceName = geronimoConnectionDefinition.getConnectionfactoryInterface().getStringValue().trim();
                    GBeanData connectionFactoryGBeanData = (GBeanData) earContext.getRefContext().getConnectionFactoryInfo(resourceAdapterModuleObjectName, connectionFactoryInterfaceName);

                    if (connectionFactoryGBeanData == null) {
                        throw new DeploymentException("No connection definition for ConnectionFactory class: " + connectionFactoryInterfaceName);
                    }

                    for (int j = 0; j < geronimoConnectionDefinition.getConnectiondefinitionInstanceArray().length; j++) {
                        GerConnectiondefinitionInstanceType connectionfactoryInstance = geronimoConnectionDefinition.getConnectiondefinitionInstanceArray()[j];

                        addOutboundGBeans(earContext, moduleJ2eeContext, resourceAdapterObjectName, connectionFactoryGBeanData, connectionfactoryInstance, transactionSupport, cl);
                    }
                }
            }
        }
        // admin objects (think message queuse and topics)

        // add configured admin objects
        for (int i = 0; i < geronimoConnector.getAdminobjectArray().length; i++) {
            GerAdminobjectType gerAdminObject = geronimoConnector.getAdminobjectArray()[i];

            String adminObjectInterface = gerAdminObject.getAdminobjectInterface().getStringValue();
            GBeanData adminObjectGBeanData = (GBeanData) earContext.getRefContext().getAdminObjectInfo(resourceAdapterModuleObjectName, adminObjectInterface);

            if (adminObjectGBeanData == null) {
                throw new DeploymentException("No admin object declared for interface: " + adminObjectInterface);
            }

            for (int j = 0; j < gerAdminObject.getAdminobjectInstanceArray().length; j++) {
                GBeanData adminObjectInstanceGBeanData = new GBeanData(adminObjectGBeanData);
                GerAdminobjectInstanceType gerAdminObjectInstance = gerAdminObject.getAdminobjectInstanceArray()[j];
                setDynamicGBeanDataAttributes(adminObjectInstanceGBeanData, gerAdminObjectInstance.getConfigPropertySettingArray(), cl);
                // add it
                ObjectName adminObjectObjectName = null;
                try {
                    adminObjectObjectName = NameFactory.getResourceComponentName(null, null, null, null, gerAdminObjectInstance.getMessageDestinationName(), NameFactory.JCA_ADMIN_OBJECT, moduleJ2eeContext);
                } catch (MalformedObjectNameException e) {
                    throw new DeploymentException("Could not construct admin object object name", e);
                }
                adminObjectInstanceGBeanData.setName(adminObjectObjectName);
                earContext.addGBean(adminObjectInstanceGBeanData, cl);
            }
        }
    }

    private Map getActivationSpecInfoMap(MessagelistenerType[] messagelistenerArray, ClassLoader cl) throws DeploymentException {
        Map activationSpecInfos = new HashMap();
        for (int i = 0; i < messagelistenerArray.length; i++) {
            MessagelistenerType messagelistenerType = messagelistenerArray[i];
            String messageListenerInterface = messagelistenerType.getMessagelistenerType().getStringValue().trim();
            ActivationspecType activationspec = messagelistenerType.getActivationspec();
            String activationSpecClassName = activationspec.getActivationspecClass().getStringValue();
            GBeanInfoBuilder infoBuilder = new GBeanInfoBuilder(ActivationSpecWrapper.class.getName(), cl);

            //add all javabean properties that have both getter and setter.  Ignore the "required" flag from the dd.
            Map getters = new HashMap();
            Set setters = new HashSet();
            Method[] methods = null;
            try {
                Class activationSpecClass = cl.loadClass(activationSpecClassName);
                methods = activationSpecClass.getMethods();
            } catch (ClassNotFoundException e) {
                throw new DeploymentException("Can not load activation spec class", e);
            }
            for (int j = 0; j < methods.length; j++) {
                Method method = methods[j];
                String methodName = method.getName();
                if ((methodName.startsWith("get") || methodName.startsWith("is")) && method.getParameterTypes().length == 0) {
                    String attributeName = (methodName.startsWith("get")) ? methodName.substring(3) : methodName.substring(2);
                    getters.put(Introspector.decapitalize(attributeName), method.getReturnType().getName());
                } else if (methodName.startsWith("set") && method.getParameterTypes().length == 1) {
                    setters.add(Introspector.decapitalize(methodName.substring(3)));
                }
            }
            getters.keySet().retainAll(setters);
            getters.remove("resourceAdapter");

            for (Iterator iterator = getters.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                infoBuilder.addAttribute(new DynamicGAttributeInfo((String) entry.getKey(), (String) entry.getValue(), true, true, true));
            }

            GBeanInfo gbeanInfo = infoBuilder.getBeanInfo();
            try {
                //make sure the class is available, but we don't use it.
                cl.loadClass(activationSpecClassName);
            } catch (ClassNotFoundException e) {
                throw new DeploymentException("Could not load ActivationSpec class", e);
            }
            GBeanData activationSpecInfo = new GBeanData(gbeanInfo);
            activationSpecInfo.setAttribute("activationSpecClass", activationSpecClassName);
            activationSpecInfos.put(messageListenerInterface, activationSpecInfo);
        }
        return activationSpecInfos;
    }

    private Map getManagedConnectionFactoryInfoMap(ConnectionDefinitionType[] connectionDefinitionArray, ClassLoader cl) throws DeploymentException {
        Map managedConnectionFactoryInfos = new HashMap();
        for (int i = 0; i < connectionDefinitionArray.length; i++) {
            ConnectionDefinitionType connectionDefinition = connectionDefinitionArray[i];

            GBeanInfoBuilder adminObjectInfoFactory = new GBeanInfoBuilder(ManagedConnectionFactoryWrapper.class.getName(), cl);
            GBeanData managedConnectionFactoryGBeanData = setUpDynamicGBean(adminObjectInfoFactory, connectionDefinition.getConfigPropertyArray(), cl);

            // set the standard properties
            String connectionfactoryInterface = connectionDefinition.getConnectionfactoryInterface().getStringValue().trim();
            managedConnectionFactoryGBeanData.setAttribute("managedConnectionFactoryClass", connectionDefinition.getManagedconnectionfactoryClass().getStringValue().trim());
            managedConnectionFactoryGBeanData.setAttribute("connectionFactoryInterface", connectionfactoryInterface);
            managedConnectionFactoryGBeanData.setAttribute("connectionFactoryImplClass", connectionDefinition.getConnectionfactoryImplClass().getStringValue().trim());
            managedConnectionFactoryGBeanData.setAttribute("connectionInterface", connectionDefinition.getConnectionInterface().getStringValue().trim());
            managedConnectionFactoryGBeanData.setAttribute("connectionImplClass", connectionDefinition.getConnectionImplClass().getStringValue().trim());
            managedConnectionFactoryInfos.put(connectionfactoryInterface, managedConnectionFactoryGBeanData);
        }
        return managedConnectionFactoryInfos;
    }

    private Map getAdminObjectInfoMap(AdminobjectType[] adminobjectArray, ClassLoader cl) throws DeploymentException {
        Map adminObjectInfos = new HashMap();
        for (int i = 0; i < adminobjectArray.length; i++) {
            AdminobjectType adminObject = adminobjectArray[i];

            GBeanInfoBuilder adminObjectInfoFactory = new GBeanInfoBuilder(AdminObjectWrapper.class.getName(), cl);
            GBeanData adminObjectGBeanData = setUpDynamicGBean(adminObjectInfoFactory, adminObject.getConfigPropertyArray(), cl);

            // set the standard properties
            String adminObjectInterface = adminObject.getAdminobjectInterface().getStringValue().trim();
            adminObjectGBeanData.setAttribute("adminObjectInterface", adminObjectInterface);
            adminObjectGBeanData.setAttribute("adminObjectClass", adminObject.getAdminobjectClass().getStringValue().trim());
            adminObjectInfos.put(adminObjectInterface, adminObjectGBeanData);
        }
        return adminObjectInfos;
    }


    private GBeanData setUpDynamicGBean(GBeanInfoBuilder infoBuilder, ConfigPropertyType[] configProperties, ClassLoader cl) throws DeploymentException {
        for (int i = 0; i < configProperties.length; i++) {
            infoBuilder.addAttribute(new DynamicGAttributeInfo(configProperties[i].getConfigPropertyName().getStringValue().trim(), configProperties[i].getConfigPropertyType().getStringValue().trim(), true, true, true));
        }

        GBeanInfo gbeanInfo = infoBuilder.getBeanInfo();
        GBeanData gbeanData = new GBeanData(gbeanInfo);
        for (int i = 0; i < configProperties.length; i++) {
            if (configProperties[i].isSetConfigPropertyValue()) {
                gbeanData.setAttribute(configProperties[i].getConfigPropertyName().getStringValue(),
                        getValue(configProperties[i].getConfigPropertyType().getStringValue(),
                                configProperties[i].getConfigPropertyValue().getStringValue(),
                                cl));
            }
        }
        return gbeanData;
    }

    private void setDynamicGBeanDataAttributes(GBeanData gbeanData, GerConfigPropertySettingType[] configProperties, ClassLoader cl) throws DeploymentException {

        try {
            for (int i = 0; i < configProperties.length; i++) {
                String name = configProperties[i].getName();
                GAttributeInfo attributeInfo = gbeanData.getGBeanInfo().getAttribute(name);
                if (attributeInfo == null) {
                    throw new DeploymentException("The plan is trying to set attribute: " + name + " which does not exist.  Known attributs are: " + gbeanData.getGBeanInfo().getAttributes());
                }
                String type = attributeInfo.getType();
                gbeanData.setAttribute(name,
                        getValue(type, configProperties[i].getStringValue().trim(), cl));
            }
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentException(e);
        }
    }

    private Object getValue(String type, String value, ClassLoader cl) throws DeploymentException {
        if (value == null) {
            return null;
        }

        Class clazz;
        try {
            clazz = cl.loadClass(type);
        } catch (ClassNotFoundException e) {
            throw new DeploymentException("Could not load attribute class:  type: " + type, e);
        }

        PropertyEditor editor = PropertyEditors.getEditor(clazz);
        editor.setAsText(value);
        return editor.getValue();
    }

    private ObjectName configureConnectionManager(EARContext earContext, J2eeContext j2eeContext, String ddTransactionSupport, GerConnectiondefinitionInstanceType connectionfactoryInstance, ClassLoader cl) throws DeploymentException {
        if (connectionfactoryInstance.getConnectionmanagerRef() != null) {
            //we don't configure anything, just use the supplied gbean
            try {
                return ObjectName.getInstance(connectionfactoryInstance.getConnectionmanagerRef());
            } catch (MalformedObjectNameException e) {
                throw new DeploymentException("Invalid ObjectName string supplied for ConnectionManager reference", e);
            }
        }

        //we configure our connection manager
        GerConnectionmanagerType connectionManager = connectionfactoryInstance.getConnectionmanager();
        GBeanMBean connectionManagerGBean;
        try {
            connectionManagerGBean = new GBeanMBean(GBeanInfo.getGBeanInfo("org.apache.geronimo.connector.outbound.GenericConnectionManager", cl), cl);
        } catch (InvalidConfigurationException e) {
            throw new DeploymentException("Unable to create GMBean", e);
        }
        TransactionSupport transactionSupport = null;
        if (connectionManager.isSetNoTransaction()) {
            transactionSupport = NoTransactions.INSTANCE;
        } else if (connectionManager.isSetLocalTransaction()) {
            transactionSupport = LocalTransactions.INSTANCE;
        } else if (connectionManager.isSetTransactionLog()) {
            transactionSupport = TransactionLog.INSTANCE;
        } else if (connectionManager.isSetXaTransaction()) {
            transactionSupport = new XATransactions(connectionManager.getXaTransaction().isSetTransactionCaching(),
                    connectionManager.getXaTransaction().isSetThreadCaching());
        } else if ("NoTransaction".equals(ddTransactionSupport)) {
            transactionSupport = NoTransactions.INSTANCE;
        } else if ("LocalTransaction".equals(ddTransactionSupport)) {
            transactionSupport = LocalTransactions.INSTANCE;
        } else if ("XATransaction".equals(ddTransactionSupport)) {
            transactionSupport = new XATransactions(defaultXATransactionCaching, defaultXAThreadCaching);
        } else {
            //this should not happen
            throw new DeploymentException("Unexpected transaction support element");
        }
        PoolingSupport pooling = null;
        if (connectionManager.getSinglePool() != null) {
            GerSinglepoolType pool = connectionManager.getSinglePool();

            pooling = new SinglePool(pool.isSetMaxSize() ? pool.getMaxSize() : defaultMaxSize,
                    pool.isSetMinSize() ? pool.getMinSize() : defaultMinSize,
                    pool.isSetBlockingTimeoutMilliseconds() ? pool.getBlockingTimeoutMilliseconds() : defaultBlockingTimeoutMilliseconds,
                    pool.isSetIdleTimeoutMinutes() ? pool.getIdleTimeoutMinutes() : defaultIdleTimeoutMinutes,
                    pool.getMatchOne() != null,
                    pool.getMatchAll() != null,
                    pool.getSelectOneAssumeMatch() != null);
        } else if (connectionManager.getPartitionedPool() != null) {
            GerPartitionedpoolType pool = connectionManager.getPartitionedPool();
            pooling = new PartitionedPool(pool.isSetMaxSize() ? pool.getMaxSize() : defaultMaxSize,
                    pool.isSetMinSize() ? pool.getMinSize() : defaultMinSize,
                    pool.isSetBlockingTimeoutMilliseconds() ? pool.getBlockingTimeoutMilliseconds() : defaultBlockingTimeoutMilliseconds,
                    pool.isSetIdleTimeoutMinutes() ? pool.getIdleTimeoutMinutes() : defaultIdleTimeoutMinutes,
                    pool.getMatchOne() != null,
                    pool.getMatchAll() != null,
                    pool.getSelectOneAssumeMatch() != null,
                    pool.isSetPartitionByConnectionrequestinfo(),
                    pool.isSetPartitionBySubject());
        } else if (connectionManager.getNoPool() != null) {
            pooling = new NoPool();
        } else {
            throw new DeploymentException("Unexpected pooling support element");
        }
        try {
            connectionManagerGBean.setAttribute("name", connectionfactoryInstance.getName());
            connectionManagerGBean.setAttribute("transactionSupport", transactionSupport);
            connectionManagerGBean.setAttribute("pooling", pooling);
            connectionManagerGBean.setReferencePattern("ConnectionTracker", earContext.getConnectionTrackerObjectName());
            if (connectionManager.getRealmBridge() != null) {
                connectionManagerGBean.setReferencePattern("RealmBridge", ObjectName.getInstance(BASE_REALM_BRIDGE_NAME + connectionManager.getRealmBridge()));
            }
            connectionManagerGBean.setReferencePattern("TransactionContextManager", earContext.getTransactionContextManagerObjectName());
        } catch (Exception e) {
            throw new DeploymentException("Problem setting up ConnectionManager", e);
        }

        // add it
        ObjectName connectionManagerObjectName = null;
        try {
            connectionManagerObjectName = NameFactory.getResourceComponentName(null, null, null, null, connectionfactoryInstance.getName(), NameFactory.JCA_CONNECTION_MANAGER, j2eeContext);
        } catch (MalformedObjectNameException e) {
            throw new DeploymentException("Could not construct connection manager object name", e);
        }
        earContext.addGBean(connectionManagerObjectName, connectionManagerGBean);
        return connectionManagerObjectName;
    }

    private void addOutboundGBeans(EARContext earContext,
                                   J2eeContext j2eeContext,
                                   ObjectName resourceAdapterObjectName,
                                   GBeanData managedConnectionFactoryPrototypeGBeanData,
                                   GerConnectiondefinitionInstanceType connectiondefinitionInstance,
                                   String transactionSupport,
                                   ClassLoader cl) throws DeploymentException {
        GBeanData managedConnectionFactoryInstanceGBeanData = new GBeanData(managedConnectionFactoryPrototypeGBeanData);
        // ConnectionManager
        ObjectName connectionManagerObjectName = configureConnectionManager(earContext, j2eeContext, transactionSupport, connectiondefinitionInstance, cl);

        // ManagedConnectionFactory
        setDynamicGBeanDataAttributes(managedConnectionFactoryInstanceGBeanData, connectiondefinitionInstance.getConfigPropertySettingArray(), cl);
        try {
            if (connectiondefinitionInstance.isSetGlobalJndiName()) {
                managedConnectionFactoryInstanceGBeanData.setAttribute("globalJNDIName", connectiondefinitionInstance.getGlobalJndiName().trim());
            }
            if (resourceAdapterObjectName != null) {
                managedConnectionFactoryInstanceGBeanData.setReferencePattern("ResourceAdapterWrapper", resourceAdapterObjectName);
            }
            managedConnectionFactoryInstanceGBeanData.setReferencePattern("ConnectionManagerFactory", connectionManagerObjectName);
            if (connectiondefinitionInstance.getCredentialInterface() != null && "javax.resource.spi.security.PasswordCredential".equals(connectiondefinitionInstance.getCredentialInterface().getStringValue())) {
                GBeanMBean realmGBean = new GBeanMBean(PasswordCredentialRealm.getGBeanInfo(), cl);
                realmGBean.setAttribute("realmName", BASE_PASSWORD_CREDENTIAL_LOGIN_MODULE_NAME + connectiondefinitionInstance.getName());
                ObjectName realmObjectNam = ObjectName.getInstance(BASE_PASSWORD_CREDENTIAL_LOGIN_MODULE_NAME + connectiondefinitionInstance.getName());
                earContext.addGBean(realmObjectNam, realmGBean);
                managedConnectionFactoryInstanceGBeanData.setReferencePattern("ManagedConnectionFactoryListener", realmObjectNam);
            }
            //additional interfaces implemented by connection factory
            FullyQualifiedClassType[] implementedInterfaceElements = connectiondefinitionInstance.getImplementedInterfaceArray();
            String[] implementedInterfaces = new String[implementedInterfaceElements == null ? 0 : implementedInterfaceElements.length];
            for (int i = 0; i < implementedInterfaceElements.length; i++) {
                FullyQualifiedClassType additionalInterfaceType = implementedInterfaceElements[i];
                implementedInterfaces[i] = additionalInterfaceType.getStringValue().trim();
            }
            managedConnectionFactoryInstanceGBeanData.setAttribute("implementedInterfaces", implementedInterfaces);

        } catch (Exception e) {
            throw new DeploymentException(e);
        }

        ObjectName managedConnectionFactoryObjectName = null;
        try {
            managedConnectionFactoryObjectName = NameFactory.getResourceComponentName(null, null, null, null, connectiondefinitionInstance.getName(), NameFactory.JCA_MANAGED_CONNECTION_FACTORY, j2eeContext);
        } catch (MalformedObjectNameException e) {
            throw new DeploymentException("Could not construct managed connection factory object name", e);
        }
        managedConnectionFactoryInstanceGBeanData.setName(managedConnectionFactoryObjectName);
        earContext.addGBean(managedConnectionFactoryInstanceGBeanData, cl);

        // ConnectionFactory
        ObjectName connectionFactoryObjectName = null;
        try {
            connectionFactoryObjectName = NameFactory.getResourceComponentName(null, null, null, null, connectiondefinitionInstance.getName(), NameFactory.JCA_CONNECTION_FACTORY, j2eeContext);
        } catch (MalformedObjectNameException e) {
            throw new DeploymentException("Could not construct connection factory object name", e);
        }
        GBeanData connectionFactoryGBeanData = new GBeanData(connectionFactoryObjectName, JCAConnectionFactoryImpl.GBEAN_INFO);
        connectionFactoryGBeanData.setReferencePattern("J2EEServer", earContext.getServerObjectName());
        connectionFactoryGBeanData.setAttribute("managedConnectionFactory", managedConnectionFactoryObjectName.getCanonicalName());

        earContext.addGBean(connectionFactoryGBeanData, cl);
    }

    private static URI getDependencyURI(GerDependencyType dependency) throws DeploymentException {
        if (dependency.isSetUri()) {
            try {
                return new URI(dependency.getUri());
            } catch (URISyntaxException e) {
                throw new DeploymentException("Invalid dependency URI " + dependency.getUri(), e);
            }
        } else {
            String id = dependency.getGroupId() + "/jars/" + dependency.getArtifactId() + '-' + dependency.getVersion() + ".jar";
            try {
                return new URI(id);
            } catch (URISyntaxException e) {
                throw new DeploymentException("Unable to construct URI for groupId=" + dependency.getGroupId() + ", artifactId=" + dependency.getArtifactId() + ", version=" + dependency.getVersion(), e);
            }
        }
    }

    //ResourceReferenceBuilder implementation
    public Reference createResourceRef(String containerId, Class iface) throws DeploymentException {
        Reference ref = new Reference(null, RefAddrContentObjectFactory.class.getName(), null);
        ref.add(new GBeanGetResourceRefAddr(null, containerId, iface));
        return ref;
    }

    public Reference createAdminObjectRef(String containerId, Class iface) throws DeploymentException {
        Reference ref = new Reference(null, RefAddrContentObjectFactory.class.getName(), null);
        ref.add(new GBeanGetResourceRefAddr(null, containerId, iface));
        return ref;
    }

    public ObjectName locateResourceName(ObjectName query) throws DeploymentException {
        Set names = kernel.listGBeans(query);
        if (names.size() != 1) {
            throw new DeploymentException("Unknown or ambiguous resource name query: " + query + " match count: " + names.size());
        }
        return (ObjectName) names.iterator().next();
    }

    public GBeanData locateActivationSpecInfo(ObjectName resourceAdapterModuleName, String messageListenerInterface) throws DeploymentException {
        Map activationSpecInfos = null;
        try {
            activationSpecInfos = (Map) kernel.getAttribute(resourceAdapterModuleName, "activationSpecInfoMap");
        } catch (Exception e) {
            throw new DeploymentException("Could not get activation spec infos for resource adapter named: " + resourceAdapterModuleName, e);
        }
        return (GBeanData) activationSpecInfos.get(messageListenerInterface);
    }

    public GBeanData locateResourceAdapterGBeanData(ObjectName resourceAdapterModuleName) throws DeploymentException {
        try {
            return (GBeanData) kernel.getAttribute(resourceAdapterModuleName, "resourceAdapterInfo");
        } catch (Exception e) {
            throw new DeploymentException("Could not get resource adapter info for resource adapter named: " + resourceAdapterModuleName, e);
        }
    }

    public GBeanData locateAdminObjectInfo(ObjectName resourceAdapterModuleName, String adminObjectInterfaceName) throws DeploymentException {
        Map activationSpecInfos = null;
        try {
            activationSpecInfos = (Map) kernel.getAttribute(resourceAdapterModuleName, "adminObjectInfoMap");
        } catch (Exception e) {
            throw new DeploymentException("Could not get admin object infos for resource adapter named: " + resourceAdapterModuleName, e);
        }
        return (GBeanData) activationSpecInfos.get(adminObjectInterfaceName);
    }

    public GBeanData locateConnectionFactoryInfo(ObjectName resourceAdapterModuleName, String connectionFactoryInterfaceName) throws DeploymentException {
        Map activationSpecInfos = null;
        try {
            activationSpecInfos = (Map) kernel.getAttribute(resourceAdapterModuleName, "managedConnectionFactoryInfoMap");
        } catch (Exception e) {
            throw new DeploymentException("Could not get connection factory infos for resource adapter named: " + resourceAdapterModuleName, e);
        }
        return (GBeanData) activationSpecInfos.get(connectionFactoryInterfaceName);
    }

    public static final GBeanInfo GBEAN_INFO;

    static {
        GBeanInfoBuilder infoBuilder = new GBeanInfoBuilder(ConnectorModuleBuilder.class);

        infoBuilder.addAttribute("defaultParentId", URI.class, true);
        infoBuilder.addAttribute("defaultMaxSize", int.class, true);
        infoBuilder.addAttribute("defaultMinSize", int.class, true);
        infoBuilder.addAttribute("defaultBlockingTimeoutMilliseconds", int.class, true);
        infoBuilder.addAttribute("defaultIdleTimeoutMinutes", int.class, true);
        infoBuilder.addAttribute("defaultXATransactionCaching", boolean.class, true);
        infoBuilder.addAttribute("defaultXAThreadCaching", boolean.class, true);

        infoBuilder.addAttribute("kernel", Kernel.class, false);

        infoBuilder.addInterface(ModuleBuilder.class);
        infoBuilder.addInterface(ResourceReferenceBuilder.class);

        infoBuilder.setConstructor(new String[]{"defaultParentId", "defaultMaxSize", "defaultMinSize", "defaultBlockingTimeoutMilliseconds", "defaultIdleTimeoutMinutes", "defaultXATransactionCaching", "defaultXAThreadCaching", "kernel"});
        GBEAN_INFO = infoBuilder.getBeanInfo();
    }

    public static GBeanInfo getGBeanInfo
            () {
        return GBEAN_INFO;
    }
}
