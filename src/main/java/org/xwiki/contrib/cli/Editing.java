/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xwiki.contrib.cli;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;

import org.xwiki.contrib.cli.document.InputDoc;
import org.xwiki.contrib.cli.document.InputOutputDoc;
import org.xwiki.contrib.cli.document.OutputDoc;

import static java.lang.System.out;

final class Editing
{
    private static final String MACRO_TAG_END = "}}";

    private static final String MACRO_BEGIN = "{{";

    private static final String MACRO_TAG_BEGIN_END = "{{/";

    private Editing()
    {
        // ignore
    }

    public static void editValue(Command cmd, String oldValue, File folder, File file, EditingCallback callback)
        throws IOException, InterruptedException
    {
        try (var writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(oldValue);
        }

        var filename = file.getAbsolutePath();
        var editor = getEditor(cmd);

        if (Utils.isEmpty(editor)) {
            out.println("Please select an editor with --editor or set an EDITOR environment variable");
            return;
        }

        new ProcessBuilder(editor, filename).inheritIO().start();
        final var path = FileSystems.getDefault().getPath(folder.getAbsolutePath());

        try (var watchService = FileSystems.getDefault().newWatchService()) {
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            while (true) {
                //TODO: catch if a ENTRY_MODIFY and ENTRY_CREATE are fired together
                final var wk = watchService.take();
                for (var event : wk.pollEvents()) {
                    final var changed = (Path) event.context();
                    if (filename.endsWith(changed.toString())) {
                        callback.run(Files.readString(Path.of(filename)));
                    }
                }
                wk.reset();
            }
        }
    }

