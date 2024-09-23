package org.xwiki.contrib.cli;

import java.io.IOException;
import java.nio.file.Path;

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
        super(cmd, Path.of(baseDir, Utils.pageToXARPath(cmd.page)).toString());
    }
}
