package org.xwiki.contrib.cli.document.element;

import java.util.Optional;

public record Property(String name, String value, Optional<String> scriptingExtension)
{
}
