package org.xwiki.contrib.cli.document;

import java.io.IOException;
import java.nio.file.Path;

import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.Utils;

/**
 * This class represents a "XAR" directory.
 *
 * @version $Id$
 */
public class MvnRepoFileDoc extends XMLFileDoc implements InputDoc, OutputDoc
{
    /**
     * @param cmd cmd the command line called.
     * @param reference the reference of the page.
     * @throws IOException if something when wrong while initializing this object.
     */
    public MvnRepoFileDoc(Command cmd, String reference) throws IOException
    {
        super(cmd, Path.of(cmd.mvnRepo(), "src", "main", "resources",
            Utils.fromReferenceToMvnReposPath(reference)) + ".xml");
    }
}
