/**
 *
 * Copyright 2005 The Apache Software Foundation
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
package org.apache.geronimo.system.main;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.geronimo.gbean.AbstractName;
import org.apache.geronimo.gbean.AbstractNameQuery;
import org.apache.geronimo.gbean.GAttributeInfo;
import org.apache.geronimo.gbean.GBeanData;
import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.kernel.Kernel;
import org.apache.geronimo.kernel.management.State;

/**
 * @version $Rev$ $Date$
 */
public class StartupMonitorUtil {
    private final static Log log = LogFactory.getLog(StartupMonitor.class.getName());

    public static synchronized void wrapUp(PrintStream out, Kernel kernel) {
        List apps = new ArrayList();  // type = String (message)
        List webs = new ArrayList();  // type = WebAppInfo
        List ports = new ArrayList(); // type = AddressHolder
        Map containers = new HashMap();
        Map failed = new HashMap();   // key = AbstractName, value = String (message)
        String serverInfo = null;
        try {
            Set gbeans = kernel.listGBeans((AbstractNameQuery) null);
            Map beanInfos = new HashMap(); // key = GBeanInfo, value = List (of attribute names)
            for (Iterator it = gbeans.iterator(); it.hasNext();) {
                AbstractName name = (AbstractName) it.next();
                if (isApplicationModule(name)) {
                    apps.add("    " + decodeModule(name.getNameProperty("j2eeType")) + ": " + name.getNameProperty("name"));
                }
                if (isWebModule(name)) {
                    String context = (String) kernel.getAttribute(name, "contextPath");
                    String containerName = (String) kernel.getAttribute(name, "containerName");
                    webs.add(new WebAppInfo(containerName, name, context));
                }

                int stateValue = kernel.getGBeanState(name);
                if (stateValue != State.RUNNING_INDEX) {
                    GBeanData data = kernel.getGBeanData(name);
                    State state = State.fromInt(stateValue);
                    StringBuffer buf = new StringBuffer();
                    buf.append("(").append(state.getName());
                    // Since it's not unusual for a failure to be caused by a port binding failure
                    //    we'll see if there's a likely looking port attribute in the config data
                    //    for the GBean.  It's a long shot, but hey.
                    if (data != null && data.getAttributes() != null) {
                        Map map = data.getAttributes();
                        for (Iterator it2 = map.keySet().iterator(); it2.hasNext();) {
                            String att = (String) it2.next();
                            if (att.equals("port") || att.indexOf("Port") > -1) {
                                buf.append(",").append(att).append("=").append(map.get(att));
                            }
                        }
                    }
                    buf.append(")");
                    failed.put(name, buf.toString());
                    continue;
                }

                // Check if this is ServerInfo
                GBeanInfo info = kernel.getGBeanInfo(name);
                if (info.getClassName().equals("org.apache.geronimo.system.serverinfo.ServerInfo")) {
                    serverInfo = (String) kernel.getAttribute(name, "version");
                }

                // Look for any SocketAddress properties
                List list = (List) beanInfos.get(info);
                if (list == null) {
                    list = new ArrayList(3);
                    beanInfos.put(info, list);
                    Set atts = info.getAttributes();
                    for (Iterator it2 = atts.iterator(); it2.hasNext();) {
                        GAttributeInfo att = (GAttributeInfo) it2.next();
                        if (att.getType().equals("java.net.InetSocketAddress")) {
                            list.add(att);
                        }
                    }
                }
                for (int i = 0; i < list.size(); i++) {
                    GAttributeInfo att = (GAttributeInfo) list.get(i);
                    try {
                        InetSocketAddress addr = (InetSocketAddress) kernel.getAttribute(name, att.getName());
                        if (addr == null) {
                            log.debug("No value for GBean " + name + " attribute " + att.getName());
                            continue;
                        } else if (addr.getAddress() == null || addr.getAddress().getHostAddress() == null) {
                            log.debug("Null address or host for GBean " + name + " " + att.getName() + ": " + addr.getAddress());
                        }
                        String attName = info.getName();
                        if (list.size() > 1) {
                            attName += " " + decamelize(att.getName());
                        } else if (info.getAttribute("name") != null) {
                            attName += " " + kernel.getAttribute(name, "name");
                        }
                        ports.add(new AddressHolder(attName, addr));
                    } catch (IllegalStateException e) {
                        // We weren't able to load a port for this service -- that's a bummer
                    }
                }
            }
            // Look up a URL for each WebContainer in the server (Manager -< Container -< Connector)
            containers = WebAppUtil.mapContainersToURLs(kernel);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.sort(apps);

        // Helpful output: list of ports we listen on
        if (ports.size() > 0) {
            Collections.sort(ports);
            System.out.println("  Listening on Ports:");
            int max = 0;
            for (int i = 0; i < ports.size(); i++) {
                AddressHolder holder = (AddressHolder) ports.get(i);
                if (holder.getAddress().getAddress() != null && holder.getAddress().getAddress().getHostAddress() != null)
                {
                    max = Math.max(max, holder.getAddress().getAddress().getHostAddress().length());
                }
            }
            for (int i = 0; i < ports.size(); i++) {
                AddressHolder holder = (AddressHolder) ports.get(i);
                StringBuffer buf = new StringBuffer();
                buf.append("   ");
                if (holder.getAddress().getPort() < 10) {
                    buf.append(' ');
                }
                if (holder.getAddress().getPort() < 100) {
                    buf.append(' ');
                }
                if (holder.getAddress().getPort() < 1000) {
                    buf.append(' ');
                }
                if (holder.getAddress().getPort() < 10000) {
                    buf.append(' ');
                }
                buf.append(holder.getAddress().getPort()).append(' ');
                String address = holder.getAddress().getAddress() == null || holder.getAddress().getAddress().getHostAddress() == null ? "" :
                        holder.getAddress().getAddress().getHostAddress();
                buf.append(address);
                for (int j = address.length(); j <= max; j++) {
                    buf.append(' ');
                }
                buf.append(holder.getName());
                out.println(buf.toString());
            }
            out.println();
        }
        // Helpful output: list of applications started
        if (apps.size() > 0) {
            out.println("  Started Application Modules:");
            for (int i = 0; i < apps.size(); i++) {
                out.println((String) apps.get(i));
            }
            out.println();
        }
        // Helpful output: Web URLs
        if (webs.size() > 0) {
            Collections.sort(webs);
            out.println("  Web Applications:");
            for (int i = 0; i < webs.size(); i++) {
                WebAppInfo app = (WebAppInfo) webs.get(i);
                out.println("    " + containers.get(app.getContainerObjectName()) + app.getContext());
            }
            out.println();
        }

        // Helpful output: list of GBeans that did not start
        if (failed.size() > 0) {
            out.println("  WARNING: Some GBeans were not started successfully:");
            for (Iterator it = failed.keySet().iterator(); it.hasNext();) {
                AbstractName name = (AbstractName) it.next();
                String state = (String) failed.get(name);
                if (name.getNameProperty("name") != null) {
                    log.debug("Unable to start " + name + " " + state);
                    out.println("    " + name.getNameProperty("name") + " " + state);
                } else {
                    out.println("    " + name + " " + state);
                }
            }
            out.println();
        }

        StringBuffer msg = new StringBuffer();
        msg.append("Geronimo Application Server started");
        if (serverInfo != null) {
            msg.append(" (version ").append(serverInfo).append(")");
        }
        out.println(msg.toString());
        out.flush();
    }

    private static boolean isApplicationModule(AbstractName abstractName) {
        String type = abstractName.getNameProperty("j2eeType");
        String app = abstractName.getNameProperty("J2EEApplication");
        String name = abstractName.getNameProperty("name");
        if (type != null && (app == null || app.equals("null"))) {
            return (type.equals("WebModule") || type.equals("J2EEApplication") || type.equals("EJBModule") || type.equals("AppClientModule") || type.equals("ResourceAdapterModule")) && !name.startsWith("org/apache/geronimo/System");
        }
        return false;
    }

    private static boolean isWebModule(AbstractName abstractName) {
        String type = abstractName.getNameProperty("j2eeType");
        return type != null && type.equals("WebModule");
    }

    private static String decodeModule(String value) {
        if (value.equals("WebModule")) {
            return "WAR";
        } else if (value.equals("J2EEApplication")) {
            return "EAR";
        } else if (value.equals("EJBModule")) {
            return "JAR";
        } else if (value.equals("AppClientModule")) {
            return "CAR";
        } else if (value.equals("ResourceAdapterModule")) {
            return "RAR";
        } else {
            return "UNK";
        }
    }

    private static String decamelize(String s) {
        if (s == null || s.equals("")) {
            return s;
        }
        StringBuffer buf = new StringBuffer();
        buf.append(Character.toUpperCase(s.charAt(0)));
        for (int i = 1; i < s.length(); i++) {
            if (Character.isUpperCase(s.charAt(i))) {
                if (s.length() > i + 1 && Character.isLowerCase(s.charAt(i + 1))) {
                    buf.append(" ");
                }
            }
            buf.append(s.charAt(i));
        }
        return buf.toString();
    }

    private static class AddressHolder implements Comparable {
        private String name;
        private InetSocketAddress address;

        public AddressHolder(String name, InetSocketAddress address) {
            this.name = name;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public InetSocketAddress getAddress() {
            return address;
        }

        public void setAddress(InetSocketAddress address) {
            this.address = address;
        }

        public int compareTo(Object o) {
            AddressHolder other = (AddressHolder) o;
            int value = address.getPort() - other.address.getPort();
            return value == 0 ? address.getAddress().toString().compareTo(other.address.getAddress().toString()) : value;
        }
    }

    private static class WebAppInfo implements Comparable {
        private String containerObjectName;
        private AbstractName webAppAbstractName;
        private String context;

        public WebAppInfo(String containerObjectName, AbstractName webAppAbstractName, String context) {
            this.containerObjectName = containerObjectName;
            this.webAppAbstractName = webAppAbstractName;
            this.context = context;
        }

        public String getContainerObjectName() {
            return containerObjectName;
        }

        public AbstractName getWebAppAbstractName() {
            return webAppAbstractName;
        }

        public String getContext() {
            return context;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WebAppInfo)) return false;

            final WebAppInfo webAppInfo = (WebAppInfo) o;

            if (!containerObjectName.equals(webAppInfo.containerObjectName)) return false;
            if (!context.equals(webAppInfo.context)) return false;
            return webAppAbstractName.equals(webAppInfo.webAppAbstractName);
        }

        public int hashCode() {
            int result;
            result = containerObjectName.hashCode();
            result = 29 * result + webAppAbstractName.hashCode();
            result = 29 * result + context.hashCode();
            return result;
        }

        public int compareTo(Object o) {
            WebAppInfo other = (WebAppInfo) o;
            int test = containerObjectName.compareTo(other.containerObjectName);
            if (test != 0) return test;
            return context.compareTo(other.context);
        }
    }
}
