package org.xwiki.contrib.cli.scriptservicesbinding;

import java.util.Map;

/**
 * Class used to generate a script file which declare all classes for the groovy and velocity binding.
 *
 * @version $Id$
 */
public class ScriptContextGenerator
{
    private static final String PACKAGE_ORG_XWIKI_CLI = "package org.xwiki.cli\n";

    private static final String CLASS = "class ";

    private static final String CLASS_OBJECT = "class java.lang.Object";

    private static final String EXTENDS = " extends ";

    private static final String PUBLIC = "public ";

    private static final String GVY_BINDING_SCRIPT_SERVICE = "GvyBindingScriptService";

    private final Map<String, BindingClass> bindingClasses;

    /**
     * Constructor.
     *
     * @param bindingClasses the binding classes used to build the script.
     */
    public ScriptContextGenerator(Map<String, BindingClass> bindingClasses)
    {
        this.bindingClasses = bindingClasses;
    }

    /**
     * Build a velocity binding script.
     *
     * @return a velocity script which declare all macro and velocity variable for the binding.
     */
    public String buildVelocityBinding()
    {
        var result = new StringBuilder("#* @implicitly included *#\n");
        result.append("#* @vtlvariable name=\"NULL\" type=\"java.lang.null\" *#\n");
        result.append("#* @vtlmacrolibrary path=\"/org/xwiki/cli/macros.vm\"  *#\n");

        for (var k : bindingClasses.entrySet()) {
            if (k.getValue().fieldName() == null) {
                continue;
            }
            if (k.getValue().rootOfBinding()) {
                result.append("#* @vtlvariable name=\"").append(k.getValue().fieldName());
                if (shouldGenerateSubclass(k.getValue().baseClass())) {
                    result.append("\" type=\"org.xwiki.cli.").append(k.getKey())
                        .append("VmBindingScriptService\" *#\n");
                } else {
                    result.append("\" type=\"").append(k.getValue().baseClass()).append("\" *#\n");
                }
            }
        }
        return result.toString();
    }

    /**
     * Build a groovy script which declare all classes used for the binding.
     *
     * @return a groovy script.
     */
    public String buildVelocityBindingClasses()
    {
        var result = new StringBuilder(PACKAGE_ORG_XWIKI_CLI);

        for (var c : bindingClasses.entrySet()) {
            var className = c.getValue().baseClass();
            if (shouldGenerateSubclass(className)) {
                result.append(CLASS).append(c.getKey()).append("VmBindingScriptService");
                if (!className.equals(CLASS_OBJECT)) {
                    result.append(EXTENDS).append(className);
                }
                result.append(" {\n");
                if (c.getValue().constructorsDeclaration() != null) {
                    buildConstructor(c, result, "Vm");
                }
                for (var e : c.getValue().fields().entrySet()) {
                    result.append(PUBLIC).append(e.getValue()).append("VmBindingScriptService get")
                        .append(e.getKey())
                        .append("() {}").append('\n');
                }
                result.append("}\n");
            }
        }
        return result.toString();
    }

    /**
     * Build a groovy script which declare all classes for the groovy binding.
     *
     * @return a groovy script.
     */
    public String buildGroovyBinding()
    {
        var result = new StringBuilder(PACKAGE_ORG_XWIKI_CLI);

        for (var c : bindingClasses.entrySet()) {
            var className = c.getValue().baseClass();
            if (shouldGenerateSubclass(className)) {
                result.append(CLASS).append(c.getKey()).append(GVY_BINDING_SCRIPT_SERVICE);
                if (!className.equals(CLASS_OBJECT)) {
                    result.append(EXTENDS).append(className);
                }
                result.append(" {\n");
                if (c.getValue().constructorsDeclaration() != null) {
                    buildConstructor(c, result, "Gvy");
                }

                for (var e : c.getValue().fields().entrySet()) {
                    result.append(PUBLIC).append(e.getValue()).append(GVY_BINDING_SCRIPT_SERVICE)
                        .append(' ')
                        .append(e.getKey())
                        .append('\n');
                }
                result.append("}\n");
            }
        }

        result.append("abstract class GvyScriptContext extends Script {");
        for (var k : bindingClasses.entrySet()) {
            if ("velocity".equals(k.getValue().kind()) && !"services".equals(k.getValue().fullName())) {
                continue;
            }
            if (k.getValue().fieldName() == null) {
                continue;
            }
            if (k.getValue().rootOfBinding()) {
                if (shouldGenerateSubclass(k.getValue().baseClass())) {
                    result.append(k.getKey()).append(GVY_BINDING_SCRIPT_SERVICE)
                        .append(' ')
                        .append(k.getValue().fieldName())
                        .append(" = (")
                        .append(k.getKey()).append("GvyBindingScriptService)\"\"\n");
                } else {
                    String className = k.getValue().baseClass();
                    result.append(className).append(' ').append(k.getValue().fieldName())
                        .append(" = (")
                        .append(className).append(")\"\"\n");
                }
            }
        }
        result.append("}");
        return result.toString();
    }

    private boolean shouldGenerateSubclass(String classname)
    {
        return classname.equals("java.lang.Object") || !classname.startsWith("java.");
    }

    private void buildConstructor(Map.Entry<String, BindingClass> c, StringBuilder result, String classPrefix)
    {
        for (var con : c.getValue().constructorsDeclaration()) {
            result.append(c.getKey()).append(classPrefix).append("BindingScriptService(");
            char param = 'a';
            int i = 0;
            for (var p : con) {
                if (i > 0) {
                    result.append(',');
                }
                i++;
                result.append(p).append(' ').append(param);
                param++;
            }
            result.append(") { super(");
            i = 0;
            param = 'a';
            for (var p : con) {
                if (i > 0) {
                    result.append(',');
                }
                i++;
                result.append(param);
                param++;
            }
            result.append(")}\n");
        }
    }
}
