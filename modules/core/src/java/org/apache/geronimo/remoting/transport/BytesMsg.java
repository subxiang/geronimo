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
package org.apache.geronimo.remoting.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.geronimo.remoting.MarshalledObject;

/**
 * @version $Revision: 1.2 $ $Date: 2003/09/06 13:52:11 $
 */
public class BytesMsg implements Msg {

    ArrayList objectStack = new ArrayList(5);

    /**
     * @see org.apache.geronimo.remoting.transport.Msg#pushMarshaledObject(org.apache.geronimo.remoting.MarshalledObject)
     */
    public void pushMarshaledObject(MarshalledObject mo) throws IOException {
        objectStack.add((BytesMarshalledObject) mo);
    }

    /**
     * @see org.apache.geronimo.remoting.transport.Msg#popMarshaledObject()
     */
    public MarshalledObject popMarshaledObject() throws IOException {
        if (objectStack.size() == 0)
            throw new ArrayIndexOutOfBoundsException("Object stack is empty.");
        return (MarshalledObject) objectStack.remove(objectStack.size() - 1);
    }
    
    public int getStackSize() {
        return objectStack.size();
    }

    /**
     * @see org.apache.geronimo.remoting.transport.Msg#createMsg()
     */
    public Msg createMsg() {
        return new BytesMsg();
    }

    public void writeExternal(DataOutput out) throws IOException {
        out.writeByte(objectStack.size());
        for (int i = 0; i < objectStack.size(); i++) {
            BytesMarshalledObject mo = (BytesMarshalledObject) objectStack.get(i);
            byte[] bs = mo.getBytes();
            out.writeInt(bs.length);
            out.write(bs);
        }
    }

    public void readExternal(DataInput in) throws IOException {
        objectStack.clear();
        int s = in.readByte();
        for (int i = 0; i < s; i++) {
            int l = in.readInt();
            byte t[] = new byte[l];
            in.readFully(t);
            BytesMarshalledObject mo = new BytesMarshalledObject();
            mo.setBytes(t);
            objectStack.add(mo);
        }
    }
    
    public byte[] getAsBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(baos);
        writeExternal(os);
        os.close();
        return baos.toByteArray();
    }
    
    public void setFromBytes(byte data[]) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream is = new DataInputStream(bais);
        readExternal(is);
    }
}