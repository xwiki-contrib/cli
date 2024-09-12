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

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class OutputXMLRestPage extends AbstractXMLDoc implements OutputDoc
{
    private final String url;
    private final Command cmd;
    private String content;
    private String title;
    private Map<String, List<ObjectValueSetter>> values;
    InputXMLRestPage input;

    private class ObjectValueSetter
    {
        String property;
        String value;

        ObjectValueSetter(String property, String value)
        {
            this.property   = property;
            this.value      = value;
        }
    }

    OutputXMLRestPage(Command cmd) throws DocException
    {
        super(cmd);
        url = Utils.getDocRestURLFromCommand(cmd, false);
        this.cmd = cmd;
    }

    OutputXMLRestPage(Command cmd, InputXMLRestPage input) throws DocException
    {
        super(cmd);
        url = Utils.getDocRestURLFromCommand(cmd, false);
        this.cmd = cmd;
        this.input = input;
    }

    @Override
    public void setContent(String str) throws DocException
    {
        content = str;
    }

    @Override
    public void setValue(String objectClass, String objectNumber, String property, String value) throws DocException
    {
        if (values == null) {
            values = new HashMap<>();
        }

        String objectSpec;
        if (Utils.isEmpty(objectClass) || Utils.isEmpty(objectClass)) {
            if (input == null) {
                input = new InputXMLRestPage(cmd);
            }
            objectSpec = input.getObjectSpec(objectClass, objectNumber, property);
        } else {
            objectSpec = objectClass + "/" + objectNumber;
        }

        var valuesForGivenObjectSpec = values.get(objectSpec);
        if (valuesForGivenObjectSpec == null) {
            valuesForGivenObjectSpec = new ArrayList<>();
            values.put(objectSpec, valuesForGivenObjectSpec);
        }
        valuesForGivenObjectSpec.add(new ObjectValueSetter(property, value));
    }

    @Override
    public void setTitle(String str) throws DocException
    {
        title = str;
    }

    @Override
    public void save() throws DocException
    {
        if (content == null && title == null && values == null) {
            return;
        }

        // if (content == null && title == null && values != null && onlyOneValue()) {
        //     var v = values.get(0);
        //     checkStatus(Utils.httpPut(
        //         cmd,
        //         url + "/objects/" + v.objectSpec + "/properties/" + v.property,
        //         v.value,
        //         "text/plain; charset=utf-8"));
        //     return;
        // }

        if (values != null) {
            for (var byObjectSpec : values.entrySet()) {

                var objectSpec = byObjectSpec.getKey();
                var slash = objectSpec.indexOf('/');
                var objectClassName = objectSpec.substring(0, slash);
                var objectNumber = objectSpec.substring(slash + 1);

                int builderSize = 500 + byObjectSpec.getValue().stream()
                    .map(i -> i.value.length() + 100).reduce(0, Integer::sum);
                StringBuilder xml = new StringBuilder(builderSize);
                xml.append("<object xmlns='http://www.xwiki.org'>");
                xml.append("<className>").append(objectClassName).append("</className>");
                xml.append("<number>").append(objectNumber).append("</number>");

                for (var propWithValue : byObjectSpec.getValue()) {
                    xml.append("<property name='").append(Utils.escapeXML(propWithValue.property)).append("'>")
                        .append("<value>").append(Utils.escapeXML(propWithValue.value)).append("</value>")
                        .append("</property>");
                }
                xml.append("</object>");
                checkStatus(Utils.httpPut(cmd,
                    url + "/objects/" + objectClassName + "/" + objectNumber,
                    xml.toString(), "application/xml; charset=utf-8"));
            }
        }

        if (content != null || title != null) {
            int builderSize = (content == null ? 0 : content.length()) +
                (title == null ? 0 : title.length()) +
                500;
            var xml = new StringBuilder(builderSize);
            xml.append("<page xmlns='http://www.xwiki.org'>");

            if (content != null) {
                xml.append("<content>").append(Utils.escapeXML(content)).append("</content>");
            }

            if (title != null) {
                xml.append("<title>").append(Utils.escapeXML(title)).append("</title>");
            }

            xml.append("</page>");

            if (values != null) {
                values.clear();
            }
            content = null;
            title = null;
            checkStatus(Utils.httpPut(cmd, url, xml.toString(), "application/xml; charset=utf-8"));
        }
    }

    private void checkStatus(HttpResponse<String> response) throws MessageForUserDocException
    {
        var status = response.statusCode();

        if (status >= 200 && status < 300) {
            return;
        }

        throw new MessageForUserDocException(
            "Unexpected status "
            + status
            + ". "
            + (cmd.debug
                ? "Body: " + response.body()
                : " Use --debug to print the body of the HTTP request")
        );
    }

    @Override
    public String getFriendlyName()
    {
        return "the page at [" + url + "]";
    }
}
