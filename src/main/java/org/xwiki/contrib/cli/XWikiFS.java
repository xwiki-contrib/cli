/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;

import jnr.ffi.Pointer;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

class XWikiFS extends FuseStubFS
{
    private static final String DIR_NAME_PROPERTIES = "properties";

    private static final String ATTACHMENTS_PATTERN = "^/attachments/([^/]+)$";

    private static final Pattern ATTACHMENTS_PATTERN_MATCHER = Pattern.compile(ATTACHMENTS_PATTERN);

    private static final String DIR_NAME_SPACES = "spaces";

    private static final String DIR_NAME_PAGES = "pages";

    private static final Pattern ATTACHMENT_PATTERN =
        Pattern.compile("^(/wikis/[^/]+/spaces(?:/[^/]+/spaces)*/[^/]+/pages/[^/]+/attachments)/([^/]+)$");

    private static final Pattern WIKI_DIRECTORY_CONTENTS_PATTERN = Pattern.compile("^/wikis/([^/]+)$");

    private static final Pattern SPACE_LIST_PATTERN = Pattern.compile("^/wikis/([^/]+)/spaces((?:/[^/]+/spaces)*)$");

    private static final Pattern SPACE_CONTENT_PATTERN =
        Pattern.compile("^/wikis/[^/]+/spaces/[^/]+(?:/spaces/[^/]+)*$");

    private static final Pattern CLASSES_PATTERN = Pattern.compile("^/wikis/[^/]+/classes$");

    private static final Pattern PAGES_DIRECTORY_PATTERN =
        Pattern.compile("^/wikis/(?:[^/]+)/spaces(?:/[^/]+/spaces)*/[^/]+/pages$");

    private static final Pattern SINGLE_PAGE_DIRECTORY_PATTERN =
        Pattern.compile("^/wikis/(?:[^/]+)/spaces(?:/[^/]+/spaces)*/[^/]+/pages/[^/]+$");

    private static final Pattern ATTACHMENTS_DIRECTORY_PATTERN =
        Pattern.compile("^/wikis/(?:[^/]+)/spaces(?:/[^/]+/spaces)*/[^/]+/pages/[^/]+/attachments");

    private static final Pattern SINGLE_CLASS_DIRECTORY_PATTERN =
        Pattern.compile("^/wikis/(?:[^/]+)/spaces(?:/[^/]+/spaces)*/[^/]+/pages/[^/]+/class$");

    private static final Pattern OBJECTS_DIRECTORY_PATTERN =
        Pattern.compile("^/wikis/(?:[^/]+)/spaces(?:/[^/]+/spaces)*/[^/]+/pages/[^/]+/objects$");

    private static final Pattern OBJECT_INSTANCES_PATTERN =
        Pattern.compile("^(/wikis/(?:[^/]+)/spaces(?:/[^/]+/spaces)*/[^/]+/pages/[^/]+/objects)/([^/]+)$");

    private static final Pattern OBJECT_CONTENT_PATTERN =
        Pattern.compile("^/wikis/(?:[^/]+)/spaces(?:/[^/]+/spaces)*/[^/]+/pages/[^/]+/objects/[^/]+/[^/]+$");

    private static final Pattern PROPERTIES_DIRECTORY_PATTERN =
        Pattern.compile("^(/wikis/[^/]+/spaces(?:/[^/]+/spaces)*/[^/]+/pages/[^/]+/objects/([^/]+)/[^/]+)/properties$");

    private static final Pattern CLASS_PROPERTIES_MATCHER =
        Pattern.compile("^(/wikis/[^/]+)/spaces((?:/[^/]+/spaces)*/[^/]+)/pages/([^/]+)/class/properties$");

    private static final Pattern CLASS_PROPERTY_MATCHER =
        Pattern.compile("^(/wikis/[^/]+)/spaces((?:/[^/]+/spaces)*/[^/]+)/pages/([^/]+)/class/properties/([^/]+)$");

    private static final String URL_PART_REST = "/rest";

    private static final String URL_PART_SPACES = "/spaces/";

    private static final String DOT = ".";

    private static final String SLASH = "/";

    private static final String ESCAPED_DOT = "\\.";

    private static final String URL_PART_CLASSES = "/classes/";

    private static final String URL_PART_CONTENT = "/content";

    private static final String URL_PART_TITLE = "/title";

