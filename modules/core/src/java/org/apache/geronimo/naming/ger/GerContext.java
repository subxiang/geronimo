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

package org.apache.geronimo.naming.ger;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.apache.geronimo.naming.java.ReadOnlyContext;

/**
 *
 *
 * @version $Revision: 1.1 $ $Date: 2004/01/14 08:28:33 $
 *
 * */
public class GerContext extends ReadOnlyContext {

    GerContext() {
        super();
    }

    GerContext(GerContext context, Hashtable environment) {
        super(context, environment);
    }

    protected synchronized Map internalBind(String name, Object value) throws NamingException {
        return super.internalBind(name, value);
    }

    protected ReadOnlyContext newContext() {
        return new GerContext();
    }

    protected synchronized Set internalUnbind(String name) throws NamingException {
        assert name != null;
        assert !name.equals("");
        Set removeBindings = new HashSet();
        int pos = name.indexOf('/');
        if (pos == -1) {
            if (treeBindings.remove(name) == null) {
                throw new NamingException("Nothing was bound at " + name);
            }
            bindings.remove(name);
            removeBindings.add(name);
        } else {
            String segment = name.substring(0, pos);
            assert segment != null;
            assert !segment.equals("");
            Object o = treeBindings.get(segment);
            if (o == null) {
                throw new NamingException("No context was bound at " + name);
            } else if (!(o instanceof GerContext)) {
                throw new NamingException("Something else bound where a subcontext should be " + o);
            }
            GerContext gerContext = (GerContext)o;

            String remainder = name.substring(pos + 1);
            Set subBindings = gerContext.internalUnbind(remainder);
            for (Iterator iterator = subBindings.iterator(); iterator.hasNext();) {
                String subName = segment + "/" + (String) iterator.next();
                treeBindings.remove(subName);
                removeBindings.add(subName);
            }
            if (gerContext.bindings.isEmpty()) {
                bindings.remove(segment);
                treeBindings.remove(segment);
                removeBindings.add(segment);
            }
        }
        return removeBindings;
    }


    public synchronized Object lookup(String name) throws NamingException {
        return super.lookup(name);
    }

    public Object lookup(Name name) throws NamingException {
        return super.lookup(name);
    }

    public synchronized Object lookupLink(String name) throws NamingException {
        return super.lookupLink(name);
    }

    public synchronized Name composeName(Name name, Name prefix) throws NamingException {
        return super.composeName(name, prefix);
    }

    public synchronized String composeName(String name, String prefix) throws NamingException {
        return super.composeName(name, prefix);
    }

    public synchronized NamingEnumeration list(String name) throws NamingException {
        return super.list(name);
    }

    public synchronized NamingEnumeration listBindings(String name) throws NamingException {
        return super.listBindings(name);
    }

    public synchronized Object lookupLink(Name name) throws NamingException {
        return super.lookupLink(name);
    }

    public synchronized NamingEnumeration list(Name name) throws NamingException {
        return super.list(name);
    }

    public synchronized NamingEnumeration listBindings(Name name) throws NamingException {
        return super.listBindings(name);
    }

}
