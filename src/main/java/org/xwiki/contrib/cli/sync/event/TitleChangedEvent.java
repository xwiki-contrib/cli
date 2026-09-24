package org.xwiki.contrib.cli.sync.event;

public class TitleChangedEvent extends AbstractEvent
{
    private final String title;

    public TitleChangedEvent(String reference, String title)
    {
        super(reference);
        this.title = title;
    }

    public String title()
    {
        return title;
    }
}