    private static final String DIR_NAME_CONTENT = "content";

    private static final Pattern PAGES_PATTERN_MATCHER =
        Pattern.compile("^/wikis/([^/]+)/(spaces(?:/[^/]+/spaces)*/[^/]+)/pages/([^/]+)");

    private static final Pattern OBJECTS_PROPERTIES_PATTERN_MATCHER =
        Pattern.compile("^/objects/([^/]+)/([^/]+)/properties/([^/]+)$");

    private final Command command;

    XWikiFS(Command command)
    {
        this.command = command;
    }

    @Override
    public int getattr(String path, FileStat stat)
    {
        int result = 0;
        Matcher matcher = ATTACHMENT_PATTERN.matcher(path);
        if (matcher.find()) {
            String filename = matcher.group(2);
            String encodedURLPart = FSDirUtils.escapeURLWithSlashes(matcher.group(1));
            String attachmentsRestURL = this.command.getUrl() + URL_PART_REST + encodedURLPart;
            String a = null;
            try {
                a = Utils.httpGet(this.command, attachmentsRestURL).body();
                Document doc = Utils.parseXML(a);
                Element root = doc.getRootElement();
                Node attachment = root.selectSingleNode(String.format("/xwiki:attachments/xwiki:attachment[./xwiki:name"
                    + "/text() = %s]", Utils.escapeXPathString(filename)));
                if (attachment != null) {
                    Node sizeNode = attachment.selectSingleNode("xwiki:longSize | xwiki:size");
                    long size = Long.parseLong(sizeNode.getText());
                    stat.st_mode.set(FileStat.S_IFREG | 0644);
                    stat.st_size.set(size);
                } else {
                    result = -ErrorCodes.ENOENT();
                }
            } catch (DocException e) {
                result = -ErrorCodes.ENOENT();
            }
        } else if (path.matches("/wikis/[^/]+/classes/[^/]+$")
            || path.matches("^.+/properties/[^/]+\\.[^/]+$")
            || path.matches("^.+/class/properties/[^/]+/[^/]+\\.[^/]+$")
            || path.matches("^.+/pages/[^/]+/content\\.[^/]+$"))
        {
            stat.st_mode.set(FileStat.S_IFLNK | 0644);
            stat.st_size.set(0);
        } else {
            try {
                listDir(path, true);
                stat.st_mode.set(FileStat.S_IFDIR | 0755);
                stat.st_size.set(0);
            } catch (DocException e) {
                stat.st_mode.set(FileStat.S_IFREG | 0644);
                stat.st_size.set(getValue(path).length);
            }
        }

        return result;
    }

    @Override
    public int readlink(String path, Pointer buf, long size)
    {
        Pattern pattern1 = Pattern.compile("^.+/properties/(?:[^/]+/)?([^/]+)\\.[^/.]+$");
        Matcher matcher1 = pattern1.matcher(String.valueOf(path));
        if (matcher1.find()) {
            String content = matcher1.group(1);
            buf.putString(0L, content, (int) size, StandardCharsets.UTF_8);
            return 0;
        }

        Pattern pattern2 = Pattern.compile("^.+/pages/(?:[^/]+/)(content)\\.[^/.]+$");
        Matcher matcher2 = pattern2.matcher(String.valueOf(path));
        if (matcher2.find()) {
            String content = matcher2.group(1);
            buf.putString(0L, content, (int) size, StandardCharsets.UTF_8);
            return 0;
        }

        Pattern pattern3 = Pattern.compile("/wikis/[^/]+/classes/([^/]+)$");
        Matcher matcher3 = pattern3.matcher(String.valueOf(path));
        if (matcher3.find()) {
            String c = ".." + pageToSpacesAndPagesRESTURLPart(String.valueOf(matcher3.group(1))) + "/class";
            buf.putString(0L, c, (int) size, StandardCharsets.UTF_8);
            return 0;
        }

        return -ErrorCodes.ENOENT();
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, long offset, FuseFileInfo fi)
    {
        try {
            Arrays.stream(listDir(path, false)).forEach(
                filename -> filter.apply(buf, filename, null, 0)
            );
        } catch (DocException e) {
            return -ErrorCodes.ENOENT();
        }

        return 0;
    }

