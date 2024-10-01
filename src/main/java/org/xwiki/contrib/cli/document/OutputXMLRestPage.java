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

import java.net.http.HttpResponse;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.MessageForUserDocException;
import org.xwiki.contrib.cli.Utils;
import org.xwiki.contrib.cli.document.element.ObjectInfo;
import org.xwiki.contrib.cli.document.element.Property;

class OutputXMLRestPage extends AbstractXMLDoc implements OutputDoc
{
    private static final String APPLICATION_XML_CHARSET_UTF_8 = "application/xml; charset=utf-8";

    protected final String wiki;

    protected final String page;

    private final String url;

    private String content;

    private String title;

    private List<ObjectInfo> objectValues = new LinkedList<>();

    private InputXMLRestPage inputPage;

    OutputXMLRestPage(Command cmd, String wiki, String page) throws DocException
    {
        super(cmd);
        url = Utils.getDocRestURLFromCommand(cmd, wiki, page, false);
        this.wiki = wiki;
        this.page = page;
    }

    @Override
    public void setContent(String str) throws DocException
    {
        content = str;
    }

    @Override
    public void setValue(String objectClass, String objectNumber, String property, String value) throws DocException
    {
        ObjectInfo objectSpec;
        if (Utils.isEmpty(objectClass) || Utils.isEmpty(objectNumber)) {
            objectSpec = getInputPage()
                .getObjectSpec(objectClass, objectNumber, property)
                .orElseThrow(() -> new DocException(String.format("Can't find object of class %s number %s",
                    objectClass, objectNumber)));
        } else {
            objectSpec = new ObjectInfo(objectClass, Integer.parseInt(objectNumber), new LinkedList<>());
        }

        var valuesForGivenObjectSpec = objectValues.stream()
            .filter(o -> objectSpec.objectClass().equals(o.objectClass()) && objectSpec.number() == o.number())
            .findFirst().orElseGet(() -> {
                objectValues.add(objectSpec);
                return objectSpec;
            });
        var previousToRemove = valuesForGivenObjectSpec.properties()
            .stream().filter(p -> p.name().equals(property)).toList();
        previousToRemove.forEach(o -> valuesForGivenObjectSpec.properties().remove(o));
        valuesForGivenObjectSpec.properties().add(new Property(property, value, Optional.empty()));
    }

    @Override
    public void setTitle(String str) throws DocException
    {
        title = str;
    }

    @Override
    public void save() throws DocException
    {
        if (content == null && title == null && objectValues == null) {
            return;
        }

        if (objectValues != null) {
            for (var objectSpec : objectValues) {
                var objectClassName = objectSpec.objectClass();
                var objectNumber = objectSpec.number();

                int builderSize =
                    500 + objectSpec.properties().stream().map(i -> i.value().length() + 100).reduce(0, Integer::sum);
                StringBuilder xml = new StringBuilder(builderSize);
                xml.append("<object xmlns='http://www.xwiki.org'>");
                xml.append("<className>").append(objectClassName).append("</className>");
                xml.append("<number>").append(objectNumber).append("</number>");

                for (var propWithValue : objectSpec.properties()) {
                    xml.append("<property name='").append(Utils.escapeXML(propWithValue.name())).append("'>")
                        .append("<value>").append(Utils.escapeXML(propWithValue.value())).append("</value>")
                        .append("</property>");
                }
                xml.append("</object>");
                checkStatus(Utils.httpPut(cmd, url + "/objects/" + objectClassName + '/' + objectNumber, xml.toString(),
                    APPLICATION_XML_CHARSET_UTF_8));
            }
        }

        if (content != null || title != null) {
            int builderSize = (content == null ? 0 : content.length()) + (title == null ? 0 : title.length()) + 500;
            var xml = new StringBuilder(builderSize);
            xml.append("<page xmlns='http://www.xwiki.org'>");

            if (content != null) {
                xml.append("<content>").append(Utils.escapeXML(content)).append("</content>");
            }

            if (title != null) {
                xml.append("<title>").append(Utils.escapeXML(title)).append("</title>");
            }

            xml.append("</page>");

            if (objectValues != null) {
                objectValues.clear();
            }
            content = null;
            title = null;
            checkStatus(Utils.httpPut(cmd, url, xml.toString(), APPLICATION_XML_CHARSET_UTF_8));
        }
    }

    @Override
    public void setAttachment(String attachmentName, byte[] content) throws DocException
    {
        String attachmentURL =
            Utils.getAttachmentRestURLFromCommand(cmd, wiki, page, attachmentName);
        Utils.httpPut(cmd, attachmentURL, content, "application/octet-stream");
    }

    @Override
    public String getFriendlyName()
    {
        return "the page at [" + url + "]";
    }

    private InputXMLRestPage getInputPage() throws DocException
    {
        if (inputPage == null) {
            inputPage = new InputXMLRestPage(cmd, wiki, page);
        }
        return inputPage;
    }

    private void checkStatus(HttpResponse<String> response) throws MessageForUserDocException
    {
        var status = response.statusCode();

        if (status >= 200 && status < 300) {
            return;
        }

        throw new MessageForUserDocException(
            "Unexpected status " + status + ". " + (cmd.debug() ? "Body: " + response.body()
                : " Use --debug to print the body of the HTTP request"));
    }

    private record ObjectValueSetter(String property, String value)
    {
    }
}
