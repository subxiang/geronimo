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

package org.apache.geronimo.jetty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.security.jacc.WebResourcePermission;
import javax.security.jacc.WebUserDataPermission;

import org.apache.geronimo.security.deploy.DefaultPrincipal;
import org.apache.geronimo.security.deploy.PrincipalInfo;
import org.apache.geronimo.security.deploy.Role;
import org.apache.geronimo.security.deploy.Security;
import org.apache.geronimo.security.deployment.GeronimoSecurityBuilderImpl;
import org.apache.geronimo.security.jacc.ComponentPermissions;


/**
 * Tests the JAAC security for Jetty by using both explicit and auto role mapping
 *
 * @version $Rev$ $Date$
 */
public class SecurityTest extends AbstractWebModuleTest {
    /**
     * Test the explicit map feature.  Only Alan should be able to log in.
     *
     * @throws Exception thrown if an error in the test occurs
     */
    public void testExplicitMapping() throws Exception {
        Security securityConfig = new Security();
        securityConfig.setUseContextHandler(false);

        DefaultPrincipal defaultPrincipal = new DefaultPrincipal();
        PrincipalInfo principalInfo = new PrincipalInfo("org.apache.geronimo.security.realm.providers.GeronimoUserPrincipal", "izumi", false);
        defaultPrincipal.setPrincipal(principalInfo);

        securityConfig.setDefaultPrincipal(defaultPrincipal);

        Role role = new Role();
        role.setRoleName("content-administrator");
        principalInfo = new PrincipalInfo("org.apache.geronimo.security.realm.providers.GeronimoGroupPrincipal", "it", false);
        role.getPrincipals().add(principalInfo);

        securityConfig.getRoleMappings().put(role.getRoleName(), role);

        Map roleDesignates = new HashMap();
        Map principalRoleMap = new HashMap();
        buildPrincipalRoleMap(securityConfig, roleDesignates, principalRoleMap);

        PermissionCollection uncheckedPermissions = new Permissions();

        PermissionCollection excludedPermissions = new Permissions();
        excludedPermissions.add(new WebResourcePermission("/auth/login.html", ""));
        excludedPermissions.add(new WebUserDataPermission("/auth/login.html", ""));

        Map rolePermissions = new HashMap();
        PermissionCollection permissions = new Permissions();
        permissions.add(new WebUserDataPermission("/protected/*", ""));
        permissions.add(new WebResourcePermission("/protected/*", ""));
        rolePermissions.put("content-administrator", permissions);
        rolePermissions.put("auto-administrator", permissions);

        Set securityRoles = new HashSet();
        securityRoles.add("content-administrator");
        securityRoles.add("auto-administrator");

        ComponentPermissions componentPermissions = new ComponentPermissions(excludedPermissions, uncheckedPermissions, rolePermissions);

        startWebApp(roleDesignates, principalRoleMap, componentPermissions, defaultPrincipal, permissions, securityRoles);

        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:5678/test/protected/hello.txt").openConnection();
        connection.setInstanceFollowRedirects(false);
        assertEquals(HttpURLConnection.HTTP_MOVED_TEMP, connection.getResponseCode());

        String cookie = connection.getHeaderField("Set-Cookie");
        cookie = cookie.substring(0, cookie.lastIndexOf(';'));
        String location = connection.getHeaderField("Location");

        connection = (HttpURLConnection) new URL(location).openConnection();
        connection.setInstanceFollowRedirects(false);
        assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());

        location = location.substring(0, location.lastIndexOf('/')) + "/j_security_check?j_username=alan&j_password=starcraft";

        connection = (HttpURLConnection) new URL(location).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Cookie", cookie);
        connection.setInstanceFollowRedirects(false);
        assertEquals(HttpURLConnection.HTTP_MOVED_TEMP, connection.getResponseCode());

        connection = (HttpURLConnection) new URL("http://localhost:5678/test/protected/hello.txt").openConnection();
        connection.setRequestProperty("Cookie", cookie);
        connection.setInstanceFollowRedirects(false);
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());
        assertEquals("Hello World", reader.readLine());
        connection.disconnect();

        connection = (HttpURLConnection) new URL("http://localhost:5678/test/protected/hello.txt").openConnection();
        connection.setInstanceFollowRedirects(false);
        assertEquals(HttpURLConnection.HTTP_MOVED_TEMP, connection.getResponseCode());

        cookie = connection.getHeaderField("Set-Cookie");
        cookie = cookie.substring(0, cookie.lastIndexOf(';'));
        location = connection.getHeaderField("Location");

        connection = (HttpURLConnection) new URL(location).openConnection();
        connection.setInstanceFollowRedirects(false);
        assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());

        location = location.substring(0, location.lastIndexOf('/')) + "/j_security_check?j_username=izumi&j_password=violin";

        connection = (HttpURLConnection) new URL(location).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Cookie", cookie);
        connection.setInstanceFollowRedirects(false);
        assertEquals(HttpURLConnection.HTTP_MOVED_TEMP, connection.getResponseCode());

        try {
            connection = (HttpURLConnection) new URL("http://localhost:5678/test/protected/hello.txt").openConnection();
            connection.setRequestProperty("Cookie", cookie);
            connection.setInstanceFollowRedirects(false);
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            fail("Should throw an IOException for HTTP 403 response");
        } catch (IOException e) {
        }

        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, connection.getResponseCode());
        connection.disconnect();

        stopWebApp();
    }

    protected void startWebApp(Map roleDesignates, Map principalRoleMap, ComponentPermissions componentPermissions, DefaultPrincipal defaultPrincipal, PermissionCollection checked, Set securityRoles) throws Exception {
        JettyWebAppContext app = setUpSecureAppContext(roleDesignates, principalRoleMap, componentPermissions, defaultPrincipal, checked, securityRoles);
        setUpStaticContentServlet(app);
//        start(appName, app);
    }

    protected void stopWebApp() throws Exception {
//        stop(appName);
    }

    protected void setUp() throws Exception {
        super.setUp();
        setUpSecurity();
    }

    protected void tearDown() throws Exception {
        tearDownSecurity();
        super.tearDown();
    }

    //copied from SecurityBuilder
    public void buildPrincipalRoleMap(Security security, Map roleDesignates, Map principalRoleMap) {
        Map roleToPrincipalMap = new HashMap();
        GeronimoSecurityBuilderImpl.buildRolePrincipalMap(security, roleDesignates, roleToPrincipalMap, getClass().getClassLoader());
        invertMap(roleToPrincipalMap, principalRoleMap);
    }

    private static Map invertMap(Map roleToPrincipalMap, Map principalRoleMapping) {
        for (Iterator roles = roleToPrincipalMap.entrySet().iterator(); roles.hasNext();) {
            Map.Entry entry = (Map.Entry) roles.next();
            String role = (String) entry.getKey();
            Set principals = (Set) entry.getValue();
            for (Iterator iter = principals.iterator(); iter.hasNext();) {
                java.security.Principal principal = (java.security.Principal) iter.next();

                HashSet roleSet = (HashSet) principalRoleMapping.get(principal);
                if (roleSet == null) {
                    roleSet = new HashSet();
                    principalRoleMapping.put(principal, roleSet);
                }
                roleSet.add(role);
            }
        }
        return principalRoleMapping;
    }
}
