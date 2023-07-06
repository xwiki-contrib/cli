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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static java.lang.System.out;
import static java.lang.System.err;

class MultipleDoc implements InputDoc, OutputDoc
{
    private final List<InputDoc> inputDocs;
    private final List<OutputDoc> outputDocs;

    MultipleDoc(Command cmd) throws DocException, IOException
    {
        inputDocs = new ArrayList<>();
        outputDocs = new ArrayList<>();

        inputDocs.add(new CommandDoc(cmd));

        if (Utils.present(cmd.inputFile)) {
            inputDocs.add(new XMLFileDoc(cmd, cmd.inputFile));
        }

        if (Utils.present(cmd.outputFile)) {
            outputDocs.add(new XMLFileDoc(cmd, cmd.outputFile));
        }

        if (Utils.present(cmd.base)) {
            inputDocs.add(new InputXMLRestPage(cmd));
            outputDocs.add(new OutputXMLRestPage(cmd));
        }

    }

    private InputDoc pickInputFile(String what) throws DocException
    {
        out.println(
            "Input documents have various " + what
            + ". Please select one of these choices, or 'c' to cancel."
        );

        var i = 1;
        for (var d : inputDocs) {
            out.println(i + ": " + d.getFriendlyName());
            ++i;
        }

        out.print("> ");
        out.flush();

        final String choice = new Scanner(System.in).next().trim();

        if ("c".equals(choice)) {
            throw new CancelledOperationDocException();
        }

        try {
            return inputDocs.get(Integer.parseInt(choice));
        } catch (NumberFormatException e) {
            // nothing, we'll tell the user we didn't understand the choice
        }

        err.println("Sorry, I didn't understand your answer. Please retry.");
        return pickInputFile(what);
    }

    public String getContent() throws DocException
    {
        String content = null;
        for (var inputDoc : inputDocs) {
            var newContent = inputDoc.getContent();
            if (content == null) {
                content = newContent;
            } else if (newContent != null && !content.equals(newContent)) {
                return pickInputFile("the content").getContent();
            }
        }

        return content;
    }

    public String getTitle() throws DocException
    {
        String title = null;
        for (var inputDoc : inputDocs) {
            var newTitle = inputDoc.getTitle();
            if (title == null) {
                title = newTitle;
            } else if (newTitle != null && !title.equals(newTitle)) {
                return pickInputFile("the title").getContent();
            }
        }

        return title;
    }

    public Collection<String> getObjects(String objectClass, String objectNumber, String property) throws DocException
    {
        Collection<String> objects = null;
        for (var inputDoc : inputDocs) {
            var newObjects = inputDoc.getObjects(objectClass, objectNumber, property);
            if (objects == null) {
                objects = newObjects;
            } else if (newObjects != null && !objects.equals(newObjects)) {
                return pickInputFile("the object list").getObjects(objectClass, objectNumber, property);
            }
        }

        return objects;
    }

    public Collection<Attachment> getAttachments() throws DocException
    {
        Collection<Attachment> attachments = null;
        for (var inputDoc : inputDocs) {
            var newAttachments = inputDoc.getAttachments();
            if (attachments == null) {
                attachments = newAttachments;
            } else if (newAttachments != null && !attachments.equals(newAttachments)) {
                return pickInputFile("the attachment list").getAttachments();
            }
        }
        return attachments;
    }

    public Map<String, String> getProperties(String objectClass, String objectNumber, String property, boolean fullPath)
        throws DocException
    {
        Map<String, String> properties = null;
        for (var inputDoc : inputDocs) {
            var newProperties = inputDoc.getProperties(objectClass, objectNumber, property, fullPath);
            if (properties == null) {
                properties = newProperties;
            } else if (newProperties != null && !properties.equals(newProperties)) {
                return pickInputFile("the property list").getProperties(objectClass, objectNumber, property, fullPath);
            }
        }

        return properties;
    }

    public String getValue(String objectClass, String objectNumber, String property) throws DocException
    {
        String value = null;
        for (var inputDoc : inputDocs) {
            var newValue = inputDoc.getValue(objectClass, objectNumber, property);
            if (value == null) {
                value = newValue;
            } else if (newValue != null && !value.equals(newValue)) {
                return pickInputFile("the value").getValue(objectClass, objectNumber, property);
            }
        }

        return value;
    }

    public void setContent(String str) throws DocException
    {
        for (var outputDoc : outputDocs) {
            outputDoc.setContent(str);
        }
    }

    public void setTitle(String str) throws DocException
    {
        for (var outputDoc : outputDocs) {
            outputDoc.setTitle(str);
        }
    }

    public void setValue(String objectClass, String objectNumber, String property, String value) throws DocException
    {
        for (var outputDoc : outputDocs) {
            outputDoc.setValue(objectClass, objectNumber, property, value);
        }
    }

    public void save() throws DocException
    {
        for (var outputDoc : outputDocs) {
            outputDoc.save();
        }
    }

    public String getFriendlyName()
    {
        return "the merged document";
    }
}
