package org.xwiki.contrib.cli.document.element;

import java.util.Map;

public record Macro(String name, int number, Map<String, String> parameters, String content)
{
}
