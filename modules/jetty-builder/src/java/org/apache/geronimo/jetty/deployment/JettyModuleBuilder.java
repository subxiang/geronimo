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

package org.apache.geronimo.jetty.deployment;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.geronimo.common.DeploymentException;
import org.apache.geronimo.deployment.service.EnvironmentBuilder;
import org.apache.geronimo.deployment.service.ServiceConfigBuilder;
import org.apache.geronimo.deployment.util.DeploymentUtil;
import org.apache.geronimo.deployment.xbeans.EnvironmentType;
import org.apache.geronimo.deployment.xbeans.GbeanType;
import org.apache.geronimo.deployment.xmlbeans.XmlBeansUtil;
import org.apache.geronimo.gbean.AbstractName;
import org.apache.geronimo.gbean.GBeanData;
import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.gbean.AbstractNameQuery;
import org.apache.geronimo.j2ee.deployment.EARContext;
import org.apache.geronimo.j2ee.deployment.Module;
import org.apache.geronimo.j2ee.deployment.ModuleBuilder;
import org.apache.geronimo.j2ee.deployment.WebModule;
import org.apache.geronimo.j2ee.deployment.WebServiceBuilder;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.jetty.JettyFilterHolder;
import org.apache.geronimo.jetty.JettyFilterMapping;
import org.apache.geronimo.jetty.JettyServletHolder;
import org.apache.geronimo.jetty.JettyWebAppContext;
import org.apache.geronimo.kernel.GBeanAlreadyExistsException;
import org.apache.geronimo.kernel.GBeanNotFoundException;
import org.apache.geronimo.kernel.Kernel;
import org.apache.geronimo.kernel.Naming;
import org.apache.geronimo.kernel.config.ConfigurationData;
import org.apache.geronimo.kernel.repository.Artifact;
import org.apache.geronimo.kernel.repository.Environment;
import org.apache.geronimo.naming.deployment.ENCConfigBuilder;
import org.apache.geronimo.naming.deployment.GBeanResourceEnvironmentBuilder;
import org.apache.geronimo.schema.SchemaConversionUtils;
import org.apache.geronimo.security.deploy.DefaultPrincipal;
import org.apache.geronimo.security.deployment.SecurityBuilder;
import org.apache.geronimo.security.deployment.SecurityConfiguration;
import org.apache.geronimo.security.jacc.ComponentPermissions;
import org.apache.geronimo.transaction.context.OnlineUserTransaction;
import org.apache.geronimo.web.deployment.AbstractWebModuleBuilder;
import org.apache.geronimo.web.deployment.GenericToSpecificPlanConverter;
import org.apache.geronimo.xbeans.geronimo.naming.GerMessageDestinationType;
import org.apache.geronimo.xbeans.geronimo.web.jetty.JettyWebAppDocument;
import org.apache.geronimo.xbeans.geronimo.web.jetty.JettyWebAppType;
import org.apache.geronimo.xbeans.geronimo.web.jetty.config.GerJettyDocument;
import org.apache.geronimo.xbeans.j2ee.DispatcherType;
import org.apache.geronimo.xbeans.j2ee.ErrorPageType;
import org.apache.geronimo.xbeans.j2ee.FilterMappingType;
import org.apache.geronimo.xbeans.j2ee.FilterType;
import org.apache.geronimo.xbeans.j2ee.FormLoginConfigType;
import org.apache.geronimo.xbeans.j2ee.JspConfigType;
import org.apache.geronimo.xbeans.j2ee.ListenerType;
import org.apache.geronimo.xbeans.j2ee.LocaleEncodingMappingListType;
import org.apache.geronimo.xbeans.j2ee.LocaleEncodingMappingType;
import org.apache.geronimo.xbeans.j2ee.LoginConfigType;
import org.apache.geronimo.xbeans.j2ee.MessageDestinationType;
import org.apache.geronimo.xbeans.j2ee.MimeMappingType;
import org.apache.geronimo.xbeans.j2ee.ParamValueType;
import org.apache.geronimo.xbeans.j2ee.ServletMappingType;
import org.apache.geronimo.xbeans.j2ee.ServletType;
import org.apache.geronimo.xbeans.j2ee.TaglibType;
import org.apache.geronimo.xbeans.j2ee.WebAppDocument;
import org.apache.geronimo.xbeans.j2ee.WebAppType;
import org.apache.geronimo.xbeans.j2ee.WelcomeFileListType;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.mortbay.http.BasicAuthenticator;
import org.mortbay.http.ClientCertAuthenticator;
import org.mortbay.http.DigestAuthenticator;
import org.mortbay.jetty.servlet.FormAuthenticator;

import javax.servlet.Servlet;
import javax.transaction.UserTransaction;
import javax.management.ObjectName;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;


/**
 * @version $Rev:385659 $ $Date$
 */
public class JettyModuleBuilder extends AbstractWebModuleBuilder {
    private final static Log log = LogFactory.getLog(JettyModuleBuilder.class);
    private final Environment defaultEnvironment;
    private final AbstractNameQuery jettyContainerObjectName;
    private final Collection defaultServlets;
    private final Collection defaultFilters;
    private final Collection defaultFilterMappings;
    private final GBeanData pojoWebServiceTemplate;
    private final boolean defaultContextPriorityClassloader;

    private final WebServiceBuilder webServiceBuilder;

    private final List defaultWelcomeFiles;
    private final Integer defaultSessionTimeoutSeconds;

    private static final String JETTY_NAMESPACE = JettyWebAppDocument.type.getDocumentElementName().getNamespaceURI();

