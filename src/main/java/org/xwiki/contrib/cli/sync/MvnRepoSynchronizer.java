package org.xwiki.contrib.cli.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.List;
import java.util.function.Consumer;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.PageSync;
import org.xwiki.contrib.cli.Utils;
import org.xwiki.contrib.cli.document.MvnRepoFileDoc;
import org.xwiki.contrib.cli.document.XMLFileDoc;
import org.xwiki.contrib.cli.sync.event.AbstractEvent;
import org.xwiki.contrib.cli.sync.event.AttachmentAddedEvent;
import org.xwiki.contrib.cli.sync.event.AttachmentDeletedEvent;
import org.xwiki.contrib.cli.sync.event.ObjectAddedEvent;
import org.xwiki.contrib.cli.sync.event.ObjectRemovedEvent;
import org.xwiki.contrib.cli.sync.event.PageCreatedEvent;
import org.xwiki.contrib.cli.sync.event.PageDeletedEvent;
import org.xwiki.rendering.parser.ParseException;

public class MvnRepoSynchronizer extends AbstractFileSynchronizer implements PagesSynchronizer
{
    public MvnRepoSynchronizer(Command cmd, MemoryDocumentManager memoryDocumentManager)
    {
        super(cmd, memoryDocumentManager);
    }

    @Override
    public void startNotifier(Consumer<AbstractEvent> callback)
    {
        if (command.noMvnRepoRead()) {
            return;
        }
        super.startNotifier(callback);
    }

    @Override
    protected List<AbstractEvent> getEvent(Path path, WatchEvent.Kind<?> kind) throws IOException, DocException
    {
        logger.debug("change detected for path  [{}], kindName [{}], kindClass [{}]", path, kind.name(),
            kind.getClass().getName());

        if (!Files.exists(path) && kind == StandardWatchEventKinds.ENTRY_DELETE) {
            logger.debug("File seem removed, considering that the page was removed");
            // Try to guess the reference from the file path
            // ideally we would like to have a stronger solution, but this would require to store a full mapping
            // of file path <> reference in memory
            var guessedReference =
                Utils.getMvnReposRessourcePath(command)
                    .relativize(path).toString()
                    .replaceAll("\\.xml$", "")
                    .replace("/", ".");
            if (memoryDocumentManager.docExist(guessedReference)) {
                return List.of(new PageDeletedEvent(guessedReference));
            } else {
                return List.of();
            }
        }
        var doc = new XMLFileDoc(command, path.toString());

        // check that the document is "valid" in the sense that, the event could be also when the file is created and so
        // is empty. So this check is mainly to ensure that we have a valid XML.
        try {
            doc.getReference();
        } catch (DocException e) {
            logger.debug("Ignoring event for empty file [{}]", path);
            return List.of();
        }
        return super.createEventsFromDoc(doc);
    }

    @Override
    protected Path getPathToMonitor()
    {
        return Utils.getMvnReposRessourcePath(command);
    }

    @Override
    public void onDocumentChanged(AbstractEvent event)
    {
        if (command.noMvnRepoWrite()) {
            return;
        }
        logger.debug("Handling event [{}]", event.getClass().getSimpleName());
        try {
            if (event instanceof PageCreatedEvent
                || event instanceof AttachmentAddedEvent
                || event instanceof AttachmentDeletedEvent
                || event instanceof ObjectAddedEvent
                || event instanceof ObjectRemovedEvent)
            {
                if (command.noWriteWiki() || command.noReadWiki()) {
                    logger.warn(
                        "The event of type [{}] for when --no-read-wiki or --no-write-wiki is enabled is not supported. "
                            + "Handling this event require a XWiki instance working and writable.",
                        event.getClass().getName());
                } else {
                    var pageSync = new PageSync(command);
                    pageSync.pullPage(event.reference());
                }
            } else if (event instanceof PageDeletedEvent) {
                var filePath = Path.of(command.mvnRepo(), "src", "main", "resources",
                    Utils.fromReferenceToMvnReposPath(event.reference()) + ".xml");
                Files.deleteIfExists(filePath);
            } else {
                var doc = new MvnRepoFileDoc(command, event.reference());
                var modified = handleEventOnDocument(event, doc);
                if (modified) {
                    markFileAsEdited(Path.of(doc.getFilename()));
                    doc.save();
                }
            }
        } catch (IOException | DocException | ComponentLookupException | ParseException e) {
            logger.error("Error writing content to file", e);
        }
    }
}
