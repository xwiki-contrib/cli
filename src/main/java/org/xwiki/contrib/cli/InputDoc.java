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

import java.util.Collection;
import java.util.Map;

/**
 * A readable document. Readonly if OutputDoc is not also implemented.
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
     * @return a key:value map of properties matching the given filters.
     * @param objectClass the class of object to consider, or empty if no class is specified.
     * @param objectNumber the number of the object to consider, or empty if not specified.
     * @param property the property name, or empty if not specified.
     * @param fullPath whether the property names should be prefixed with the object class
     *                 and number, also allowing duplicate properties.
     */
    Map<String, String> getProperties(String objectClass, String objectNumber, String property, boolean fullPath) throws DocException;

    /**
     * @return the name of objects matching the given filters.
     * @param objectClass the class of object to consider, or empty if no class is specified.
     * @param objectNumber the number of the object to consider, or empty if not specified.
     * @param property if not empty, the object should contain this property.
     */
    Collection<String> getObjects(String objectClass, String objectNumber, String property) throws DocException;

    /**
     * @return the name of attachments.
     */
    Collection<Attachment> getAttachments() throws DocException;

    /**
     * @return the first value of the property matching the given filters.
     * @param objectClass the class of object to consider, or empty if no class is specified.
     * @param objectNumber the number of the object to consider, or empty if not specified.
     * @param property the property name, or empty if not specified.
     */
    String getValue(String objectClass, String objectNumber, String property) throws DocException;

    /**
     * @return a friendly string like "the XML file SomeDoc.xml"
     */
    String getFriendlyName();
}
