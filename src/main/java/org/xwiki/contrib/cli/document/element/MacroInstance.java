package org.xwiki.contrib.cli.document.element;

import java.nio.file.Path;

/**
 * Represent a macro instance.
 *
 * @param xffPath the XFF path of the file containing the macro.
 * @param name the name of the macro. For instance groovy, velocity...
 * @param number the position number of the macro in the content.
 *
 * @version $Id$
 */
public record MacroInstance(Path xffPath, String name, int number)
{
}
