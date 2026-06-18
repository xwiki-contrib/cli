package org.xwiki.contrib.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.cli.document.InputDoc;
import org.xwiki.contrib.cli.document.InputXMLRestPage;
import org.xwiki.contrib.cli.document.MvnRepoFileDoc;
import org.xwiki.contrib.cli.document.OutputDoc;
import org.xwiki.contrib.cli.document.OutputXMLRestPage;
import org.xwiki.contrib.cli.document.XMLFileDoc;
import org.xwiki.contrib.cli.document.element.AttachmentInfo;
import org.xwiki.contrib.cli.document.element.ObjectInfo;

class PageSync
{
    private final Logger logger = LoggerFactory.getLogger(PageSync.class);

    protected final Command cmd;

    PageSync(Command cmd)
    {
        this.cmd = cmd;
    }

    /**
     * Push a page from the Maven repository to XWiki.
     */
    public void pushPage() throws IOException, DocException
    {
        var mvnPage = new MvnRepoFileDoc(cmd, cmd.pushReference());
        var xwikiPage = new OutputXMLRestPage(cmd, cmd.wiki(), cmd.pushReference());
        var xwikiPageRead = new InputXMLRestPage(cmd, cmd.wiki(), cmd.pushReference());
        // TODO we would proabably refactor this in a better way to not duplicate the output doc and inputdoc
        syncPage(mvnPage, xwikiPage, xwikiPageRead);
    }

    /**
     * Pull a page from XWiki to the Maven repository.
     */
    public void pullPage() throws DocException, IOException
    {
        var extractedPage = Utils.getXarOfPages(List.of(cmd.pullReference()), cmd).entrySet().stream().findFirst();
        if (extractedPage.isEmpty()) {
            logger.error("Can't extract page [{}]", cmd.pullReference());
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
            var xwikiPage = new OutputXMLRestPage(cmd, cmd.wiki(), pageRef);
            var xwikiPageRead = new InputXMLRestPage(cmd, cmd.wiki(), pageRef);
            syncPage(mvnPage, xwikiPage, xwikiPageRead);
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
            if (!res.containsKey(object.objectClass())) {
                res.put(object.objectClass(), new ArrayList<>());
            }
            res.get(object.objectClass()).add(object);
        }
        return res;
    }

    private void syncPage(InputDoc source, OutputDoc target, InputDoc targetRead)
        throws DocException
    {
        target.setTitle(source.getTitle());
        target.setContent(source.getContent());
        var allObjects = source.getObjects(null, null, null);
        var allObjectsDestination = targetRead.getObjects(null, null, null);
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
            for (int i = 0; i < Math.max(srcObjs.size(), destObjs.size()); i++) {
                ObjectInfo srcObj = null;
                ObjectInfo destObj = null;
                if (i < srcObjs.size()) {
                    srcObj = srcObjs.get(i);
                }
                if (i < destObjs.size()) {
                    destObj = destObjs.get(i);
                }
                // need to check if we need to add or remove the object before updating it
                if (srcObj == null) {
                    objectToDelete.add(destObj);
                } else {
                    // Note that the object added is empty, so we will need after to update it with the correct values
                    if (destObj == null) {
                        objectToAdd.add(srcObj);
                    }
                    for (var p : srcObj.properties()) {
                        target.setValue(srcObj.objectClass(), String.valueOf(srcObj.number()), p.name(), p.value());
                    }
                }
            }
        }

        var attachmentsByName = source.getAttachments().stream().map(AttachmentInfo::name).collect(Collectors.toSet());
        var attachmentsTargetByName =
            targetRead.getAttachments().stream().map(AttachmentInfo::name).collect(Collectors.toSet());
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
