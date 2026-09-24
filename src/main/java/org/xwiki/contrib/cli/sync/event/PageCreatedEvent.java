package org.xwiki.contrib.cli.sync.event;

public class PageCreatedEvent extends AbstractEvent
{
    private String syntax;

    public PageCreatedEvent(String reference, String syntax)
    {
        super(reference);
    }

    public String syntax()
    {
        return syntax;
    }
}
