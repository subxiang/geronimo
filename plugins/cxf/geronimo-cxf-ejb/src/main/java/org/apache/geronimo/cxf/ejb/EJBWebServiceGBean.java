/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geronimo.cxf.ejb;

import java.net.URL;
import java.util.Collection;
import java.util.Properties;

import javax.naming.Context;

import org.apache.cxf.Bus;
import org.apache.geronimo.cxf.CXFCatalogUtils;
import org.apache.geronimo.cxf.CXFWebServiceContainer;
import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.gbean.GBeanLifecycle;
import org.apache.geronimo.gbean.annotation.GBean;
import org.apache.geronimo.gbean.annotation.ParamReference;
import org.apache.geronimo.gbean.annotation.ParamAttribute;
import org.apache.geronimo.gbean.annotation.ParamSpecial;
import org.apache.geronimo.gbean.annotation.SpecialAttributeType;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.jaxws.JAXWSUtils;
import org.apache.geronimo.jaxws.JNDIResolver;
import org.apache.geronimo.jaxws.PortInfo;
import org.apache.geronimo.jaxws.ServerJNDIResolver;
import org.apache.geronimo.kernel.Kernel;
import org.apache.geronimo.openejb.EjbDeployment;
import org.apache.geronimo.webservices.SoapHandler;
import org.apache.geronimo.security.jaas.ConfigurationFactory;
import org.apache.openejb.DeploymentInfo;

@GBean(j2eeType = NameFactory.WEB_SERVICE_LINK)
public class EJBWebServiceGBean implements GBeanLifecycle {

    private SoapHandler soapHandler;
    private String location;
    private EJBWebServiceContainer container;

    public EJBWebServiceGBean(@ParamReference(name="EjbDeployment")EjbDeployment ejbDeploymentContext,
                              @ParamAttribute(name="portInfo")PortInfo portInfo,
                              @ParamSpecial(type = SpecialAttributeType.kernel)Kernel kernel,
                              @ParamAttribute(name="configurationBaseUrl")URL configurationBaseUrl,
                              @ParamReference(name="WebServiceContainer")Collection<SoapHandler> webContainers,
                              @ParamAttribute(name="policyContextID")String policyContextID,
                              @ParamReference(name="ConfigurationFactory")ConfigurationFactory configurationFactory,
                              @ParamAttribute(name="realmName")String realmName,
                              @ParamAttribute(name="transportGuarantee")String transportGuarantee,
                              @ParamAttribute(name="authMethod")String authMethod,
                              @ParamAttribute(name="protectedMethods")String[] protectedMethods,
                              @ParamAttribute(name="virtualHosts")String[] virtualHosts,
                              @ParamAttribute(name="properties")Properties properties) throws Exception {
        if (ejbDeploymentContext == null || webContainers == null || webContainers.isEmpty() || portInfo == null) {
            return;
        }
                
        this.soapHandler = webContainers.iterator().next();
        this.location = portInfo.getLocation();
        
        assert this.location != null : "null location received";
                
        Class beanClass = ejbDeploymentContext.getBeanClass();    
        Context context = ejbDeploymentContext.getComponentContext();
        
        Bus bus = CXFWebServiceContainer.getBus();
        bus.setExtension(new ServerJNDIResolver(context), JNDIResolver.class);
        bus.setExtension(portInfo, PortInfo.class);  
        bus.setExtension(ejbDeploymentContext.getDeploymentInfo(), DeploymentInfo.class);
        
        ClassLoader classLoader = ejbDeploymentContext.getClassLoader();
        
        URL catalog = JAXWSUtils.getOASISCatalogURL(configurationBaseUrl, 
                                                    classLoader, 
                                                    JAXWSUtils.DEFAULT_CATALOG_EJB);
        if (catalog != null) {
            CXFCatalogUtils.loadOASISCatalog(bus, catalog);
        }
        
        this.container = new EJBWebServiceContainer(bus, configurationBaseUrl, beanClass);
        
        soapHandler.addWebService(this.location, 
                                  virtualHosts, 
                                  this.container,
                                  policyContextID,
                                  configurationFactory,
                                  realmName, 
                                  transportGuarantee, 
                                  authMethod, 
                                  protectedMethods, 
                                  properties,
                                  classLoader);        
    }

    public void doStart() throws Exception {
    }

    public void doStop() throws Exception {        
        if (this.soapHandler != null) {
            this.soapHandler.removeWebService(this.location);
        } 
        if (this.container != null) {
            this.container.destroy();
        }
    }

    public void doFail() {
    }


}