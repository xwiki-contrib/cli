package org.xwiki.contrib.cli.sync.event;

public class PageDeletedEvent extends AbstractEvent
{
    public PageDeletedEvent(String reference)
    {
        super(reference);
    }
}
