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

package org.xwiki.contrib.cli.document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.Utils;
import org.xwiki.contrib.cli.document.element.AttachmentInfo;
import org.xwiki.contrib.cli.document.element.ObjectInfo;
import org.xwiki.contrib.cli.document.element.Property;

import static java.lang.System.err;

abstract class AbstractXMLDoc
{
    protected static final String NODE_NAME_CLASS_NAME = "className";

    protected static final String NODE_NAME_NUMBER = "number";

    protected static final String NODE_NAME_CONTENT = "content";

    protected static final String NODE_NAME_TITLE = "title";

    protected static final String NODE_NAME_OBJECT = "object";

    protected static final String NODE_XWIKI_SPACE = "xwiki:";

    protected static final String NODE_NAME = "name";

    protected static final String NODE_NAME_REST_OBJECT = "xwiki:objects/xwiki:objectSummary";

    protected static final String NODE_NAME_PROPERTY = "property";

    protected static final String NODE_NAME_ATTACHMENT = "attachment";

    protected static final String NODE_NAME_REST_ATTACHMENT = "xwiki:attachments/xwiki:attachment";

    protected static final String LINE = "\n-----\n";

    protected static final String NODE_PROPERTY_NAME = "xwiki:property[@name = '%s']/xwiki:value";

    protected static final String NODE_PROPERTY = "property/%s";

    protected final Command cmd;

    protected String xml;

    protected Document dom;

    protected boolean isFromRest()
    {
        return fromRest;
    }

    private boolean fromRest;

    AbstractXMLDoc(Command cmd)
    {
        this.cmd = cmd;
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
    }

    public Map<String, String> getProperties(String objectClass, String objectNumber, String property, boolean fullPath)
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

            String propertyName = null;
            Element propertyElement = null;

            for (var prop : getElements(object, NODE_NAME_PROPERTY)) {
                if (fromRest) {
                    propertyElement = (Element) prop.selectSingleNode("xwiki:value");
                    propertyName = ((Element) prop).attributeValue(NODE_NAME);
                } else {
                    for (var pElement : ((Element) prop).elements()) {
                        propertyName = pElement.getName();
                        propertyElement = pElement;
                    }
                }

                if (propertyElement != null && (property == null || property.equals(propertyName))) {
                    if (fullPath) {
                        propertyName = getObjectSpec((Element) object) + "." + propertyName;
                    }
                    properties.put(propertyName, propertyElement.getText());
                }
            }
        }

        return properties;
    }

    public static String getObjectSpec(Element object)
    {
        var classNameElement = getElement(object, NODE_NAME_CLASS_NAME);
        if (classNameElement == null) {
            return null;
        }

        var className = classNameElement.getText();
        if (Utils.isEmpty(className)) {
            return null;
        }
        var numberElement = getElement(object, NODE_NAME_NUMBER);
        if (numberElement == null) {
            return null;
        }

        var number = numberElement.getText();
        if (Utils.isEmpty(number)) {
            return null;
        }

        return className + '/' + number;
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
                    ? String.format(NODE_PROPERTY_NAME, property)
                    : String.format(NODE_PROPERTY, property)
            );
            if (propertyElement == null) {
                continue;
            }

            return getObjectSpec((Element) object);
        }

        return null;
    }

    public Collection<String> getObjects(String objectClass, String objectNumber, String property)
        throws DocException
    {
        var domdoc = getDom();

        if (domdoc == null) {
            throw new DocumentNotFoundException();
        }

        var root = domdoc.getRootElement();
        var objects = root.selectNodes(fromRest ? NODE_NAME_REST_OBJECT : NODE_NAME_OBJECT);
        var objs = new ArrayList<String>();
        for (var object : objects) {
            if (!objectMatchesFilter(object, objectClass, objectNumber)) {
                continue;
            }

            var classNameElement = getElement((Element) object, NODE_NAME_CLASS_NAME);
            if (classNameElement == null) {
                throw new MissingNodeException("class name of object");
            }

            String className = classNameElement.getText();

            var numberElement = getElement((Element) object, NODE_NAME_NUMBER);
            if (numberElement == null) {
                throw new MissingNodeException("number of object");
            }

            String number = numberElement.getText();

            if (!Utils.isEmpty(property)) {
                var propertyElement = object.selectSingleNode(
                    fromRest
                        ? String.format(NODE_PROPERTY_NAME, property)
                        : String.format(NODE_PROPERTY, property)
                );

                if (propertyElement == null) {
                    continue;
                }
            }

            objs.add(className + "/" + number);
        }

        return objs;
    }

    public Collection<AttachmentInfo> getAttachments() throws DocException
    {
        var domdoc = getDom();

        if (domdoc == null) {
            throw new DocumentNotFoundException();
        }

        var root = domdoc.getRootElement();
        var attachments = root.selectNodes(fromRest ? NODE_NAME_REST_ATTACHMENT : NODE_NAME_ATTACHMENT);
        var res = new ArrayList<AttachmentInfo>(attachments.size());
        for (var attachment : attachments) {
            if (fromRest) {
                // TODO test if it works well
                res.add(new AttachmentInfo(
                    getElement((Element) attachment, "name").getText(),
                    Long.parseLong(getElement((Element) attachment, "size").getText())
                ));
            } else {
                res.add(new AttachmentInfo(
                    getElement((Element) attachment, "filename").getText(),
                    Long.parseLong(getElement((Element) attachment, "filesize").getText())
                ));
            }
        }

        return res;
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

    public String getReference() throws DocException
    {
        return getDom().valueOf("//xwikidoc/@reference");
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
        if (dom == null && xml != null) {
            try {
                this.dom = Utils.parseXML(this.xml);
            } catch (DocException e) {
                if (cmd.isDebug()) {
                    err.println(
                        "A parse error occured. Here is the content we attempted to parse."
                            + LINE + xml + LINE
                    );
                }

                throw e;
            }

            if (cmd.isPrintXML()) {
                err.println(LINE + xml + LINE);
            }
        }

        return dom;
    }

    protected static Element getElement(Element parent, String nodeName)
    {
        var element = (Element) parent.selectSingleNode(nodeName);
        if (element == null) {
            element = (Element) parent.selectSingleNode(NODE_XWIKI_SPACE + nodeName);
        }
        return element;
    }

    private static List<Node> getElements(Node parent, String nodeName)
    {
        var elements = parent.selectNodes(nodeName);
        if (elements == null || elements.isEmpty()) {
            elements = parent.selectNodes(NODE_XWIKI_SPACE + nodeName);
        }
        return elements;
    }

    private boolean objectMatchesFilter(Node object, String objectClass, String objectNumber)
    {
        if (objectClass != null) {
            var classNameElement = getElement((Element) object, NODE_NAME_CLASS_NAME);
            if (classNameElement == null) {
                err.println("Couldn't find class name of object");
                return false;
            }

            if (!objectClass.equals(classNameElement.getText())) {
                return false;
            }
        }

        if (objectNumber != null) {
            var numberElement = getElement((Element) object, NODE_NAME_NUMBER);
            if (numberElement == null) {
                err.println("Couldn't find class number of object");
                return false;
            }

            return objectNumber.equals(numberElement.getText());
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
}
