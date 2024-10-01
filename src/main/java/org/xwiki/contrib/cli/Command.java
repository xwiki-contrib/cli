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

import org.xwiki.contrib.cli.document.MultipleDoc;

import static java.lang.System.err;
import static java.lang.System.out;

public record Command(
    Action action,
    String wiki,
    String page,
    String objectClass,
    String objectNumber,
    String property,
    String value,
    String editor,
    boolean wikiReadonly,
    boolean wikiWriteonly,
    String outputFile,
    String inputFile,
    String xmlReadDir,
    String xmlWriteDir,
    Map<String, String> headers,
    String url,
    String user,
    String pass,
    String content,
    String title,
    String mountPath,
    String syncPath,
    String syncDataSource,
    boolean printXML,
    String fileExtension,
    boolean debug,
    boolean pom,
    boolean acceptNewDocument)
{
    private static final String LINE = "\n\u001B[32m-----\u001B[0m";

    private static final String EDIT_PREFIX_CONTENT = "content-";

    private static final String OBJECT_PROPERTY_NAME_CODE = "code";

    private static final String XWIKI_FILE_EXTENSION = ".xwiki";

    private static final String ERROR_COULD_NOT_SAVE_DOCUMENT = "Could not save document";

    private static final String HELP_TEXT = """
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
            --sync PATH              Sync data to PATH with content from maven repository.

        Parameters:
            --debug                  Enable debug mode (for now, more verbose logs)
            --print-xml              Print received XML code (for debugging)
            -b HOST[:PORT][/PATH]    Use this host and port to connect to XWiki.
            --editor EDITOR          Use this editor (necessary if environment variable EDITOR is not set)
            --pom                    Autocreate or reuse a XWiki maven project for autocompletion
            -p PAGE                  Specify the page (dotted notation)
            -u, --url URL            Specify the page's URL
            -w WIKI                  Specify the wiki
            --wiki-readonly          Don't write on the wiki
            --wiki-writeonly         Don't read from the wiki.
                                     Note that in this case you need to use an other source,
                                     generally the XML dir.
            -o CLASS[/NUMBER]        Specify the class and optionally the number of the object to consider
            -v VALUE                 The value to use
            --read-from-xml FILE     Read the document from the given file
            --write-to-xml FILE      Write the document to the given file
            --xml-file FILE          Same as --write-to-xml FILE --read-from-xml FILE
            --write-to-mvn-repository DIR   Same as --write-to-xml but for a maven repository
            --sync-data-source DIR   Path to the maven repository
            -H 'Header-Name: Val'    Add a custom HTTP header (repeat to have several ones)
            -n, --new                Allow creation of a document using --edit-content (and no input file given)
            -H 'Header-Name: Val'  Add a custom HTTP header (repeat to have several ones)
            --read-from-xml-dir DIR  Same as --read-from-xml but for a full wiki directory
            --write-to-xml-dir DIR   Same as --write-to-xml but for a full wiki directory
            --xml-dir DIR            Same as --read-from-xml-dir DIR --write-to-xml-dir DIR
            -H 'Header-Name: Val'    Add a custom HTTP header (repeat to have several ones)
            --ext EXT                Use this as a file extension when editing a file

        Authentication:
            --user USENAME
                The XWiki username to use.
            --pass PASS
                The XWiki user’s password.
        """.trim();

    enum Action
    {
        EDIT_CONTENT {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                Editing.editValue(cmd, doc.getContent(), EDIT_PREFIX_CONTENT, XWIKI_FILE_EXTENSION, newValue -> {
                    try {
                        doc.setContent(newValue);
                        doc.save();
                    } catch (DocException e) {
                        // FIXME we can't really print stuff here, it will mess up any terminal editor.
                        err.println(ERROR_COULD_NOT_SAVE_DOCUMENT);
                        e.printStackTrace();
                    }
                });
            }
        },
        EDIT_PAGE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                var objects = doc.getObjects(null, null, null);
                String res = "title=" + doc.getTitle() + "\n\n";
                for (var o : objects) {
                    for (var p : o.properties()) {
                        res += o.objectClass() + "/" + o.number() + "." + p.name() + "=" + protectValue(p.value());
                    }
                }
                res += "\n\ncontent=" + protectValue(doc.getContent());
                Editing.editValue(cmd, res, EDIT_PREFIX_CONTENT, XWIKI_FILE_EXTENSION, newRes -> {
                    try {
                        Editing.updateDocFromTextPage(doc, newRes);
                    } catch (DocException e) {
                        // FIXME we can't really print stuff here, it will mess up any terminal editor.
                        err.println(ERROR_COULD_NOT_SAVE_DOCUMENT);
                        e.printStackTrace();
                    }
                });
            }
        },
        EDIT_PROPERTY {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                var val = doc.getValue(cmd.objectClass, cmd.objectNumber, cmd.property);
                if (val.isEmpty()) {
                    throw new MessageForUserDocException("This property does not exist");
                }
                var objectClass = cmd.objectClass;
                if (Utils.isEmpty(objectClass)) {
                    objectClass = doc.getObjects(cmd.objectClass, cmd.objectNumber, cmd.property)
                        .stream().findFirst().get()
                        .objectClass();
                }

                String ext = Utils.present(cmd.fileExtension)
                    ? '.' + cmd.fileExtension
                    : cmd.getFileExtension(objectClass, cmd.property);

                Editing.editValue(cmd, val.get(), "property-", ext, newValue -> {
                    try {
                        doc.setValue(cmd.objectClass, cmd.objectNumber, cmd.property, newValue);
                        doc.save();
                    } catch (DocException e) {
                        // FIXME we can't really print stuff here, it will mess up any terminal editor.
                        err.println(ERROR_COULD_NOT_SAVE_DOCUMENT);
                        e.printStackTrace();
                    }
                });
            }
        },
        GET_CONTENT {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                out.println(value(cmd, doc.getContent()));
            }
        },
        SET_CONTENT {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                doc.setContent(cmd.content);
                doc.save();
            }
        },
        GET_TITLE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                out.println(value(cmd, doc.getTitle()));
            }
        },
        SET_TITLE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                doc.setTitle(cmd.title);
                doc.save();
            }
        },
        GET_PROPERTY_VALUE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                out.println(value(cmd, doc.getValue(cmd.objectClass, cmd.objectNumber, cmd.property).orElse("empty")));
            }
        },
        SET_PROPERTY_VALUE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
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
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                for (var object : doc.getObjects(cmd.objectClass, cmd.objectNumber, cmd.property)) {
                    out.println(object.objectClass() + "/" + object.number());
                }
            }
        },
        LIST_PROPERTIES {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                for (var obj : doc.getObjects(cmd.objectClass, cmd.objectNumber, cmd.property)) {
                    for (var prop : obj.properties()) {
                        var val = prop.value();
                        if (val == null) {
                            val = "\u001B[33m(missing value)\u001B[0m";
                        } else if (val.isEmpty()) {
                            val = "\u001B[32m(empty)\u001B[0m";
                        } else if (severalLines(val)) {
                            val = LINE + '\n' + val + LINE;
                        }
                        out.println(prop.name() + " = " + val);
                    }
                }
            }
        },
        MOUNT {
            @Override
            void run(Command cmd) throws Exception
            {
                XWikiFS fs = new XWikiFS(cmd);
                try {
                    fs.mount(Path.of(cmd.mountPath), true, cmd.debug);
                } finally {
                    fs.umount();
                }
            }
        },
        SYNC {
            @Override
            void run(Command cmd) throws Exception
            {
                XWikiDirSync ds = new XWikiDirSync(cmd);
                try {
                    ds.sync();
                    ds.monitor();
                } catch (Exception e) {
                    err.println(e);
                    e.printStackTrace();
                } finally {
                    // TODO
                }
            }
        },
        LIST_ATTACHMENTS {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                for (var attachment : doc.getAttachments()) {
                    out.println(attachment.name() + " (size: " + attachment.size() + ")");
                }
            }
        },
        HELP {
            @Override
            void run(Command cmd) throws Exception
            {
                out.println(HELP_TEXT);
            }
        };

        void run(Command cmd) throws Exception
        {
            err.println("No action was specified");
        }
    }

    void print()
    {
        out.println(""
            + "\nAction:        " + action
            + "\nWiki:          " + wiki
            + "\nPage:          " + page
            + "\nObject Class:  " + objectClass
            + "\nObject Number: " + objectNumber
            + "\nProperty:      " + property
            + "\nWiki readonly: " + wikiReadonly
            + "\nWiki writeonly:" + wikiWriteonly
            + "\nInput file:    " + inputFile
            + "\nOutput file:   " + outputFile
            + "\nXML write dir: " + xmlWriteDir
            + "\nURL:           " + url
            + "\nUser:          " + user
            + "\nPass:          " + given(pass)
            + "\nContent:       " + given(content)
            + "\nTitle:         " + title
            + "\nAccept New:    " + acceptNewDocument
            + "\nMount Path:      " + mountPath
            + "\nSync Path:       " + syncPath
            + "\nSync data source:" + syncDataSource
            + "\nUsed Doc URL:  " + getDocURL()
            + "\nDebug:         " + debug
            + "\n + printXML:   " + printXML);
    }

    private static String value(Command cmd, String value)
    {
        if (value == null) {
            return cmd.debug() ? "(null)" : "";
        }
        return value;
    }

    private static String protectValue(String value)
    {
        if (value.indexOf('\n') == -1) {
            return value + '\n';
        }

        var line = "----";

        while (value.contains(line)) {
            line += '-';
        }

        var lineWithNL = '\n' + line + '\n';

        return lineWithNL + value + lineWithNL + '\n';
    }

    private static boolean severalLines(String value)
    {
        var pos = value.indexOf('\n');
        return pos != -1 && value.indexOf('\n', pos) != -1;
    }

    private String getDocURL()
    {
        try {
            return Utils.getDocRestURLFromCommand(this, wiki, page, false);
        } catch (DocException e) {
            return "(N/A)";
        }
    }

    private String given(String v)
    {
        return "(" + (v == null ? "not " : "") + "given)";
    }

    // TODO replace with Utils.getScriptLangFromObjectInfo(...)
    private String getFileExtension(String objectClass, String property)
    {
        if (objectClass.equals("XWiki.StyleSheetExtension") && property.equals(OBJECT_PROPERTY_NAME_CODE)) {
            return ".less";
        }

        if (objectClass.equals("XWiki.JavaScriptExtension") && property.equals(OBJECT_PROPERTY_NAME_CODE)) {
            return ".js";
        }

        if (objectClass.equals("XWiki.XWikiSkinFileOverrideClass") && property.equals("content")) {
            return ".vm";
        }

        if (this.pom || (objectClass.equals("XWiki.ScriptComponentClass") && property.equals("script_content"))) {
            return ".groovy";
        }

        return XWIKI_FILE_EXTENSION;
    }
}
