package org.xwiki.contrib.cli.sync;

import java.util.function.Consumer;

import org.xwiki.contrib.cli.sync.event.AbstractEvent;

public interface PagesSynchronizer
{
    void onDocumentChanged(AbstractEvent event);

    /**
     * Int page synchronizer
     *
     * @args callback the method to call when a change are detected with the page reference of the page which
     *     changed.
     */
    void startNotifier(Consumer<AbstractEvent> callback);
}
