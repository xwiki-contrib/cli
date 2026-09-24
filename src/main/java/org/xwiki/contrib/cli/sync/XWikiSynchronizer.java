package org.xwiki.contrib.cli.sync;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.Utils;
import org.xwiki.contrib.cli.document.XMLRestPage;
import org.xwiki.contrib.cli.document.element.XWikiEventTypes;
import org.xwiki.contrib.cli.sync.event.AbstractEvent;
import org.xwiki.contrib.cli.sync.event.PageCreatedEvent;
import org.xwiki.contrib.cli.sync.event.PageDeletedEvent;
import org.xwiki.rendering.parser.ParseException;

import com.fasterxml.jackson.core.JsonProcessingException;

public class XWikiSynchronizer extends AbstractSynchronizer implements PagesSynchronizer
{
    private final Map<String, Long> pagesChangedByMyself = new HashMap<>();

    public XWikiSynchronizer(Command cmd, MemoryDocumentManager memoryDocumentManager)
    {
        super(cmd, memoryDocumentManager);
    }

    @Override
    public void onDocumentChanged(AbstractEvent event)
    {
        if (command.noWriteWiki()) {
            return;
        }
        logger.debug("Handling event [{}]", event.getClass().getSimpleName());
        try {
            if (event instanceof PageDeletedEvent) {
                logger.warn("Page deleted not implemented for XWiki instance");
            } else if (event instanceof PageCreatedEvent) {
                var doc = new XMLRestPage(command, command.wiki(), event.reference(), true);
                doc.save();
            } else {
                var doc = new XMLRestPage(command, command.wiki(), event.reference(), false);
                var modified = handleEventOnDocument(event, doc);
                if (modified) {
                    pagesChangedByMyself.put(doc.getReference(), System.currentTimeMillis());
                    doc.save();
                }
            }
        } catch (IOException | DocException | ComponentLookupException | ParseException e) {
            logger.error("Error writing content to file", e);
        }
    }

    @Override
    public void startNotifier(Consumer<AbstractEvent> callback)
    {
        if (command.noReadWiki()) {
            return;
        }
        var lastRequestDate = System.currentTimeMillis();
        try {
            while (true) {
                try {
                    var nextRequestDate = 0L;
                    var xwikiEvents = Utils.getEvents(command);
                    for (var event : xwikiEvents) {
                        var newestDate = event.dates().stream().max(Long::compareTo).orElse(0L);
                        if (nextRequestDate < newestDate) {
                            nextRequestDate = newestDate;
                        }
                        if (newestDate <= lastRequestDate) {
                            continue;
                        }
                        // The document reference is global so we need to convert it to local reference
                        var localRef = event.document().split(":")[1];

                        var selfEditTimestamps = pagesChangedByMyself.getOrDefault(localRef, 0L);
                        if (newestDate < selfEditTimestamps + 100) {
                            continue;
                        }
                        logger.debug("Handling event for doc reference [{}]", localRef);

                        List<AbstractEvent> events = List.of();
                        if (event.type() == XWikiEventTypes.CREATE || event.type() == XWikiEventTypes.UPDATE) {
                            // Ignore new document created which is not expected to be handled by XWiki CLI
                            if (event.type() == XWikiEventTypes.CREATE && !isDocManaged(localRef)) {
                                continue;
                            }
                            var doc = new XMLRestPage(command, command.wiki(), localRef, false);
                            // Note createEventsFromDoc will automatically add a PageCreatedEvent if needed
                            events = super.createEventsFromDoc(doc);
                        } else if (event.type() == XWikiEventTypes.DELETE) {
                            if (memoryDocumentManager.docExist(localRef)) {
                                events = List.of(new PageDeletedEvent(localRef));
                            }
                        } else {
                            logger.warn("Unknown event type [{}]", event.type());
                        }

                        for (var e : events) {
                            callback.accept(e);
                        }
                    }
                    lastRequestDate = nextRequestDate;
                } catch (DocException | JsonProcessingException e) {
                    logger.error("Error while retrieving the list of document which changed", e);
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for XWiki synchronization", e);
        }
    }

    /**
     * This will try to guess if this document is expected to be handled by XWiki CLI or not.
     *
     * @param reference reference of the doc to check.
     * @return true if the new document is spected to be handled by XWiki CLI.
     */
    private boolean isDocManaged(String reference)
    {
        return memoryDocumentManager.docExist(reference)
            || command.spaces().stream().anyMatch(it -> it.startsWith(reference))
            || isDocInSpacesThatIsHandled(reference);
    }

    private boolean isDocInSpacesThatIsHandled(String reference)
    {
        return memoryDocumentManager.getAllDocReference().stream().anyMatch(it -> {
            var pageLastSpace = it.replaceAll("\\.[^.]*$", "");
            return reference.startsWith(pageLastSpace);
        });
    }
}
