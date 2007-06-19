/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.geronimo.connector.outbound.security;

import java.util.Properties;
import javax.resource.spi.ManagedConnectionFactory;

import org.apache.geronimo.security.jaas.LoginModuleGBean;
import org.apache.geronimo.connector.outbound.ManagedConnectionFactoryWrapper;

/**
 * @version $Rev$ $Date$
 */
public class PasswordCredentialLoginModuleWrapper extends LoginModuleGBean {
    public static final String MANAGED_CONNECTION_FACTORY_OPTION = "geronimo.managedconnectionfactory.option";

    public PasswordCredentialLoginModuleWrapper(String loginModuleClass, String objectName, boolean serverSide, boolean wrapPrincipals, Properties options, String loginDomainName, ManagedConnectionFactoryWrapper managedConnectionFactoryWrapper, ClassLoader classLoader) {
        super(loginModuleClass, objectName, serverSide, wrapPrincipals, options, loginDomainName, classLoader);
        ManagedConnectionFactory managedConnectionFactory = managedConnectionFactoryWrapper.$getManagedConnectionFactory();
        getOptions().put(MANAGED_CONNECTION_FACTORY_OPTION, managedConnectionFactory);
    }

}