    public String[] listDir(String path, boolean onlyTestIfExists) throws DocException
    {
        if (path.equals(SLASH)) {
            return new String[] { "wikis" };
        }

        if (path.equals("/wikis")) {
            if (onlyTestIfExists) {
                return null;
            }

            String wikisRestURL = command.getUrl() + "/rest/wikis";
            Element root = getRootOfRestDocument(wikisRestURL);

            return root.selectNodes("/xwiki:wikis/xwiki:wiki/xwiki:id").stream()
                .map(Node::getText)
                .toArray(String[]::new);
        }

        // Match pattern: /^/wikis/([^/]+)/spaces((?:/[^/]+/spaces)*)$/
        Matcher spaceListMatch = SPACE_LIST_PATTERN.matcher(path);
        if (spaceListMatch.matches()) {
            if (onlyTestIfExists) {
                return null;
            }

            List<String> spaces = new ArrayList<>();
            String wiki = spaceListMatch.group(1);
            String expectedSpace = FSDirUtils.getSpaceFromPathPart(spaceListMatch.group(2));

            String spacesRestURL = command.getUrl() + "/rest/wikis/" + Utils.encodeURLPart(wiki) + "/spaces";
            Element spacesRoot = getRootOfRestDocument(spacesRestURL);
            for (Node node : spacesRoot.selectNodes("/xwiki:spaces/xwiki:space")) {
                String id = node.selectSingleNode("xwiki:id").getText();
                String name = node.selectSingleNode("xwiki:name").getText();
                if (expectedSpace.isEmpty()) {
                    if (Objects.equals(id, wiki + ':' + name)) {
                        spaces.add(name);
                    }
                } else if (id.equals(wiki + ':' + expectedSpace + '.' + name)) {
                    spaces.add(name);
                }
            }
            return spaces.toArray(new String[0]);
        }

        // Match contents of the root of the wiki directory of every wiki
        if (WIKI_DIRECTORY_CONTENTS_PATTERN.matcher(path).matches()) {
            return new String[] { DIR_NAME_SPACES, "classes"/*, "wiki.xml"*/ };
        }

        // Match pattern: /^/wikis/(?:[^/]+)/classes$/
        if (CLASSES_PATTERN.matcher(path).matches()) {
            String classesRestURL = command.getUrl() + URL_PART_REST + FSDirUtils.escapeURLWithSlashes(path);
            return getRootOfRestDocument(classesRestURL)
                .selectNodes("/xwiki:classes/xwiki:class/xwiki:id")
                .stream()
                .map(Node::getText)
                .toArray(String[]::new);
        }

        if (SPACE_CONTENT_PATTERN.matcher(path).matches()) {
            return new String[] { DIR_NAME_SPACES, DIR_NAME_PAGES/*, "space.xml"*/ };
        }

        if (PAGES_DIRECTORY_PATTERN.matcher(path).matches()) {
            if (onlyTestIfExists) {
                return null;
            }
            String pagesRestURL = command.getUrl() + URL_PART_REST + FSDirUtils.escapeURLWithSlashes(path);
            Element root = getRootOfRestDocument(pagesRestURL);
            return root.selectNodes("/xwiki:pages/xwiki:pageSummary/xwiki:name")
                .stream()
                .map(Node::getText)
                .toArray(String[]::new);
        }

        if (SINGLE_PAGE_DIRECTORY_PATTERN.matcher(path).matches()) {
            return new String[] { "attachments", "class", "objects", /*"page.xml", */DIR_NAME_CONTENT, "content.xwiki",
                "title" };
        }

        if (ATTACHMENTS_DIRECTORY_PATTERN.matcher(path).matches()) {
            if (onlyTestIfExists) {
                return null;
            }
            String attachmentsRestURL = command.getUrl() + URL_PART_REST + FSDirUtils.escapeURLWithSlashes(path);
            Element root = getRootOfRestDocument(attachmentsRestURL);
            return root.selectNodes("/xwiki:attachments/xwiki:attachment/xwiki:name")
                .stream()
                .map(Node::getText)
                .toArray(String[]::new);
        }

        if (SINGLE_CLASS_DIRECTORY_PATTERN.matcher(path).matches()) {
            return new String[] {/*"class.xml", */DIR_NAME_PROPERTIES };
        }

        if (OBJECTS_DIRECTORY_PATTERN.matcher(path).matches()) {
            if (onlyTestIfExists) {
                return null;
            }
            String objectsRestURL = command.getUrl() + URL_PART_REST + FSDirUtils.escapeURLWithSlashes(path);
            Element root = getRootOfRestDocument(objectsRestURL);
            return root.selectNodes("/xwiki:objects/xwiki:objectSummary/xwiki:className")
                .stream()
                .map(Node::getText)
                .distinct()
                .toArray(String[]::new);
        }

        Matcher objectInstancesMatcher = OBJECT_INSTANCES_PATTERN.matcher(path);
        if (objectInstancesMatcher.matches()) {
            if (onlyTestIfExists) {
                return null;
            }
            String objectsRestURL =
                command.getUrl() + URL_PART_REST + FSDirUtils.escapeURLWithSlashes(objectInstancesMatcher.group(1));
            Element root = getRootOfRestDocument(objectsRestURL);
            return root.selectNodes(
                    String.format("/xwiki:objects/xwiki:objectSummary[xwiki:className/text() = %s]/xwiki:number",
                        Utils.escapeXPathString(objectInstancesMatcher.group(2))))
                .stream()
                .map(Node::getText)
                .toArray(String[]::new);
        }

        if (OBJECT_CONTENT_PATTERN.matcher(path).matches()) {
            return new String[] { DIR_NAME_PROPERTIES/*, "object.xml"*/ };
        }

        Matcher propertiesDirectoryMatcher = PROPERTIES_DIRECTORY_PATTERN.matcher(path);
        if (propertiesDirectoryMatcher.matches()) {
            if (onlyTestIfExists) {
                return null;
            }
            List<String> properties = new ArrayList<>();
            String objectRestURL =
                command.getUrl() + URL_PART_REST + FSDirUtils.escapeURLWithSlashes(propertiesDirectoryMatcher.group(1));
            Element root = getRootOfRestDocument(objectRestURL);
            for (Node node : root.selectNodes("/xwiki:object/xwiki:property/@name")) {
                String name = node.getText();
                String className = propertiesDirectoryMatcher.group(2);

                if (name.equals("code")) {
                    if (className.equals("XWiki.StyleSheetExtension")) {
                        properties.add(name + ".less");
                    } else if (className.equals("XWiki.JavaScriptExtension")) {
                        properties.add(name + ".js");
                    }
                } else if (name.equals("script_content") && className.equals("XWiki.ScriptComponentClass")) {
                    Node scriptLanguage = root.selectSingleNode(
                        "/xwiki:object/xwiki:property[@name = 'script_language']/xwiki:value");
                    if (scriptLanguage != null) {
                        properties.add(name + '.' + extFromLanguageName(scriptLanguage.getText()));
                    }
                } else if (name.equals(DIR_NAME_CONTENT) && className.equals("XWiki.XWikiSkinFileOverrideClass")) {
                    Node templatePath = root.selectSingleNode(
                        "/xwiki:object/xwiki:property[@name = 'path']/xwiki:value");
                    if (templatePath != null) {
                        properties.add(name + ".vm");
                    }
                }

                properties.add(name);
            }

            return properties.toArray(new String[0]);
        }

        Matcher matcher = CLASS_PROPERTIES_MATCHER.matcher(path);
        if (matcher.matches()) {
            if (onlyTestIfExists) {
                return null;
            }
            String fullName = FSDirUtils.getSpaceFromPathPart(matcher.group(2)) + '.' + matcher.group(3);
            Element root = getRootOfRestDocument(command.getUrl() + URL_PART_REST
                + FSDirUtils.escapeURLWithSlashes(matcher.group(1)) + URL_PART_CLASSES + Utils.encodeURLPart(fullName));
            return root.selectNodes("/xwiki:class/xwiki:property/@name")
                .stream()
                .map(Node::getText)
                .toArray(String[]::new);
        }

        matcher = CLASS_PROPERTY_MATCHER.matcher(path);
        if (matcher.find()) {
            String fullName = FSDirUtils.getSpaceFromPathPart(matcher.group(2)) + '.' + matcher.group(3);
            Element root = getRootOfRestDocument(command.getUrl() + URL_PART_REST
                + FSDirUtils.escapeURLWithSlashes(matcher.group(1)) + URL_PART_CLASSES + Utils.encodeURLPart(fullName));
            Element property = (Element) root.selectSingleNode(String.format("/xwiki:class/xwiki:property[name = %s]",
                Utils.escapeXPathString(matcher.group(4))));
            if (property != null) {
                if (onlyTestIfExists) {
                    return null;
                }

                List<String> attributes = new ArrayList<>();

                for (Node attributeName : property.selectNodes("./attribute/@name")) {
                    String name = attributeName.getStringValue();
                    attributes.add(name);
                    if (name.equals("customDisplay")) {
                        attributes.add(name + ".xwiki");
                    }
                }

                return attributes.toArray(new String[0]);
            }
        }

        throw new DocException("Unknown directory");
    }

