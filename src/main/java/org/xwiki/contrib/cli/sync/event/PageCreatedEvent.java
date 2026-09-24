package org.xwiki.contrib.cli.sync.event;

public class PageCreatedEvent extends AbstractEvent
{
    private final String syntax;

    public PageCreatedEvent(String reference, String syntax)
    {
        super(reference);
        this.syntax = syntax;
    }

    public String syntax()
    {
        return syntax;
    }
}
