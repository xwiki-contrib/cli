package org.xwiki.contrib.cli.sync.event;

public class AttachmentUpdatedEvent extends AbstractEvent
{
    private String name;

    private byte[] data;

    public AttachmentUpdatedEvent(String reference, String name, byte[] data)
    {
        super(reference);
        this.name = name;
        this.data = data;
    }

    public String name()
    {
        return name;
    }

    public byte[] data()
    {
        return data;
    }
}
