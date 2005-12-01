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

package org.apache.geronimo.jetty.connector;

import javax.net.ssl.KeyManagerFactory;

import org.mortbay.http.SslListener;

import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.management.geronimo.WebManager;
import org.apache.geronimo.jetty.JettyContainer;
import org.apache.geronimo.jetty.JettySecureConnector;
import org.apache.geronimo.system.serverinfo.ServerInfo;

/**
 * Implementation of a HTTPS connector based on Jetty's SslConnector (which uses pure JSSE).
 *
 * @version $Rev$ $Date$
 */
public class HTTPSConnector extends JettyConnector implements JettySecureConnector {
    private final SslListener https;
    private final ServerInfo serverInfo;
    private String keystore;
    private String algorithm;

    public HTTPSConnector(JettyContainer container, ServerInfo serverInfo) {
        super(container, new SslListener());
        this.serverInfo = serverInfo;
        https = (SslListener) listener;
    }

    public int getDefaultPort() {
        return 443;
    }

    public String getProtocol() {
        return WebManager.PROTOCOL_HTTPS;
    }

    public String getKeystoreFileName() {
        // this does not delegate to https as it needs to be resolved against ServerInfo
        return keystore;
    }

    public void setKeystoreFileName(String keystore) {
        // this does not delegate to https as it needs to be resolved against ServerInfo
        this.keystore = keystore;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Algorithm to use.
     * As different JVMs have different implementations available, the default algorithm can be used by supplying the value "Default".
     *
     * @param algorithm the algorithm to use, or "Default" to use the default from {@link javax.net.ssl.KeyManagerFactory#getDefaultAlgorithm()}
     */
    public void setAlgorithm(String algorithm) {
        // cache the value so the null
        this.algorithm = algorithm;
        if ("default".equalsIgnoreCase(algorithm)) {
            algorithm = KeyManagerFactory.getDefaultAlgorithm();
        }
        https.setAlgorithm(algorithm);
    }

    public void setKeystorePassword(String password) {
        https.setPassword(password);
    }

    public void setKeyPassword(String password) {
        https.setKeyPassword(password);
    }

    public String getSecureProtocol() {
        return https.getProtocol();
    }

    public void setSecureProtocol(String protocol) {
        https.setProtocol(protocol);
    }

    public String getKeystoreType() {
        return https.getKeystoreType();
    }

    public void setKeystoreType(String keystoreType) {
        https.setKeystoreType(keystoreType);
    }

    public void setClientAuthRequired(boolean needClientAuth) {
        https.setNeedClientAuth(needClientAuth);
    }

    public boolean isClientAuthRequired() {
        return https.getNeedClientAuth();
    }

    public void doStart() throws Exception {
        https.setKeystore(serverInfo.resolvePath(keystore));
        super.doStart();
    }

    public static final GBeanInfo GBEAN_INFO;

    static {
        GBeanInfoBuilder infoFactory = GBeanInfoBuilder.createStatic("Jetty Connector HTTPS", HTTPSConnector.class, JettyConnector.GBEAN_INFO);
        infoFactory.addAttribute("keystoreFileName", String.class, true, true);
        infoFactory.addAttribute("algorithm", String.class, true, true);
        infoFactory.addAttribute("keystorePassword", String.class, true, true);
        infoFactory.addAttribute("keyPassword", String.class, true, true);
        infoFactory.addAttribute("secureProtocol", String.class, true, true);
        infoFactory.addAttribute("keystoreType", String.class, true, true);
        infoFactory.addAttribute("clientAuthRequired", boolean.class, true, true);
        infoFactory.addReference("ServerInfo", ServerInfo.class, NameFactory.GERONIMO_SERVICE);
        infoFactory.addInterface(JettySecureConnector.class);
        infoFactory.setConstructor(new String[]{"JettyContainer", "ServerInfo"});
        GBEAN_INFO = infoFactory.getBeanInfo();
    }

    public static GBeanInfo getGBeanInfo() {
        return GBEAN_INFO;
    }
}
