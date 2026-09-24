package org.xwiki.contrib.cli.sync.event;

import org.xwiki.contrib.cli.document.element.MacroInstance;

public class MacroInContentChangedEvent extends AbstractEvent
{
    private final String macroContent;

    private final MacroInstance macroInstance;

    public MacroInContentChangedEvent(String reference, MacroInstance macroInstance, String macroContent)
    {
        super(reference);
        this.macroContent = macroContent;
        this.macroInstance = macroInstance;
    }

    public String macroContent()
    {
        return macroContent;
    }

    public MacroInstance macroInstance()
    {
        return macroInstance;
    }
}
