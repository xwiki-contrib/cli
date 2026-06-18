package org.xwiki.contrib.cli;

import java.util.ArrayList;

/**
 * Reference of a XWiki document.
 *
 * @param spaces the spaces of the page.
 * @param page the page name.
 */
public record PageReference(ArrayList<String> spaces, String page)
{
}
