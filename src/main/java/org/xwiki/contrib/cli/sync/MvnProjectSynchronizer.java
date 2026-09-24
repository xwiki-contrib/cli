package org.xwiki.contrib.cli.sync;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.Utils;
import org.xwiki.contrib.cli.document.InputDoc;
import org.xwiki.contrib.cli.document.element.ExtensionInfos;
import org.xwiki.contrib.cli.document.element.ExtensionInfosList;
import org.xwiki.contrib.cli.document.element.MacroInstance;
import org.xwiki.contrib.cli.document.element.ObjectProperty;
import org.xwiki.contrib.cli.document.element.Property;
import org.xwiki.contrib.cli.scriptservicesbinding.BindingClassMap;
import org.xwiki.contrib.cli.scriptservicesbinding.ScriptContextGenerator;
import org.xwiki.contrib.cli.sync.event.AbstractEvent;
import org.xwiki.contrib.cli.sync.event.AttachmentAddedEvent;
import org.xwiki.contrib.cli.sync.event.AttachmentDeletedEvent;
import org.xwiki.contrib.cli.sync.event.AttachmentUpdatedEvent;
import org.xwiki.contrib.cli.sync.event.ContentChangedEvent;
import org.xwiki.contrib.cli.sync.event.MacroInContentChangedEvent;
import org.xwiki.contrib.cli.sync.event.MacroInObjectPropertyChangedEvent;
import org.xwiki.contrib.cli.sync.event.ObjectAddedEvent;
import org.xwiki.contrib.cli.sync.event.ObjectPropertyChangedEvent;
import org.xwiki.contrib.cli.sync.event.ObjectRemovedEvent;
import org.xwiki.contrib.cli.sync.event.PageCreatedEvent;
import org.xwiki.contrib.cli.sync.event.PageDeletedEvent;
import org.xwiki.contrib.cli.sync.event.TitleChangedEvent;
import org.xwiki.rendering.parser.ParseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.xwiki.contrib.cli.Utils.LANG_GROOVY;
import static org.xwiki.contrib.cli.Utils.LANG_VELOCITY_EXTENSION;

public class MvnProjectSynchronizer extends AbstractWorkingDirSynchronizer implements PagesSynchronizer
{
    private static final String POM_XML = "pom.xml";

    public static final String SEMI_COLUMN = ":";

    private static final String DOT = ".";

    private static final String TITLE = "title";

    private static final String VM = "vm";

    private static final String PATH_SRC = "src";

    private static final String PATH_MAIN = "main";

    private static final String PATH_JAVA = "java";

    private static final String UNDERSCORE = "_";

    private static final String GROOVY = "groovy";

    private static final String VELOCITY = "velocity";

    private static final String EXTENSION_XWIKI = "xwiki";

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

    private final Path mavenSyncPath;

    private final Path javaPath;

    private final Path xwikiCliJavaPath;

    // Map of: <filePath, docReference>
    private final Map<Path, String> titleMap = new HashMap<>();

    // Map of: <filePath, <docReference, macro>>
    private final Map<Path, Pair<String, MacroInstance>> macroInContentMap = new HashMap<>();

    // Map of: <filePath, <docReference, Object>>
    private final Map<Path, Pair<String, ObjectProperty>> objectPropertyContentMap = new HashMap<>();

    // Map of: <filePath, <docReference, Object, macro>>
    private final Map<Path, Triple<String, ObjectProperty, MacroInstance>> objectPropertyMacroMap = new HashMap<>();

    public MvnProjectSynchronizer(Command cmd, MemoryDocumentManager memoryDocumentManager)
    {
        super(cmd, memoryDocumentManager);
        mavenSyncPath = Path.of(cmd.workingDirectory(), "mvn");
        javaPath = Path.of(mavenSyncPath.toString(), PATH_SRC, PATH_MAIN, PATH_JAVA);
        xwikiCliJavaPath = Path.of(javaPath.toString(), "org", EXTENSION_XWIKI, "cli");
    }

    private Set<Path> getManagedFiles()
    {
        var res = new HashSet<Path>();
        res.addAll(titleMap.keySet());
        res.addAll(macroInContentMap.keySet());
        res.addAll(objectPropertyContentMap.keySet());
        res.addAll(objectPropertyMacroMap.keySet());
        return res;
    }

