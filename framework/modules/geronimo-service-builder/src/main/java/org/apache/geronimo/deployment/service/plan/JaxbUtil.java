/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.geronimo.deployment.service.plan;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * @version $Rev:$ $Date:$
 */
public class JaxbUtil {

    public static final XMLInputFactory XMLINPUT_FACTORY = XMLInputFactory.newInstance();
    private static final JAXBContext MODULE_CONTEXT;
    private static final JAXBContext JAVABEAN_CONTEXT;
    static {
        try {
            MODULE_CONTEXT = JAXBContext.newInstance(ModuleType.class);
            JAVABEAN_CONTEXT = JAXBContext.newInstance(JavabeanType.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public static ModuleType unmarshalModule(InputStream in, boolean validate) throws XMLStreamException, JAXBException {
        XMLStreamReader xmlStream = XMLINPUT_FACTORY.createXMLStreamReader(in);
        Unmarshaller unmarshaller = MODULE_CONTEXT.createUnmarshaller();
        JAXBElement<ModuleType> element = unmarshaller.unmarshal(xmlStream, ModuleType.class);
        ModuleType moduleType = element.getValue();
        return moduleType;
    }

    public static JavabeanType unmarshalJavabean(Reader in, boolean validate) throws XMLStreamException, JAXBException {
        XMLStreamReader xmlStream = XMLINPUT_FACTORY.createXMLStreamReader(in);
        Unmarshaller unmarshaller = JAVABEAN_CONTEXT.createUnmarshaller();
        JAXBElement<JavabeanType> element = unmarshaller.unmarshal(xmlStream, JavabeanType.class);
        JavabeanType javabeanType = element.getValue();
        return javabeanType;
    }

    public static <T> void marshal(Class<T> type, Object object, Writer out) throws JAXBException {
        JAXBContext ctx2 = JAXBContext.newInstance(type);
        Marshaller marshaller = ctx2.createMarshaller();

        marshaller.setProperty("jaxb.formatted.output", true);

        marshaller.marshal(object, out);
    }


    /**
     * Read in a T from the input stream.
     *
     * @param type     Class of object to be read in
     * @param in       input stream to read
     * @param validate whether to validate the input.
     * @param <T>      class of object to be returned
     * @return a T read from the input stream
     * @throws javax.xml.parsers.ParserConfigurationException is the SAX parser can not be configured
     * @throws org.xml.sax.SAXException                 if there is an xml problem
     * @throws JAXBException                if the xml cannot be marshalled into a T.
     */
    public static <T> T unmarshal(Class<T> type, InputStream in, boolean validate) throws ParserConfigurationException, SAXException, JAXBException {
        InputSource inputSource = new InputSource(in);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(validate);
        SAXParser parser = factory.newSAXParser();

        JAXBContext ctx = JAXBContext.newInstance(type);
        Unmarshaller unmarshaller = ctx.createUnmarshaller();
        unmarshaller.setEventHandler(new ValidationEventHandler() {
            public boolean handleEvent(ValidationEvent validationEvent) {
                System.out.println(validationEvent);
                return false;
            }
        });

        XMLFilter xmlFilter = new NoSourceFilter(parser.getXMLReader());
        xmlFilter.setContentHandler(unmarshaller.getUnmarshallerHandler());

        SAXSource source = new SAXSource(xmlFilter, inputSource);

        return type.cast(unmarshaller.unmarshal(source));
    }

    public static class NoSourceFilter extends XMLFilterImpl {
        private static final InputSource EMPTY_INPUT_SOURCE = new InputSource(new ByteArrayInputStream(new byte[0]));

        public NoSourceFilter(XMLReader xmlReader) {
            super(xmlReader);
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            return EMPTY_INPUT_SOURCE;
        }

        @Override
        public void startElement(String uri, String localName, String qname, Attributes atts) throws SAXException {
            super.startElement("http://karaf.apache.org/xmlns/features/v1.0.0", localName, qname, atts);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement("http://karaf.apache.org/xmlns/features/v1.0.0", localName, qName);
        }
    }


}