    @Override
    public int open(String path, FuseFileInfo fi)
    {
        return 0;
    }

    @Override
    public int read(String path, Pointer buf, long size, long offset, FuseFileInfo fi)
    {
        byte[] value = getValue(path);
        if (value == null) {
            return -ErrorCodes.ENOENT();
        }

        if (offset < value.length) {
            int sizeToWrite = (int) Math.min(size, value.length - offset);
            buf.put(0, value, (int) offset, sizeToWrite);
            return sizeToWrite;
        }

        return 0;
    }

    @Override
    public int write(String path, Pointer buf, long size, long offset, FuseFileInfo fi)
    {
        byte[] content = getValue(path);

        if (content == null) {
            return -ErrorCodes.ENOENT();
        }

        // Remove the part of the old content after the offset if there is any.
        if (offset < content.length) {
            content = Arrays.copyOf(content, (int) offset);
        }

        // Append the new content.
        byte[] newContent = new byte[(int) (offset + size)];
        System.arraycopy(content, 0, newContent, 0, content.length);
        buf.get(0, newContent, (int) offset, (int) size);
        int bytesWritten = putValue(path, newContent);
        return Math.min(bytesWritten, (int) size);
    }

    @Override
    public int truncate(String path, long size)
    {
        byte[] content = getValue(path);
        if (content == null) {
            return -ErrorCodes.ENOENT();
        }

        if (size < content.length) {
            byte[] newContent = Arrays.copyOf(content, (int) size);
            return putValue(path, newContent);
        }

        return content.length;
    }

