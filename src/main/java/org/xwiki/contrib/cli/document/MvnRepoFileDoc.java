package org.xwiki.contrib.cli.document;

import java.io.IOException;
import java.nio.file.Path;

import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.Utils;

/**
 * This class represents a "XAR" directory.
 *
 * @version $Id$
 */
public class MvnRepoFileDoc extends XMLFileDoc implements InputDoc, OutputDoc
{
    MvnRepoFileDoc(Command cmd)
    {
        super(cmd);
    }

    MvnRepoFileDoc(Command cmd, String baseDir, String wiki, String page) throws DocException, IOException
    {
        super(cmd, Path.of(baseDir, "src", "main", "resources",
            Utils.fromReferenceToMvnReposPath(page)) + ".xml");
    }
}
