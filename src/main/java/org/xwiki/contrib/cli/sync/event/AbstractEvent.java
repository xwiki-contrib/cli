package org.xwiki.contrib.cli.sync.event;

public abstract class AbstractEvent
{
    private final String reference;

    public AbstractEvent(String reference)
    {
        this.reference = reference;
    }

    public String reference()
    {
        return reference;
    }
}
