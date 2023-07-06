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

import java.nio.file.Path;
import java.util.Map;

import static java.lang.System.out;

import java.io.File;
import java.nio.file.Files;

import static java.lang.System.err;

class Command
{
    private static final String LINE = "\n\u001B[32m-----\u001B[0m";

    enum Action
    {
        EDIT_CONTENT {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                var content = doc.getContent();
                var dir = Files.createTempDirectory("xwiki-cli");
                var dirFile = dir.toFile();
                var tmpFile = File.createTempFile("content-", ".xwiki", dirFile);
                Editing.editValue(cmd, content, dirFile, tmpFile, newValue -> {
                    try {
                        doc.setContent(newValue);
                        doc.save();
                    } catch (DocException e) {
                        err.println("Could not save document");
                        e.printStackTrace();
                    }
                });
            }
        },
        EDIT_PAGE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                var properties = doc.getProperties(null, null, null, true);
                String res = "title=" + doc.getTitle() + "\n\n";
                for (var p : properties.entrySet()) {
                    res += p.getKey() + "=" + protectValue(p.getValue());
                }
                res += "\n\ncontent=" + protectValue(doc.getContent());
                var dir = Files.createTempDirectory("xwiki-cli");
                var dirFile = dir.toFile();
                var tmpFile = File.createTempFile("content-", ".xwiki", dirFile);
                Editing.editValue(cmd, res, dirFile, tmpFile, newRes -> {
                    try {
                        final var len = newRes.length();
                        var curIndex = 0;
                        while (curIndex < len) {
                            final var eq = newRes.indexOf('=', curIndex);
                            if (eq == -1) {
                                // TODO handle unexpected garbage at the end
                                break;
                            }
                            final var prop = newRes.substring(curIndex, eq).trim();
                            final var valueStart = eq + 1;
                            String value = null;

                            if (valueStart >= len) {
                                // TODO handle unexpected end of file
                                break;
                            }

                            final var nextNL = newRes.indexOf('\n', valueStart);
                            var beforeNL = nextNL == -1 ? "" : newRes.substring(valueStart, nextNL);

                            if (nextNL == -1) {
                                value = newRes.substring(valueStart);
                                curIndex = len;
                            } else if (beforeNL.isBlank() && valueStart + 1 < len && newRes.charAt(valueStart + 1) == '-') {
                                final var lineEnd = newRes.indexOf('\n', valueStart + 1);
                                if (lineEnd == -1) {
                                    // TODO handle unexpected end of file
                                    break;
                                }
                                final var line = newRes.substring(valueStart, lineEnd + 1);
                                final var valueEnd = newRes.indexOf(line, lineEnd + 1);
                                if (valueEnd == -1) {
                                    // TODO handle missing closing line
                                    break;
                                }
                                value = newRes.substring(lineEnd + 1, valueEnd);
                                curIndex = valueEnd + line.length();
                            } else {
                                value = beforeNL;
                                curIndex = nextNL + 1;
                            }

                            switch (prop) {
                                case "#" -> { /* Intentionally left blank */ }
                                case "content" -> doc.setContent(value);
                                case "title" -> doc.setTitle(value);
                                default -> {
                                    var dot = prop.lastIndexOf('.');
                                    if (dot < 1) {
                                        // TODO handle missing dot, or a dot at the start of the line
                                        continue;
                                    }
                                    var objectSpec = prop.substring(0, dot);

                                    var slash = objectSpec.indexOf('/');
                                    if (slash < 1) {
                                        // TODO handle missing /, or at the start of the line
                                        continue;
                                    }
                                    var objectClass = objectSpec.substring(0, slash);
                                    var objectNumber = objectSpec.substring(slash + 1);
                                    var propertyName = prop.substring(dot + 1);
                                    doc.setValue(objectClass, objectNumber, propertyName, value);
                                }
                            }
                        }
                        doc.save();
                    } catch (DocException e) {
                        err.println("Could not save document");
                        e.printStackTrace();
                    }
                });
            }
        },
        EDIT_PROPERTY {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                var val = doc.getValue(cmd.objectClass, cmd.objectNumber, cmd.property);
                if (val == null) {
                    throw new MessageForUserDocException("This property does not exist");
                }
                var dir = Files.createTempDirectory("xwiki-cli");
                var dirFile = dir.toFile();
                var objectClass = cmd.objectClass;
                if (Utils.isEmpty(objectClass)) {
                    objectClass = doc.getObjects(cmd.objectClass, cmd.objectNumber, cmd.property)
                        .stream().findFirst().get()
                        .split("/")[0];
                }
                var tmpFile = File.createTempFile("property-", getFileExtension(objectClass, cmd.property), dirFile);
                Editing.editValue(cmd, val, dirFile, tmpFile, newValue -> {
                    try {
                        doc.setValue(cmd.objectClass, cmd.objectNumber, cmd.property, newValue);
                        doc.save();
                    } catch (DocException e) {
                        err.println("Could not save document");
                        e.printStackTrace();
                    }
                });
            }
        },
        GET_CONTENT {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                out.println(value(cmd, doc.getContent()));
            }
        },
        SET_CONTENT {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                doc.setContent(cmd.content);
                doc.save();
            }
        },
        GET_TITLE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                out.println(value(cmd, doc.getTitle()));
            }
        },
        SET_TITLE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                doc.setTitle(cmd.title);
                doc.save();
            }
        },
        GET_PROPERTY_VALUE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                out.println(value(cmd, doc.getValue(cmd.objectClass, cmd.objectNumber, cmd.property)));
            }
        },
        SET_PROPERTY_VALUE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                if (cmd.value == null) {
                    err.println("--set-property: please provide a value to set with -v VALUE");
                }
                doc.setValue(cmd.objectClass, cmd.objectNumber, cmd.property, cmd.value);
                doc.save();
            }
        },
        LIST_OBJECTS {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                for (var object : doc.getObjects(cmd.objectClass, cmd.objectNumber, cmd.property)) {
                    out.println(object);
                }
            }
        },
        LIST_PROPERTIES {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                for (var prop : doc.getProperties(cmd.objectClass, cmd.objectNumber, cmd.property, false).entrySet()) {
                    var val = prop.getValue();
                    if (val == null) {
                        val = "\u001B[33m(missing value)\u001B[0m";
                    } else if (val.isEmpty()) {
                        val = "\u001B[32m(empty)\u001B[0m";
                    } else if (severalLines(val)) {
                        val = LINE + "\n" + val + LINE;
                    }
                    out.println(prop.getKey() + " = " + val);
                }
            }
        },

        MOUNT {
            @Override
            void run(Command cmd) throws Exception
            {
                XWikiFS fs = new XWikiFS(cmd);
                try {
                    fs.mount(Path.of(cmd.mountPath), true, true);
                } finally {
                    fs.umount();
                }
            }
        },

        LIST_ATTACHMENTS {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                for (var attachment : doc.getAttachments()) {
                    out.println(attachment.name + " (size: " + attachment.size + ")");
                }
            }
        },
        HELP {
            @Override
            void run(Command cmd) throws Exception
            {
                out.println(
                    """
                    xwiki-cli JAVA

                    Actions:
                        --help                   Show the help
                        --edit-page              Edit a complete XWiki document
                        --get-content            Get the content of a XWiki document
                        --set-content CONTENT    Set the content of a XWiki document
                        --edit-content           Edit the content of a XWiki document with a text editor
                        --get-title              Get the title of a XWiki document
                        --set-title TITLE        Set the title of a XWiki document
                        --list-properties        List the document's properties,
                                                 optionally from the given object
                        --list-objects           List the document's objects,
                                                 optionally from the given class
                        --edit-content PROPERTY  Edit the content of a given property with a text editor
                        --get-property PROPERTY  Get the value of the given property,
                                                 optionally from the given object
                        --set-property PROPERTY  Set the value of the given property,
                                                 optionally from the given object (see -v to give a value)
                        --mount PATH             Mount a FUSE filesystem with the wiki contents at PATH

                    Parameters:
                        --debug                Enable debug mode (for now, more verbose logs)
                        --print-xml            Print received XML code (for debugging)
                        -b HOST[:PORT][/PATH]  Use this host and port to connect to XWiki.
                        --editor EDITOR        Use this editor (necessary if environnement variable EDITOR is not set)
                        -p PAGE                Specify the page (dotted notation)
                        -u, --url URL          Specify the page's URL
                        -w WIKI                Specify the wiki
                        -o CLASS[/NUMBER]      Specify the class and optionally the number of the object to consider
                        -v VALUE               The value to use
                        --xml-file FILE        Same as --write-to-xml FILE --read-from-xml FILE
                        --read-from-xml FILE   Read the document from the given file
                        --write-to-xml FILE    Write the document to the given file
                        -H 'Header-Name: Val'  Add a custom HTTP header (repeat to have several ones)

                    Authentication:
                        --user USENAME
                            The XWiki username to use.
                        --pass PASS
                            The XWiki user’s password.
                    """.trim());
            }
        };

        void run(Command cmd) throws Exception
        {
            err.println("No action was specified");
        }
    }

    public Action action;
    public String base;
    public String wiki;
    public String page;
    public String objectClass;
    public String objectNumber;
    public String property;
    public String value;
    public String editor;
    public String outputFile;
    public String inputFile;
    public Map<String, String> headers;
    public String url;
    public String user;
    public String pass;
    public String content;
    public String title;
    public String mountPath;

    public boolean printXML;
    public boolean debug;

    void print()
    {
        out.println(""
            + "\nAction:        " + action
            + "\nBase:          " + base
            + "\nWiki:          " + wiki
            + "\nPage:          " + page
            + "\nObject Class:  " + objectClass
            + "\nObject Number: " + objectNumber
            + "\nProperty:      " + property
            + "\nInput file:    " + inputFile
            + "\nOutput file:   " + outputFile
            + "\nURL:           " + url
            + "\nUser:          " + user
            + "\nPass:          " + given(pass)
            + "\nContent:       " + given(content)
            + "\nTitle:         " + title
            + "\nMount Path:    " + mountPath
            + "\nUsed Doc URL:  " + getDocURL(false)
            + "\nDebug:         " + debug
            + "\n + printXML:   " + printXML);
    }

    private static String value(Command cmd, String value)
    {
        if (value == null) {
            return cmd.debug ? "(null)" : "";
        }
        return value;
    }

    private static String protectValue(String value)
    {
        if (value.indexOf('\n') == -1) {
            return value + "\n";
        }

        var line = "----";

        while (value.contains(line)) {
            line += "-";
        }

        var lineWithNL = "\n" + line + "\n";

        return lineWithNL + value + lineWithNL + "\n";
    }

    private static boolean severalLines(String value)
    {
        var pos = value.indexOf('\n');
        return pos != -1 && value.indexOf('\n', pos) != -1;
    }

    private String getDocURL(boolean withObjects)
    {
        try {
            return Utils.getDocRestURLFromCommand(this, withObjects);
        } catch (DocException e) {
            return "(N/A)";
        }
    }

    private String given(String v)
    {
        return "(" + (v == null ? "not " : "") + "given)";
    }

    private static String getFileExtension(String objectClass, String property)
    {
        if (objectClass.equals("XWiki.StyleSheetExtension") && property.equals("code")) {
            return ".less";
        } else if (objectClass.equals("XWiki.JavaScriptExtension") && property.equals("code")) {
            return ".js";
        } else {
            return ".xwiki";
        }
    }
}
