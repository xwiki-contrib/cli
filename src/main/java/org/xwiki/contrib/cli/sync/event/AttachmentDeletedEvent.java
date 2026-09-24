package org.xwiki.contrib.cli.sync.event;

public class AttachmentDeletedEvent extends AbstractEvent
{
    private String name;

    public AttachmentDeletedEvent(String reference, String name)
    {
        super(reference);
        this.name = name;
    }

    public String name()
    {
        return name;
    }
}
