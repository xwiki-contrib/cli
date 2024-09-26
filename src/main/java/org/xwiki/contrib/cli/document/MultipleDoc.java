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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;

import org.xwiki.contrib.cli.CancelledOperationDocException;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.Utils;
import org.xwiki.contrib.cli.document.element.AttachmentInfo;
import org.xwiki.contrib.cli.document.element.ObjectInfo;

import static java.lang.System.err;
import static java.lang.System.out;

public class MultipleDoc implements InputDoc, OutputDoc
{
    private final List<InputDoc> inputDocs;

    private final List<OutputDoc> outputDocs;

    public MultipleDoc(Command cmd, String wiki, String page) throws DocException, IOException
    {
        inputDocs = new ArrayList<>();
        outputDocs = new ArrayList<>();

        inputDocs.add(new CommandDoc(cmd));

        if (Utils.present(cmd.getInputFile())) {
            inputDocs.add(new XMLFileDoc(cmd, cmd.getInputFile()));
        }

        if (Utils.present(cmd.getOutputFile())) {
            outputDocs.add(new XMLFileDoc(cmd, cmd.getOutputFile()));
        }

        if (Utils.present(cmd.getXmlReadDir())) {
            inputDocs.add(new MvnRepoFileDoc(cmd, cmd.getXmlReadDir(), wiki, page));
        }

        if (Utils.present(cmd.getXmlWriteDir())) {
            outputDocs.add(new MvnRepoFileDoc(cmd, cmd.getXmlWriteDir(), wiki, page));
        }

        if (Utils.present(cmd.getUrl())) {
            if (!cmd.isWikiWriteonly()) {
                inputDocs.add(new InputXMLRestPage(cmd, wiki, page));
            }
            if (!cmd.isWikiReadonly()) {
                outputDocs.add(new OutputXMLRestPage(cmd, wiki, page));
            }
        }
    }

    MultipleDoc(List<OutputDoc> outputDocs, List<InputDoc> inputDocs)
    {
        this.inputDocs = inputDocs;
        this.outputDocs = outputDocs;
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

    public Collection<ObjectInfo> getObjects(String objectClass, String objectNumber, String property)
        throws DocException
    {
        Collection<ObjectInfo> objects = null;
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

    public Collection<AttachmentInfo> getAttachments() throws DocException
    {
        Collection<AttachmentInfo> attachmentInfos = null;
        for (var inputDoc : inputDocs) {
            var newAttachments = inputDoc.getAttachments();
            if (attachmentInfos == null) {
                attachmentInfos = newAttachments;
            } else if (newAttachments != null && !attachmentInfos.equals(newAttachments)) {
                return pickInputFile("the attachment list").getAttachments();
            }
        }
        return attachmentInfos;
    }

    @Override
    public byte[] getAttachment(String attachmentName) throws DocException
    {
        byte[] attachment = null;
        for (var inputDoc : inputDocs) {
            var newAttachment = inputDoc.getAttachment(attachmentName);
            if (attachment == null) {
                attachment = newAttachment;
            } else if (newAttachment != null && !attachment.equals(newAttachment)) {
                return pickInputFile("the attachment list").getAttachment(attachmentName);
            }
        }
        return attachment;
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

    @Override
    public void setAttachment(String attachmentName, byte[] content) throws DocException
    {
        for (var outputDoc : outputDocs) {
            outputDoc.setAttachment(attachmentName, content);
        }
    }

    public String getFriendlyName()
    {
        return "the merged document";
    }
}
