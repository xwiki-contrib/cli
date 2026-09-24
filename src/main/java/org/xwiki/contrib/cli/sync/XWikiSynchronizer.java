package org.xwiki.contrib.cli.sync;

import java.io.IOException;
import java.util.function.Consumer;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.document.XMLRestPage;
import org.xwiki.contrib.cli.sync.event.AbstractEvent;
import org.xwiki.rendering.parser.ParseException;

public class XWikiSynchronizer extends AbstractSynchronizer implements PagesSynchronizer
{
    public XWikiSynchronizer(Command cmd, MemoryDocumentManager memoryDocumentManager)
    {
        super(cmd,  memoryDocumentManager);
    }

    @Override
    public void onDocumentChanged(AbstractEvent event)
    {
        if (command.noWriteWiki()) {
            return;
        }
        logger.debug("Handling event [{}]", event.getClass().getSimpleName());
        try {
            var doc = new XMLRestPage(command, command.wiki(), event.reference());
            var modified = handleEventOnDocument(event, doc);
            if (modified) {
                doc.save();
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
        logger.warn("Doing nothing, not implemented yet");
    }
}
