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

package org.apache.geronimo.security.jaas;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import javax.management.ObjectName;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.geronimo.gbean.GBeanData;
import org.apache.geronimo.security.AbstractTest;
import org.apache.geronimo.security.ContextManager;
import org.apache.geronimo.security.IdentificationPrincipal;
import org.apache.geronimo.security.RealmPrincipal;
import org.apache.geronimo.security.DomainPrincipal;
import org.apache.geronimo.security.realm.GenericSecurityRealm;
import org.apache.geronimo.system.serverinfo.BasicServerInfo;
import org.apache.geronimo.system.serverinfo.ServerInfo;


/**
 * @version $Rev$ $Date$
 */
public class LoginPropertiesFileTest extends AbstractTest {

    protected ObjectName serverInfo;
    protected ObjectName loginConfiguration;
    protected ObjectName clientLM;
    protected ObjectName clientCE;
    protected ObjectName testCE;
    protected ObjectName testRealm;

    public void setUp() throws Exception {
        super.setUp();

        GBeanData gbean;

        serverInfo = new ObjectName("geronimo.system:role=ServerInfo");
        gbean = new GBeanData(serverInfo, BasicServerInfo.GBEAN_INFO);
        gbean.setAttribute("baseDirectory", ".");
        kernel.loadGBean(gbean, ServerInfo.class.getClassLoader());
        kernel.startGBean(serverInfo);

        loginConfiguration = new ObjectName("geronimo.security:type=LoginConfiguration");
        gbean = new GBeanData(loginConfiguration, GeronimoLoginConfiguration.getGBeanInfo());
        Set configurations = new HashSet();
        configurations.add(new ObjectName("geronimo.security:type=SecurityRealm,*"));
        configurations.add(new ObjectName("geronimo.security:type=ConfigurationEntry,*"));
        gbean.setReferencePatterns("Configurations", configurations);
        kernel.loadGBean(gbean, GeronimoLoginConfiguration.class.getClassLoader());

        clientLM = new ObjectName("geronimo.security:type=LoginModule,name=properties-client");
        gbean = new GBeanData(clientLM, LoginModuleGBean.getGBeanInfo());
        gbean.setAttribute("loginModuleClass", "org.apache.geronimo.security.jaas.client.JaasLoginCoordinator");
        gbean.setAttribute("serverSide", new Boolean(false));
        Properties props = new Properties();
        props.put("host", "localhost");
        props.put("port", "4242");
        props.put("realm", "properties-realm");
        gbean.setAttribute("options", props);
        kernel.loadGBean(gbean, LoginModuleGBean.class.getClassLoader());

        clientCE = new ObjectName("geronimo.security:type=ConfigurationEntry,jaasId=properties-client");
        gbean = new GBeanData(clientCE, DirectConfigurationEntry.getGBeanInfo());
        gbean.setAttribute("applicationConfigName", "properties-client");
        gbean.setAttribute("controlFlag", LoginModuleControlFlag.REQUIRED);
        gbean.setAttribute("wrapPrincipals", Boolean.TRUE);
        gbean.setReferencePatterns("Module", Collections.singleton(clientLM));
        kernel.loadGBean(gbean, DirectConfigurationEntry.class.getClassLoader());

        testCE = new ObjectName("geronimo.security:type=LoginModule,name=properties");
        gbean = new GBeanData(testCE, LoginModuleGBean.getGBeanInfo());
        gbean.setAttribute("loginModuleClass", "org.apache.geronimo.security.realm.providers.PropertiesFileLoginModule");
        gbean.setAttribute("serverSide", new Boolean(true));
        props = new Properties();
        props.put("usersURI", new File(new File("."), "src/test-data/data/users.properties").toURI().toString());
        props.put("groupsURI", new File(new File("."), "src/test-data/data/groups.properties").toURI().toString());
        gbean.setAttribute("options", props);
        gbean.setAttribute("loginDomainName", "TestProperties");
        gbean.setAttribute("wrapPrincipals", Boolean.TRUE);
        kernel.loadGBean(gbean, LoginModuleGBean.class.getClassLoader());

        ObjectName testUseName = new ObjectName("geronimo.security:type=LoginModuleUse,name=properties");
        gbean = new GBeanData(testUseName, JaasLoginModuleUse.getGBeanInfo());
        gbean.setAttribute("controlFlag", "REQUIRED");
        gbean.setReferencePattern("LoginModule", testCE);
        kernel.loadGBean(gbean, JaasLoginModuleUse.class.getClassLoader());

        testRealm = new ObjectName("geronimo.security:type=SecurityRealm,realm=properties-realm");
        gbean = new GBeanData(testRealm, GenericSecurityRealm.getGBeanInfo());
        gbean.setAttribute("realmName", "properties-realm");
//        gbean.setAttribute("loginModuleConfiguration", props);
        gbean.setReferencePattern("LoginModuleConfiguration", testUseName);
        gbean.setReferencePatterns("ServerInfo", Collections.singleton(serverInfo));
        gbean.setReferencePattern("LoginService", loginService);
        kernel.loadGBean(gbean, GenericSecurityRealm.class.getClassLoader());

        kernel.startGBean(loginConfiguration);
        kernel.startGBean(clientLM);
        kernel.startGBean(clientCE);
        kernel.startGBean(testUseName);
        kernel.startGBean(testCE);
        kernel.startGBean(testRealm);
    }

