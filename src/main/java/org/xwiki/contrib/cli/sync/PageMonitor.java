package org.xwiki.contrib.cli.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.cli.sync.event.AbstractEvent;

public class PageMonitor
{
    private final Logger logger = LoggerFactory.getLogger(PageMonitor.class);

    private final LinkedBlockingQueue<Pair<PagesSynchronizer, AbstractEvent>> queue = new LinkedBlockingQueue<>();

    private final List<PagesSynchronizer> synchronizers;

    private final MemoryDocumentManager memoryDocumentManager;

    public PageMonitor(List<PagesSynchronizer> synchronizers, MemoryDocumentManager memoryDocumentManager)
    {
        this.synchronizers = synchronizers;
        this.memoryDocumentManager = memoryDocumentManager;
    }

    public void start() throws InterruptedException
    {
        for (var synchronizer : synchronizers) {
            var t = new Thread(() ->
                synchronizer.startNotifier(e ->
                    queue.add(new ImmutablePair<>(synchronizer, e))
                )
            );
            t.start();
        }
        logger.info("Ready! Press CTRL+C to interrupt.");

        while (true) {
            var event = queue.take();
            try {
                for (PagesSynchronizer listener : synchronizers) {
                    // Don't notify the emitter itself
                    if (listener != event.getLeft()) {
                        listener.onDocumentChanged(event.getRight());
                    }
                }
                memoryDocumentManager.onDocumentChanged(event.getRight());
            } catch (Exception e) {
                logger.error("Error while processing event [{}]", event, e);
            }
        }
    }
}