    public JettyModuleBuilder(Environment defaultEnvironment,
            Integer defaultSessionTimeoutSeconds,
            boolean defaultContextPriorityClassloader,
            List defaultWelcomeFiles,
            AbstractNameQuery jettyContainerName,
            Collection defaultServlets,
            Collection defaultFilters,
            Collection defaultFilterMappings,
            Object pojoWebServiceTemplate,
            WebServiceBuilder webServiceBuilder,
            Kernel kernel) throws GBeanNotFoundException {
        super(kernel);
        this.defaultEnvironment = defaultEnvironment;
        this.defaultSessionTimeoutSeconds = (defaultSessionTimeoutSeconds == null) ? new Integer(30 * 60) : defaultSessionTimeoutSeconds;
        this.defaultContextPriorityClassloader = defaultContextPriorityClassloader;
        this.jettyContainerObjectName = jettyContainerName;
        this.defaultServlets = defaultServlets;
        this.defaultFilters = defaultFilters;
        this.defaultFilterMappings = defaultFilterMappings;
        this.pojoWebServiceTemplate = getGBeanData(kernel, pojoWebServiceTemplate);
        this.webServiceBuilder = webServiceBuilder;

        //todo locale mappings

        this.defaultWelcomeFiles = defaultWelcomeFiles;
    }

    private static GBeanData getGBeanData(Kernel kernel, Object template) throws GBeanNotFoundException {
        if (template == null) {
            return null;
        }
        AbstractName templateName = kernel.getProxyManager().getProxyTarget(template);
        return kernel.getGBeanData(templateName);
    }

    protected Module createModule(Object plan, JarFile moduleFile, String targetPath, URL specDDUrl, boolean standAlone, String contextRoot, AbstractName earName, Naming naming) throws DeploymentException {
        assert moduleFile != null: "moduleFile is null";
        assert targetPath != null: "targetPath is null";
        assert !targetPath.endsWith("/"): "targetPath must not end with a '/'";

        // parse the spec dd
        String specDD;
        WebAppType webApp;
        try {
            if (specDDUrl == null) {
                specDDUrl = DeploymentUtil.createJarURL(moduleFile, "WEB-INF/web.xml");
            }

            // read in the entire specDD as a string, we need this for getDeploymentDescriptor
            // on the J2ee management object
            specDD = DeploymentUtil.readAll(specDDUrl);
        } catch (Exception e) {
            //no web.xml, not for us
            return null;
        }
        //we found web.xml, if it won't parse that's an error.
        try {
            // parse it
            XmlObject parsed = XmlBeansUtil.parse(specDD);
            WebAppDocument webAppDoc = SchemaConversionUtils.convertToServletSchema(parsed);
            webApp = webAppDoc.getWebApp();
        } catch (XmlException xmle) {
            throw new DeploymentException("Error parsing web.xml", xmle);
        }
        check(webApp);

        // parse vendor dd
        JettyWebAppType jettyWebApp = getJettyWebApp(plan, moduleFile, standAlone, targetPath, webApp);
        if (contextRoot == null) {
            if (jettyWebApp.isSetContextRoot()) {
                contextRoot = jettyWebApp.getContextRoot();
            } else {
                contextRoot = determineDefaultContextRoot(webApp, standAlone, moduleFile, targetPath);
            }
        }

        EnvironmentType environmentType = jettyWebApp.getEnvironment();
        Environment environment = EnvironmentBuilder.buildEnvironment(environmentType, defaultEnvironment);
        if (!standAlone && environment.getConfigId() == null) {
            Artifact configID = new Artifact(Artifact.DEFAULT_GROUP_ID, contextRoot, "1", "car");
            environment.setConfigId(configID);
        }
        boolean contextPriorityClassLoader = defaultContextPriorityClassloader;
        if (jettyWebApp.isSetContextPriorityClassloader()) {
            contextPriorityClassLoader = jettyWebApp.getContextPriorityClassloader();
        }
        //TODO decide if we should eliminate this flag as redundant w/environment setting.
        environment.setInverseClassLoading(contextPriorityClassLoader);

        Map servletNameToPathMap = buildServletNameToPathMap(webApp, contextRoot);

        //look for a webservices dd
        Map portMap = Collections.EMPTY_MAP;
        try {
            URL wsDDUrl = DeploymentUtil.createJarURL(moduleFile, "WEB-INF/webservices.xml");
            portMap = webServiceBuilder.parseWebServiceDescriptor(wsDDUrl, moduleFile, false, servletNameToPathMap);
        } catch (MalformedURLException e) {
            //no descriptor
        }
        AbstractName moduleName;
        if (earName == null) {
            earName = naming.createRootName(environment.getConfigId(), NameFactory.NULL, NameFactory.J2EE_APPLICATION);
            moduleName = naming.createChildName(earName, environment.getConfigId().toString(), NameFactory.WEB_MODULE);
        } else {
            moduleName = naming.createChildName(earName, targetPath, NameFactory.WEB_MODULE);
        }

        return new WebModule(standAlone, moduleName, environment, moduleFile, targetPath, webApp, jettyWebApp, specDD, contextRoot, portMap, JETTY_NAMESPACE);
    }

