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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;

/**
 * This class represents a "XAR" XML file.
 *
 * @version $Id$
 */
public class XMLFileDoc extends AbstractXMLDoc implements InputDoc, OutputDoc
{
    private String outputFile;

    XMLFileDoc(Command cmd)
    {
        super(cmd);
    }

    XMLFileDoc(Command cmd, String filename) throws DocException, IOException
    {
        super(cmd);
        setXML(Files.readString(Path.of(filename)), false);
        outputFile = filename;
    }

    @Override
    public void save() throws DocException
    {
        if (xml == null) {
            if (dom == null) {
                throw new DocException("Nothing to save");
            }
        }
        OutputFormat outFormat = OutputFormat.createCompactFormat();
        outFormat.setTrimText(false);
        outFormat.setEncoding("utf-8");
        outFormat.setExpandEmptyElements(false);
        outFormat.setOmitEncoding(true);
        outFormat.setSuppressDeclaration(true);
        try {
            var out = new FileOutputStream(outputFile);
            out.write("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n\n".getBytes(Charset.forName("UTF-8")));
            XMLWriter writer = new XMLWriter(out, outFormat);
            writer.write(dom);
            writer.flush();
        } catch (IOException e) {
            throw new DocException(e);
        }
    }

    @Override
    public void setAttachment(String attachmentName, byte[] content)
    {
        // TODO
    }

    @Override
    public byte[] getAttachment(String attachmentName) throws DocException
    {
        var domdoc = getDom();
        if (domdoc == null) {
            throw new DocumentNotFoundException();
        }
        var root = domdoc.getRootElement();
        var attachments = root.selectNodes(NODE_NAME_ATTACHMENT);
        for (var attachment : attachments) {
            if (attachmentName.equals(getElement((Element) attachment, "filename").getText())) {
                return Base64.getDecoder().decode(getElement((Element) attachment, "content").getText());
            }
        }
        return null;
    }

    @Override
    public String getFriendlyName()
    {
        return "the XML file [" + outputFile + "]";
    }
}
