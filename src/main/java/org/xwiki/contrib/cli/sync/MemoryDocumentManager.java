package org.xwiki.contrib.cli.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.Editing;
import org.xwiki.contrib.cli.document.InputDoc;
import org.xwiki.contrib.cli.document.MvnRepoFileDoc;
import org.xwiki.contrib.cli.document.XMLRestPage;
import org.xwiki.contrib.cli.document.element.AttachmentInfo;
import org.xwiki.contrib.cli.document.element.MemoryDocument;
import org.xwiki.contrib.cli.document.element.ObjectInfo;
import org.xwiki.contrib.cli.document.element.Property;
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
import org.xwiki.contrib.cli.sync.event.PageDeletedEvent;
import org.xwiki.contrib.cli.sync.event.TitleChangedEvent;
import org.xwiki.rendering.parser.ParseException;

public class MemoryDocumentManager
{
    private final Logger logger = LoggerFactory.getLogger(MemoryDocumentManager.class);

    private final Map<String, MemoryDocument> documents = new HashMap<>();

    private final Editing editing = new Editing();

    private final Command command;

    public MemoryDocumentManager(Command command)
    {
        this.command = command;
    }

    public void createInitDocFile(InputDoc doc) throws DocException
    {
        var objects = doc.getObjects(null, null, null).stream()
            .map(o ->
                new ObjectInfo(o.objectClass(), o.number(), o.properties().stream().map(p ->
                    // Convert the property just to remove the full content which could be big in memory and just replace it by a checksum
                    new Property(p.name(), Long.toString(convertToChecksum(p.value())), p.scriptingExtension())
                ).toList())
            ).collect(Collectors.toList());

        var ref = doc.getReference();
        var d = new MemoryDocument(ref, doc.getSyntaxId(), convertToChecksum(doc.getTitle()),
            convertToChecksum(doc.getContent()), objects,
            doc.getAttachments().stream()
                .map(a -> {
                    try {
                        return new ImmutablePair<>(a, convertToChecksum(doc.getAttachment(a.name())));
                    } catch (DocException e) {
                        logger.error("Can't get attachment for doc [{}] with name [{}]", ref, a.name());
                        return new ImmutablePair<>(a, 0L);
                    }
                }).collect(Collectors.toList()));
        documents.put(doc.getReference(), d);
    }

    private long convertToChecksum(byte[] value)
    {
        CRC32 crc = new CRC32();
        crc.update(value);
        return crc.getValue();
    }

    private long convertToChecksum(String value)
    {
        return convertToChecksum(value.getBytes(StandardCharsets.UTF_8));
    }

    private InputDoc getInputDoc(String reference) throws IOException, DocException
    {
        if (command.noMvnRepoWrite()) {
            return new XMLRestPage(command, command.wiki(), reference, false);
        } else {
            return new MvnRepoFileDoc(command, reference);
        }
    }

    private Optional<Property> getObjectProperty(String reference, String className, int number, String property)
    {
        return Optional.ofNullable(documents.get(reference))
            .flatMap(d ->
                d.objects().stream()
                    .filter(o -> o.objectClass().equals(className) && o.number() == number)
                    .findAny().flatMap(o ->
                        o.properties().stream()
                            .filter(p -> p.name().equals(property))
                            .findAny()
                    )
            );
    }

    private MemoryDocument getDocOrDefault(String reference)
    {
        return documents.getOrDefault(reference,
            new MemoryDocument(reference, null, 0, 0, List.of(), List.of()));
    }

    public boolean docExist(String reference)
    {
        return documents.containsKey(reference);
    }

    public List<String> getAllDocReference()
    {
        return documents.keySet().stream().toList();
    }

    public boolean isTitleEquals(String reference, String value)
    {
        var d = getDocOrDefault(reference);
        if (d == null) {
            return false;
        }
        return convertToChecksum(value) == d.titleChecksum();
    }

    public boolean isContentEquals(String reference, String value)
    {
        var d = getDocOrDefault(reference);
        if (d == null) {
            return false;
        }
        return convertToChecksum(value) == d.contentChecksum();
    }

    public boolean isObjectPropertyEquals(String reference, String className, int number, String property, String value)
    {
        var p = getObjectProperty(reference, className, number, property);
        return p.filter(prop -> Long.toString(convertToChecksum(value)).equals(prop.value())).isPresent();
    }

    public boolean isAttachmentEquals(String reference, String name, byte[] value)
    {
        return getDocOrDefault(reference).attachments().stream()
            .anyMatch(a -> a.getLeft().name().equals(name) && a.getRight() == convertToChecksum(value));
    }

    public List<String> getAttachments(String reference)
    {
        return getDocOrDefault(reference).attachments().stream().map(a -> a.getLeft().name()).toList();
    }

    public Map<String, List<Integer>> getObjects(String reference)
    {
        var res = new HashMap<String, List<Integer>>();
        for (var o : getDocOrDefault(reference).objects()) {
            res.computeIfAbsent(o.objectClass(), k -> new ArrayList<>()).add(o.number());
        }
        return res;
    }

