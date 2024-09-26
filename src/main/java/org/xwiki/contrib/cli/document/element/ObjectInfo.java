package org.xwiki.contrib.cli.document.element;

import java.util.List;

public record ObjectInfo(String objectClass, int number, List<Property> properties)
{
}
