package org.xwiki.contrib.cli.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.Editing;
import org.xwiki.contrib.cli.FSDirUtils;
import org.xwiki.contrib.cli.Utils;
import org.xwiki.contrib.cli.document.InputDoc;
import org.xwiki.contrib.cli.document.element.ObjectProperty;
import org.xwiki.contrib.cli.document.element.Property;
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

public class XFFSynchronizer extends AbstractWorkingDirSynchronizer implements PagesSynchronizer
{

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

    private static final String VM = "vm";

    private static final String OBJECTS = "objects";

    private static final String PROPERTIES = "properties";

    private static final String ATTACHMENTS = "attachments";

    private final Editing editing = new Editing();

    private final Set<Path> managedFiles = new HashSet<>();

    private final Path syncPath;

    public XFFSynchronizer(Command cmd, MemoryDocumentManager memoryDocumentManager)
    {
        super(cmd, memoryDocumentManager);
        syncPath = Path.of(cmd.workingDirectory(), "xff");
    }

    @Override
    public void createInitDocFile(InputDoc doc) throws DocException, IOException
    {
        var dstDir = Path.of(syncPath.toString() + Utils.fromReferenceToXFFPath(doc.getReference()));
        createBaseDocFile(dstDir, doc.getSyntaxId(), doc.getTitle(), doc.getContent());

        for (var attachment : doc.getAttachments()) {
            var attachmentContent = doc.getAttachment(attachment.name());
            createAttachment(dstDir, attachment.name(), attachmentContent);
        }

        for (var obj : doc.getObjects(null, null, null)) {
            var objClass = obj.objectClass();
            addObject(dstDir, objClass, obj.number(), obj.properties());
        }
    }

    private void addObject(Path dstDir, String objClass, int objNumber, Collection<Property> properties)
        throws IOException
    {
        for (var property : properties) {
            var propertyValueFileName =
                Path.of(dstDir.toString(), OBJECTS, objClass, Integer.toString(objNumber), PROPERTIES,
                    property.name());
            Files.createDirectories(propertyValueFileName.getParent());
            Files.writeString(propertyValueFileName, property.value());
            managedFiles.add(propertyValueFileName);
            var scriptingExtension = property.scriptingExtension();
            if (scriptingExtension.isPresent()) {
                var linkPropertyPath = Path.of(propertyValueFileName + DOT + scriptingExtension.get());
                if (!Files.exists(linkPropertyPath)) {
                    Files.createSymbolicLink(linkPropertyPath, Path.of(property.name()));
                }
            }
        }
    }

    private void createAttachment(Path dstDir, String name, byte[] attachmentContent) throws IOException
    {
        var attachmentFilePath = Path.of(dstDir.toString(), ATTACHMENTS, name);
        Files.createDirectories(attachmentFilePath.getParent());
        Files.write(attachmentFilePath, attachmentContent);
        managedFiles.add(attachmentFilePath);
    }

    private void createBaseDocFile(Path dstDir, String syntax, String title, String content) throws IOException
    {
        Files.createDirectories(dstDir);

        var contentExtension = Utils.getExtensionFromSyntaxId(syntax);
        var contentFilePath = Path.of(dstDir.toString(), CONTENT);
        Files.writeString(contentFilePath, content);
        managedFiles.add(contentFilePath);
        var linkPath = Path.of(contentFilePath + DOT + contentExtension);
        if (!Files.exists(linkPath)) {
            Files.createSymbolicLink(linkPath, Path.of(CONTENT));
        }

        var titleFilePath = Path.of(dstDir.toString(), TITLE);
        Files.writeString(titleFilePath, title);
        managedFiles.add(titleFilePath);
        linkPath = Path.of(titleFilePath + DOT + VM);
        if (!Files.exists(linkPath)) {
            Files.createSymbolicLink(linkPath, Path.of(TITLE));
        }
    }

    @Override
    protected List<AbstractEvent> getEvent(Path fullPath, WatchEvent.Kind<?> kind) throws IOException
    {
        logger.debug("change detected for path  [{}], kindName [{}], kindClass [{}]", fullPath, kind.name(),
            kind.getClass().getName());
        if (!managedFiles.contains(fullPath)) {
            return List.of();
        }

        var value = Files.readAllBytes(fullPath);
        var path = syncPath.relativize(fullPath).toString();
        Matcher pageMatcher = PAGES_PATTERN_MATCHER.matcher(path);
        if (pageMatcher.find()) {
            String space = FSDirUtils.getSpaceFromPathPart(pageMatcher.group(1));
            String page = pageMatcher.group(2).replace(FSDirUtils.DOT, FSDirUtils.ESCAPED_DOT);

            var reference = space + '.' + page;

            String remainingPath = path.substring(pageMatcher.end());

            Matcher propertyMatcher = OBJECTS_PROPERTIES_PATTERN_MATCHER.matcher(remainingPath);
            if (propertyMatcher.matches()) {
                String className = propertyMatcher.group(1);
                String objectNumber = propertyMatcher.group(2);
                String propertyName = propertyMatcher.group(3);
                String stringValue = new String(value, StandardCharsets.UTF_8);

                return List.of(new ObjectPropertyChangedEvent(reference,
                    new ObjectProperty(className, Integer.parseInt(objectNumber), propertyName), stringValue));
            }

            if (remainingPath.equals(URL_PART_CONTENT) || remainingPath.equals(URL_PART_TITLE)) {
                String stringValue = new String(value, StandardCharsets.UTF_8);
                if (remainingPath.equals(URL_PART_TITLE)) {
                    return List.of(new TitleChangedEvent(reference, stringValue));
                } else {
                    return List.of(new ContentChangedEvent(reference, stringValue));
                }
            }

            Matcher attachmentMatcher = ATTACHMENTS_PATTERN_MATCHER.matcher(remainingPath);
            if (attachmentMatcher.matches()) {
                String attachmentName = attachmentMatcher.group(1);
                return List.of(new AttachmentUpdatedEvent(reference, attachmentName, value));
            }
        }

        logger.warn("Wasn't able to detect the corresponding event for the file at the path [{}]", fullPath);
        return List.of();
    }

