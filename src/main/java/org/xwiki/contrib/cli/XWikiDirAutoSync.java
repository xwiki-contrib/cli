package org.xwiki.contrib.cli;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.document.MultipleDoc;
import org.xwiki.contrib.cli.document.XMLFileDoc;
import org.xwiki.contrib.cli.document.element.ExtensionInfos;
import org.xwiki.contrib.cli.document.element.ExtensionInfosList;
import org.xwiki.contrib.cli.document.element.MacroInstance;
import org.xwiki.contrib.cli.document.element.XFFMacroInstance;
import org.xwiki.contrib.cli.scriptservicesbinding.BindingClassMap;
import org.xwiki.contrib.cli.scriptservicesbinding.ScriptContextGenerator;
import org.xwiki.rendering.parser.ParseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static org.xwiki.contrib.cli.Utils.LANG_GROOVY;
import static org.xwiki.contrib.cli.Utils.LANG_VELOCITY_EXTENSION;

class XWikiDirAutoSync
{
    public static final String SEMI_COLUMN = ":";

    private static final String URL_PART_CONTENT = "/content";

    private static final String URL_PART_TITLE = "/title";

    private static final Pattern PAGES_PATTERN_MATCHER =
        Pattern.compile("^(spaces(?:/[^/]+/spaces)*/[^/]+)/pages/([^/]+)");

    private static final Pattern OBJECTS_PROPERTIES_PATTERN_MATCHER =
        Pattern.compile("^/objects/([^/]+)/([^/]+)/properties/([^/]+)$");

    private static final String ATTACHMENTS_PATTERN = "^/attachments/([^/]+)$";

    private static final Pattern ATTACHMENTS_PATTERN_MATCHER = Pattern.compile(ATTACHMENTS_PATTERN);

    private static final String DOT = ".";

    private static final String CONTENT = "content";

    private static final String TITLE = "title";

    private static final String GROOVY_HEADER_DELIMITER = "//////// BEGIN CODE ////////\n";

    private static final String GROOVY_CONTENT_HEADER = """
        package org.xwiki.cli
        import groovy.transform.BaseScript
        import org.xwiki.cli.GvyScriptContext
        @BaseScript GvyScriptContext mainScript
        """ + GROOVY_HEADER_DELIMITER;

    private static final String VELOCITY_HEADER_DELIMITER = "####### BEGIN CODE #######\n";

    private static final String VELOCITY_CONTENT_HEADER = """
        """ + VELOCITY_HEADER_DELIMITER;

    private static final String GROOVY = "groovy";

    private static final String VELOCITY = "velocity";

    private static final String UNDERSCORE = "_";

    private static final String POM_XML = "pom.xml";

    private static final String VM = "vm";

    private static final String OBJECTS = "objects";

    private static final String PROPERTIES = "properties";

    private static final String PATH_SRC = "src";

    private static final String PATH_MAIN = "main";

    private static final String PATH_JAVA = "java";

    private static final String EXTENSION_XWIKI = "xwiki";

    private final Logger logger = LoggerFactory.getLogger(XWikiDirAutoSync.class);

    private final Command command;

    private final Path syncPath;

    private final Path mavenSyncPath;

    private final Set<Path> managedFiles = new HashSet<>();

    private final Map<Path, XFFMacroInstance> macroMap = new HashMap<>();

    private final Editing editing = new Editing();

    XWikiDirAutoSync(Command cmd)
    {
        command = cmd;
        syncPath = Path.of(cmd.cliDir());
        mavenSyncPath = Path.of(cmd.cliDir(), "maven");
    }

    public void monitor() throws IOException, DocException, ComponentLookupException, ParseException
    {
        WatchService watcher = FileSystems.getDefault().newWatchService();
        WatchKey key;
        Map<WatchKey, Path> keyMaps = new HashMap<>();
        try {
            watchDir(keyMaps, syncPath, watcher);
        } catch (IOException e) {
            logger.error("Can't watch dir", e);
            return;
        }

        logger.info("Ready! Press CTRL+C to interrupt.");

        while (true) {
            // wait for key to be signaled
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                // This key is registered only
                // for ENTRY_CREATE events,
                // but an OVERFLOW event can
                // occur regardless if events
                // are lost or discarded.
                if (kind == OVERFLOW) {
                    continue;
                }

                // The filename is the
                // context of the event.
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();

                // Verify that the new
                //  file is a text file.
                // Resolve the filename against the directory.
                // If the filename is "test" and the directory is "foo",
                // the resolved name is "test/foo".
                Path child = keyMaps.get(key).resolve(filename);
                syncFileFromSyncedDir(child, kind);
            }

            // Reset the key -- this step is critical if you want to
            // receive further watch events.  If the key is no longer valid,
            // the directory is inaccessible so exit the loop.
            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
        logger.debug("Ending watch loop");
    }