    JettyWebAppType getJettyWebApp(Object plan, JarFile moduleFile, boolean standAlone, String targetPath, WebAppType webApp) throws DeploymentException {
        XmlObject rawPlan = null;
        try {
            // load the geronimo-web.xml from either the supplied plan or from the earFile
            try {
                if (plan instanceof XmlObject) {
                    rawPlan = (XmlObject) plan;
                } else {
                    if (plan != null) {
                        rawPlan = XmlBeansUtil.parse(((File) plan).toURL());
                    } else {
                        URL path = DeploymentUtil.createJarURL(moduleFile, "WEB-INF/geronimo-web.xml");
                        try {
                            rawPlan = XmlBeansUtil.parse(path);
                        } catch (FileNotFoundException e) {
                            path = DeploymentUtil.createJarURL(moduleFile, "WEB-INF/geronimo-jetty.xml");
                            try {
                                rawPlan = XmlBeansUtil.parse(path);
                            } catch (FileNotFoundException e1) {
                                log.warn("Web application does not contain a WEB-INF/geronimo-web.xml deployment plan.  This may or may not be a problem, depending on whether you have things like resource references that need to be resolved.  You can also give the deployer a separate deployment plan file on the command line.");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                log.warn(e);
            }

            JettyWebAppType jettyWebApp;
            if (rawPlan != null) {
                XmlObject webPlan = new GenericToSpecificPlanConverter(GerJettyDocument.type.getDocumentElementName().getNamespaceURI(),
                        JettyWebAppDocument.type.getDocumentElementName().getNamespaceURI(), "jetty").convertToSpecificPlan(rawPlan);
                jettyWebApp = (JettyWebAppType) webPlan.changeType(JettyWebAppType.type);
                SchemaConversionUtils.validateDD(jettyWebApp);
            } else {
                String defaultContextRoot = determineDefaultContextRoot(webApp, standAlone, moduleFile, targetPath);
                jettyWebApp = createDefaultPlan(defaultContextRoot);
            }
            return jettyWebApp;
        } catch (XmlException e) {
            throw new DeploymentException("xml problem", e);
        }
    }

    private JettyWebAppType createDefaultPlan(String contextRoot) {
        JettyWebAppType jettyWebApp = JettyWebAppType.Factory.newInstance();
        jettyWebApp.setContextRoot(contextRoot);
        jettyWebApp.setContextPriorityClassloader(defaultContextPriorityClassloader);
        return jettyWebApp;
    }

    public void initContext(EARContext earContext, Module module, ClassLoader cl) throws DeploymentException {
        WebAppType webApp = (WebAppType) module.getSpecDD();
        MessageDestinationType[] messageDestinations = webApp.getMessageDestinationArray();
        JettyWebAppType gerWebApp = (JettyWebAppType) module.getVendorDD();
        GerMessageDestinationType[] gerMessageDestinations = gerWebApp.getMessageDestinationArray();

        ENCConfigBuilder.registerMessageDestinations(earContext.getRefContext(), module.getName(), messageDestinations, gerMessageDestinations);
        if ((webApp.getSecurityConstraintArray().length > 0 || webApp.getSecurityRoleArray().length > 0) &&
                !gerWebApp.isSetSecurityRealmName()) {
            throw new DeploymentException("web.xml includes security elements but Geronimo deployment plan is not provided or does not contain <security-realm-name> element necessary to configure security accordingly.");
        }
        if (gerWebApp.isSetSecurity()) {
            if (!gerWebApp.isSetSecurityRealmName()) {
                throw new DeploymentException("You have supplied a security configuration for web app " + module.getName() + " but no security-realm-name to allow login");
            }
            SecurityConfiguration securityConfiguration = SecurityBuilder.buildSecurityConfiguration(gerWebApp.getSecurity(), cl);
            earContext.setSecurityConfiguration(securityConfiguration);
        }
    }

    public void addGBeans(EARContext earContext, Module module, ClassLoader cl, Collection repository) throws DeploymentException {
        EARContext moduleContext = module.getEarContext();
        ClassLoader moduleClassLoader = moduleContext.getClassLoader();
        AbstractName moduleName = moduleContext.getModuleName();
        WebModule webModule = (WebModule) module;

        WebAppType webApp = (WebAppType) webModule.getSpecDD();
        JettyWebAppType jettyWebApp = (JettyWebAppType) webModule.getVendorDD();

        GbeanType[] gbeans = jettyWebApp.getGbeanArray();
        ServiceConfigBuilder.addGBeans(gbeans, moduleClassLoader, moduleName, moduleContext);

        UserTransaction userTransaction = new OnlineUserTransaction();
        //this may add to the web classpath with enhanced classes.
        //N.B. we use the ear context which has all the gbeans we could possibly be looking up from this ear.
        Map compContext = buildComponentContext(earContext, webModule, webApp, jettyWebApp, userTransaction, moduleClassLoader);

        GBeanData webModuleData = new GBeanData(moduleName, JettyWebAppContext.GBEAN_INFO);
        try {
            if (moduleContext.getServerName() != null) {
                webModuleData.setReferencePattern("J2EEServer", moduleContext.getServerName());
            }
            if (!module.isStandAlone()) {
                webModuleData.setReferencePattern("J2EEApplication", earContext.getModuleName());
            }

            webModuleData.setAttribute("deploymentDescriptor", module.getOriginalSpecDD());
            Set securityRoles = collectRoleNames(webApp);
            Map rolePermissions = new HashMap();

            webModuleData.setAttribute("uri", URI.create(module.getTargetPath() + "/"));

            String[] hosts = jettyWebApp.getVirtualHostArray();
            for (int i = 0; i < hosts.length; i++) {
                hosts[i] = hosts[i].trim();
            }
            webModuleData.setAttribute("virtualHosts", hosts);

            //session manager
            webModuleData.setAttribute("sessionManager", jettyWebApp.getSessionManager());

            //Add dependencies on managed connection factories and ejbs in this app
            //This is overkill, but allows for people not using java:comp context (even though we don't support it)
            //and sidesteps the problem of circular references between ejbs.
            Set dependencies = findGBeanDependencies(earContext);
            webModuleData.addDependencies(dependencies);

            webModuleData.setAttribute("componentContext", compContext);
            webModuleData.setAttribute("userTransaction", userTransaction);
            //classpath may have been augmented with enhanced classes
//            webModuleData.setAttribute("webClassPath", webModule.getWebClasspath());
            // unsharableResources, applicationManagedSecurityResources
            GBeanResourceEnvironmentBuilder rebuilder = new GBeanResourceEnvironmentBuilder(webModuleData);
            //N.B. use earContext not moduleContext
            ENCConfigBuilder.setResourceEnvironment(webModule.getModuleURI(), rebuilder, webApp.getResourceRefArray(), jettyWebApp.getResourceRefArray());

            webModuleData.setAttribute("contextPath", webModule.getContextRoot());

            webModuleData.setReferencePattern("TransactionContextManager", moduleContext.getTransactionContextManagerObjectName());
            webModuleData.setReferencePattern("TrackedConnectionAssociator", moduleContext.getConnectionTrackerObjectName());
            webModuleData.setReferencePattern("JettyContainer", jettyContainerObjectName);
            //stuff that jetty used to do
            if (webApp.getDisplayNameArray().length > 0) {
                webModuleData.setAttribute("displayName", webApp.getDisplayNameArray()[0].getStringValue());
            }

            ParamValueType[] contextParamArray = webApp.getContextParamArray();
            Map contextParams = new HashMap();
            for (int i = 0; i < contextParamArray.length; i++) {
                ParamValueType contextParam = contextParamArray[i];
                contextParams.put(contextParam.getParamName().getStringValue().trim(), contextParam.getParamValue().getStringValue().trim());
            }
            webModuleData.setAttribute("contextParamMap", contextParams);

            ListenerType[] listenerArray = webApp.getListenerArray();
            Collection listeners = new ArrayList();
            for (int i = 0; i < listenerArray.length; i++) {
                ListenerType listenerType = listenerArray[i];
                listeners.add(listenerType.getListenerClass().getStringValue());
            }
            webModuleData.setAttribute("listenerClassNames", listeners);

            webModuleData.setAttribute("distributable", webApp.getDistributableArray().length == 1 ? Boolean.TRUE : Boolean.FALSE);

            webModuleData.setAttribute("sessionTimeoutSeconds",
                    (webApp.getSessionConfigArray().length == 1 && webApp.getSessionConfigArray(0).getSessionTimeout() != null) ?
                            new Integer(webApp.getSessionConfigArray(0).getSessionTimeout().getBigIntegerValue().intValue() * 60) :
                            defaultSessionTimeoutSeconds);

            MimeMappingType[] mimeMappingArray = webApp.getMimeMappingArray();
            Map mimeMappingMap = new HashMap();
            for (int i = 0; i < mimeMappingArray.length; i++) {
                MimeMappingType mimeMappingType = mimeMappingArray[i];
                mimeMappingMap.put(mimeMappingType.getExtension().getStringValue(), mimeMappingType.getMimeType().getStringValue());
            }
            webModuleData.setAttribute("mimeMap", mimeMappingMap);

            WelcomeFileListType[] welcomeFileArray = webApp.getWelcomeFileListArray();
            List welcomeFiles;
            if (welcomeFileArray.length > 0) {
                welcomeFiles = new ArrayList();
                for (int i = 0; i < welcomeFileArray.length; i++) {
                    String[] welcomeFileListType = welcomeFileArray[i].getWelcomeFileArray();
                    for (int j = 0; j < welcomeFileListType.length; j++) {
                        String welcomeFile = welcomeFileListType[j].trim();
                        welcomeFiles.add(welcomeFile);
                    }
                }
            } else {
                welcomeFiles = new ArrayList(defaultWelcomeFiles);
            }
            webModuleData.setAttribute("welcomeFiles", welcomeFiles.toArray(new String[welcomeFiles.size()]));

            LocaleEncodingMappingListType[] localeEncodingMappingListArray = webApp.getLocaleEncodingMappingListArray();
            Map localeEncodingMappingMap = new HashMap();
            for (int i = 0; i < localeEncodingMappingListArray.length; i++) {
                LocaleEncodingMappingType[] localeEncodingMappingArray = localeEncodingMappingListArray[i].getLocaleEncodingMappingArray();
                for (int j = 0; j < localeEncodingMappingArray.length; j++) {
                    LocaleEncodingMappingType localeEncodingMapping = localeEncodingMappingArray[j];
                    localeEncodingMappingMap.put(localeEncodingMapping.getLocale(), localeEncodingMapping.getEncoding());
                }
            }
            webModuleData.setAttribute("localeEncodingMapping", localeEncodingMappingMap);

            ErrorPageType[] errorPageArray = webApp.getErrorPageArray();
            Map errorPageMap = new HashMap();
            for (int i = 0; i < errorPageArray.length; i++) {
                ErrorPageType errorPageType = errorPageArray[i];
                if (errorPageType.isSetErrorCode()) {
                    errorPageMap.put(errorPageType.getErrorCode().getStringValue(), errorPageType.getLocation().getStringValue());
                } else {
                    errorPageMap.put(errorPageType.getExceptionType().getStringValue(), errorPageType.getLocation().getStringValue());
                }
            }
            webModuleData.setAttribute("errorPages", errorPageMap);

            JspConfigType[] jspConfigArray = webApp.getJspConfigArray();
            if (jspConfigArray.length > 1) {
                throw new DeploymentException("At most one jsp-config element, not " + jspConfigArray.length);
            }
            Map tagLibMap = new HashMap();
            for (int i = 0; i < jspConfigArray.length; i++) {
                TaglibType[] tagLibArray = jspConfigArray[i].getTaglibArray();
                for (int j = 0; j < tagLibArray.length; j++) {
                    TaglibType taglib = tagLibArray[j];
                    tagLibMap.put(taglib.getTaglibUri().getStringValue().trim(), taglib.getTaglibLocation().getStringValue().trim());
                }
            }
            webModuleData.setAttribute("tagLibMap", tagLibMap);

            LoginConfigType[] loginConfigArray = webApp.getLoginConfigArray();
            if (loginConfigArray.length > 1) {
                throw new DeploymentException("At most one login-config element, not " + loginConfigArray.length);
            }
            if (loginConfigArray.length == 1) {
                LoginConfigType loginConfig = loginConfigArray[0];
                if (loginConfig.isSetAuthMethod()) {
                    String authMethod = loginConfig.getAuthMethod().getStringValue();
                    if ("BASIC".equals(authMethod)) {
                        webModuleData.setAttribute("authenticator", new BasicAuthenticator());
                    } else if ("DIGEST".equals(authMethod)) {
                        webModuleData.setAttribute("authenticator", new DigestAuthenticator());
                    } else if ("FORM".equals(authMethod)) {

                        FormAuthenticator formAuthenticator = new FormAuthenticator();
                        webModuleData.setAttribute("authenticator", formAuthenticator);
                        if (loginConfig.isSetFormLoginConfig()) {
                            FormLoginConfigType formLoginConfig = loginConfig.getFormLoginConfig();
                            formAuthenticator.setLoginPage(formLoginConfig.getFormLoginPage().getStringValue());
                            formAuthenticator.setErrorPage(formLoginConfig.getFormErrorPage().getStringValue());
                        }
                    } else if ("CLIENT-CERT".equals(authMethod)) {
                        webModuleData.setAttribute("authenticator", new ClientCertAuthenticator());
                    }
                }
                if (loginConfig.isSetRealmName()) {
                    webModuleData.setAttribute("realmName", loginConfig.getRealmName().getStringValue());
                }

            }
            moduleContext.addGBean(webModuleData);

            // Make sure that servlet mappings point to available servlets
            ServletType[] servletTypes = webApp.getServletArray();
            Set knownServlets = new HashSet();
            for (int i = 0; i < servletTypes.length; i++) {
                ServletType type = servletTypes[i];
                knownServlets.add(type.getServletName().getStringValue().trim());
            }
            //never add a duplicate pattern.
            Set knownServletMappings = new HashSet();

            ServletMappingType[] servletMappingArray = webApp.getServletMappingArray();
            Map servletMappings = new HashMap();
            for (int i = 0; i < servletMappingArray.length; i++) {
                ServletMappingType servletMappingType = servletMappingArray[i];
                String servletName = servletMappingType.getServletName().getStringValue().trim();
                if (!knownServlets.contains(servletName)) {
                    throw new DeploymentException("Servlet mapping refers to servlet '" + servletName + "' but no such servlet was found!");
                }
                String urlPattern = servletMappingType.getUrlPattern().getStringValue().trim();
                if (!knownServletMappings.contains(urlPattern)) {
                    knownServletMappings.add(urlPattern);
                    checkString(urlPattern);
                    Set urlsForServlet = (Set) servletMappings.get(servletName);
                    if (urlsForServlet == null) {
                        urlsForServlet = new HashSet();
                        servletMappings.put(servletName, urlsForServlet);
                    }
                    urlsForServlet.add(urlPattern);
                }
            }

            //"previous" filter mapping for linked list to keep dd's ordering.
            AbstractName previous = null;

            //add default filters
            if (defaultFilters != null) {
                for (Iterator iterator = defaultFilters.iterator(); iterator.hasNext();) {
                    Object defaultFilter = iterator.next();
                    GBeanData filterGBeanData = getGBeanData(kernel, defaultFilter);
                    String filterName = (String) filterGBeanData.getAttribute("filterName");
                    AbstractName defaultFilterAbstractName = earContext.getNaming().createChildName(moduleName, filterName, NameFactory.WEB_FILTER);
                    filterGBeanData.setAbstractName(defaultFilterAbstractName);
                    filterGBeanData.setReferencePattern("JettyServletRegistration", moduleName);
                    moduleContext.addGBean(filterGBeanData);
                    //add a mapping to /*

                    GBeanData filterMappingGBeanData = new GBeanData(JettyFilterMapping.GBEAN_INFO);
                    if (previous != null) {
                        filterMappingGBeanData.setReferencePattern("Previous", previous);
                    }
                    filterMappingGBeanData.setReferencePattern("JettyServletRegistration", moduleName);
                    String urlPattern = "/*";
                    filterMappingGBeanData.setAttribute("urlPattern", urlPattern);
                    AbstractName filterMappingName = earContext.getNaming().createChildName(defaultFilterAbstractName, urlPattern, NameFactory.URL_WEB_FILTER_MAPPING);
                    filterMappingGBeanData.setAbstractName(filterMappingName);
                    previous = filterMappingName;


                    filterMappingGBeanData.setAttribute("requestDispatch", Boolean.TRUE);
                    filterMappingGBeanData.setAttribute("forwardDispatch", Boolean.TRUE);
                    filterMappingGBeanData.setAttribute("includeDispatch", Boolean.TRUE);
                    filterMappingGBeanData.setAttribute("errorDispatch", Boolean.FALSE);
                    filterMappingGBeanData.setReferencePattern("Filter", defaultFilterAbstractName);
                    moduleContext.addGBean(filterMappingGBeanData);
                }
            }

            //add default filtermappings
//            if (defaultFilterMappings != null) {
//                for (Iterator iterator = defaultFilterMappings.iterator(); iterator.hasNext();) {
//                    Object defaultFilterMapping = iterator.next();
//                    GBeanData filterMappingGBeanData = getGBeanData(kernel, defaultFilterMapping);
//                    String filterName = (String) filterMappingGBeanData.getAttribute("filterName");
//                    ObjectName defaultFilterMappingObjectName;
//                    if (filterMappingGBeanData.getAttribute("urlPattern") != null) {
//                        String urlPattern = (String) filterMappingGBeanData.getAttribute("urlPattern");
//                        defaultFilterMappingObjectName = NameFactory.getWebFilterMappingName(null, null, null, null, filterName, null, urlPattern, moduleName);
//                    } else {
//                        Set servletNames = filterMappingGBeanData.getReferencePatterns("Servlet");
//                        if (servletNames == null || servletNames.size() != 1) {
//                            throw new DeploymentException("Exactly one servlet name must be supplied");
//                        }
//                        ObjectName servletObjectName = (ObjectName) servletNames.iterator().next();
//                        String servletName = servletObjectName.getKeyProperty("name");
//                        defaultFilterMappingObjectName = NameFactory.getWebFilterMappingName(null, null, null, null, filterName, servletName, null, moduleName);
//                    }
//                    filterMappingGBeanData.setName(defaultFilterMappingObjectName);
//                    filterMappingGBeanData.setReferencePattern("JettyFilterMappingRegistration", webModuleName);
//                    moduleContext.addGBean(filterMappingGBeanData);
//                }
//            }

            FilterMappingType[] filterMappingArray = webApp.getFilterMappingArray();
            for (int i = 0; i < filterMappingArray.length; i++) {
                FilterMappingType filterMappingType = filterMappingArray[i];
                String filterName = filterMappingType.getFilterName().getStringValue().trim();
                GBeanData filterMappingData = new GBeanData(JettyFilterMapping.GBEAN_INFO);
                if (previous != null) {
                    filterMappingData.setReferencePattern("Previous", previous);
                }
                filterMappingData.setReferencePattern("JettyServletRegistration", moduleName);
                AbstractName filterAbstractName = earContext.getNaming().createChildName(moduleName, filterName, NameFactory.WEB_FILTER);

                AbstractName filterMappingName = null;
                if (filterMappingType.isSetUrlPattern()) {
                    String urlPattern = filterMappingType.getUrlPattern().getStringValue().trim();
                    filterMappingData.setAttribute("urlPattern", urlPattern);
                    filterMappingName = earContext.getNaming().createChildName(filterAbstractName, ObjectName.quote(urlPattern), NameFactory.URL_WEB_FILTER_MAPPING);
                }
                if (filterMappingType.isSetServletName()) {
                    String servletName = filterMappingType.getServletName().getStringValue().trim();
                    AbstractName servletAbstractName = earContext.getNaming().createChildName(moduleName, servletName, NameFactory.SERVLET);
                    filterMappingData.setReferencePattern("Servlet", servletAbstractName);
                    filterMappingName = earContext.getNaming().createChildName(filterAbstractName, servletName, NameFactory.SERVLET_WEB_FILTER_MAPPING);
                }
                filterMappingData.setAbstractName(filterMappingName);
                previous = filterMappingName;

                boolean request = filterMappingType.getDispatcherArray().length == 0;
                boolean forward = false;
                boolean include = false;
                boolean error = false;
                for (int j = 0; j < filterMappingType.getDispatcherArray().length; j++) {
                    DispatcherType dispatcherType = filterMappingType.getDispatcherArray()[j];
                    if (dispatcherType.getStringValue().equals("REQUEST")) {
                        request = true;
                    } else if (dispatcherType.getStringValue().equals("FORWARD")) {
                        forward = true;
                    } else if (dispatcherType.getStringValue().equals("INCLUDE")) {
                        include = true;
                    } else if (dispatcherType.getStringValue().equals("ERROR")) {
                        error = true;
                    }
                }
                filterMappingData.setAttribute("requestDispatch", Boolean.valueOf(request));
                filterMappingData.setAttribute("forwardDispatch", Boolean.valueOf(forward));
                filterMappingData.setAttribute("includeDispatch", Boolean.valueOf(include));
                filterMappingData.setAttribute("errorDispatch", Boolean.valueOf(error));
                filterMappingData.setReferencePattern("Filter", filterAbstractName);
                moduleContext.addGBean(filterMappingData);
            }

            FilterType[] filterArray = webApp.getFilterArray();
            for (int i = 0; i < filterArray.length; i++) {
                FilterType filterType = filterArray[i];
                String filterName = filterType.getFilterName().getStringValue().trim();
                AbstractName filterAbstractName = earContext.getNaming().createChildName(moduleName, filterName, NameFactory.WEB_FILTER);
                GBeanData filterData = new GBeanData(filterAbstractName, JettyFilterHolder.GBEAN_INFO);
                filterData.setAttribute("filterName", filterName);
                filterData.setAttribute("filterClass", filterType.getFilterClass().getStringValue().trim());
                Map initParams = new HashMap();
                ParamValueType[] initParamArray = filterType.getInitParamArray();
                for (int j = 0; j < initParamArray.length; j++) {
                    ParamValueType paramValueType = initParamArray[j];
                    initParams.put(paramValueType.getParamName().getStringValue().trim(), paramValueType.getParamValue().getStringValue().trim());
                }
                filterData.setAttribute("initParams", initParams);
                filterData.setReferencePattern("JettyServletRegistration", moduleName);
                moduleContext.addGBean(filterData);
            }

            //add default servlets
            if (defaultServlets != null) {
                for (Iterator iterator = defaultServlets.iterator(); iterator.hasNext();) {
                    Object defaultServlet = iterator.next();
                    GBeanData servletGBeanData = getGBeanData(kernel, defaultServlet);
                    AbstractName defaultServletObjectName = earContext.getNaming().createChildName(moduleName, (String) servletGBeanData.getAttribute("servletName"), NameFactory.SERVLET);
                    servletGBeanData.setAbstractName(defaultServletObjectName);
                    servletGBeanData.setReferencePattern("JettyServletRegistration", moduleName);
                    Set defaultServletMappings = new HashSet((Collection) servletGBeanData.getAttribute("servletMappings"));
                    defaultServletMappings.removeAll(knownServletMappings);
                    servletGBeanData.setAttribute("servletMappings", defaultServletMappings);
                    moduleContext.addGBean(servletGBeanData);
                }
            }

            //set up servlet gbeans.
            Map portMap = webModule.getPortMap();

            addServlets(moduleName, webModule.getModuleFile(), servletTypes, servletMappings, securityRoles, rolePermissions, portMap, moduleClassLoader, moduleContext);

            if (jettyWebApp.isSetSecurityRealmName()) {
                if (earContext.getSecurityConfiguration() == null) {
                    throw new DeploymentException("You have specified a <security-realm-name> for the webapp " + moduleName + " but no <security> configuration (role mapping) is supplied in the Geronimo plan for the web application (or the Geronimo plan for the EAR if the web app is in an EAR)");
                }
                String securityRealmName = jettyWebApp.getSecurityRealmName().trim();
                webModuleData.setAttribute("securityRealmName", securityRealmName);

                /**
                 * TODO - go back to commented version when possible.
                 */
                String policyContextID = moduleName.toString().replaceAll("[, :]", "_");
                //String policyContextID = webModuleName.getCanonicalName();
                webModuleData.setAttribute("policyContextID", policyContextID);

                ComponentPermissions componentPermissions = buildSpecSecurityConfig(webApp, securityRoles, rolePermissions);
                webModuleData.setAttribute("excludedPermissions", componentPermissions.getExcludedPermissions());
                PermissionCollection checkedPermissions = new Permissions();
                for (Iterator iterator = rolePermissions.values().iterator(); iterator.hasNext();) {
                    PermissionCollection permissionsForRole = (PermissionCollection) iterator.next();
                    for (Enumeration iterator2 = permissionsForRole.elements(); iterator2.hasMoreElements();) {
                        Permission permission = (Permission) iterator2.nextElement();
                        checkedPermissions.add(permission);
                    }
                }
                webModuleData.setAttribute("checkedPermissions", checkedPermissions);

                earContext.addSecurityContext(policyContextID, componentPermissions);
                DefaultPrincipal defaultPrincipal = earContext.getSecurityConfiguration().getDefaultPrincipal();
                webModuleData.setAttribute("defaultPrincipal", defaultPrincipal);

                webModuleData.setReferencePattern("RoleDesignateSource", earContext.getJaccManagerName());
            }
            if (!module.isStandAlone()) {
                ConfigurationData moduleConfigurationData = moduleContext.getConfigurationData();
                earContext.addChildConfiguration(moduleConfigurationData);
            }
        } catch (DeploymentException de) {
            throw de;
        } catch (Exception e) {
            throw new DeploymentException("Unable to initialize webapp GBean", e);
        }
    }

    public String getSchemaNamespace() {
        return JETTY_NAMESPACE;
    }

    /**
     * Adds the provided servlets, taking into account the load-on-startup ordering.
     *
     * @param webModuleName   an <code>ObjectName</code> value
     * @param moduleFile      a <code>JarFile</code> value
     * @param servletTypes    a <code>ServletType[]</code> value, contains the <code>servlet</code> entries from <code>web.xml</code>.
     * @param servletMappings a <code>Map</code> value
     * @param securityRoles   a <code>Set</code> value
     * @param rolePermissions a <code>Map</code> value
     * @param portMap         a <code>Map</code> value
     * @param webClassLoader  a <code>ClassLoader</code> value
     * @param earContext      an <code>EARContext</code> value
     * @throws DeploymentException if an error occurs
     */
    private void addServlets(AbstractName webModuleName,
            JarFile moduleFile,
            ServletType[] servletTypes,
            Map servletMappings,
            Set securityRoles,
            Map rolePermissions, Map portMap,
            ClassLoader webClassLoader,
            EARContext earContext) throws DeploymentException {

        // this TreeSet will order the ServletTypes based on whether
        // they have a load-on-startup element and what its value is
        TreeSet loadOrder = new TreeSet(new StartupOrderComparator());

        // add all of the servlets to the sorted set
        for (int i = 0; i < servletTypes.length; i++) {
            loadOrder.add(servletTypes[i]);
        }

        // now that they're sorted, read them in order and add them to
        // the context.  we'll use a GBean reference to enforce the
        // load order.  Each servlet GBean (except the first) has a
        // reference to the previous GBean.  The kernel will ensure
        // that each "previous" GBean is running before it starts any
        // other GBeans that reference it.  See also
        // o.a.g.jetty.JettyFilterMapping which provided the example
        // of how to do this.
        // http://issues.apache.org/jira/browse/GERONIMO-645
        AbstractName previousServlet = null;
        for (Iterator servlets = loadOrder.iterator(); servlets.hasNext();) {
            ServletType servletType = (ServletType) servlets.next();
            previousServlet = addServlet(webModuleName, moduleFile, previousServlet, servletType, servletMappings, securityRoles, rolePermissions, portMap, webClassLoader, earContext);
        }

        // JACC v1.0 secion B.19
        addUnmappedJSPPermissions(securityRoles, rolePermissions);
    }

    /**
     * @param webModuleName
     * @param moduleFile
     * @param previousServlet
     * @param servletType
     * @param servletMappings
     * @param securityRoles
     * @param rolePermissions
     * @param portMap
     * @param webClassLoader
     * @param earContext
     * @return AbstractName of servlet gbean added
     * @throws DeploymentException
     */
    private AbstractName addServlet(AbstractName webModuleName,
            JarFile moduleFile,
            AbstractName previousServlet,
            ServletType servletType,
            Map servletMappings,
            Set securityRoles,
            Map rolePermissions, Map portMap,
            ClassLoader webClassLoader,
            EARContext earContext) throws DeploymentException {
        String servletName = servletType.getServletName().getStringValue().trim();
        AbstractName servletAbstractName = earContext.getNaming().createChildName(webModuleName, servletName, NameFactory.SERVLET);
        GBeanData servletData;
        if (servletType.isSetServletClass()) {
            String servletClassName = servletType.getServletClass().getStringValue().trim();
            Class servletClass;
            try {
                servletClass = webClassLoader.loadClass(servletClassName);
            } catch (ClassNotFoundException e) {
                throw new DeploymentException("Could not load servlet class " + servletClassName, e);
            }
            Class baseServletClass;
            try {
                baseServletClass = webClassLoader.loadClass(Servlet.class.getName());
            } catch (ClassNotFoundException e) {
                throw new DeploymentException("Could not load javax.servlet.Servlet in web classloader", e);
            }
            if (baseServletClass.isAssignableFrom(servletClass)) {
                servletData = new GBeanData(servletAbstractName, JettyServletHolder.GBEAN_INFO);
                servletData.setAttribute("servletClass", servletClassName);
            } else {
                servletData = new GBeanData(pojoWebServiceTemplate);
                servletData.setAbstractName(servletAbstractName);
                //let the web service builder deal with configuring the gbean with the web service stack
                Object portInfo = portMap.get(servletName);
                if (portInfo == null) {
                    throw new DeploymentException("No web service deployment info for servlet name " + servletName);
                }
                webServiceBuilder.configurePOJO(servletData, moduleFile, portInfo, servletClassName, webClassLoader);
            }
        } else if (servletType.isSetJspFile()) {
            servletData = new GBeanData(servletAbstractName, JettyServletHolder.GBEAN_INFO);
            servletData.setAttribute("jspFile", servletType.getJspFile().getStringValue().trim());
            //TODO MAKE THIS CONFIGURABLE!!! Jetty uses the servlet mapping set up from the default-web.xml
            servletData.setAttribute("servletClass", "org.apache.jasper.servlet.JspServlet");
        } else {
            throw new DeploymentException("Neither servlet class nor jsp file is set for " + servletName);
        }

        // link to previous servlet, if there is one, so that we
        // preserve the <load-on-startup> ordering.
        // http://issues.apache.org/jira/browse/GERONIMO-645
        if (null != previousServlet) {
            servletData.setReferencePattern("Previous", previousServlet);
        }

        //TODO in init param setter, add classpath if jspFile is not null.
        servletData.setReferencePattern("JettyServletRegistration", webModuleName);
        servletData.setAttribute("servletName", servletName);
        Map initParams = new HashMap();
        ParamValueType[] initParamArray = servletType.getInitParamArray();
        for (int j = 0; j < initParamArray.length; j++) {
            ParamValueType paramValueType = initParamArray[j];
            initParams.put(paramValueType.getParamName().getStringValue().trim(), paramValueType.getParamValue().getStringValue().trim());
        }
        servletData.setAttribute("initParams", initParams);
        if (servletType.isSetLoadOnStartup()) {
            Integer loadOnStartup = new Integer(servletType.getLoadOnStartup().getBigIntegerValue().intValue());
            servletData.setAttribute("loadOnStartup", loadOnStartup);
        }

        Set mappings = (Set) servletMappings.get(servletName);
        servletData.setAttribute("servletMappings", mappings == null ? Collections.EMPTY_SET : mappings);

        //run-as
        if (servletType.isSetRunAs()) {
            servletData.setAttribute("runAsRole", servletType.getRunAs().getRoleName().getStringValue().trim());
        }

        processRoleRefPermissions(servletType, securityRoles, rolePermissions);

        try {
            earContext.addGBean(servletData);
        } catch (GBeanAlreadyExistsException e) {
            throw new DeploymentException("Could not add servlet gbean to context", e);
        }
        return servletAbstractName;
    }

    private Map buildComponentContext(EARContext earContext, Module webModule, WebAppType webApp, JettyWebAppType jettyWebApp, UserTransaction userTransaction, ClassLoader cl) throws DeploymentException {
        return ENCConfigBuilder.buildComponentContext(earContext,
                earContext.getConfiguration(),
                webModule,
                userTransaction,
                webApp.getEnvEntryArray(),
                webApp.getEjbRefArray(), jettyWebApp.getEjbRefArray(),
                webApp.getEjbLocalRefArray(), jettyWebApp.getEjbLocalRefArray(),
                webApp.getResourceRefArray(), jettyWebApp.getResourceRefArray(),
                webApp.getResourceEnvRefArray(), jettyWebApp.getResourceEnvRefArray(),
                webApp.getMessageDestinationRefArray(),
                webApp.getServiceRefArray(), jettyWebApp.getServiceRefArray(),
                cl);
    }

    public static final GBeanInfo GBEAN_INFO;

    static {
        GBeanInfoBuilder infoBuilder = GBeanInfoBuilder.createStatic(JettyModuleBuilder.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addAttribute("defaultEnvironment", Environment.class, true, true);
        infoBuilder.addAttribute("defaultSessionTimeoutSeconds", Integer.class, true, true);
        infoBuilder.addAttribute("defaultContextPriorityClassloader", boolean.class, true, true);
        infoBuilder.addAttribute("defaultWelcomeFiles", List.class, true, true);
        infoBuilder.addAttribute("jettyContainerObjectName", AbstractNameQuery.class, true, true);
        infoBuilder.addReference("DefaultServlets", Object.class, NameFactory.DEFAULT_SERVLET);
        infoBuilder.addReference("DefaultFilters", Object.class);
        infoBuilder.addReference("DefaultFilterMappings", Object.class);
        infoBuilder.addReference("PojoWebServiceTemplate", Object.class, "ServletWebServiceTemplate");
        infoBuilder.addReference("WebServiceBuilder", WebServiceBuilder.class, NameFactory.MODULE_BUILDER);
        infoBuilder.addAttribute("kernel", Kernel.class, false);
        infoBuilder.addInterface(ModuleBuilder.class);

        infoBuilder.setConstructor(new String[]{
                "defaultEnvironment",
                "defaultSessionTimeoutSeconds",
                "defaultContextPriorityClassloader",
                "defaultWelcomeFiles",
                "jettyContainerObjectName",
                "DefaultServlets",
                "DefaultFilters",
                "DefaultFilterMappings",
                "PojoWebServiceTemplate",
                "WebServiceBuilder",
                "kernel"});
        GBEAN_INFO = infoBuilder.getBeanInfo();
    }

    public static GBeanInfo getGBeanInfo() {
        return GBEAN_INFO;
    }

    static class StartupOrderComparator implements Comparator {
        /**
         * comparator that compares first on the basis of startup order, and then on the lexicographical
         * ordering of servlet name.  Since the servlet names have a uniqueness constraint, this should
         * provide a total ordering consistent with equals.  All servlets with no startup order are after
         * all servlets with a startup order.
         *
         * @param o1 first ServletType object
         * @param o2 second ServletType object
         * @return an int < 0 if o1 precedes o2, 0 if they are equal, and > 0 if o2 preceeds o1.
         */
        public int compare(Object o1, Object o2) {
            ServletType s1 = (ServletType) o1;
            ServletType s2 = (ServletType) o2;

            // load-on-startup is set for neither.  the
            // ordering at this point doesn't matter, but we
            // should return "0" only if the two objects say
            // they are equal
            if (!s1.isSetLoadOnStartup() && !s2.isSetLoadOnStartup()) {
                return s1.equals(s2) ? 0 : s1.getServletName().getStringValue().trim().compareTo(s2.getServletName().getStringValue().trim());
            }

            // load-on-startup is set for one but not the
            // other.  whichever one is set will be "less
            // than", i.e. it will be loaded first
            if (s1.isSetLoadOnStartup() && !s2.isSetLoadOnStartup()) {
                return -1;
            }
            if (!s1.isSetLoadOnStartup() && s2.isSetLoadOnStartup()) {
                return 1;
            }

            // load-on-startup is set for both.  whichever one
            // has a smaller value is "less than"
            int comp = s1.getLoadOnStartup().getBigIntegerValue().compareTo(s2.getLoadOnStartup().getBigIntegerValue());
            if (comp == 0) {
                return s1.getServletName().getStringValue().trim().compareTo(s2.getServletName().getStringValue().trim());
            }
            return comp;
        }
    }
}