    @Override
    protected Path getPathToMonitor()
    {
        return syncPath;
    }

    @Override
    public void onDocumentChanged(AbstractEvent event)
    {
        logger.debug("Handling event [{}]", event.getClass().getSimpleName());
        var dstDir = Path.of(syncPath.toString() + Utils.fromReferenceToXFFPath(event.reference()));
        var docSyntax = this.memoryDocumentManager.getDocSyntax(event.reference());
        try {
            switch (event) {
                case PageCreatedEvent e:
                    createBaseDocFile(dstDir, e.syntax(), "", "");
                    break;
                case PageDeletedEvent e:
                    FileUtils.deleteDirectory(dstDir.toFile());
                    var managedFilesToDelete = managedFiles.stream()
                        .filter(i -> i.toString().startsWith(dstDir.toString())).toList();
                    managedFiles.removeAll(managedFilesToDelete);
                    break;
                case TitleChangedEvent e:
                    var titleFilePath = Path.of(dstDir.toString(), TITLE);
                    writeInFileOnlyIfChanged(titleFilePath, e.title());
                    break;
                case ContentChangedEvent e:
                    writeInFileOnlyIfChanged(Path.of(dstDir.toString(), CONTENT), e.content());
                    break;
                case MacroInContentChangedEvent e: {
                    var contentFilePath = Path.of(dstDir.toString(), CONTENT);
                    var fileContent = Files.readString(contentFilePath);
                    var newFileContent =
                        editing.updateMacro(fileContent, docSyntax.orElseThrow(), e.macroInstance(),
                            e.macroContent());
                    writeInFileOnlyIfChanged(contentFilePath, newFileContent);
                }
                break;
                case ObjectAddedEvent e:
                    addObject(dstDir, e.className(), e.number(), e.properties());
                    break;
                case ObjectRemovedEvent e:
                    var pathToRemove = Path.of(dstDir.toString(), OBJECTS, e.className(), Integer.toString(e.number()));
                    FileUtils.deleteDirectory(pathToRemove.toFile());
                    var managedFilesToRemove =
                        managedFiles.stream().filter(f -> f.toString().startsWith(pathToRemove.toString())).toList();
                    for (var f : managedFilesToRemove) {
                        managedFiles.remove(f);
                    }
                    break;
                case MacroInObjectPropertyChangedEvent e: {
                    var objClass = e.className();
                    var objNumber = Integer.toString(e.number());
                    var propertyValueFileName =
                        Path.of(dstDir.toString(), OBJECTS, objClass, objNumber, PROPERTIES, e.property());
                    var propertyContent = Files.readString(propertyValueFileName);
                    var newFileContent =
                        editing.updateMacro(propertyContent, docSyntax.orElseThrow(), e.macroInstance(),
                            e.macroContent());
                    writeInFileOnlyIfChanged(propertyValueFileName, newFileContent);
                }
                break;
                case ObjectPropertyChangedEvent e: {
                    var objClass = e.className();
                    var objNumber = Integer.toString(e.number());
                    var propertyValueFileName =
                        Path.of(dstDir.toString(), OBJECTS, objClass, objNumber, PROPERTIES, e.property());
                    writeInFileOnlyIfChanged(propertyValueFileName, e.newValue());
                }
                break;
                case AttachmentAddedEvent e:
                    createAttachment(dstDir, e.name(), e.data());
                    break;
                case AttachmentDeletedEvent e:
                    var path = Path.of(dstDir.toString(), ATTACHMENTS, e.name());
                    Files.delete(path);
                    managedFiles.remove(path);
                    break;
                case AttachmentUpdatedEvent e:
                    var attachmentFilePath = Path.of(dstDir.toString(), ATTACHMENTS, e.name());
                    if (!Arrays.equals(Files.readAllBytes(attachmentFilePath), e.data())) {
                        Files.write(attachmentFilePath, e.data());
                    }
                    break;
                default:
                    logger.error("Unimplemented event: [{}]", event.getClass());
            }
        } catch (IOException | DocException | ComponentLookupException | ParseException e) {
            logger.error("Error writing content to file", e);
        }
    }

    @Override
    public void cleanUnmanagedFiles() throws IOException
    {
        cleanUnmanagedFiles(syncPath);
    }

    private void cleanUnmanagedFiles(Path path) throws IOException
    {
        try (var dirList = Files.list(path)) {
            for (var d : dirList.toList()) {
                if (Files.isDirectory(d)) {
                    cleanUnmanagedFiles(d);
                } else if (!Files.isSymbolicLink(d) && !managedFiles.contains(d)) {
                    Files.delete(d);
                }
            }
        }
    }
}
