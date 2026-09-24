package org.xwiki.contrib.cli.sync.event;

public class ContentChangedEvent extends AbstractEvent
{
    private final String content;

    public ContentChangedEvent(String reference, String content)
    {
        super(reference);
        this.content = content;
    }

    public String content()
    {
        return content;
    }
}
