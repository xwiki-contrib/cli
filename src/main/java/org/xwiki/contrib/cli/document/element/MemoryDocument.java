package org.xwiki.contrib.cli.document.element;

import java.util.Collection;

import org.apache.commons.lang3.tuple.Pair;

public class MemoryDocument
{
    private final String reference;

    private String syntax;

    private long titleChecksum;

    private long contentChecksum;

    private Collection<ObjectInfo> objects;

    private Collection<Pair<AttachmentInfo, Long>> attachments;

    public MemoryDocument(String reference, String syntax, long titleChecksum, long contentChecksum,
        Collection<ObjectInfo> objects, Collection<Pair<AttachmentInfo, Long>> attachments)
    {
        this.reference = reference;
        this.syntax = syntax;
        this.titleChecksum = titleChecksum;
        this.contentChecksum = contentChecksum;
        this.objects = objects;
        this.attachments = attachments;
    }

    public String reference()
    {
        return reference;
    }

    public String syntax()
    {
        return syntax;
    }

    public void setSyntax(String syntax)
    {
        this.syntax = syntax;
    }

    public long titleChecksum()
    {
        return titleChecksum;
    }

    public void setTitleChecksum(long titleChecksum)
    {
        this.titleChecksum = titleChecksum;
    }

    public long contentChecksum()
    {
        return contentChecksum;
    }

    public void setContentChecksum(long contentChecksum)
    {
        this.contentChecksum = contentChecksum;
    }

    public Collection<ObjectInfo> objects()
    {
        return objects;
    }

    public void setObjects(Collection<ObjectInfo> objects)
    {
        this.objects = objects;
    }

    public Collection<Pair<AttachmentInfo, Long>> attachments()
    {
        return attachments;
    }

    public void setAttachments(Collection<Pair<AttachmentInfo, Long>> attachments)
    {
        this.attachments = attachments;
    }
}
