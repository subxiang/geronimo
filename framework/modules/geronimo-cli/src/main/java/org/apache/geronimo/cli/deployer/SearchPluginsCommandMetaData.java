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
package org.apache.geronimo.cli.deployer;

import org.apache.geronimo.cli.CLParserException;


/**
 * @version $Rev: 515007 $ $Date: 2007-03-06 18:26:41 +1100 (Tue, 06 Mar 2007) $
 */
public class SearchPluginsCommandMetaData extends BaseCommandMetaData  {
    public static final CommandMetaData META_DATA = new SearchPluginsCommandMetaData();

    private SearchPluginsCommandMetaData() {
        super("search-plugins", "3. Geronimo Plugins", "[MavenRepoURL]",
                "Lists the Geronimo plugins available in a Maven repository "+
                "and lets you select a plugin to download and install.  This "+
                "is used to add new functionality to the Geronimo server.  If " +
                "no repository is specified the default repositories will be " +
                "listed to select from, but this means there must have been " +
                "some default repositories set (by hand or by having the " +
                "console update to the latest defaults).");
    }

    public CommandArgs parse(String[] newArgs) throws CLParserException {
        if (1 < newArgs.length) {
            throw new CLParserException("Only one Maven repository can be specified.");
        }
        return new BaseCommandArgs(newArgs);
    }
    
}
