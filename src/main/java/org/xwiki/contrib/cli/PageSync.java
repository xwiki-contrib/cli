package org.xwiki.contrib.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.cli.document.InputDoc;
import org.xwiki.contrib.cli.document.MvnRepoFileDoc;
import org.xwiki.contrib.cli.document.OutputDoc;
import org.xwiki.contrib.cli.document.OutputXMLRestPage;
import org.xwiki.contrib.cli.document.XMLFileDoc;

public class PageSync
{
    private final Logger logger = LoggerFactory.getLogger(PageSync.class);

    protected final Command cmd;

    PageSync(Command cmd)
    {
        this.cmd = cmd;
    }

    /**
     * Push a page from the Maven repository to XWiki.
     */
    public void pushPage() throws IOException, DocException
    {
        var mvnPage = new MvnRepoFileDoc(cmd, cmd.pushReference());
        var xwikiPage = new OutputXMLRestPage(cmd, cmd.wiki(), cmd.pushReference());
        syncPage(xwikiPage, mvnPage);
    }

    /**
     * Pull a page from XWiki to the Maven repository.
     */
    public void pullPage() throws DocException, IOException
    {
        var extractedPage = Utils.getXarOfPages(List.of(cmd.pullReference()), cmd).entrySet().stream().findFirst();
        if (extractedPage.isEmpty()) {
            logger.error("Can't extract page " + cmd.pullReference());
            return;
        }
        var targetFile = Path.of(extractedPage.get().getKey());
        Files.writeString(targetFile, extractedPage.get().getValue());
    }

    /**
     * Push all pages from the Maven repository to XWiki.
     */
    public void pushPages() throws IOException, DocException
    {
        var allPagesReferences = getAllPagesRefencesMvnRepo();
        for (var pageRef : allPagesReferences) {
            var mvnPage = new MvnRepoFileDoc(cmd, pageRef);
            var xwikiPage = new OutputXMLRestPage(cmd, cmd.wiki(), pageRef);
            syncPage(xwikiPage, mvnPage);
        }
    }

    /**
     * Pull all pages from XWiki to the Maven repository.
     */
    public void pullPages() throws IOException, DocException
    {
        var allPagesReferences = getAllPagesRefencesMvnRepo();
        var allExtractedXml = Utils.getXarOfPages(allPagesReferences, cmd);
        for (var page : allExtractedXml.entrySet()) {
            if (page.getValue().isEmpty()) {
                logger.error("Can't extract page [{}]", page.getKey());
            }
            var targetFile = Path.of(page.getKey());
            Files.writeString(targetFile, page.getValue());
        }
    }

    private void syncPage(OutputDoc target, InputDoc source) throws DocException
    {
        target.setTitle(source.getTitle());
        target.setContent(source.getContent());
        for (var o : source.getObjects(null, null, null)) {
            for (var p : o.properties()) {
                target.setValue(o.objectClass(), String.valueOf(o.number()), p.name(), p.value());
            }
        }
        for (var a : source.getAttachments()) {
            var content = source.getAttachment(a.name());
            target.setAttachment(a.name(), content);
        }
    }

    private ArrayList<String> getAllPagesRefencesMvnRepo() throws IOException, DocException
    {
        var allPagesToPull = Utils.listAllPagesMvnRepos(cmd);
        var allPagesReferences = new ArrayList<String>(allPagesToPull.size());
        for (var pagePath : allPagesToPull) {
            var xmlDoc = new XMLFileDoc(cmd, pagePath.toString());
            allPagesReferences.add(xmlDoc.getReference());
        }
        return allPagesReferences;
    }
}
