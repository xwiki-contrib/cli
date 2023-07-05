/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xwiki.contrib.cli;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.StringReader;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import org.xml.sax.SAXException;

import static java.lang.System.err;

abstract class AbstractXMLDoc
{
    private static final String NODE_NAME_CLASS_NAME = "className";
    private static final String NODE_NAME_NUMBER = "number";
    private static final String NODE_NAME_CONTENT = "content";
    private static final String NODE_NAME_TITLE = "title";
    private static final String NODE_NAME_OBJECT = "object";
    private static final String NODE_NAME_REST_OBJECT = "xwiki:objects/xwiki:objectSummary";
    private static final String NODE_NAME_PROPERTY = "property";

    private static final String LINE = "\n-----\n";

    protected String xml;
    protected Document dom;

    boolean fromRest = false;

    private final Command cmd;

    class DocumentNotFoundException extends DocException
    {
        DocumentNotFoundException()
        {
            super("Document not found");
        }
    }

    class MissingNodeException extends DocException
    {
        MissingNodeException(String what)
        {
            super("Could not find " + what);
        }
    }

    AbstractXMLDoc(Command cmd)
    {
        this.cmd = cmd;
    }

    protected void setXML(String str, boolean fromRest)
    {
        this.fromRest = fromRest;
        dom = null;
        xml = str;
    }

    protected String getXML()
    {
        if (xml != null) {
            return xml;
        }

        // get XML from domdoc;
        return "";
    }

    protected Document getDom() throws DocException
    {
        if (dom == null) {
            if (xml != null) {
                var reader = new SAXReader();
                try {
                    reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                } catch (SAXException e) {
                    throw new DocException(e);
                }

                try {
                    dom = reader.read(new StringReader(xml));
                    dom.getRootElement().add(new Namespace("xwiki", "http://www.xwiki.org"));
                } catch (DocumentException e) {
                    if (cmd.debug) {
                        err.println(
                            "A parse error occured. Here is the content we attempted to parse."
                            + LINE + xml + LINE
                        );
                    }
                    throw new DocException(e);
                }

                if (cmd.printXML) {
                    err.println(LINE + xml + LINE);
                }
            }
        }

        return dom;
    }

    private Element getElement(Element parent, String nodeName)
    {
        var element = (Element) parent.selectSingleNode(nodeName);
        if (element == null) {
            element = (Element) parent.selectSingleNode("xwiki:" + nodeName);
        }
        return element;
    }

    private List<Node> getElements(Node parent, String nodeName)
    {
        var elements = ((Element) parent).selectNodes(nodeName);
        if (elements == null || elements.isEmpty()) {
            elements = ((Element) parent).selectNodes("xwiki:" + nodeName);
        }
        return elements;
    }

    public String getContent() throws DocException
    {
        var domdoc = getDom();
        var root = (Element) domdoc.getRootElement();
        var content = (Element) getElement(root, NODE_NAME_CONTENT);
        if (content == null) {
            return null;
        }
        return content.getText();
    }

    public void setContent(String str) throws DocException
    {
        var domdoc = getDom();
        if (domdoc == null) {
            throw new DocumentNotFoundException();
        }
        var root = (Element) domdoc.getRootElement();
        var content = (Element) getElement(root, NODE_NAME_CONTENT);
        if (content == null) {
            throw new DocException("Content not found");
        }

        content.setText(str);
        xml = null;
        return;
    }

    public String getTitle() throws DocException
    {
        var domdoc = getDom();
        var root = (Element) domdoc.getRootElement();
        var title = (Element) getElement(root, NODE_NAME_TITLE);
        if (title == null) {
            return null;
        }
        return title.getText();
    }

    public void setTitle(String str) throws DocException
    {
        var domdoc = getDom();
        if (domdoc == null) {
            throw new DocumentNotFoundException();
        }
        var root = domdoc.getRootElement();
        var title = (Element) getElement(root, NODE_NAME_TITLE);
        if (title == null) {
            throw new DocException("Title not found");
        }

        title.setText(str);
        xml = null;
        return;
    }

    public Map<String, String> getProperties(String objectClass, String objectNumber, String property)
            throws DocException
    {
        var domdoc = getDom();

        if (domdoc == null) {
            throw new DocumentNotFoundException();
        }

        var properties = new HashMap<String, String>();

        var root = (Element) domdoc.getRootElement();
        var objects = root.selectNodes(fromRest ? NODE_NAME_REST_OBJECT : NODE_NAME_OBJECT);
        for (var object : objects) {
            if (!objectMatchesFilter(object, objectClass, objectNumber)) {
                continue;
            }

            for (var prop : getElements(object, NODE_NAME_PROPERTY)) {
                if (fromRest) {
                    var value = (Element) prop.selectSingleNode("xwiki:value");
                    var valueElement =  value==null?null:value.getText();
                    properties.put(((Element) prop).attributeValue("name"), valueElement);
                } else {
                    for (var propertyElement : ((Element) prop).elements()) {
                        var p = propertyElement.getName();
                        if (property == null || property.equals(p)) {
                            properties.put(p, propertyElement.getText());
                        }
                    }
                }
            }
        }
        return properties;
    }

