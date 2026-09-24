package org.xwiki.contrib.cli.document.element;

import java.util.List;

public record XWikiEvent(XWikiEventTypes type, String document, List<Long> dates, List<String> ids)
{
}
