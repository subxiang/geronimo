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
package org.apache.geronimo.jetty.app;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.j2ee.j2eeobjectnames.NameFactory;
import org.apache.geronimo.webservices.WebServiceInvoker;

/**
 * @version $Rev:  $ $Date:  $
 */
public class MockWebServiceInvoker implements WebServiceInvoker {
    public void invoke(InputStream in, OutputStream out) throws Exception {

    }

    public void getWsdl(URI wsdlURi, OutputStream out) throws Exception {

    }

    public static final GBeanInfo GBEAN_INFO;
    static {
        GBeanInfoBuilder infoBuilder = new GBeanInfoBuilder(MockWebServiceInvoker.class, NameFactory.STATELESS_SESSION_BEAN);
        infoBuilder.addInterface(WebServiceInvoker.class);
        GBEAN_INFO = infoBuilder.getBeanInfo();
    }
}
