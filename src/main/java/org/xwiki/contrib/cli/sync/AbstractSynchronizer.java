package org.xwiki.contrib.cli.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.Editing;
import org.xwiki.contrib.cli.document.InputDoc;
import org.xwiki.contrib.cli.document.InputOutputDoc;
import org.xwiki.contrib.cli.document.element.AttachmentInfo;
import org.xwiki.contrib.cli.document.element.ObjectInfo;
import org.xwiki.contrib.cli.document.element.ObjectProperty;
import org.xwiki.contrib.cli.sync.event.AbstractEvent;
import org.xwiki.contrib.cli.sync.event.AttachmentAddedEvent;
import org.xwiki.contrib.cli.sync.event.AttachmentDeletedEvent;
import org.xwiki.contrib.cli.sync.event.AttachmentUpdatedEvent;
import org.xwiki.contrib.cli.sync.event.ContentChangedEvent;
import org.xwiki.contrib.cli.sync.event.MacroInContentChangedEvent;
import org.xwiki.contrib.cli.sync.event.MacroInObjectPropertyChangedEvent;
import org.xwiki.contrib.cli.sync.event.ObjectAddedEvent;
import org.xwiki.contrib.cli.sync.event.ObjectPropertyChangedEvent;
import org.xwiki.contrib.cli.sync.event.ObjectRemovedEvent;
import org.xwiki.contrib.cli.sync.event.PageCreatedEvent;
import org.xwiki.contrib.cli.sync.event.TitleChangedEvent;
import org.xwiki.rendering.parser.ParseException;

public abstract class AbstractSynchronizer implements PagesSynchronizer
{
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final Editing editing = new Editing();

    protected final Command command;

    protected final MemoryDocumentManager memoryDocumentManager;

    protected AbstractSynchronizer(Command cmd, MemoryDocumentManager memoryDocumentManager)
    {
        this.command = cmd;
        this.memoryDocumentManager = memoryDocumentManager;
    }

    protected List<AbstractEvent> createEventsFromDoc(InputDoc doc) throws DocException
    {
        var res = new ArrayList<AbstractEvent>();
        var ref = doc.getReference();

        if (!memoryDocumentManager.docExist(ref)) {
            res.add(new PageCreatedEvent(ref, doc.getSyntaxId()));
        }

        // check each doc property to see what changed

        if (memoryDocumentManager.isTitleEquals(ref, doc.getTitle())) {
            res.add(new TitleChangedEvent(ref, doc.getTitle()));
        }
        if (memoryDocumentManager.isContentEquals(ref, doc.getContent())) {
            res.add(new ContentChangedEvent(ref, doc.getContent()));
        }

        // Objects
        var currentObjects = memoryDocumentManager.getObjects(ref);
        var newObjects = doc.getObjects(null, null, null);
        var allClassesToHandle = new HashSet<>(currentObjects.keySet());
        allClassesToHandle.addAll(newObjects.stream().map(ObjectInfo::objectClass).toList());

        var newObjectsMap = new HashMap<String, List<ObjectInfo>>();
        for (var object : newObjects) {
            newObjectsMap.computeIfAbsent(object.objectClass(), k -> new ArrayList<>()).add(object);
        }

        // Let's handle the object by classes as it's the way XWiki manage the objects
        for (var objClass : allClassesToHandle) {
            var crObjs = currentObjects.getOrDefault(objClass, new ArrayList<>());
            var newObjs = newObjectsMap.getOrDefault(objClass, new ArrayList<>());
            var max = Integer.max(
                crObjs.stream().max(Comparator.comparingInt(i -> i)).orElse(0),
                newObjs.stream().max(Comparator.comparingInt(ObjectInfo::number)).map(ObjectInfo::number).orElse(0));
            for (int i = 0; i <= max; i++) {
                int index = i;
                var crObj = crObjs.stream().filter(n -> n == index).findAny();
                var newObj = newObjs.stream().filter(o -> o.number() == index).findAny();
                if (newObj.isEmpty() && crObj.isEmpty()) {
                    continue;
                }
                if (newObj.isEmpty()) {
                    res.add(new ObjectRemovedEvent(ref, objClass, crObj.get()));
                } else {
                    if (crObj.isEmpty()) {
                        res.add(new ObjectAddedEvent(ref, objClass, i, newObj.get().properties()));
                    } else {
                        for (var p : newObj.get().properties()) {
                            var newValue = p.value();
                            if (!memoryDocumentManager.isObjectPropertyEquals(ref, objClass, i, p.name(), p.value())) {
                                res.add(new ObjectPropertyChangedEvent(ref, new ObjectProperty(objClass, i, p.name()),
                                    newValue));
                            }
                        }
                    }
                }
            }
        }

        // Attachments
        var currentAttachments = new HashSet<>(memoryDocumentManager.getAttachments(ref));
        var newAttachments = doc.getAttachments().stream().map(AttachmentInfo::name).collect(Collectors.toSet());

        var attachmentsAdded = new HashSet<>(newAttachments);
        attachmentsAdded.removeAll(currentAttachments);
        var attachmentsRemoved = new HashSet<>(currentAttachments);
        attachmentsRemoved.removeAll(newAttachments);
        var attachmentsAlreadyPresents = new HashSet<>(newAttachments);
        attachmentsAlreadyPresents.retainAll(currentAttachments);

        for (var a : attachmentsAdded) {
            res.add(new AttachmentAddedEvent(ref, a, doc.getAttachment(a)));
        }
        for (var a : attachmentsRemoved) {
            res.add(new AttachmentDeletedEvent(ref, a));
        }
        for (var a : attachmentsAlreadyPresents) {
            var attachmentContent = doc.getAttachment(a);
            if (!memoryDocumentManager.isAttachmentEquals(ref, a, attachmentContent)) {
                res.add(new AttachmentUpdatedEvent(ref, a, attachmentContent));
            }
        }

        return res;
    }

