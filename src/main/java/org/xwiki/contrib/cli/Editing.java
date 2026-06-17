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
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.embed.EmbeddableComponentManager;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.document.InputDoc;
import org.xwiki.contrib.cli.document.InputOutputDoc;
import org.xwiki.contrib.cli.document.OutputDoc;
import org.xwiki.contrib.cli.document.element.MacroInstance;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;

final class Editing
{
    private static final List<String> KNOWN_MACRO_WITH_WIKI_SYNTAX = List.of("job");

    private final Logger logger = LoggerFactory.getLogger(Editing.class);

    private final EmbeddableComponentManager componentManager;

    Editing()
    {
        componentManager = new EmbeddableComponentManager();
        componentManager.initialize(this.getClass().getClassLoader());
    }

    public void editValue(Command cmd, String oldValue, File folder, File file, EditingCallback callback)
        throws IOException, InterruptedException
    {
        try (var writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(oldValue);
        }

        var filename = file.getAbsolutePath();
        var editor = getEditor(cmd);

        if (StringUtils.isEmpty(editor)) {
            logger.error("Please select an editor with --editor or set an EDITOR environment variable");
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

    public void editValue(Command cmd, String oldValue, String prefix, String suffix, EditingCallback callback)
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
        editValue(cmd, oldValue, dirFile, tmpFile, callback);
    }

    public static String getEditor(Command cmd)
    {
        var editor = "";
        if (!StringUtils.isEmpty(cmd.editor())) {
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

    public String getMacroContent(InputDoc doc, String objectClass, String objectNumber, String property,
        MacroInstance macroSpec) throws DocException, ComponentLookupException, ParseException
    {
        String content = getContentOrValue(doc, objectClass, objectNumber, property);
        return getMacroContent(content, doc.getSyntaxId(), macroSpec);
    }

    public String getMacroContent(String content, String syntax, MacroInstance macroSpec)
        throws DocException, ComponentLookupException, ParseException
    {
        var xdom = parseContent(syntax, content);
        var blocks = getMacroBlocks(xdom, syntax, macroSpec.name());
        if (macroSpec.position() >= blocks.size()) {
            throw new DocException("Can't find macro with spec: " + macroSpec);
        }
        MacroBlock block = (MacroBlock) blocks.get(macroSpec.position());
        return block.getContent();
    }

    public void setMacro(InputOutputDoc doc, String objectClass, String objectNumber, String property,
        MacroInstance macroSpec, String macroContent)
        throws DocException, ComponentLookupException, ParseException
    {
        String content = getContentOrValue(doc, objectClass, objectNumber, property);
        String newContent = updateMacro(content, doc.getSyntaxId(), macroSpec, macroContent);
        setContentOrValue(doc, objectClass, objectNumber, property, newContent);
    }

    public String updateMacro(String content, String syntax, MacroInstance macroSpec, String macroContent)
        throws DocException, ComponentLookupException, ParseException
    {
        var res = updateMacro(content, syntax, macroSpec, macroContent, new MacroCount());
        if (res.isPresent()) {
            return res.get();
        } else {
            logger.error("Can't find macro with spec [{}]", macroSpec);
            return content;
        }
    }

    static class MacroCount
    {
        int count;
    }

    private Optional<String> updateMacro(String content, String syntax, MacroInstance macroSpec, String macroContent,
        MacroCount macroCount)
        throws DocException, ComponentLookupException, ParseException
    {
        var xdom = parseContent(syntax, content);
        var rawBlock = xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        boolean updated = false;

        for (Block block : rawBlock) {
            if (updated) {
                break;
            }
            var macroBlock = (MacroBlock) block;
            if (macroBlock.getId().equals(macroSpec.name())) {
                if (macroCount.count == macroSpec.position()) {
                    MacroBlock newBlock =
                        new MacroBlock(macroBlock.getId(), block.getParameters(), macroContent, macroBlock.isInline());
                    macroBlock.getParent().replaceChild(newBlock, block);
                    updated = true;
                }
                macroCount.count++;
            } else if (KNOWN_MACRO_WITH_WIKI_SYNTAX.contains(macroBlock.getId())) {
                var res = updateMacro(macroBlock.getContent(), syntax, macroSpec, macroContent, macroCount);
                if (res.isPresent()) {
                    MacroBlock newBlock =
                        new MacroBlock(macroBlock.getId(), block.getParameters(), res.get(), macroBlock.isInline());
                    macroBlock.getParent().replaceChild(newBlock, block);
                    updated = true;
                }
            }
        }
        if (updated) {
            return Optional.of(renderXDOM(syntax, xdom));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Return the number of occurrence of the specified not inline macro.
     *
     * @param content the content to analyse
     * @param macroName the macro name
     * @return the number of occurrence of not inline macros
     */
    public int getMacroOccurrences(String content, String syntax, String macroName)
        throws DocException, ComponentLookupException, ParseException
    {
        var xdom = parseContent(syntax, content);
        return getMacroBlocks(xdom, syntax, macroName).size();
    }

    private XDOM parseContent(String syntax, String content)
        throws DocException, ComponentLookupException, ParseException
    {
        if (!Syntax.XWIKI_2_1.toIdString().equals(syntax) && !Syntax.XWIKI_2_0.toIdString().equals(syntax)) {
            throw new DocException("Syntax " + syntax + " not supported");
        }
        Parser parser = componentManager.getInstance(Parser.class, syntax);
        return parser.parse(new StringReader(content));
    }

    private String renderXDOM(String syntax, XDOM xdom) throws ComponentLookupException, DocException
    {
        if (!Syntax.XWIKI_2_1.toIdString().equals(syntax) && !Syntax.XWIKI_2_0.toIdString().equals(syntax)) {
            throw new DocException("Syntax " + syntax + " not supported");
        }
        WikiPrinter printer = new DefaultWikiPrinter();
        BlockRenderer renderer = componentManager.getInstance(BlockRenderer.class, syntax);
        renderer.render(xdom, printer);
        return printer.toString();
    }

    private List<Block> getMacroBlocks(XDOM xdom, String syntax, String macroSpec)
        throws DocException, ComponentLookupException, ParseException
    {
        var rawBlock = xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        var result = new ArrayList<Block>();
        for (Block block : rawBlock) {
            var macroBlock = (MacroBlock) block;
            if (macroBlock.getId().equals(macroSpec)) {
                result.add(block);
            } else if (KNOWN_MACRO_WITH_WIKI_SYNTAX.contains(macroBlock.getId())) {
                var content = parseContent(syntax, macroBlock.getContent());
                result.addAll(getMacroBlocks(content, syntax, macroSpec));
            }
        }
        return result;
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

    public static String getFileExtensionForMacroSpec(MacroInstance macroSpec)
    {
        if (macroSpec != null) {
            String m = macroSpec.name();
            switch (m) {
                case "groovy" -> {
                    return ".groovy";
                }
                case "velocity" -> {
                    return ".vm";
                }
                case "python" -> {
                    return ".py";
                }
                case "html" -> {
                    return ".html";
                }
                case "javascript" -> {
                    return ".js";
                }
                default -> {
                    return ".txt";
                }
            }
        }
        return ".txt";
    }

    interface EditingCallback
    {
        void run(String newValue);
    }
}
