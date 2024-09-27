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

import org.xwiki.contrib.cli.DocException;

/**
 * A writable document. Write only if InputDoc is not also implemented.
 *
 * @version $Id$
 */
public interface OutputDoc
{
    /**
     * Set the content of the document.
     *
     * @param str the new content
     */
    void setContent(String str) throws DocException;

    /**
     * Set the title of the document.
     *
     * @param str the new title
     */
    void setTitle(String str) throws DocException;

    /**
     * Set the value of the property in the first object specified by the given parameters.
     *
     * @param objectClass the class of the object to update, or empty if no class is specified.
     * @param objectNumber the number of the object to update, or empty if not specified.
     * @param property the property to set.
     * @param value the value to set.
     */
    void setValue(String objectClass, String objectNumber, String property, String value) throws DocException;

    /**
     * Save the changes.
     */
    void save() throws DocException;

    void setAttachment(String attachmentName, byte[] content) throws DocException;

    /**
     * @return a friendly string like "the XML file SomeDoc.xml"
     */
    String getFriendlyName();

    void setClassPropertyField(String property, String field, String value) throws DocException;
}
