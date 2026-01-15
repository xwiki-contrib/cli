package org.xwiki.contrib.cli.scriptservicesbinding;

import java.util.List;
import java.util.Map;

public record BindingClass(
    Map<String, String> fields,
    String baseClass,
    String fieldName,
    String fullName,
    boolean rootOfBinding,
    List<List<String>> constructorsDeclaration,
    String kind
)
{
}