    private boolean objectMatchesFilter(Node object, String objectClass, String objectNumber)
    {
        if (objectClass != null) {
            var classNameElement = object.selectSingleNode(NODE_NAME_CLASS_NAME);
            if (classNameElement == null) {
                err.println("Couldn't find class name of object");
                return false;
            }

            if (!objectClass.equals(classNameElement.getText())) {
                return false;
            }
        }

        if (objectNumber != null) {
            var numberElement = object.selectSingleNode(NODE_NAME_NUMBER);
            if (numberElement == null) {
                err.println("Couldn't find class number of object");
                return false;
            }

            if (!objectNumber.equals(numberElement.getText())) {
                return false;
            }
        }

        return true;
    }

    private Node getPropertyValueElement(String objectClass, String objectNumber, String property)
            throws DocException
    {
        var domdoc = getDom();

        if (domdoc == null) {
            throw new DocumentNotFoundException();
        }

        if (property == null) {
            throw new DocException("property is null. getValue expects a property.");
        }

        var root = domdoc.getRootElement();
        var objects = root.selectNodes(fromRest ? NODE_NAME_REST_OBJECT : NODE_NAME_OBJECT);
        for (var object : objects) {
            if (!objectMatchesFilter(object, objectClass, objectNumber)) {
                continue;
            }

            var propertyElement = object.selectSingleNode(
                fromRest
                    ? "xwiki:property[@name = '" + property + "']/xwiki:value"
                    : "property/" + property
            );

            if (propertyElement == null) {
                continue;
            }


            return propertyElement;
        }

        return null;
    }

    public String getObjectSpec(String objectClass, String objectNumber, String property)
            throws DocException
    {
        var domdoc = getDom();

        if (domdoc == null) {
            throw new DocumentNotFoundException();
        }

        if (property == null) {
            throw new DocException("property is null. getObjectSpec expects a property.");
        }

        var root = domdoc.getRootElement();
        var objects = root.selectNodes(fromRest ? NODE_NAME_REST_OBJECT : NODE_NAME_OBJECT);
        for (var object : objects) {
            if (!objectMatchesFilter(object, objectClass, objectNumber)) {
                continue;
            }

            var propertyElement = object.selectSingleNode(
                fromRest
                    ? "xwiki:property[@name = '" + property + "']/xwiki:value"
                    : "property/" + property
            );

            if (propertyElement == null) {
                continue;
            }

            var classNameElement = getElement((Element) object, "className");
            if (classNameElement == null) {
                return null;
            }

            var className = classNameElement.getText();
            if (Utils.isEmpty(className)) {
                return null;
            }
            var numberElement = getElement((Element) object, "number");
            if (numberElement == null) {
                return null;
            }

            var number = classNameElement.getText();
            if (Utils.isEmpty(number)) {
                return null;
            }

            return className + "/" + number;
        }

        return null;
    }

    public Collection<String> getObjects(String objectClass, String objectNumber)
            throws DocException
    {
        var domdoc = getDom();

        if (domdoc == null) {
            throw new DocumentNotFoundException();
        }

        var root = domdoc.getRootElement();
        var objects = root.selectNodes(NODE_NAME_OBJECT);
        var objs = new ArrayList<String>();
        for (var object : objects) {
            if (!objectMatchesFilter(object, objectClass, objectNumber)) {
                continue;
            }

            var classNameElement = object.selectSingleNode(NODE_NAME_CLASS_NAME);
            if (classNameElement == null) {
                throw new MissingNodeException("class name of object");
            }

            String className = classNameElement.getText();

            var numberElement = object.selectSingleNode(NODE_NAME_NUMBER);
            if (numberElement == null) {
                throw new MissingNodeException("number of object");
            }

            String number = numberElement.getText();

            objs.add(className + "/" + number);
        }

        return objs;
    }

    public String getValue(String objectClass, String objectNumber, String property)
            throws DocException
    {
        var propertyElement = getPropertyValueElement(objectClass, objectNumber, property);
        if (propertyElement == null) {
            return null;
        }

        return propertyElement.getText();
    }

    public void setValue(String objectClass, String objectNumber, String property, String value)
            throws DocException
    {
        var propertyElement = getPropertyValueElement(objectClass, objectNumber, property);
        if (propertyElement == null) {
            throw new DocException("Couln't find this property");
        }

        propertyElement.setText(value);
    }
}
