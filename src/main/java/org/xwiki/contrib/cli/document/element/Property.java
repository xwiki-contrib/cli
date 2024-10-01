package org.xwiki.contrib.cli.document.element;

import java.util.Optional;

/**
 * Properties for an object.
 *
 * @param name name of the property.
 * @param value value of the property.
 * @param scriptingExtension scripting extension (if available).
 *
 * @version $Id$
 */
public record Property(String name, String value, Optional<String> scriptingExtension)
{
}
