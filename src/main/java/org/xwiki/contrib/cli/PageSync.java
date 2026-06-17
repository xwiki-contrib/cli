package org.xwiki.contrib.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.lang.System.err;

public class PageSync
{
    protected final Command cmd;

    PageSync(Command cmd)
    {
        this.cmd = cmd;
    }

    /**
     * Push a page from the Maven repository to XWiki.
     */
    public void pushPage()
    {

    }

    /**
     * Pull a page from Xwiki to the Maven repository.
     */
    public void pullPage() throws DocException, IOException
    {
        var xarManager = new XARManager(cmd);
        var extractedPage = xarManager.getXarOfPages(List.of(cmd.pullReference())).entrySet().stream().findFirst();
        if (extractedPage.isEmpty()) {
            err.println("Can't extract page " + cmd.pullReference());
            return;
        }
        var targetFile = Path.of(cmd.pullReference());
        Files.writeString(targetFile, extractedPage.get().getValue());
    }

    /**
     * Push a page from the Maven repository to XWiki.
     */
    public void pushPages()
    {

    }

    /**
     * Pull a page from Xwiki to the Maven repository.
     */
    public void pullPages()
    {

    }
}
