package org.xwiki.contrib.cli.sync.event;

public class ObjectRemovedEvent extends AbstractObjectEvent
{
    public ObjectRemovedEvent(String reference, String className, int number)
    {
        super(reference, className, number);
    }
}
