package org.xwiki.contrib.cli.sync.event;

import org.xwiki.contrib.cli.document.element.MacroInstance;
import org.xwiki.contrib.cli.document.element.ObjectProperty;

public class MacroInObjectPropertyChangedEvent extends ObjectPropertyChangedEvent
{
    private final String macroContent;

    private final MacroInstance macroInstance;

    public MacroInObjectPropertyChangedEvent(String reference, ObjectProperty objectProperty,
        MacroInstance macroInstance, String macroContent)
    {
        super(reference, objectProperty, null);
        this.macroContent = macroContent;
        this.macroInstance = macroInstance;
    }

    @Override
    public String newValue()
    {
        throw new UnsupportedOperationException();
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