    void doFirstSync() throws DocException, IOException, ComponentLookupException, ParseException
    {
        Utils.executeScriptOnXWiki("/ensure_scripting_doc_api_installed.xwiki", this.command);
        if (command.pom()) {
            createMavenProject();
        }
        syncFromMavenRepos();
    }

    private List<ExtensionInfos> getDependencyFromXWiki() throws DocException, JsonProcessingException
    {
        var responseStr = Utils.executeScriptOnXWiki("/generate_dependencies.xwiki", this.command);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(responseStr, ExtensionInfosList.class);
    }

    private void createMavenProject() throws IOException, DocException
    {
        Files.createDirectories(mavenSyncPath);

        // Create POM file
        var sourceProjectPomPath = Path.of(command.mvnRepo(), POM_XML);
        var xmlFile = Path.of(mavenSyncPath.toString(), POM_XML);
        Files.copy(sourceProjectPomPath, xmlFile, StandardCopyOption.REPLACE_EXISTING);

        var pomXml = Utils.parseXML(Files.readString(xmlFile));
        var dependenciesNode =
            pomXml.getRootElement().selectSingleNode("//*[local-name()='project']/*[local-name()='dependencies']");
        if (dependenciesNode == null) {
            dependenciesNode = pomXml.getRootElement().addElement("dependencies");
        }
        var dependencyNodes = dependenciesNode.selectNodes("*[local-name()='dependency']");
        var currentExtensions = new HashMap<String, ExtensionInfos>();
        for (var n : dependencyNodes) {
            var groupId = n.selectSingleNode("*[local-name()='groupId']").getStringValue();
            var artifactId = n.selectSingleNode("*[local-name()='artifactId']").getStringValue();
            currentExtensions.put(groupId + SEMI_COLUMN + artifactId,
                new ExtensionInfos(
                    groupId,
                    artifactId,
                    n.selectNodes("*[local-name()='version']").stream().findFirst().map(Node::getStringValue)
                        .orElse("")
                ));
        }
        for (var n : getDependencyFromXWiki()) {
            var currentExtensionsKey = n.artefactId() + SEMI_COLUMN + n.groupId();
            if (!currentExtensions.containsKey(currentExtensionsKey)) {
                Element e = ((Element) dependenciesNode).addElement("dependency");
                Element groupId = e.addElement("groupId");
                Element artifactId = e.addElement("artifactId");
                Element version = e.addElement("version");
                groupId.setText(n.groupId());
                artifactId.setText(n.artefactId());
                version.setText(n.version());
            }
        }

        OutputFormat outFormat = OutputFormat.createCompactFormat();
        outFormat.setTrimText(false);
        outFormat.setEncoding("utf-8");
        outFormat.setExpandEmptyElements(false);
        outFormat.setOmitEncoding(true);
        outFormat.setSuppressDeclaration(true);
        try (var out = new FileOutputStream(xmlFile.toFile())) {
            out.write("<?xml version=\"1.0\"?>\n".getBytes(StandardCharsets.UTF_8));
            XMLWriter writer = new XMLWriter(out, outFormat);
            writer.write(pomXml);
            writer.flush();
        } catch (IOException e) {
            throw new DocException(e);
        }

        // Create JAVA project structure
        var bindingClasses = Utils.executeScriptOnXWiki("/get_binding.xwiki", this.command);
        var mapper = new ObjectMapper();
        var scriptContextGenerator =
            new ScriptContextGenerator(mapper.readValue(bindingClasses, BindingClassMap.class));

        var baseJavaPath = Path.of(mavenSyncPath.toString(), PATH_SRC, PATH_MAIN, PATH_JAVA);
        var xwikiCliJavaPath = Path.of(baseJavaPath.toString(), "org", EXTENSION_XWIKI, "cli");
        Files.createDirectories(xwikiCliJavaPath);

        Files.writeString(Path.of(xwikiCliJavaPath.toString(), "GvyScriptContext.groovy"),
            scriptContextGenerator.buildGroovyBinding());
        Files.writeString(Path.of(xwikiCliJavaPath.toString(), "VmScriptContext.groovy"),
            scriptContextGenerator.buildVelocityBindingClasses());
        Files.writeString(Path.of(baseJavaPath.toString(), "velocity_implicit.vm"),
            scriptContextGenerator.buildVelocityBinding());

        // Define default macro lib
        var macrosVmContent = Utils.executeScriptOnXWiki("/get_macros_vm_content.xwiki", this.command);
        Files.writeString(Path.of(xwikiCliJavaPath.toString(), "macros.vm"), macrosVmContent);
    }

