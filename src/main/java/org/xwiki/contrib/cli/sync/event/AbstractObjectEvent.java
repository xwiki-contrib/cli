package org.xwiki.contrib.cli.sync.event;

public abstract class AbstractObjectEvent extends AbstractEvent
{
    private final String className;

    private final int number;



    public AbstractObjectEvent(String reference, String className, int number)
    {
        super(reference);
        this.className = className;
        this.number = number;
    }

    public String className()
    {
        return className;
    }

    public int number()
    {
        return number;
    }

}
