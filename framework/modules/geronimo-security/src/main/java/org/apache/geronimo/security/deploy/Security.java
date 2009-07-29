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
package org.apache.geronimo.security.deploy;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


/**
 * @version $Rev$ $Date$
 */
public class Security implements Serializable {

    private boolean doAsCurrentCaller;
    private boolean useContextHandler;
    private String defaultRole;
    private SubjectInfo defaultSubjectInfo;
    private Map<String, Role> roleMappings = new HashMap<String, Role>();
    private Map<String, SubjectInfo> roleSubjectMappings = new HashMap<String, SubjectInfo>();

    public Security() {
    }

    public boolean isDoAsCurrentCaller() {
        return doAsCurrentCaller;
    }

    public void setDoAsCurrentCaller(boolean doAsCurrentCaller) {
        this.doAsCurrentCaller = doAsCurrentCaller;
    }

    public boolean isUseContextHandler() {
        return useContextHandler;
    }

    public void setUseContextHandler(boolean useContextHandler) {
        this.useContextHandler = useContextHandler;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    public Map<String, Role> getRoleMappings() {
        return roleMappings;
    }

    public SubjectInfo getDefaultSubjectInfo() {
        return defaultSubjectInfo;
    }

    public void setDefaultSubjectInfo(SubjectInfo defaultSubjectInfo) {
        this.defaultSubjectInfo = defaultSubjectInfo;
    }

    public Map<String, SubjectInfo> getRoleSubjectMappings() {
        return roleSubjectMappings;
    }
}