    private void syncFromMavenRepos() throws IOException, DocException, ComponentLookupException, ParseException
    {
        var allPages = Utils.listAllPagesMvnRepos(command);
        for (var p : allPages) {
            syncFileFromMvnRepos(p);
            if (command.pom()) {
                syncMavenRepos(p);
            }
        }
    }

    private void syncFileFromMvnRepos(Path srcFile) throws DocException, IOException
    {
        var xmlFile = new XMLFileDoc(command, srcFile.toString());
        var dstFile = syncPath.toString() + Utils.fromReferenceToXFFPath(xmlFile.getReference());
        var content = xmlFile.getContent();
        var contentExtension = Utils.getExtensionFromSyntaxId(xmlFile.getSyntaxId());
        var contentFilePath = Path.of(dstFile, CONTENT);
        Files.createDirectories(contentFilePath.getParent());
        Files.writeString(contentFilePath, content);
        managedFiles.add(contentFilePath);
        var linkPath = Path.of(contentFilePath + DOT + contentExtension);
        if (!Files.exists(linkPath)) {
            Files.createSymbolicLink(linkPath, Path.of(CONTENT));
        }

        var title = xmlFile.getTitle();
        var titleFilePath = Path.of(dstFile, TITLE);
        Files.createDirectories(titleFilePath.getParent());
        Files.writeString(titleFilePath, title);
        managedFiles.add(titleFilePath);
        linkPath = Path.of(titleFilePath + DOT + VM);
        if (!Files.exists(linkPath)) {
            Files.createSymbolicLink(linkPath, Path.of(TITLE));
        }

        for (var attachment : xmlFile.getAttachments()) {
            var attachmentContent = xmlFile.getAttachment(attachment.name());
            var attachmentFilePath = Path.of(dstFile, "attachments", attachment.name());
            Files.createDirectories(attachmentFilePath.getParent());
            Files.write(attachmentFilePath, attachmentContent);
            managedFiles.add(attachmentFilePath);
        }

        for (var obj : xmlFile.getObjects(null, null, null)) {
            var objClass = obj.objectClass();
            var objNumber = Integer.toString(obj.number());
            for (var property : obj.properties()) {
                var propertyValueFileName = Path.of(dstFile, OBJECTS, objClass, objNumber, PROPERTIES,
                    property.name());
                Files.createDirectories(propertyValueFileName.getParent());
                Files.writeString(propertyValueFileName, property.value());
                managedFiles.add(propertyValueFileName);
                if (property.scriptingExtension().isPresent()) {
                    var linkPropertyPath = Path.of(propertyValueFileName + DOT + property.scriptingExtension().get());
                    if (!Files.exists(linkPropertyPath)) {
                        Files.createSymbolicLink(linkPropertyPath, Path.of(property.name()));
                    }
                }
            }
        }
    }

