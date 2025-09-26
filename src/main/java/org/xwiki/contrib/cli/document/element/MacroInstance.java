package org.xwiki.contrib.cli.document.element;

import java.nio.file.Path;

public record MacroInstance(Path xffPath, String name, int number)
{
}