    public void tearDown() throws Exception {
        kernel.stopGBean(testRealm);
        kernel.stopGBean(testCE);
        kernel.stopGBean(clientCE);
        kernel.stopGBean(clientLM);
        kernel.stopGBean(loginConfiguration);
        kernel.stopGBean(serverInfo);

        kernel.unloadGBean(testCE);
        kernel.unloadGBean(testRealm);
        kernel.unloadGBean(clientCE);
        kernel.unloadGBean(clientLM);
        kernel.unloadGBean(loginConfiguration);
        kernel.unloadGBean(serverInfo);

        super.tearDown();
    }

    public void testLogin() throws Exception {

        LoginContext context = new LoginContext("properties-client", new AbstractTest.UsernamePasswordCallback("alan", "starcraft"));

        context.login();
        Subject subject = context.getSubject();

        assertTrue("expected non-null subject", subject != null);
        assertTrue("subject should have one remote principal", subject.getPrincipals(IdentificationPrincipal.class).size() == 1);
        IdentificationPrincipal remote = (IdentificationPrincipal) subject.getPrincipals(IdentificationPrincipal.class).iterator().next();
        assertTrue("subject should be associated with remote id", ContextManager.getRegisteredSubject(remote.getId()) != null);
        assertEquals("subject should have seven principals (" + subject.getPrincipals().size() + ")", 7, subject.getPrincipals().size());
        assertEquals("subject should have 2 realm principals (" + subject.getPrincipals(RealmPrincipal.class).size() + ")", 2, subject.getPrincipals(RealmPrincipal.class).size());
        assertEquals("subject should have 2 domain principals (" + subject.getPrincipals(DomainPrincipal.class).size() + ")", 2, subject.getPrincipals(DomainPrincipal.class).size());

        subject = ContextManager.getServerSideSubject(subject);

        assertTrue("expected non-null subject", subject != null);
        assertTrue("subject should have one remote principal", subject.getPrincipals(IdentificationPrincipal.class).size() == 1);
        remote = (IdentificationPrincipal) subject.getPrincipals(IdentificationPrincipal.class).iterator().next();
        assertTrue("subject should be associated with remote id", ContextManager.getRegisteredSubject(remote.getId()) != null);
        assertEquals("subject should have seven principals (" + subject.getPrincipals().size() + ")", 7, subject.getPrincipals().size());
        assertEquals("subject should have 2 realm principals (" + subject.getPrincipals(RealmPrincipal.class).size() + ")", 2, subject.getPrincipals(RealmPrincipal.class).size());
        assertEquals("subject should have 2 domain principals (" + subject.getPrincipals(DomainPrincipal.class).size() + ")", 2, subject.getPrincipals(DomainPrincipal.class).size());

        context.logout();

        assertTrue("id of server subject should be null", ContextManager.getSubjectId(subject) == null);
    }

    public void testNullUserLogin() throws Exception {
        LoginContext context = new LoginContext("properties-client", new UsernamePasswordCallback(null, "starcraft"));

        try {
            context.login();
            fail("Should not allow this login with null username");
        } catch (LoginException e) {
        }
    }

    public void testBadUserLogin() throws Exception {
        LoginContext context = new LoginContext("properties-client", new UsernamePasswordCallback("bad", "starcraft"));

        try {
            context.login();
            fail("Should not allow this login with null username");
        } catch (LoginException e) {
        }
    }

    public void testNullPasswordLogin() throws Exception {
        LoginContext context = new LoginContext("properties-client", new UsernamePasswordCallback("alan", null));

        try {
            context.login();
            fail("Should not allow this login with null password");
        } catch (LoginException e) {
        }
    }

    public void testBadPasswordLogin() throws Exception {
        LoginContext context = new LoginContext("properties-client", new UsernamePasswordCallback("alan", "bad"));

        try {
            context.login();
            fail("Should not allow this login with null password");
        } catch (LoginException e) {
        }
    }
}
