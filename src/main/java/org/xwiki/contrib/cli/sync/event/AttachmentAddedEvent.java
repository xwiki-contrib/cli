package org.xwiki.contrib.cli.sync.event;

public class AttachmentAddedEvent extends AbstractEvent
{
    private String name;

    private byte[] data;

    public AttachmentAddedEvent(String reference, String name, byte[] data)
    {
        super(reference);
        this.name = name;
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