    @Override
    public void createInitDocFile(InputDoc doc)
        throws DocException, IOException, ComponentLookupException, ParseException
    {
        if (!"xwiki/2.1".equals(doc.getSyntaxId()) && !"xwiki/2.0".equals(doc.getSyntaxId())) {
            logger.info("Ignoring document at reference [{}] with unknown syntax [{}]", doc.getReference(),
                doc.getSyntaxId());
            return;
        }

        var dstJava = Path.of(javaPath.toString(), Utils.fromReferenceToJavaNamespace(doc.getReference()));
        Files.createDirectories(dstJava);

        // Handle title
        var titleFilePath = Path.of(dstJava.toString(), TITLE + DOT + VM);
        Files.writeString(titleFilePath, doc.getTitle());
        titleMap.put(titleFilePath, doc.getReference());

        // Handle content
        var content = doc.getContent();
        createOrUpdateDocContent(dstJava, content, doc.getSyntaxId(), doc.getReference());

        // Handle objects
        for (var obj : doc.getObjects(null, null, null)) {
            createObjectProperties(dstJava, doc.getReference(), doc.getSyntaxId(), obj.objectClass(), obj.number(),
                obj.properties());
        }
    }

    private void createObjectProperties(Path dstJava, String reference, String syntax, String objClass, int number,
        Collection<Property> properties)
        throws IOException, DocException, ComponentLookupException, ParseException
    {
        var objNumber = Integer.toString(number);
        for (var property : properties) {
            if (property.scriptingExtension().isEmpty()) {
                continue;
            }
            var objectProperty = new ObjectProperty(objClass, Integer.parseInt(objNumber), property.name());

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
                Files.writeString(scriptFileName, property.value());
                objectPropertyContentMap.put(scriptFileName,
                    new ImmutablePair<>(reference, objectProperty));
            } else if (EXTENSION_XWIKI.equals(property.scriptingExtension().orElse(""))) {
                var objContent = property.value();
                createOrUpdateWikiObjectProperty(dstJava, syntax, reference, objContent,
                    objClass, objNumber, objectProperty);
            }
        }
    }

    private void createOrUpdateWikiObjectProperty(Path dstJava, String syntax, String reference, String objContent,
        String objClass, String objNumber, ObjectProperty objectProperty)
        throws DocException, ComponentLookupException, ParseException, IOException
    {
        for (int i = 0; i < editing.getMacroOccurrences(objContent, syntax, GROOVY); i++) {
            var macroContent =
                editing.getMacroContent(objContent, syntax, new MacroInstance(GROOVY, i));
            var scriptContent = GROOVY_CONTENT_HEADER + macroContent;
            Path filePath = Path.of(dstJava.toString(),
                objClass.replace(DOT, UNDERSCORE) + UNDERSCORE + objNumber + "_GroovyMacro_" + i + DOT
                    + GROOVY);
            writeInFileOnlyIfChanged(filePath, scriptContent);
            objectPropertyMacroMap.put(filePath,
                new ImmutableTriple<>(reference, objectProperty, new MacroInstance(GROOVY, i)));
        }
        for (int i = 0; i < editing.getMacroOccurrences(objContent, syntax, VELOCITY); i++) {
            var macroContent =
                editing.getMacroContent(objContent, syntax, new MacroInstance(VELOCITY, i));
            var scriptContent = VELOCITY_CONTENT_HEADER + macroContent;
            Path filePath = Path.of(dstJava.toString(),
                objClass.replace(DOT, UNDERSCORE) + UNDERSCORE + objNumber + "_VelocityMacro_" + i + ".vm");
            writeInFileOnlyIfChanged(filePath, scriptContent);
            objectPropertyMacroMap.put(filePath,
                new ImmutableTriple<>(reference, objectProperty, new MacroInstance(VELOCITY, i)));
        }
    }

    private void createOrUpdateDocContent(Path dstJava, String content, String syntax, String reference)
        throws DocException, ComponentLookupException, ParseException, IOException
    {
        for (int i = 0; i < editing.getMacroOccurrences(content, syntax, GROOVY); i++) {
            var macroContent =
                editing.getMacroContent(content, syntax, new MacroInstance(GROOVY, i));
            String scriptContent = GROOVY_CONTENT_HEADER + macroContent;
            Path filePath = Path.of(dstJava.toString(), "GroovyMacro_" + i + DOT + GROOVY);
            writeInFileOnlyIfChanged(filePath, scriptContent);
            macroInContentMap.put(filePath, new ImmutablePair<>(reference, new MacroInstance(GROOVY, i)));
        }
        for (int i = 0; i < editing.getMacroOccurrences(content, syntax, VELOCITY); i++) {
            var macroContent =
                editing.getMacroContent(content, syntax, new MacroInstance(VELOCITY, i));
            String scriptContent = VELOCITY_CONTENT_HEADER + macroContent;
            Path filePath = Path.of(dstJava.toString(), "VelocityMacro_" + i + DOT + VM);
            writeInFileOnlyIfChanged(filePath, scriptContent);
            macroInContentMap.put(filePath,
                new ImmutablePair<>(reference, new MacroInstance(VELOCITY, i)));
        }
    }

    private String getMacroContent(Path path) throws IOException
    {
        var newMacroContentWithHeader = Files.readString(path);
        if (StringUtils.isEmpty(newMacroContentWithHeader)) {
            logger.debug("Ignoring empty file");
            return "";
        }
        if (path.getFileName().toString().endsWith('.' + LANG_GROOVY)) {
            return newMacroContentWithHeader.substring(
                newMacroContentWithHeader.indexOf(GROOVY_HEADER_DELIMITER) + GROOVY_HEADER_DELIMITER.length());
        } else if (path.getFileName().toString().endsWith('.' + LANG_VELOCITY_EXTENSION)) {
            return newMacroContentWithHeader.substring(
                newMacroContentWithHeader.indexOf(VELOCITY_CONTENT_HEADER) + VELOCITY_CONTENT_HEADER.length());
        } else {
            return newMacroContentWithHeader;
        }
    }

    @Override
    protected List<AbstractEvent> getEvent(Path path, WatchEvent.Kind<?> kind) throws IOException
    {
        logger.debug("change detected for path  [{}], kindName [{}], kindClass [{}]", path, kind.name(),
            kind.getClass().getName());
        if (titleMap.containsKey(path)) {
            return List.of(new TitleChangedEvent(titleMap.get(path), Files.readString(path)));
        } else if (macroInContentMap.containsKey(path)) {
            var macroInfo = macroInContentMap.get(path);
            var macroContent = getMacroContent(path);
            return List.of(
                new MacroInContentChangedEvent(macroInfo.getLeft(), macroInfo.getRight(), macroContent)
            );
        } else if (objectPropertyContentMap.containsKey(path)) {
            var objectInfo = objectPropertyContentMap.get(path);
            return List.of(
                new ObjectPropertyChangedEvent(objectInfo.getLeft(), objectInfo.getRight(), Files.readString(path))
            );
        } else if (objectPropertyMacroMap.containsKey(path)) {
            var macroInfo = objectPropertyMacroMap.get(path);
            var macroContent = getMacroContent(path);
            return List.of(
                new MacroInObjectPropertyChangedEvent(macroInfo.getLeft(), macroInfo.getMiddle(), macroInfo.getRight(),
                    macroContent)
            );
        }

        logger.debug("Wasn't able to detect the corresponding event for the file at the path [{}]", path);
        return List.of();
    }

    @Override
    protected Path getPathToMonitor()
    {
        return javaPath;
    }

    @Override
    public void onDocumentChanged(AbstractEvent event)
    {
        logger.debug("Handling event [{}]", event.getClass().getSimpleName());
        var docSyntax = this.memoryDocumentManager.getDocSyntax(event.reference());
        if (docSyntax.isEmpty() && !(event instanceof PageCreatedEvent)) {
            logger.debug("Ignoring event for reference [{}] because it's not created", event.reference());
            return;
        }
        if (docSyntax.isEmpty() && !(event instanceof PageCreatedEvent)) {
            logger.error("Memory document not found with reference [{}]", event.reference());
            return;
        }
        var dstJava = Path.of(javaPath.toString(), Utils.fromReferenceToJavaNamespace(event.reference()));

        try {
            switch (event) {
                case PageCreatedEvent e: {
                    Files.createDirectories(dstJava);
                    var titleFilePath = Path.of(dstJava.toString(), TITLE + DOT + VM);
                    Files.writeString(titleFilePath, "");
                    titleMap.put(titleFilePath, e.reference());
                }
                break;
                case PageDeletedEvent e:
                    FileUtils.deleteDirectory(dstJava.toFile());
                    for (var m : List.of(titleMap, macroInContentMap, objectPropertyContentMap,
                        objectPropertyMacroMap)) {
                        var managedFilesToRemove = m.keySet().stream()
                            .filter(i -> i.toString().startsWith(dstJava.toString()))
                            .toList();
                        for (var managedFile : managedFilesToRemove) {
                            m.remove(managedFile);
                        }
                    }
                    break;
                case TitleChangedEvent e: {
                    var titleFilePath = Path.of(dstJava.toString(), TITLE + DOT + VM);
                    Files.writeString(titleFilePath, e.title());
                }
                break;
                case ContentChangedEvent e:
                    createOrUpdateDocContent(dstJava, e.content(), docSyntax.get(), e.reference());
                    // Clean macros file which might be removed
                    cleanUnmanagedFiles();
                    break;
                case MacroInContentChangedEvent e: {
                    var macroInContent = macroInContentMap.entrySet().stream()
                        .filter(i -> {
                            var entry = i.getValue();
                            var macroInstance = entry.getRight();
                            return entry.getLeft().equals(e.reference())
                                && macroInstance.name().equals(e.macroInstance().name())
                                && macroInstance.position() == e.macroInstance().position();
                        })
                        .findAny();
                    if (macroInContent.isPresent()) {
                        String scriptContent;
                        if (GROOVY.equals(e.macroInstance().name())) {
                            scriptContent = GROOVY_CONTENT_HEADER + e.macroContent();
                        } else if (VELOCITY.equals(e.macroInstance().name())) {
                            scriptContent = VELOCITY_CONTENT_HEADER + e.macroContent();
                        } else {
                            // Should never happen
                            throw new DocException("Invalid macro name");
                        }
                        writeInFileOnlyIfChanged(macroInContent.get().getKey(), scriptContent);
                    }
                }
                break;
                case ObjectAddedEvent e:
                    createObjectProperties(dstJava, e.reference(), docSyntax.get(), e.className(), e.number(),
                        e.properties());
                    break;
                case ObjectRemovedEvent e:
                    var entriesToRemove = objectPropertyMacroMap.entrySet().stream()
                        .filter(i -> {
                            var entry = i.getValue();
                            var obj = entry.getMiddle();
                            return entry.getLeft().equals(e.reference())
                                && obj.objectClass().equals(e.className())
                                && obj.number() == e.number();
                        })
                        .map(Map.Entry::getKey)
                        .toList();
                    for (var p : entriesToRemove) {
                        objectPropertyMacroMap.remove(p);
                    }
                    entriesToRemove = objectPropertyContentMap.entrySet().stream()
                        .filter(i -> {
                            var entry = i.getValue();
                            var obj = entry.getRight();
                            return entry.getLeft().equals(e.reference())
                                && obj.objectClass().equals(e.className())
                                && obj.number() == e.number();
                        })
                        .map(Map.Entry::getKey)
                        .toList();
                    for (var p : entriesToRemove) {
                        objectPropertyContentMap.remove(p);
                    }
                    cleanUnmanagedFiles();
                    break;
                case MacroInObjectPropertyChangedEvent e: {
                    var macroInObj = objectPropertyMacroMap.entrySet().stream()
                        .filter(i -> {
                            var entry = i.getValue();
                            var obj = entry.getMiddle();
                            var macroInstance = entry.getRight();
                            return entry.getLeft().equals(e.reference())
                                && obj.objectClass().equals(e.className())
                                && obj.number() == e.number()
                                && obj.property().equals(e.property())
                                && macroInstance.name().equals(e.macroInstance().name())
                                && macroInstance.position() == e.macroInstance().position();
                        })
                        .findAny();
                    if (macroInObj.isPresent()) {
                        String scriptContent;
                        if (GROOVY.equals(e.macroInstance().name())) {
                            scriptContent = GROOVY_CONTENT_HEADER + e.macroContent();
                        } else if (VELOCITY.equals(e.macroInstance().name())) {
                            scriptContent = VELOCITY_CONTENT_HEADER + e.macroContent();
                        } else {
                            // Should never happen
                            throw new DocException("Invalid macro name");
                        }
                        writeInFileOnlyIfChanged(macroInObj.get().getKey(), scriptContent);
                    }
                }
                break;
                case ObjectPropertyChangedEvent e: {
                    var objectProperty = objectPropertyContentMap.entrySet().stream()
                        .filter(i -> {
                            var entry = i.getValue();
                            var obj = entry.getRight();
                            return entry.getLeft().equals(e.reference())
                                && obj.objectClass().equals(e.className())
                                && obj.number() == e.number()
                                && obj.property().equals(e.property());
                        })
                        .findAny();
                    if (objectProperty.isPresent()) {
                        writeInFileOnlyIfChanged(objectProperty.get().getKey(), e.newValue());
                    }

                    var syntax = memoryDocumentManager.getObjectPropertyScriptingExtension(e.reference(), e.className(),
                        e.number(),
                        e.property());
                    if (EXTENSION_XWIKI.equals(syntax.orElse(""))) {
                        createOrUpdateWikiObjectProperty(dstJava, docSyntax.get(), e.reference(), e.newValue(),
                            e.className(), Integer.toString(e.number()), e.objectProperty());

                        // Clean macros file which might be removed
                        cleanUnmanagedFiles();
                    }
                }
                break;
                // We don't need to support these events
                case AttachmentAddedEvent ignored:
                    break;
                case AttachmentUpdatedEvent ignored:
                    break;
                case AttachmentDeletedEvent ignored:
                    break;
                default:
                    logger.error("Unimplemented event: [{}]", event.getClass());
            }
        } catch (IOException | DocException | ComponentLookupException | ParseException e) {
            logger.error("Error writing content to file", e);
        }
    }

    private List<ExtensionInfos> getDependencyFromXWiki() throws DocException, JsonProcessingException
    {
        var responseStr = Utils.executeScriptOnXWiki("/generate_dependencies.xwiki", this.command);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(responseStr, ExtensionInfosList.class);
    }

    public void createMavenProject() throws IOException, DocException
    {
        Utils.executeScriptOnXWiki("/ensure_scripting_doc_api_installed.xwiki", this.command);

        Files.createDirectories(mavenSyncPath);

        // Create POM file
        var xmlFile = Path.of(mavenSyncPath.toString(), POM_XML);
        if (command.firstSyncFrom() == Command.FirstSyncFrom.WIKI) {
            var pom =
                new BufferedReader(
                    new InputStreamReader(MvnProjectSynchronizer.class.getResourceAsStream("/default_pom.xml")))
                    .lines().collect(Collectors.joining("\n"));
            pom = pom.replace("__XWIKI_RUNNING_VERSION__", Utils.getXWikiRunningVersion(command));
            Files.writeString(xmlFile, pom);
        } else {
            var sourceProjectPomPath = Path.of(command.mvnRepo(), POM_XML);
            Files.copy(sourceProjectPomPath, xmlFile, StandardCopyOption.REPLACE_EXISTING);
        }

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

        Files.createDirectories(xwikiCliJavaPath);

        Files.writeString(Path.of(xwikiCliJavaPath.toString(), "GvyScriptContext.groovy"),
            scriptContextGenerator.buildGroovyBinding());
        Files.writeString(Path.of(xwikiCliJavaPath.toString(), "VmScriptContext.groovy"),
            scriptContextGenerator.buildVelocityBindingClasses());
        Files.writeString(Path.of(javaPath.toString(), "velocity_implicit.vm"),
            scriptContextGenerator.buildVelocityBinding());

        // Define default macro lib
        var macrosVmContent = Utils.executeScriptOnXWiki("/get_macros_vm_content.xwiki", this.command);
        Files.writeString(Path.of(xwikiCliJavaPath.toString(), "macros.vm"), macrosVmContent);
    }

    @Override
    public void cleanUnmanagedFiles() throws IOException
    {
        var managedFiles = getManagedFiles();
        cleanUnmanagedFiles(javaPath, managedFiles);
    }

    private void cleanUnmanagedFiles(Path path, Set<Path> managedFiles) throws IOException
    {
        try (var dirList = Files.list(path)) {
            for (var d : dirList.toList()) {
                if (Files.isDirectory(d)) {
                    cleanUnmanagedFiles(d, managedFiles);
                } else if (!Files.isSymbolicLink(d)
                    && !managedFiles.contains(d)
                    && !d.toString().startsWith(xwikiCliJavaPath.toString())
                    && !d.equals(Path.of(javaPath.toString(), "velocity_implicit.vm")))
                {
                    Files.delete(d);
                }
            }
        }
    }
}
