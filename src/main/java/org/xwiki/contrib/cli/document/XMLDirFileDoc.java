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
public class XMLDirFileDoc extends XMLFileDoc implements InputDoc, OutputDoc
{
    XMLDirFileDoc(Command cmd)
    {
        super(cmd);
    }

    XMLDirFileDoc(Command cmd, String baseDir) throws DocException, IOException
    {
        super(cmd, Path.of(baseDir, "src", "main", "resources",
            Utils.fromReferenceToMvnReposPath(cmd.getPage())) + ".xml");
    }
}