    private void syncMavenRepos(Path srcFile) throws DocException, IOException, ComponentLookupException, ParseException
    {
        var xmlFile = new XMLFileDoc(command, srcFile.toString());
        var dstXFFFile = syncPath.toString() + Utils.fromReferenceToXFFPath(xmlFile.getReference());
        var dstJava = Path.of(mavenSyncPath.toString(), PATH_SRC, PATH_MAIN, PATH_JAVA,
            Utils.fromReferenceToJavaNamespace(xmlFile.getReference()));
        Files.createDirectories(dstJava);

        // Handle title
        var titleFilePath = Path.of(dstJava.toString(), TITLE + DOT + VM);
        if (!Files.exists(titleFilePath)) {
            Files.createSymbolicLink(titleFilePath, Path.of(dstXFFFile, TITLE));
        }

        // Handle content
        if ("xwiki/2.1".equals(xmlFile.getSyntaxId())) {
            var content = xmlFile.getContent();
            Path xffContentPath = Path.of(dstXFFFile, CONTENT);
            for (int i = 0; i < editing.getMacroOccurrences(content, xmlFile.getSyntaxId(), GROOVY); i++) {
                var macroContent =
                    editing.getMacroContent(content, xmlFile.getSyntaxId(), new MacroInstance(GROOVY, i));
                String scriptContent = GROOVY_CONTENT_HEADER + macroContent;
                Path filePath = Path.of(dstJava.toString(), "GroovyMacro_" + i + DOT + GROOVY);
                Files.writeString(filePath, scriptContent);
                managedFiles.add(filePath);
                macroMap.put(filePath, new XFFMacroInstance(xffContentPath, xmlFile.getSyntaxId(), GROOVY, i));
            }
            for (int i = 0; i < editing.getMacroOccurrences(content, xmlFile.getSyntaxId(), VELOCITY); i++) {
                var macroContent =
                    editing.getMacroContent(content, xmlFile.getSyntaxId(), new MacroInstance(VELOCITY, i));
                String scriptContent = VELOCITY_CONTENT_HEADER + macroContent;
                Path filePath = Path.of(dstJava.toString(), "VelocityMacro_" + i + DOT + VM);
                Files.writeString(filePath, scriptContent);
                managedFiles.add(filePath);
                macroMap.put(filePath, new XFFMacroInstance(xffContentPath, xmlFile.getSyntaxId(), VELOCITY, i));
            }
        }

        // Handle objects
        for (var obj : xmlFile.getObjects(null, null, null)) {
            var objClass = obj.objectClass();
            var objNumber = Integer.toString(obj.number());
            for (var property : obj.properties()) {
                if (property.scriptingExtension().isEmpty()) {
                    continue;
                }
                var propertyValueFileName = Path.of(dstXFFFile, OBJECTS, objClass, objNumber, PROPERTIES,
                    property.name());

                if (GROOVY.equals(property.scriptingExtension().orElse(""))
                    || VM.equals(property.scriptingExtension().orElse("")))
                {
                    Path scriptFileName;
                    if (GROOVY.equals(property.scriptingExtension().orElse(""))) {
                        scriptFileName =
                            Path.of(dstJava.toString(),
                                objClass.replace(DOT, UNDERSCORE) + UNDERSCORE + objNumber + DOT + GROOVY);
                    } else {
                        scriptFileName =
                            Path.of(dstJava.toString(),
                                objClass.replace(DOT, UNDERSCORE) + UNDERSCORE + objNumber + DOT + VM);
                    }
                    if (!Files.exists(scriptFileName)) {
                        Files.createSymbolicLink(scriptFileName, propertyValueFileName);
                    }
                } else if (EXTENSION_XWIKI.equals(property.scriptingExtension().orElse(""))) {
                    var content = property.value();
                    for (int i = 0; i < editing.getMacroOccurrences(content, xmlFile.getSyntaxId(), GROOVY); i++) {
                        var macroContent =
                            editing.getMacroContent(content, xmlFile.getSyntaxId(), new MacroInstance(GROOVY, i));
                        var scriptContent = GROOVY_CONTENT_HEADER + macroContent;
                        Path filePath = Path.of(dstJava.toString(),
                            objClass.replace(DOT, UNDERSCORE) + UNDERSCORE + objNumber + "_GroovyMacro_" + i + DOT
                                + GROOVY);
                        Files.writeString(filePath, scriptContent);
                        managedFiles.add(filePath);
                        macroMap.put(filePath,
                            new XFFMacroInstance(propertyValueFileName, xmlFile.getSyntaxId(), GROOVY, i));
                    }
                    for (int i = 0; i < editing.getMacroOccurrences(content, xmlFile.getSyntaxId(), VELOCITY); i++) {
                        var macroContent =
                            editing.getMacroContent(content, xmlFile.getSyntaxId(), new MacroInstance(VELOCITY, i));
                        var scriptContent = VELOCITY_CONTENT_HEADER + macroContent;
                        Path filePath = Path.of(dstJava.toString(),
                            objClass.replace(DOT, UNDERSCORE) + UNDERSCORE + objNumber + "_VelocityMacro_" + i + ".vm");
                        Files.writeString(filePath, scriptContent);
                        managedFiles.add(filePath);
                        macroMap.put(filePath,
                            new XFFMacroInstance(propertyValueFileName, xmlFile.getSyntaxId(), VELOCITY, i));
                    }
                }
            }
        }
    }

