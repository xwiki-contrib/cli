package org.xwiki.contrib.cli.sync.event;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.xwiki.contrib.cli.document.element.Property;

public class ObjectAddedEvent extends AbstractObjectEvent
{
    private final Collection<Property> properties;

    public ObjectAddedEvent(String reference, String className, int number, Collection<Property> properties)
    {
        super(reference, className, number);
        this.properties = properties;
    }

    public Collection<Property> properties()
    {
        return properties;
    }
}
