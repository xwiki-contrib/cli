package org.xwiki.contrib.cli;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class XWikiDirSync
{
    private final Path XMLFileDirPath;

    private final Command command;

    private final Path syncPath;

    public XWikiDirSync(Command cmd)
    {
        command = cmd;
        XMLFileDirPath = Path.of(cmd.syncDataSource, "src", "main", "resources");
        syncPath = Path.of(cmd.syncPath);
        command.xmlWriteDir = XMLFileDirPath.toString();
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
        var dstFile = pageReferenceToDirPath(xmlFile.getDom().valueOf("//xwikidoc/@reference"));
        var content = xmlFile.getContent();
        var contentFilePath = Path.of(dstFile, "content");
        Files.createDirectories(contentFilePath.getParent());
        Files.write(contentFilePath, content.getBytes());

        var title = xmlFile.getTitle();
        var titleFilePath = Path.of(dstFile, "title");
        Files.createDirectories(titleFilePath.getParent());
        Files.write(titleFilePath, title.getBytes());

        for (var attachment : xmlFile.getAttachments()) {
            var attachmentContent = attachment.content();
            var attachmentFilePath = Path.of(dstFile, "attachments", attachment.name());
            Files.createDirectories(attachmentFilePath.getParent());
            Files.write(titleFilePath, attachmentContent);
        }

        for (var obj : xmlFile.getDom().selectNodes("//xwikidoc/object")) {
            var className = obj.valueOf("//className");
            var number = obj.valueOf("//number");

            for (var property : obj.selectNodes("//property")) {
                var propertyNode = ((org.dom4j.Element) property).elements().get(0);
                var propertyName = propertyNode.getName();
                var propertyValue = propertyNode.getStringValue();
                var propertyValueFileName = Path.of(dstFile, "objects", className, number, "properties", propertyName);
                Files.createDirectories(propertyValueFileName.getParent());
                Files.write(propertyValueFileName, propertyValue.getBytes());
            }
        }
    }

    private String pageReferenceToDirPath(String reference)
    {
        final var urlPart = new StringBuilder();
        urlPart.append(command.syncPath).append('/');
        final var len = reference.length();
        var i = 0;

        while (i < len) {
            char c = reference.charAt(i);
            switch (c) {
                case '.':
                    // TODO improve check to manage case of reference like this A.B\.xx which will return space
                    //  instead of pages
                    if (reference.indexOf(".", i + 1) > 0) {
                        urlPart.append("/spaces/");
                    } else {
                        urlPart.append("/pages/");
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

    private void syncFileFromSyncedDir(Path file)
    {
        System.out.println("Sync file at path: " + file);







        
    }

    private void watchDir(Path path, WatchService watcher) throws IOException
    {
        path.register(watcher,
            ENTRY_CREATE,
            ENTRY_DELETE,
            ENTRY_MODIFY);
        for (var d : Files.list(path).filter(Files::isDirectory).toList()) {
            watchDir(d, watcher);
        }
    }

    public void monitor() throws IOException
    {
        WatchService watcher = FileSystems.getDefault().newWatchService();
        WatchKey key;
        try {
            watchDir(syncPath, watcher);
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
                Path child = syncPath.resolve(filename);
                syncFileFromSyncedDir(child);
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
