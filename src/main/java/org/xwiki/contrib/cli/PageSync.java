package org.xwiki.contrib.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.cli.document.InputDoc;
import org.xwiki.contrib.cli.document.InputOutputDoc;
import org.xwiki.contrib.cli.document.MvnRepoFileDoc;
import org.xwiki.contrib.cli.document.XMLFileDoc;
import org.xwiki.contrib.cli.document.XMLRestPage;
import org.xwiki.contrib.cli.document.element.AttachmentInfo;
import org.xwiki.contrib.cli.document.element.ObjectInfo;

public class PageSync
{
    private final Logger logger = LoggerFactory.getLogger(PageSync.class);

    protected final Command cmd;

    public PageSync(Command cmd)
    {
        this.cmd = cmd;
    }

    /**
     * Push a page from the Maven repository to XWiki.
     */
    public void pushPage() throws IOException, DocException
    {
        pushPage(cmd.pushReference());
    }

    /**
     * Push a page from the Maven repository to XWiki.
     */
    public void pushPage(String reference) throws IOException, DocException
    {
        var mvnPage = new MvnRepoFileDoc(cmd, reference);
        var xwikiPage = new XMLRestPage(cmd, cmd.wiki(), reference);
        syncPage(mvnPage, xwikiPage);
    }

    /**
     * Pull a page from XWiki to the Maven repository.
     */
    public void pullPage() throws DocException, IOException
    {
        pullPage(cmd.pullReference());
    }

    /**
     * Pull a page from XWiki to the Maven repository.
     */
    public void pullPage(String reference) throws DocException, IOException
    {
        var extractedPage = Utils.getXarOfPages(List.of(reference), cmd).entrySet().stream().findFirst();
        if (extractedPage.isEmpty()) {
            logger.error("Can't extract page [{}]", reference);
            return;
        }
        var targetFile = Path.of(Utils.getMvnReposRessourcePath(cmd).toString(), extractedPage.get().getKey());
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, extractedPage.get().getValue());
    }

    /**
     * Push all pages from the Maven repository to XWiki.
     */
    public void pushPages() throws IOException, DocException
    {
        var allPagesReferences = getAllPagesRefencesMvnRepo();
        for (var pageRef : allPagesReferences) {
            var mvnPage = new MvnRepoFileDoc(cmd, pageRef);
            var xwikiPage = new XMLRestPage(cmd, cmd.wiki(), pageRef);
            syncPage(mvnPage, xwikiPage);
        }
    }

    /**
     * Pull all pages from XWiki to the Maven repository.
     */
    public void pullPages() throws IOException, DocException
    {
        var allPagesReferences = getAllPagesRefencesMvnRepo();
        var allExtractedXml = Utils.getXarOfPages(allPagesReferences, cmd);
        for (var page : allExtractedXml.entrySet()) {
            if (page.getValue().isEmpty()) {
                logger.error("Can't extract page [{}]", page.getKey());
            }
            var targetFile = Path.of(Utils.getMvnReposRessourcePath(cmd).toString(), page.getKey());
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, page.getValue());
        }
    }

    private Map<String, List<ObjectInfo>> convertObjectListToMap(Collection<ObjectInfo> objects)
    {
        var res = new HashMap<String, List<ObjectInfo>>();
        for (var object : objects) {
            res.computeIfAbsent(object.objectClass(), k -> new ArrayList<>()).add(object);
        }
        return res;
    }

    private void syncPage(InputDoc source, InputOutputDoc target)
        throws DocException
    {
        target.setTitle(source.getTitle());
        target.setContent(source.getContent());
        var allObjects = source.getObjects(null, null, null);
        var allObjectsDestination = target.getObjects(null, null, null);
        var objectByClass = convertObjectListToMap(allObjects);
        var objectByClassDest = convertObjectListToMap(allObjectsDestination);
        var allClassesToHandle = new HashSet<>(objectByClass.keySet());
        allClassesToHandle.addAll(objectByClassDest.keySet());
        var objectToDelete = new ArrayList<ObjectInfo>();
        var objectToAdd = new ArrayList<ObjectInfo>();

        // Let's handle the object by classes as it's the way XWiki manage the objects
        for (var objClass : allClassesToHandle) {
            var srcObjs = objectByClass.getOrDefault(objClass, new ArrayList<>());
            var destObjs = objectByClassDest.getOrDefault(objClass, new ArrayList<>());
            var max = Integer.max(
                srcObjs.stream().max(Comparator.comparingInt(ObjectInfo::number)).map(ObjectInfo::number).orElse(0),
                destObjs.stream().max(Comparator.comparingInt(ObjectInfo::number)).map(ObjectInfo::number).orElse(0));
            for (int i = 0; i <= max; i++) {
                int index = i;
                var srcObj = srcObjs.stream().filter(o -> o.number() == index).findAny();
                var destObj = destObjs.stream().filter(o -> o.number() == index).findAny();
                if (srcObj.isEmpty() && destObj.isEmpty()) {
                    continue;
                }
                // need to check if we need to add or remove the object before updating it
                if (srcObj.isEmpty()) {
                    objectToDelete.add(destObj.get());
                } else {
                    // Note that the object added is empty, so we will need after to update it with the correct values
                    if (destObj.isEmpty()) {
                        objectToAdd.add(srcObj.get());
                    }
                    for (var p : srcObj.get().properties()) {
                        target.setValue(srcObj.get().objectClass(), String.valueOf(srcObj.get().number()), p.name(),
                            p.value());
                    }
                }
            }
        }

        var attachmentsByName = source.getAttachments().stream().map(AttachmentInfo::name).collect(Collectors.toSet());
        var attachmentsTargetByName =
            target.getAttachments().stream().map(AttachmentInfo::name).collect(Collectors.toSet());
        var allAttachmentsByName = new HashSet<>(attachmentsByName);
        allAttachmentsByName.addAll(attachmentsTargetByName);
        var attachmentToRemove = new ArrayList<String>();

        for (var a : allAttachmentsByName) {
            if (!attachmentsByName.contains(a)) {
                attachmentToRemove.add(a);
            } else {
                var content = source.getAttachment(a);
                target.setAttachment(a, content);
            }
        }
        // We need to do this after the save to avoid conflict on the dom of the current outputDoc
        for (var o : objectToAdd) {
            target.addObj(o);
        }
        target.save();
        for (var o : objectToDelete) {
            target.deleteObj(o);
        }
        for (var o : attachmentToRemove) {
            target.deleteAttachment(o);
        }
    }

    private ArrayList<String> getAllPagesRefencesMvnRepo() throws IOException, DocException
    {
        var allPagesToPull = Utils.listAllPagesMvnRepos(cmd);
        var allPagesReferences = new ArrayList<String>(allPagesToPull.size());
        for (var pagePath : allPagesToPull) {
            var xmlDoc = new XMLFileDoc(cmd, pagePath.toString());
            allPagesReferences.add(xmlDoc.getReference());
        }
        return allPagesReferences;
    }
}
