package org.xwiki.contrib.cli.sync.event;

import org.xwiki.contrib.cli.document.element.ObjectProperty;

public class ObjectPropertyChangedEvent extends AbstractObjectEvent
{
    private final String newValue;

    private final ObjectProperty objectProperty;

    public ObjectPropertyChangedEvent(String reference, ObjectProperty objectProperty, String newValue)
    {
        super(reference, objectProperty.objectClass(), objectProperty.number());
        this.objectProperty = objectProperty;
        this.newValue = newValue;
    }

    public String newValue()
    {
        return newValue;
    }

    public ObjectProperty objectProperty()
    {
        return objectProperty;
    }

    public String property()
    {
        return objectProperty.property();
    }
}