    private void syncFileFromSyncedDir(Path file, WatchEvent.Kind<?> kind)
        throws IOException, DocException, ComponentLookupException, ParseException
    {
        logger.debug("Sync file at path: {}", file);

        // TODO improve it !!
        // We should not in all case rewrite the value
        write(file);
    }

    private void write(Path path) throws IOException, DocException, ComponentLookupException, ParseException
    {
        if (managedFiles.contains(path) && Files.exists(path) && macroMap.containsKey(path)) {
            var macroInfo = macroMap.get(path);
            var newMacroContentWithHeader = Files.readString(path);
            if (StringUtils.isEmpty(newMacroContentWithHeader)) {
                logger.debug("Ignoring empty file");
                return;
            }
            String newMacroContent;
            if (path.getFileName().toString().endsWith('.' + LANG_GROOVY)) {
                newMacroContent = newMacroContentWithHeader.substring(
                    newMacroContentWithHeader.indexOf(GROOVY_HEADER_DELIMITER) + GROOVY_HEADER_DELIMITER.length());
            } else if (path.getFileName().toString().endsWith('.' + LANG_VELOCITY_EXTENSION)) {
                newMacroContent = newMacroContentWithHeader.substring(
                    newMacroContentWithHeader.indexOf(VELOCITY_CONTENT_HEADER) + VELOCITY_CONTENT_HEADER.length());
            } else {
                newMacroContent = newMacroContentWithHeader;
            }

            var newValue = editing.updateMacro(
                Files.readString(macroInfo.xffPath()),
                macroInfo.syntax(),
                macroInfo.toMacroInstance(),
                newMacroContent);
            Files.writeString(macroMap.get(path).xffPath(), newValue);
        } else if (managedFiles.contains(path) && Files.exists(path)) {
            var newContent = Files.readAllBytes(path);
            putValue(syncPath.relativize(path).toString(), newContent);
        }
    }

    private void putValue(String path, byte[] value)
    {
        Matcher pageMatcher = PAGES_PATTERN_MATCHER.matcher(path);
        if (pageMatcher.find()) {
            String space = FSDirUtils.getSpaceFromPathPart(pageMatcher.group(1));
            String page = pageMatcher.group(2).replace(FSDirUtils.DOT, FSDirUtils.ESCAPED_DOT);

            try {
                MultipleDoc document = new MultipleDoc(command, command.wiki(), space + '.' + page);

                String remainingPath = path.substring(pageMatcher.end());

                Matcher propertyMatcher = OBJECTS_PROPERTIES_PATTERN_MATCHER.matcher(remainingPath);
                if (propertyMatcher.matches()) {
                    String className = propertyMatcher.group(1);
                    String objectNumber = propertyMatcher.group(2);
                    String propertyName = propertyMatcher.group(3);
                    String stringValue = new String(value, StandardCharsets.UTF_8);

                    document.setValue(className, objectNumber, propertyName, stringValue);
                    document.save();
                    return;
                }

                if (remainingPath.equals(URL_PART_CONTENT) || remainingPath.equals(URL_PART_TITLE)) {
                    String stringValue = new String(value, StandardCharsets.UTF_8);
                    if (remainingPath.equals(URL_PART_TITLE)) {
                        document.setTitle(stringValue.stripTrailing());
                    } else {
                        document.setContent(stringValue);
                    }
                    document.save();
                    return;
                }

                Matcher attachmentMatcher = ATTACHMENTS_PATTERN_MATCHER.matcher(remainingPath);
                if (attachmentMatcher.matches()) {
                    String attachmentName = attachmentMatcher.group(1);
                    document.setAttachment(attachmentName, value);
                    document.save();
                }
            } catch (DocException | IOException e) {
                logger.debug("Can't get value", e);
            }
        }
    }

    private void watchDir(Map<WatchKey, Path> keyMaps, Path path, WatchService watcher) throws IOException
    {
        keyMaps.put(path.register(watcher,
            ENTRY_CREATE,
            ENTRY_DELETE,
            ENTRY_MODIFY), path);
        try (var dirList = Files.list(path)) {
            for (var d : dirList.filter(Files::isDirectory).toList()) {
                watchDir(keyMaps, d, watcher);
            }
        }
    }
}