    protected boolean handleEventOnDocument(AbstractEvent event, InputOutputDoc doc)
        throws IOException, DocException, ComponentLookupException, ParseException
    {
        boolean changed = false;
        switch (event) {
            case TitleChangedEvent e:
                if (!doc.getTitle().strip().equals(e.title())) {
                    doc.setTitle(e.title());
                }
                break;
            case ContentChangedEvent e:
                if (!doc.getContent().strip().equals(e.content().strip())) {
                    doc.setContent(e.content());
                    changed = true;
                }
                break;
            case MacroInContentChangedEvent e: {
                var prevContent = doc.getContent();
                var newContent =
                    editing.updateMacro(prevContent, doc.getSyntaxId(), e.macroInstance(), e.macroContent());
                if (!prevContent.strip().equals(newContent.strip())) {
                    doc.setContent(newContent);
                    changed = true;
                }
            }
            break;
            case MacroInObjectPropertyChangedEvent e: {
                var objClass = e.className();
                var objNumber = Integer.toString(e.number());
                var propertyContent = doc.getValue(objClass, objNumber, e.property());
                if (propertyContent.isPresent()) {
                    var newContent =
                        editing.updateMacro(propertyContent.orElseThrow(), doc.getSyntaxId(), e.macroInstance(),
                            e.macroContent());
                    if (!propertyContent.get().strip().equals(newContent.strip())) {
                        doc.setValue(objClass, objNumber, e.property(), newContent);
                        changed = true;
                    }
                } else {
                    logger.error("Property [{}] of object [{}] seem null, can't update macro content", e.property(),
                        e.className());
                }
            }
            break;
            case ObjectPropertyChangedEvent e: {
                var objClass = e.className();
                var objNumber = Integer.toString(e.number());
                var propertyContent = doc.getValue(objClass, objNumber, e.property());
                if (!propertyContent.orElse("").strip().equals(e.newValue())) {
                    doc.setValue(objClass, objNumber, e.property(), e.newValue());
                    changed = true;
                }
            }
            break;
            case AttachmentAddedEvent e:
                if (doc.getAttachments().stream().map(AttachmentInfo::name).findAny().isEmpty()) {
                    doc.setAttachment(e.name(), e.data());
                    changed = true;
                }
                break;
            case AttachmentUpdatedEvent e:
                if (!Arrays.equals(doc.getAttachment(e.name()), e.data())) {
                    doc.setAttachment(e.name(), e.data());
                    changed = true;
                }
                break;
            case AttachmentDeletedEvent e:
                if (doc.getAttachments().stream().map(AttachmentInfo::name).findAny().isPresent()) {
                    doc.deleteAttachment(e.name());
                    changed = true;
                }
                break;
            default:
                logger.error("Unimplemented event: [{}]", event.getClass());
        }
        return changed;
    }
}
