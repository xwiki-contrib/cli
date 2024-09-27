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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.document.element.AttachmentInfo;
import org.xwiki.contrib.cli.document.element.ClassProperty;
import org.xwiki.contrib.cli.document.element.ObjectInfo;

public class CommandDoc implements InputDoc
{
    private final Command cmd;

    public CommandDoc(Command cmd)
    {
        this.cmd = cmd;
    }

    public String getContent()
    {
        return cmd.content();
    }

    public String getTitle()
    {
        return cmd.title();
    }

    public Map<String, String> getProperties(String objectClass, String objectNumber, String property, boolean fullPath)
    {
        return null;
    }

    public Collection<ObjectInfo> getObjects(String objectClass, String objectNumber, String property)
    {
        return null;
    }

    public Collection<AttachmentInfo> getAttachments()
    {
        return null;
    }

    @Override
    public byte[] getAttachment(String attachmentName) throws DocException
    {
        throw new UnsupportedOperationException("Not Implemented");
    }

    public Optional<String> getValue(String objectClass, String objectNumber, String property)
    {
        return Optional.ofNullable(cmd.value());
    }

    public String getFriendlyName()
    {
        return "the command";
    }

    @Override
    public Collection<ClassProperty> getClassInfo() throws DocException
    {
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public Optional<String> getClassPropertyField(String property, String field)
    {
        throw new UnsupportedOperationException("Not Implemented");
    }
}
