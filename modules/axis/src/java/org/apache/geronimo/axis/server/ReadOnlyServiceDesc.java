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
package org.apache.geronimo.axis.server;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.axis.constants.Style;
import org.apache.axis.constants.Use;
import org.apache.axis.description.JavaServiceDesc;
import org.apache.axis.description.OperationDesc;
import org.apache.axis.description.TypeDesc;
import org.apache.axis.encoding.TypeMapping;
import org.apache.axis.encoding.TypeMappingRegistry;
import org.apache.geronimo.axis.client.TypeInfo;

/**
 * @version $Rev$ $Date$
 */
public class ReadOnlyServiceDesc extends JavaServiceDesc implements Externalizable {
    private JavaServiceDesc serviceDesc;
    private List typeInfo;

    /**
     * Only required as Externalizable.
     */
    public ReadOnlyServiceDesc() {
    }

    public ReadOnlyServiceDesc(JavaServiceDesc serviceDesc, List typeInfo) {
        this.serviceDesc = serviceDesc;
        this.typeInfo = typeInfo;
    }

    public Class getImplClass() {
        return serviceDesc.getImplClass();
    }

    public void setImplClass(Class implClass) {
        serviceDesc.setImplClass(implClass);
    }

    public ArrayList getStopClasses() {
        return serviceDesc.getStopClasses();
    }

    public void setStopClasses(ArrayList stopClasses) {
    }

    public void loadServiceDescByIntrospection() {
        serviceDesc.loadServiceDescByIntrospection();
    }

    public void loadServiceDescByIntrospection(Class implClass) {
        serviceDesc.loadServiceDescByIntrospection(implClass);
    }

    public void loadServiceDescByIntrospection(Class cls, TypeMapping tm) {
        serviceDesc.loadServiceDescByIntrospection(cls, tm);
    }

    public Style getStyle() {
        return serviceDesc.getStyle();
    }

    public void setStyle(Style style) {
    }

    public Use getUse() {
        return serviceDesc.getUse();
    }

    public void setUse(Use use) {
    }

    public String getWSDLFile() {
        return serviceDesc.getWSDLFile();
    }

    public void setWSDLFile(String wsdlFileName) {
    }

    public List getAllowedMethods() {
        return serviceDesc.getAllowedMethods();
    }

    public void setAllowedMethods(List allowedMethods) {
    }

    public TypeMapping getTypeMapping() {
        return serviceDesc.getTypeMapping();
    }

    public void setTypeMapping(TypeMapping tm) {
    }

    public String getName() {
        return serviceDesc.getName();
    }

    public void setName(String name) {
    }

    public String getDocumentation() {
        return serviceDesc.getDocumentation();
    }

    public void setDocumentation(String documentation) {
    }

    public void removeOperationDesc(OperationDesc operation) {
    }

    public void addOperationDesc(OperationDesc operation) {
    }

    public ArrayList getOperations() {
        return serviceDesc.getOperations();
    }

    public OperationDesc[] getOperationsByName(String methodName) {
        return serviceDesc.getOperationsByName(methodName);
    }

    public OperationDesc getOperationByName(String methodName) {
        return serviceDesc.getOperationByName(methodName);
    }

    public OperationDesc getOperationByElementQName(QName qname) {
        return serviceDesc.getOperationByElementQName(qname);
    }

    public OperationDesc[] getOperationsByQName(QName qname) {
        return serviceDesc.getOperationsByQName(qname);
    }

    public void setNamespaceMappings(List namespaces) {
    }

    public String getDefaultNamespace() {
        return serviceDesc.getDefaultNamespace();
    }

    public void setDefaultNamespace(String namespace) {
    }

    public void setProperty(String name, Object value) {
        serviceDesc.setProperty(name, value);
    }

    public Object getProperty(String name) {
        return serviceDesc.getProperty(name);
    }

    public String getEndpointURL() {
        return serviceDesc.getEndpointURL();
    }

    public void setEndpointURL(String endpointURL) {
    }

    public TypeMappingRegistry getTypeMappingRegistry() {
        return serviceDesc.getTypeMappingRegistry();
    }

    public void setTypeMappingRegistry(TypeMappingRegistry tmr) {
    }

    public boolean isInitialized() {
        return serviceDesc.isInitialized();
    }

    public boolean isWrapped() {
        return serviceDesc.isWrapped();
    }

    public List getDisallowedMethods() {
        return serviceDesc.getDisallowedMethods();
    }

    public void setDisallowedMethods(List disallowedMethods) {
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        typeInfo = (List) in.readObject();
        
        // one must register the TypeDesc before to deserialize the JavaServiceDesc
        // as it contains a bunch of BeanXXXFactory instances which need 
        // them registered to properly recreate the state of the factories.
        for (Iterator iter = typeInfo.iterator(); iter.hasNext();) {
            TypeInfo info = (TypeInfo) iter.next();
            TypeDesc.registerTypeDescForClass(info.getClazz(), info.buildTypeDesc());
        }
        
        serviceDesc = (JavaServiceDesc) in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(typeInfo);
        out.writeObject(serviceDesc);
    }
}
