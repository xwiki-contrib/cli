package org.xwiki.contrib.cli.scriptservicesbinding;

import java.util.List;
import java.util.Map;

/**
 * Information about the binding classes.
 *
 * @param fields name of the field of this class for the sub-binding. For instance services.query....
 * @param baseClass name of the class.
 * @param fieldName name of the field of the binding.
 * @param fullName full name of the class.
 * @param rootOfBinding true if it's the root of the binding.
 * @param constructorsDeclaration the parameters of each constructor.
 * @param kind type of binding.
 *
 * @version $Id$
 */
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
