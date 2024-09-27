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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.err;
import static java.lang.System.out;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

class XWikiDirSync
{

    private static final String URL_PART_CONTENT = "/content";

    private static final String URL_PART_REST = "/rest";

    private static final String URL_PART_TITLE = "/title";

    private static final Pattern PAGES_PATTERN_MATCHER =
        Pattern.compile("^(spaces(?:/[^/]+/spaces)*/[^/]+)/pages/([^/]+)");

    private static final Pattern OBJECTS_PROPERTIES_PATTERN_MATCHER =
        Pattern.compile("^/objects/([^/]+)/([^/]+)/properties/([^/]+)$");

    private static final String ATTACHMENTS_PATTERN = "^/attachments/([^/]+)$";

    private static final Pattern ATTACHMENTS_PATTERN_MATCHER = Pattern.compile(ATTACHMENTS_PATTERN);

    private static final String SPACES = "/spaces/";

    private static final String PAGES = "/pages/";

    private final Path xmlFileDirPath;

    private final Command command;

    private final Path syncPath;

    private final Set<Path> managedFiles = new HashSet<>();

    XWikiDirSync(Command cmd)
    {
        command = cmd;
        xmlFileDirPath = Path.of(cmd.syncDataSource, "src", "main", "resources");
        syncPath = Path.of(cmd.syncPath);
        command.xmlWriteDir = cmd.syncDataSource;
    }

     void sync() throws DocException, IOException
    {
        syncDir(xmlFileDirPath);
    }

    void monitor() throws IOException
    {
        var watcher = FileSystems.getDefault().newWatchService();
        var keyMaps = new HashMap<WatchKey, Path>();
        try {
            watchDir(keyMaps, syncPath, watcher);
        } catch (IOException x) {

            err.println(x);
            return;
        }

        while (true) {
            // wait for key to be signaled

            WatchKey key;
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
        out.println("Ending watch loop");
    }

    private void syncDir(Path dir) throws IOException, DocException
    {
        try (var dirList = Files.list(dir)) {
            for (var d : dirList.toList()) {
                if (Files.isDirectory(d)) {
                    syncDir(d);
                } else {
                    syncFileFromMvnRepos(d);
                }
            }
        }
    }

    private void syncFileFromMvnRepos(Path srcFile) throws DocException, IOException
    {
        var xmlFile = new XMLFileDoc(command, srcFile.toString());
        var dstFile = pageReferenceToDirPath(xmlFile.getReference());
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
            var attachmentContent = attachment.content();
            var attachmentFilePath = Path.of(dstFile, "attachments", attachment.name());
            Files.createDirectories(attachmentFilePath.getParent());
            Files.write(attachmentFilePath, attachmentContent);
            managedFiles.add(attachmentFilePath);
        }

        for (var obj : xmlFile.getObjects(null, null, null)) {
            // TODO use change getObject to return already split data instead of splitting here
            String[] objectAndNumber = obj.split("/");
            var objClass = objectAndNumber[0];
            var objNumber = objectAndNumber[1];
            for (var property : xmlFile.getProperties(objClass, objNumber, null, false).entrySet()) {
                var propertyValueFileName = Path.of(dstFile, "objects", objClass, objNumber, "properties",
                    property.getKey());
                Files.createDirectories(propertyValueFileName.getParent());
                Files.writeString(propertyValueFileName, property.getValue());
                managedFiles.add(propertyValueFileName);
            }
        }
    }

    private String pageReferenceToDirPath(String reference)
    {
        final var urlPart = new StringBuilder();
        urlPart.append(command.syncPath);
        final var len = reference.length();
        var i = 0;

        // TODO Write unit test to validate this method

        // TODO improve check to manage case of reference like this A.B\.xx which will return space
        //  instead of pages
        if (reference.indexOf('.') != -1) {
            urlPart.append(SPACES);
        } else {
            urlPart.append(PAGES);
        }
        while (i < len) {
            char c = reference.charAt(i);
            switch (c) {
                case '.':
                    // TODO improve check to manage case of reference like this A.B\.xx which will return space
                    //  instead of pages
                    if (reference.indexOf('.', i + 1) > 0) {
                        urlPart.append(SPACES);
                    } else {
                        urlPart.append(PAGES);
                    }
                    break;

                case '\\':
                    if (i + 1 < len) {
                        i++;
                    }
                    c = reference.charAt(i);
                    urlPart.append(c);
                    break;

                default:
                    urlPart.append(c);
            }
            i++;
        }
        return urlPart.toString();
    }

    private void syncFileFromSyncedDir(Path file, WatchEvent.Kind<?> kind) throws IOException
    {
        out.println("Sync file at path: " + file);
        // var relativePath = syncPath.relativize(file);

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

    private int putValue(String path, byte[] value)
    {
        Matcher pageMatcher = PAGES_PATTERN_MATCHER.matcher(path);
        if (pageMatcher.find()) {
            String space = FSDirUtils.getSpaceFromPathPart(pageMatcher.group(1));
            String page = pageMatcher.group(2).replace(FSDirUtils.DOT, FSDirUtils.ESCAPED_DOT);
            this.command.page = space + '.' + page;

            try {
                MultipleDoc document = new MultipleDoc(this.command);

                String remainingPath = path.substring(pageMatcher.end());

                Matcher propertyMatcher = OBJECTS_PROPERTIES_PATTERN_MATCHER.matcher(remainingPath);
                if (propertyMatcher.matches()) {
                    String className = propertyMatcher.group(1);
                    String objectNumber = propertyMatcher.group(2);
                    String propertyName = propertyMatcher.group(3);
                    String stringValue = new String(value, StandardCharsets.UTF_8);

                    document.setValue(className, objectNumber, propertyName, stringValue);
                    document.save();
                    return value.length;
                }

                if (remainingPath.equals(URL_PART_CONTENT) || remainingPath.equals(URL_PART_TITLE)) {
                    String stringValue = new String(value, StandardCharsets.UTF_8);
                    if (remainingPath.equals(URL_PART_TITLE)) {
                        document.setTitle(stringValue.stripTrailing());
                    } else {
                        document.setContent(stringValue);
                    }
                    document.save();
                    return value.length;
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
                    /*
                    String attachmentName = attachmentMatcher.group(1);

                    return document.getAttachment(attachmentName).getBytes(StandardCharsets.UTF_8);
                     */
                    String attachmentURL = this.command.url + URL_PART_REST + FSDirUtils.escapeURLWithSlashes(path);
                    Utils.httpPut(this.command, attachmentURL, value, "application/octet-stream");
                    return value.length;
                }
            } catch (DocException | IOException e) {
                if (command.debug) {
                    e.printStackTrace();
                }
            }
        }

        return 0;
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
