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
import java.util.Optional;
import java.util.Scanner;

import org.xwiki.contrib.cli.CancelledOperationDocException;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.Utils;
import org.xwiki.contrib.cli.document.element.AttachmentInfo;
import org.xwiki.contrib.cli.document.element.ObjectInfo;

import static java.lang.System.err;
import static java.lang.System.out;

/**
 * Represent a document with multiple input or/and output.
 *
 *  @version $Id$
 */
public class MultipleDoc implements InputDoc, OutputDoc
{
    private static final String TEXT_ATTACHMENT_LIST = "the attachment list";

    private final List<InputDoc> inputDocs;

    private final List<OutputDoc> outputDocs;

    /**
     * Create a new multiple document.
     *
     * @param cmd the command.
     * @param wiki the wiki ID.
     * @param page the page reference.
     * @throws DocException
     * @throws IOException
     */
    public MultipleDoc(Command cmd, String wiki, String page) throws DocException, IOException
    {
        inputDocs = new ArrayList<>();
        outputDocs = new ArrayList<>();

        inputDocs.add(new CommandDoc(cmd));

        if (Utils.present(cmd.inputFile())) {
            inputDocs.add(new XMLFileDoc(cmd, cmd.inputFile()));
        }

        if (Utils.present(cmd.outputFile())) {
            outputDocs.add(new XMLFileDoc(cmd, cmd.outputFile()));
        }

        if (Utils.present(cmd.xmlReadDir())) {
            inputDocs.add(new MvnRepoFileDoc(cmd, cmd.xmlReadDir(), wiki, page));
        }

        if (Utils.present(cmd.xmlWriteDir())) {
            outputDocs.add(new MvnRepoFileDoc(cmd, cmd.xmlWriteDir(), wiki, page));
        }

        if (Utils.present(cmd.url())) {
            if (!cmd.wikiWriteonly()) {
                inputDocs.add(new InputXMLRestPage(cmd, wiki, page));
            }
            if (!cmd.wikiReadonly()) {
                outputDocs.add(new OutputXMLRestPage(cmd, wiki, page));
            }
        }
    }

    MultipleDoc(List<OutputDoc> outputDocs, List<InputDoc> inputDocs)
    {
        this.inputDocs = inputDocs;
        this.outputDocs = outputDocs;
    }

    @Override
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

    @Override
    public void setContent(String str) throws DocException
    {
        for (var outputDoc : outputDocs) {
            outputDoc.setContent(str);
        }
    }

    @Override
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

    @Override
    public void setTitle(String str) throws DocException
    {
        for (var outputDoc : outputDocs) {
            outputDoc.setTitle(str);
        }
    }

    @Override
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

    @Override
    public Collection<AttachmentInfo> getAttachments() throws DocException
    {
        Collection<AttachmentInfo> attachmentInfos = null;
        for (var inputDoc : inputDocs) {
            var newAttachments = inputDoc.getAttachments();
            if (attachmentInfos == null) {
                attachmentInfos = newAttachments;
            } else if (newAttachments != null && !attachmentInfos.equals(newAttachments)) {
                return pickInputFile(TEXT_ATTACHMENT_LIST).getAttachments();
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
                return pickInputFile(TEXT_ATTACHMENT_LIST).getAttachment(attachmentName);
            }
        }
        return attachment;
    }

    @Override
    public Optional<String> getValue(String objectClass, String objectNumber, String property) throws DocException
    {
        Optional<String> value = Optional.empty();
        for (var inputDoc : inputDocs) {
            var newValue = inputDoc.getValue(objectClass, objectNumber, property);
            if (value.isEmpty()) {
                value = newValue;
            } else if (newValue.isPresent() && !value.equals(newValue)) {
                return pickInputFile("the value").getValue(objectClass, objectNumber, property);
            }
        }

        return value;
    }

    @Override
    public void setValue(String objectClass, String objectNumber, String property, String value) throws DocException
    {
        for (var outputDoc : outputDocs) {
            outputDoc.setValue(objectClass, objectNumber, property, value);
        }
    }

    @Override
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

    @Override
    public String getFriendlyName()
    {
        return "the merged document";
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
}