    public static void editValue(Command cmd, String oldValue, String prefix, String suffix, EditingCallback callback)
        throws IOException, InterruptedException
    {
        var dir = Files.createTempDirectory("xwiki-cli");
        if (cmd.pom()) {
            Path pomFilePath = null;
            try {
                // Get the path of the executing JAR, to get the path to the pom file
                pomFilePath =
                    Path.of(Editing.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath())
                        .getParent().getParent().resolve("resources/pom.xml");
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            Files.copy(pomFilePath, dir.resolve("pom.xml"));
            dir = Files.createDirectories(dir.resolve("src/main/groovy"));
        }
        var dirFile = dir.toFile();
        var tmpFile = File.createTempFile(prefix, suffix, dirFile);
        Editing.editValue(cmd, oldValue, dirFile, tmpFile, callback);
    }

    public static String getEditor(Command cmd)
    {
        var editor = "";
        if (!Utils.isEmpty(cmd.editor())) {
            editor = cmd.editor();
        } else {
            try {
                editor = System.getenv("EDITOR");
            } catch (Exception e) {
                //Variable either doesn't exist or a security manager has denied access to it
            }
        }
        return editor;
    }

    public static void updateDocFromTextPage(OutputDoc doc, String pageText) throws DocException
    {
        final var len = pageText.length();
        var curIndex = 0;
        while (curIndex < len) {
            final var eq = pageText.indexOf('=', curIndex);
            if (eq == -1) {
                // TODO handle unexpected garbage at the end
                break;
            }
            final var prop = pageText.substring(curIndex, eq).trim();
            final var valueStart = eq + 1;
            String value = null;

            if (valueStart >= len) {
                // TODO handle unexpected end of file
                break;
            }

            final var nextNL = pageText.indexOf('\n', valueStart);
            var beforeNL = nextNL == -1 ? "" : pageText.substring(valueStart, nextNL);

            if (nextNL == -1) {
                value = pageText.substring(valueStart);
                curIndex = len;
            } else if (beforeNL.isBlank() && valueStart + 1 < len && pageText.charAt(valueStart + 1) == '-') {
                final var lineEnd = pageText.indexOf('\n', valueStart + 1);
                if (lineEnd == -1) {
                    // TODO handle unexpected end of file
                    break;
                }
                final var line = pageText.substring(valueStart, lineEnd + 1);
                final var valueEnd = pageText.indexOf(line, lineEnd + 1);
                if (valueEnd == -1) {
                    // TODO handle missing closing line
                    break;
                }
                value = pageText.substring(lineEnd + 1, valueEnd);
                curIndex = valueEnd + line.length();
            } else {
                value = beforeNL;
                curIndex = nextNL + 1;
            }

            switch (prop) {
                case "#" -> { /* Intentionally left blank */ }
                case "content" -> doc.setContent(value);
                case "title" -> doc.setTitle(value);
                default -> {
                    var dot = prop.lastIndexOf('.');
                    if (dot < 1) {
                        // TODO handle missing dot, or a dot at the start of the line
                        continue;
                    }
                    var objectSpec = prop.substring(0, dot);

                    var slash = objectSpec.indexOf('/');
                    if (slash < 1) {
                        // TODO handle missing /, or at the start of the line
                        continue;
                    }
                    var objectClass = objectSpec.substring(0, slash);
                    var objectNumber = objectSpec.substring(slash + 1);
                    var propertyName = prop.substring(dot + 1);
                    doc.setValue(objectClass, objectNumber, propertyName, value);
                }
            }
        }
        doc.save();
    }

    public static String getMacroContent(InputDoc doc, String objectClass, String objectNumber, String property,
        String macroSpec) throws DocException
    {
        String content = getContentOrValue(doc, objectClass, objectNumber, property);
        return getMacroContent(content, macroSpec);
    }

    public static String getMacroContent(String content, String macroSpec) throws DocException
    {
        int[] macroContentPos = getMacroContentPos(macroSpec, content);
        return content.substring(macroContentPos[0], macroContentPos[1]);
    }

    public static void setMacro(InputOutputDoc doc, String objectClass, String objectNumber,
        String property, String macroSpec, String macroContent) throws DocException
    {
        String content = getContentOrValue(doc, objectClass, objectNumber, property);
        String newContent = updateMacro(content, macroSpec, macroContent);
        setContentOrValue(doc, objectClass, objectNumber, property, newContent);
    }

    public static String updateMacro(String content, String macroSpec, String macroContent) throws DocException
    {
        int[] macroContentPos = getMacroContentPos(macroSpec, content);
        int startIndex = macroContentPos[0];
        int endIndex = macroContentPos[1];
        String start = startIndex == 0 ? "" : content.substring(0, startIndex);
        String end = endIndex < content.length() ? content.substring(endIndex) : "";
        return start + macroContent + end;
    }

    /**
     * Return the number of occurrence of the specified not inline macro.
     *
     * @param content the content to analyse
     * @param macroName the macro name
     * @return the number of occurrence of not inline macros
     */
    public static int getMacroOccurrences(String content, String macroName) throws DocException
    {
        int startIndex = 0;
        String macroStart = MACRO_BEGIN + macroName;
        String macroEnd = MACRO_TAG_BEGIN_END + macroName + MACRO_TAG_END;
        int i = 0;
        while (true) {
            int macroStartIndex = content.indexOf(macroStart, startIndex);
            if (macroStartIndex > 0 && content.charAt(macroStartIndex - 1) != '\n') {
                // ignore inline macro
                startIndex = macroStartIndex + 2;
                continue;
            }
            if (macroStartIndex == -1) {
                return i;
            }
            i++;
            int endIndex = content.indexOf(macroEnd + '\n', macroStartIndex);
            if (endIndex <= 0) {
                if (content.endsWith(macroEnd)) {
                    return i;
                } else {
                    throw new DocException("Macro not closed");
                }
            }
            startIndex = endIndex + 2;
        }
    }

    private static void setContentOrValue(InputOutputDoc doc, String objectClass, String objectNumber, String property,
        String newContent)
        throws DocException
    {
        if (property == null) {
            doc.setContent(newContent);
            return;
        }
        doc.setValue(objectClass, objectNumber, property, newContent);
    }

    private static String getContentOrValue(InputDoc doc, String objectClass, String objectNumber,
        String property) throws DocException
    {
        if (property == null) {
            return doc.getContent();
        }

        return doc.getValue(objectClass, objectNumber, property).orElseThrow(
            () -> new DocException("This property was not found"));
    }

    private static int[] getMacroContentPos(String macroSpec, String content) throws DocException
    {
        String[] macroSpecArray = macroSpec.split("/");
        String macroName;
        int macroNumber;
        if (macroSpecArray.length == 1) {
            macroName = macroSpecArray[0];
            macroNumber = 0;
        } else {
            if (macroSpecArray.length != 2) {
                throw new DocException("Invalid macro specification");
            }
            macroName = macroSpecArray[0];
            // throws NumberFormatException
            macroNumber = Integer.parseInt(macroSpecArray[1]);
        }
        return getMacroContentPos(content, macroName, macroNumber);
    }

    public static String getFileExtensionForMacroSpec(String macroSpec)
    {
        if (macroSpec != null) {
            String m = macroSpec + '/';
            if (m.startsWith("groovy/")) {
                return ".groovy";
            }

            if (m.startsWith("velocity/")) {
                return ".vm";
            }

            if (m.startsWith("python/")) {
                return ".py";
            }

            if (m.startsWith("html/")) {
                return ".html";
            }

            if (m.startsWith("javascript/")) {
                return ".js";
            }
        }

        return ".txt";
    }

    private static int[] getMacroContentPos(String content, String macroName, int macroNumber) throws DocException
    {
        int n = macroNumber;
        String macroStart = MACRO_BEGIN + macroName;
        String macroEnd = MACRO_TAG_BEGIN_END + macroName + MACRO_TAG_END;
        int macroContentStart = -1;
        int macroContentEnd = -1;
        int from = 0;
        do {
            int macroPos = content.indexOf(macroStart, from);
            if (macroPos == -1) {
                throw new DocException("Macro not found. Macro name: " + macroName + ", macro number: " + macroNumber);
            }
            if (macroPos > 0 && content.charAt(macroPos - 1) != '\n') {
                // ignore inline macro
                from = macroPos + 2;
                continue;
            }
            macroContentStart = content.indexOf(MACRO_TAG_END, macroPos + macroStart.length());
            if (macroContentStart == -1) {
                throw new DocException("Macro start tag isn't finished");
            }
            macroContentStart += MACRO_TAG_END.length();
            if (content.charAt(macroContentStart) == '\n') {
                macroContentStart++;
            }
            macroContentEnd = content.indexOf(macroEnd + '\n', macroContentStart);
            if (macroContentEnd == -1) {
                if (content.endsWith(macroEnd)) {
                    macroContentEnd = content.length() - macroEnd.length();
                } else {
                    throw new DocException(
                        "Unclosed macro. Macro name: " + macroName + ", macro number: " + macroNumber);
                }
            }
            --n;
            from = macroContentEnd + macroEnd.length();
        } while (n >= 0);
        return new int[] { macroContentStart, macroContentEnd };
    }

    interface EditingCallback
    {
        void run(String newValue);
    }
}
