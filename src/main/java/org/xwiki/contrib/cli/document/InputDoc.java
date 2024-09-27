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
import java.util.Optional;

import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.document.element.AttachmentInfo;
import org.xwiki.contrib.cli.document.element.ClassProperty;
import org.xwiki.contrib.cli.document.element.ObjectInfo;

/**
 * A readable document. Readonly if OutputDoc is not also implemented.
 *
 * @version $Id$
 */
interface InputDoc
{
    /**
     * @return the content of the document.
     */
    String getContent() throws DocException;

    /**
     * @return the title of the document.
     */
    String getTitle() throws DocException;

    /**
     * @param objectClass the class of object to consider, or empty if no class is specified.
     * @param objectNumber the number of the object to consider, or empty if not specified.
     * @param property if not empty, the object should contain this property.
     * @return the name of objects matching the given filters.
     */
    Collection<ObjectInfo> getObjects(String objectClass, String objectNumber, String property) throws DocException;

    /**
     * @return the name of attachments.
     */
    Collection<AttachmentInfo> getAttachments() throws DocException;

    byte[] getAttachment(String attachmentName) throws DocException;

    /**
     * @param objectClass the class of object to consider, or empty if no class is specified.
     * @param objectNumber the number of the object to consider, or empty if not specified.
     * @param property the property name, or empty if not specified.
     * @return the first value of the property matching the given filters.
     */
    Optional<String> getValue(String objectClass, String objectNumber, String property) throws DocException;

    /**
     * @return a friendly string like "the XML file SomeDoc.xml"
     */
    String getFriendlyName();

    Collection<ClassProperty> getClassInfo() throws DocException;

    Optional<String> getClassPropertyField(String property, String field) throws DocException;
}
