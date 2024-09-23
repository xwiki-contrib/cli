package org.xwiki.contrib.cli;

import java.util.Arrays;
import java.util.stream.Collectors;

public class FSDirUtils
{
    public static final String DOT = ".";

    public static final String SLASH = "/";
    public static final String ESCAPED_DOT = "\\.";


    public static String getSpaceFromPathPart(String part)
    {
        StringBuilder expectedSpace = new StringBuilder();
        if (part.length() != 0) {
            boolean keepElement = false;
            String[] splitParts = part.split(SLASH);
            for (String space : splitParts) {
                if (keepElement) {
                    if (expectedSpace.length() != 0) {
                        expectedSpace.append('.');
                    }
                    expectedSpace.append(space.replace(DOT, ESCAPED_DOT));
                }
                keepElement = !keepElement;
            }
        }
        return expectedSpace.toString();
    }

    public static String escapeURLWithSlashes(String path)
    {
        return Arrays.stream(path.split(SLASH)).map(Utils::encodeURLPart).collect(
            Collectors.joining(SLASH));
    }
}