    public Optional<String> getDocSyntax(String reference)
    {
        return Optional.ofNullable(documents.get(reference)).map(MemoryDocument::syntax);
    }

    public Optional<String> getObjectPropertyScriptingExtension(String reference, String className, int number,
        String property)
    {
        return getObjectProperty(reference, className, number, property).flatMap(Property::scriptingExtension);
    }

    public void onDocumentChanged(AbstractEvent event)
    {
        if (documents.get(event.reference()) == null && !(event instanceof PageCreatedEvent)) {
            logger.warn("Document [{}] does not exist in memory", event.reference());
        }
        try {
            switch (event) {
                case PageCreatedEvent e:
                    documents.put(e.reference(),
                        new MemoryDocument(e.reference(), e.syntax(), 0, 0, new ArrayList<>(), new ArrayList<>()));
                    break;
                case PageDeletedEvent e:
                    documents.remove(e.reference());
                    break;
                case TitleChangedEvent e:
                    documents.get(e.reference()).setTitleChecksum(convertToChecksum(e.title()));
                    break;
                case ContentChangedEvent e:
                    documents.get(e.reference()).setContentChecksum(convertToChecksum(e.content()));
                    break;
                case MacroInContentChangedEvent e: {
                    var currentContent = getInputDoc(e.reference()).getContent();
                    var d = documents.get(e.reference());
                    var newContent =
                        editing.updateMacro(currentContent, d.syntax(), e.macroInstance(), e.macroContent());
                    d.setContentChecksum(convertToChecksum(newContent));
                }
                break;
                case AttachmentAddedEvent e:
                    documents.get(e.reference()).attachments()
                        .add(new ImmutablePair<>(new AttachmentInfo(e.name(), e.data().length),
                            convertToChecksum(e.data())));
                    break;
                case AttachmentUpdatedEvent e: {
                    var d = documents.get(e.reference());
                    d.attachments().stream().filter(a -> a.getLeft().name().equals(e.name())).findAny()
                        .ifPresent(a ->
                        {
                            d.attachments().remove(a);
                            d.attachments().add(
                                new ImmutablePair<>(new AttachmentInfo(e.name(), e.data().length),
                                    convertToChecksum(e.data())));
                        });
                }
                break;
                case AttachmentDeletedEvent e: {
                    var d = documents.get(e.reference());
                    d.attachments().stream().filter(a -> a.getLeft().name().equals(e.name())).findAny()
                        .ifPresent(a -> d.attachments().remove(a));
                }
                break;
                case ObjectAddedEvent e:
                    documents.get(e.reference()).objects()
                        .add(new ObjectInfo(e.className(), e.number(), e.properties()));
                    break;
                case ObjectRemovedEvent e: {
                    var d = documents.get(e.reference());
                    d.objects().stream().filter(o -> o.objectClass().equals(e.className()) && o.number() == e.number())
                        .findAny()
                        .ifPresent(o -> d.objects().remove(o));
                }
                break;
                case MacroInObjectPropertyChangedEvent e: {
                    var currentContent =
                        getInputDoc(e.reference()).getValue(e.className(), Integer.toString(e.number()), e.property());
                    var d = documents.get(e.reference());
                    var newContent =
                        editing.updateMacro(currentContent.orElseThrow(() -> new DocException("Macro not found")),
                            d.syntax(),
                            e.macroInstance(),
                            e.macroContent());

                    d.objects().stream().filter(o -> o.objectClass().equals(e.className()) && o.number() == e.number())
                        .findAny()
                        .ifPresent(o -> {
                            d.objects().remove(o);
                            var newProperties = o.properties().stream().map(p ->
                                p.name().equals(e.objectProperty().property())
                                    ? new Property(p.name(), newContent, p.scriptingExtension())
                                    : p
                            ).toList();
                            d.objects().add(new ObjectInfo(e.className(), e.number(), newProperties));
                        });
                }
                break;
                case ObjectPropertyChangedEvent e: {
                    var d = documents.get(e.reference());
                    d.objects().stream().filter(o -> o.objectClass().equals(e.className()) && o.number() == e.number())
                        .findAny()
                        .ifPresent(o -> {
                            d.objects().remove(o);
                            var newProperties = o.properties().stream().map(p ->
                                p.name().equals(e.objectProperty().property())
                                    ? new Property(p.name(), e.newValue(), p.scriptingExtension())
                                    : p
                            ).toList();
                            d.objects().add(new ObjectInfo(e.className(), e.number(), newProperties));
                        });
                }
                break;
                default:
                    throw new NotImplementedException(
                        "Event for class [" + event.getClass().getName() + "] not implemented");
            }
        } catch (DocException | ComponentLookupException | ParseException | IOException ex) {
            logger.error("Error happen while handling event", ex);
        }
    }
}