    private String pageToSpacesAndPagesRESTURLPart(String page)
    {
        // TODO: handle \.
        long lastDot = page.lastIndexOf('.');
        if (lastDot == -1) {
            return URL_PART_SPACES + page;
        }

        return URL_PART_SPACES + page.substring(0, (int) lastDot).replace(DOT, URL_PART_SPACES)
            + "/pages/" + page.substring((int) (lastDot + 1));
    }

    private String extFromLanguageName(String scriptLanguage)
    {
        return switch (scriptLanguage) {
            case "ruby" -> "rb";
            case "velocity" -> "vm";
            case "python" -> "py";
            // php, groovy
            default -> scriptLanguage;
        };
    }

    private Element getRootOfRestDocument(String wikisRestURL) throws DocException
    {
        String s = Utils.httpGet(this.command, wikisRestURL).body();
        return Utils.parseXML(s).getRootElement();
    }

    private byte[] getValue(String path)
    {
        Pattern pagePattern = PAGES_PATTERN_MATCHER;
        Matcher pageMatcher = pagePattern.matcher(path);
        if (pageMatcher.find()) {
            String space = FSDirUtils.getSpaceFromPathPart(pageMatcher.group(2));
            String page = pageMatcher.group(3).replace(DOT, ESCAPED_DOT);

            try {
                MultipleDoc document = new MultipleDoc(command, pageMatcher.group(1), space + '.' + page);

                String remainingPath = path.substring(pageMatcher.end());

                Pattern propertyPattern = OBJECTS_PROPERTIES_PATTERN_MATCHER;
                Matcher propertyMatcher = propertyPattern.matcher(remainingPath);
                if (propertyMatcher.matches()) {
                    String className = propertyMatcher.group(1);
                    String objectNumber = propertyMatcher.group(2);
                    String propertyName = propertyMatcher.group(3);

                    return document.getValue(className, objectNumber, propertyName).getBytes(StandardCharsets.UTF_8);
                }

                if (remainingPath.equals(URL_PART_CONTENT)) {
                    return document.getContent().getBytes(StandardCharsets.UTF_8);
                }

                if (remainingPath.equals(URL_PART_TITLE)) {
                    return document.getTitle().getBytes(StandardCharsets.UTF_8);
                }

                /*
                Pattern classPropertyPattern = Pattern.compile("^/class/properties/([^/]+)/([^/]+)$");
                Matcher classPropertyMatcher = classPropertyPattern.matcher(path);
                if (classPropertyMatcher.matches()) {
                    String propertyName = classPropertyMatcher.group(1);
                    String attributeName = classPropertyMatcher.group(2);

                    return document.getClassAttribute(propertyName, attributeName).getBytes(StandardCharsets.UTF_8);
                }
                */

                Pattern attachmentPattern = ATTACHMENTS_PATTERN_MATCHER;
                Matcher attachmentMatcher = attachmentPattern.matcher(remainingPath);
                if (attachmentMatcher.matches()) {
                    /*
                    String attachmentName = attachmentMatcher.group(1);

                    return document.getAttachment(attachmentName).getBytes(StandardCharsets.UTF_8);
                     */
                    String attachmentName = attachmentMatcher.group(1);
                    return document.getAttachment(attachmentName);
                    /*
                    String attachmentURL = this.command.url + URL_PART_REST + FSDirUtils.escapeURLWithSlashes(path);
                    return Utils.httpGetBytes(this.command, attachmentURL).body();
                     */
                }
            } catch (DocException | IOException e) {
                if (command.isDebug()) {
                    e.printStackTrace();
                }
            }
        }

        return new byte[0];
    }

