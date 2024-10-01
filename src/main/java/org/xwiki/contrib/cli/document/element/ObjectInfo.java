package org.xwiki.contrib.cli.document.element;

import java.util.List;

/**
 * Information related to an object.
 *
 * @param objectClass class of the object.
 * @param number object number.
 * @param properties list of all properties of the object.
 *
 * @version $Id$
 */
public record ObjectInfo(String objectClass, int number, List<Property> properties)
{
}
