/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache Geronimo" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache Geronimo", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * ====================================================================
 */
package org.apache.geronimo.console.cli.controller;

import java.io.IOException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.geronimo.console.cli.TextController;
import org.apache.geronimo.console.cli.DeploymentContext;

/**
 * Main screen for operating on an EJB JAR
 *
 * @version $Revision: 1.2 $ $Date: 2003/10/20 02:46:36 $
 */
public class WorkWithEJBJAR extends TextController {
    private static final Log log = LogFactory.getLog(WorkWithEJBJAR.class);

    public WorkWithEJBJAR(DeploymentContext context) {
        super(context);
    }

    public void execute() {
        while(true) {
            context.out.println("\n\nLoaded an EJB JAR.  Working with the "+context.moduleInfo.getFileName()+" deployment descriptor.");
            context.out.println("  UN Edit the standard EJB deployment descriptor ("+context.moduleInfo.getFileName()+")");
            context.out.println("  2) Edit the corresponding server-specific deployment information");
            context.out.println("  3) Load a saved set of server-specific deployment information");
            context.out.println("  4) Save the current set of server-specific deployment information");
            context.out.println("  UN Edit web services deployment information");
            context.out.println("  6) Deploy or redeploy the JAR into the application server");
            context.out.println("  7) Select a new EJB JAR or WAR to work with"); //todo: adjust text when other modules are accepted
            context.out.println("  8) Manage existing deployments in the server");
            String choice;
            while(true) {
                context.out.print("Action ([2-4,6-8] or [B]ack): ");
                context.out.flush();
                try {
                    choice = context.in.readLine().trim().toLowerCase();
                } catch(IOException e) {
                    log.error("Unable to read user input", e);
                    return;
                }
                if(choice.equals("2")) {
                    new EditServerSpecificDD(context).execute();
                    break;
                } else if(choice.equals("3")) {
                    new LoadServerSpecificDD(context).execute();
                    break;
                } else if(choice.equals("4")) {
                    new SaveServerSpecificDD(context).execute();
                    break;
                } else if(choice.equals("6")) {
                    new DeploymentOptions(context).execute();
                    return;
                } else if(choice.equals("7")) {
                    new SelectModule(context).execute();
                    return;
                } else if(choice.equals("8")) { //todo: prompt to save if modifications were made
                    new ControlDeployments(context).execute();
                    break;
                } else if(choice.equals("b")) {
                    context.moduleInfo = null;
                    return;
                }
            }
        }
    }
}