    private int putValue(String path, byte[] value)
    {
        Pattern pagePattern = PAGES_PATTERN_MATCHER;
        Matcher pageMatcher = pagePattern.matcher(path);
        if (pageMatcher.find()) {

            String space = FSDirUtils.getSpaceFromPathPart(pageMatcher.group(2));
            String page = pageMatcher.group(3).replace(DOT, ESCAPED_DOT);

            try {
                MultipleDoc document = new MultipleDoc(this.command, pageMatcher.group(1), space + '.' + page);

                String remainingPath = path.substring(pageMatcher.end());

                Pattern propertyPattern = OBJECTS_PROPERTIES_PATTERN_MATCHER;
                Matcher propertyMatcher = propertyPattern.matcher(remainingPath);
                if (propertyMatcher.matches()) {
                    String className = propertyMatcher.group(1);
                    String objectNumber = propertyMatcher.group(2);
                    String propertyName = propertyMatcher.group(3);
                    String stringValue = new String(value, StandardCharsets.UTF_8);

                    document.setValue(className, objectNumber, propertyName, stringValue);
                    document.save();
                    return value.length;
                }

                if (remainingPath.equals(URL_PART_CONTENT) || remainingPath.equals(URL_PART_TITLE)) {
                    String stringValue = new String(value, StandardCharsets.UTF_8);

                    if (remainingPath.equals(URL_PART_TITLE)) {
                        document.setTitle(stringValue.stripTrailing());
                    } else {
                        document.setContent(stringValue);
                    }
                    document.save();
                    return value.length;
                }

                /*
                Pattern classPropertyPattern = Pattern.compile("^/class/properties/([^/]+)/([^/]+)$");
                Matcher classPropertyMatcher = classPropertyPattern.matcher(path);
                if (classPropertyMatcher.matches()) {
                    String propertyName = classPropertyMatcher.group(1);
                    String attributeName = classPropertyMatcher.group(2);

                    return document.getClassAttribute(propertyName, attributeName).getBytes(StandardCharsets.UTF_8);
                }
                */

                Pattern attachmentPattern = ATTACHMENTS_PATTERN_MATCHER;
                Matcher attachmentMatcher = attachmentPattern.matcher(remainingPath);
                if (attachmentMatcher.matches()) {
                    /*
                    String attachmentName = attachmentMatcher.group(1);

                    return document.getAttachment(attachmentName).getBytes(StandardCharsets.UTF_8);
                     */
                    String attachmentName = attachmentMatcher.group(1);
                    document.setAttachment(attachmentName, value);
                    /*
                    String attachmentURL = this.command.url + URL_PART_REST + FSDirUtils.escapeURLWithSlashes(path);
                    Utils.httpPut(this.command, attachmentURL, value, "application/octet-stream");
                    */
                    return value.length;
                }
            } catch (DocException | IOException e) {
                if (command.isDebug()) {
                    e.printStackTrace();
                }
            }
        }

        return 0;
    }
}
