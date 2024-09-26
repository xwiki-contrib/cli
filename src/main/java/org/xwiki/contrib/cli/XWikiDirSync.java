package org.xwiki.contrib.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xwiki.contrib.cli.document.MultipleDoc;
import org.xwiki.contrib.cli.document.XMLFileDoc;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class XWikiDirSync
{
    private final Path XMLFileDirPath;

    private final Command command;

    private final Path syncPath;

    private static final String URL_PART_CONTENT = "/content";

    private static final String URL_PART_REST = "/rest";

    private static final String URL_PART_TITLE = "/title";

    private static final Pattern PAGES_PATTERN_MATCHER =
        Pattern.compile("^(spaces(?:/[^/]+/spaces)*/[^/]+)/pages/([^/]+)");

    private static final Pattern OBJECTS_PROPERTIES_PATTERN_MATCHER =
        Pattern.compile("^/objects/([^/]+)/([^/]+)/properties/([^/]+)$");

    private static final String ATTACHMENTS_PATTERN = "^/attachments/([^/]+)$";

    private static final Pattern ATTACHMENTS_PATTERN_MATCHER = Pattern.compile(ATTACHMENTS_PATTERN);

    private final Set<Path> managedFiles = new HashSet<>();

    public XWikiDirSync(Command cmd)
    {
        command = cmd;
        XMLFileDirPath = Path.of(cmd.getSyncDataSource(), "src", "main", "resources");
        syncPath = Path.of(cmd.getSyncPath());
    }

    public void sync() throws DocException, IOException
    {
        syncDir(XMLFileDirPath);
    }

    private void syncDir(Path dir) throws IOException, DocException
    {
        for (var d : Files.list(dir).toList()) {
            if (Files.isDirectory(d)) {
                syncDir(d);
            } else {
                syncFileFromMvnRepos(d);
            }
        }
    }

    private void syncFileFromMvnRepos(Path srcFile) throws DocException, IOException
    {
        var xmlFile = new XMLFileDoc(command, srcFile.toString());
        var dstFile = syncPath.toString() + Utils.fromReferenceToXFFPath(xmlFile.getReference());
        var content = xmlFile.getContent();
        var contentFilePath = Path.of(dstFile, "content");
        Files.createDirectories(contentFilePath.getParent());
        Files.writeString(contentFilePath, content);
        managedFiles.add(contentFilePath);

        var title = xmlFile.getTitle();
        var titleFilePath = Path.of(dstFile, "title");
        Files.createDirectories(titleFilePath.getParent());
        Files.writeString(titleFilePath, title);
        managedFiles.add(titleFilePath);

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
                var propertyValueFileName = Path.of(dstFile, "objects", objClass, objNumber, "properties",
                    property.name());
                Files.createDirectories(propertyValueFileName.getParent());
                Files.writeString(propertyValueFileName, property.value());
                managedFiles.add(propertyValueFileName);
                if (property.scriptingExtension().isPresent()) {
                    var linkPath = Path.of(propertyValueFileName + "." + property.scriptingExtension().get());
                    if (!Files.exists(linkPath)) {
                        Files.createSymbolicLink(linkPath, propertyValueFileName);
                    }
                }
            }
        }
    }

    private void syncFileFromSyncedDir(Path file, WatchEvent.Kind<?> kind) throws IOException
    {
        System.out.println("Sync file at path: " + file);
        var relativePath = syncPath.relativize(file);

        // TODO improve it !!
        // We should not in all case rewrite the value
        write(file);
    }

    private void write(Path path) throws IOException
    {
        if (managedFiles.contains(path) && Files.exists(path)) {
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
                MultipleDoc document = new MultipleDoc(command, command.getWiki(), space + '.' + page);

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

                /*
                Pattern classPropertyPattern = Pattern.compile("^/class/properties/([^/]+)/([^/]+)$");
                Matcher classPropertyMatcher = classPropertyPattern.matcher(path);
                if (classPropertyMatcher.matches()) {
                    String propertyName = classPropertyMatcher.group(1);
                    String attributeName = classPropertyMatcher.group(2);

                    return document.getClassAttribute(propertyName, attributeName).getBytes(StandardCharsets.UTF_8);
                }
                */

                Matcher attachmentMatcher = ATTACHMENTS_PATTERN_MATCHER.matcher(remainingPath);
                if (attachmentMatcher.matches()) {
                    String attachmentName = attachmentMatcher.group(1);
                    document.setAttachment(attachmentName, value);
                    document.save();
                }
            } catch (DocException | IOException e) {
                if (command.isDebug()) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void watchDir(HashMap<WatchKey, Path> keyMaps, Path path, WatchService watcher) throws IOException
    {
        keyMaps.put(path.register(watcher,
            ENTRY_CREATE,
            ENTRY_DELETE,
            ENTRY_MODIFY), path);
        for (var d : Files.list(path).filter(Files::isDirectory).toList()) {
            watchDir(keyMaps, d, watcher);
        }
    }

    public void monitor() throws IOException
    {
        WatchService watcher = FileSystems.getDefault().newWatchService();
        WatchKey key;
        HashMap<WatchKey, Path> keyMaps = new HashMap<>();
        try {
            watchDir(keyMaps, syncPath, watcher);
        } catch (IOException x) {

            System.err.println(x);
            return;
        }

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
        System.out.println("Ending watch loop");
    }
}
