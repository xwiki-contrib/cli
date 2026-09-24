package org.xwiki.contrib.cli.sync;

import java.io.IOException;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.document.InputDoc;
import org.xwiki.rendering.parser.ParseException;

public abstract class AbstractWorkingDirSynchronizer extends AbstractFileSynchronizer
{
    protected AbstractWorkingDirSynchronizer(Command cmd, MemoryDocumentManager memoryDocumentManager)
    {
        super(cmd, memoryDocumentManager);
    }

    public abstract void createInitDocFile(InputDoc doc)
        throws DocException, IOException, ComponentLookupException, ParseException;

    public abstract void cleanUnmanagedFiles() throws IOException;
}
