package org.xwiki.contrib.cli.document.element;

/**
 * Define a macro into a document.
 *
 * @param name the name of the macro.
 * @param position the position into the content.
 * @version $Id$
 */
public record MacroInstance(String name, int position)
{
    /**
     * Parse the macro spec form and create a new {@link MacroInstance} object.
     *
     * @param spec the macro spec in the format <macro name>/<macro number>.
     * @return the new {@link MacroInstance) object.
     */
    public static MacroInstance fromString(String spec)
    {
        var specSplit = spec.split("/");
        return new MacroInstance(specSplit[0], Integer.parseInt(specSplit[1]));
    }
}
