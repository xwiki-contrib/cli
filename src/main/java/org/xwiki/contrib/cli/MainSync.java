package org.xwiki.contrib.cli;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.document.XMLFileDoc;
import org.xwiki.contrib.cli.document.XMLRestPage;
import org.xwiki.contrib.cli.sync.MemoryDocumentManager;
import org.xwiki.contrib.cli.sync.MvnProjectSynchronizer;
import org.xwiki.contrib.cli.sync.MvnRepoSynchronizer;
import org.xwiki.contrib.cli.sync.PageMonitor;
import org.xwiki.contrib.cli.sync.PagesSynchronizer;
import org.xwiki.contrib.cli.sync.XFFSynchronizer;
import org.xwiki.contrib.cli.sync.XWikiSynchronizer;
import org.xwiki.rendering.parser.ParseException;

public class MainSync
{
    private final Logger logger = LoggerFactory.getLogger(MainSync.class);

    private final Command command;

    private final MemoryDocumentManager memoryDocumentManager;

    private final MvnProjectSynchronizer mvnProjectSynchronizer;

    private final XFFSynchronizer xffSynchronizer;

    public MainSync(Command command)
    {
        this.command = command;
        this.memoryDocumentManager = new MemoryDocumentManager(command);
        this.mvnProjectSynchronizer = new MvnProjectSynchronizer(command, this.memoryDocumentManager);
        this.xffSynchronizer = new XFFSynchronizer(command, this.memoryDocumentManager);
    }

    public void createInitDocFile() throws DocException, IOException, ComponentLookupException, ParseException
    {
        if (command.noWriteWiki() && command.noMvnRepoWrite()) {
            throw new IllegalArgumentException("You must provide at least on one place");
        }

        mvnProjectSynchronizer.createMavenProject();

        switch (command.firstSyncFrom()) {
            case Command.FirstSyncFrom.WIKI -> {
                var allPages = Utils.listAllPagesForSpaceXWiki(command, command.spaces());
                for (var p : allPages) {
                    var doc = new XMLRestPage(command, command.wiki(), p, false);
                    memoryDocumentManager.createInitDocFile(doc);
                    xffSynchronizer.createInitDocFile(doc);
                    mvnProjectSynchronizer.createInitDocFile(doc);
                }
            }
            case Command.FirstSyncFrom.MVN -> {
                var allPages = Utils.listAllPagesMvnRepos(command);
                for (var p : allPages) {
                    var doc = new XMLFileDoc(command, p.toString());
                    memoryDocumentManager.createInitDocFile(doc);
                    xffSynchronizer.createInitDocFile(doc);
                    mvnProjectSynchronizer.createInitDocFile(doc);
                }
            }
            default -> throw new IllegalArgumentException("Unknown first sync from " + command.firstSyncFrom());
        }

        xffSynchronizer.cleanUnmanagedFiles();
        mvnProjectSynchronizer.cleanUnmanagedFiles();
    }

    public void monitor() throws InterruptedException
    {
        var sychronizers = new LinkedList<PagesSynchronizer>(List.of(
            xffSynchronizer,
            mvnProjectSynchronizer,
            new XWikiSynchronizer(command, this.memoryDocumentManager),
            // Note important, we need to put the MvnRepoSynchronizer after the XWikiSynchronizer
            // because in some cases we retrieve the from XWiki to update the mvn repos
            // so the event should be already handled
            new MvnRepoSynchronizer(command, this.memoryDocumentManager)
        ));

        var monitor = new PageMonitor(sychronizers, memoryDocumentManager);
        monitor.start();
    }
}
