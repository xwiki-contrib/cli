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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.cli.document.MultipleDoc;
import org.xwiki.contrib.cli.document.element.MacroInstance;

import static java.lang.System.err;
import static java.lang.System.out;
import static org.xwiki.contrib.cli.Arguments.endArgs;
import static org.xwiki.contrib.cli.Arguments.parseArgs;

/**
 * Represent a command run by a user with all parameter which can be passed.
 *
 * @version $Id$
 */
public class Command
{
    private static final String LINE = "\n\u001B[32m-----\u001B[0m";
    private static final String EDIT_PREFIX_CONTENT = "content-";
    private static final String OBJECT_PROPERTY_NAME_CODE = "code";
    private static final String XWIKI_FILE_EXTENSION = ".xwiki";
    private static final String ERROR_COULD_NOT_SAVE_DOCUMENT = "Could not save document";

    private static final Logger LOGGER = LoggerFactory.getLogger(Command.class);

    private static final String HELP_TEXT = """
        xwiki-cli JAVA

        Actions:
            -h, --help               Show the help
            -c, --configuration PATH  Read this configuration file. One --parameter value pair or item per line.
                                      This overwrites previously passed parameters and is overwritten by following
                                      parameters
            --edit-page              Edit a complete XWiki document
            --get-content            Get the content of a XWiki document
            --set-content CONTENT    Set the content of a XWiki document
            --edit-content           Edit the content of a XWiki document with a text editor
            --edit-macro NAME[/NUMBER] Edit a macro in the content of a XWiki document or a property value
                                     with a text editor, where NUMBER is the nth macro with this name (0-indexed).
                                     If NUMBER is not given, 0 (the first macro) is assumed.
            --get-title              Get the title of a XWiki document
            --set-title TITLE        Set the title of a XWiki document
            --list-properties        List the document's properties,
                                     optionally from the given object
            --list-objects           List the document's objects,
                                     optionally from the given class
            --edit-property PROPERTY Edit the content of a given property with a text editor
            --get-property PROPERTY  Get the value of the given property,
                                     optionally from the given object
            --set-property PROPERTY  Set the value of the given property,
                                     optionally from the given object (see -v to give a value)
            --mount PATH             Mount a FUSE filesystem with the wiki contents at PATH
            --sync PATH              Sync data to PATH with content from maven repository.

        Parameters:
            --loglevel               Define the log level. Default warn.
            --print-xml              Print received XML code (for debugging)
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
            -property PROPERTY       Define the property to work on
            --read-from-xml FILE     Read the document from the given file
            --write-to-xml FILE      Write the document to the given file
            --xml-file FILE          Same as --write-to-xml FILE --read-from-xml FILE
            --write-to-mvn-repository DIR   Same as --write-to-xml but for a maven repository
            --sync-data-source DIR   Path to the maven repository
            -H 'Header-Name: Val'    Add a custom HTTP header (repeat to have several ones)
            -n, --new                Allow creation of a document using --edit-content (and no input file given)
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
                var editing = new Editing();
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                editing.editValue(cmd, doc.getContent(), EDIT_PREFIX_CONTENT, XWIKI_FILE_EXTENSION, newValue -> {
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

        EDIT_MACRO {
            @Override
            void run(Command cmd) throws Exception
            {
                if (cmd.macro == null) {
                    throw new Exception("Please provide --macro");
                }
                var editing = new Editing();
                var doc = new MultipleDoc(cmd, cmd.wiki, cmd.page);
                String macroContent = editing.getMacroContent(doc, cmd.objectClass, cmd.objectNumber, cmd.property,
                    MacroInstance.fromString(cmd.macro));
                String filePrefix = cmd.macro.replace('/', '-');
                String fileEx = Editing.getFileExtensionForMacroSpec(MacroInstance.fromString(cmd.macro));
                editing.editValue(cmd, macroContent, filePrefix, fileEx, newValue -> {
                    try {
                        editing.setMacro(doc, cmd.objectClass, cmd.objectNumber, cmd.property,
                            MacroInstance.fromString(cmd.macro), newValue);
                        doc.save();
                    } catch (Exception e) {
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
                        res += o.objectClass() + '/' + o.number() + '.' + p.name() + '=' + protectValue(p.value());
                    }
                }
                var editing = new Editing();
                res += "\n\ncontent=" + protectValue(doc.getContent());
                editing.editValue(cmd, res, EDIT_PREFIX_CONTENT, XWIKI_FILE_EXTENSION, newRes -> {
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
                var oClass = cmd.objectClass;
                if (Utils.isEmpty(oClass)) {
                    oClass = doc.getObjects(cmd.objectClass, cmd.objectNumber, cmd.property)
                        .stream().findFirst().get()
                        .objectClass();
                }

                String ext = Utils.present(cmd.fileExtension)
                    ? '.' + cmd.fileExtension
                    : cmd.getFileExtension(oClass, cmd.property);
                var editing = new Editing();
                editing.editValue(cmd, val.get(), "property-", ext, newValue -> {
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
                out.println(value(doc.getContent()));
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
                out.println(value(doc.getTitle()));
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
                out.println(value(doc.getValue(cmd.objectClass, cmd.objectNumber, cmd.property).orElse("empty")));
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
                    out.println(object.objectClass() + '/' + object.number());
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
                    fs.mount(Path.of(cmd.mountPath), true);
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
                    ds.doFirstSync();
                    ds.monitor();
                } catch (Exception e) {
                    LOGGER.error("Sync crashed", e);
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

    private Action action;
    private String wiki;
    private String page;
    private String macro;
    private String objectClass;
    private String objectNumber;
    private String property;
    private String value;
    private String editor;
    private boolean wikiReadonly;
    private boolean wikiWriteonly;
    private String outputFile;
    private String inputFile;
    private String xmlReadDir;
    private String xmlWriteDir;
    private Map<String, String> headers;
    private String url;
    private String user;
    private String pass;
    private String content;
    private String title;
    private String mountPath;
    private String syncPath;
    private String syncDataSource;
    private boolean printXML;
    private String fileExtension;
    private boolean debug;
    private boolean pom;
    private boolean acceptNewDocument;

    /**
     * {@return the log level}
     */
    public String logLevel()
    {
        return logLevel;
    }

    /**
     * @param logLevel the log level
     */
    public void setLogLevel(String logLevel)
    {
        this.logLevel = logLevel;
    }

    private String logLevel;

    /**
     * {@return the wiki ID}
     */
    public String wiki()
    {
        return wiki;
    }

    /**
     * @param wiki the wiki ID.
     */
    public void setWiki(String wiki)
    {
        this.wiki = wiki;
    }

    /**
     * {@return main action of the program}
     */
    public Action action()
    {
        return action;
    }

    /**
     * @param action main action of the program.
     */
    public void setAction(Action action)
    {
        this.action = action;
    }

    /**
     * {@return the page to edit/read.}
     */
    public String page()
    {
        return page;
    }

    /**
     * @param page the page to edit/read.
     */
    public void setPage(String page)
    {
        this.page = page;
    }

    /**
     * {@return the name of the targeted macro}
     */
    public String macro()
    {
        return macro;
    }

    /**
     * @param macro the name of the targetted macro
     */
    public void setMacro(String macro)
    {
        this.macro = macro;
    }

    /**
     * {@return the class of the object to edit/read.}
     */
    public String objectClass()
    {
        return objectClass;
    }

    /**
     * @param objectClass the class of the object to edit/read.
     */
    public void setObjectClass(String objectClass)
    {
        this.objectClass = objectClass;
    }

    /**
     * {@return the object number to edit/read.}
     */
    public String objectNumber()
    {
        return objectNumber;
    }

    /**
     * @param objectNumber the object number to edit/read.
     */
    public void setObjectNumber(String objectNumber)
    {
        this.objectNumber = objectNumber;
    }

    /**
     * {@return the property of the object to edit/read.}
     */
    public String property()
    {
        return property;
    }

    /**
     * @param property the property of the object to edit/read.
     */
    public void setProperty(String property)
    {
        this.property = property;
    }

    /**
     * @return the new value to set.
     */
    public String value()
    {
        return value;
    }

    /**
     * @param value the new value to set.
     */
    public void setValue(String value)
    {
        this.value = value;
    }

    /**
     * {@return the editor to use.}
     */
    public String editor()
    {
        return editor;
    }

    /**
     * @param editor the editor to use.
     */
    public void setEditor(String editor)
    {
        this.editor = editor;
    }

    /**
     * {@return only read on the wiki, all change won't take effect on the wiki.}
     */
    public boolean wikiReadonly()
    {
        return wikiReadonly;
    }

    /**
     * @param wikiReadonly only read on the wiki, all change won't take effect on the wiki.
     */
    public void setWikiReadonly(boolean wikiReadonly)
    {
        this.wikiReadonly = wikiReadonly;
    }

    /**
     * {@return only write on the wiki, so the source should come from somewhere else}.
     */
    public boolean wikiWriteonly()
    {
        return wikiWriteonly;
    }

    /**
     * @param wikiWriteonly only write on the wiki, so the source should come from somewhere else.
     */
    public void setWikiWriteonly(boolean wikiWriteonly)
    {
        this.wikiWriteonly = wikiWriteonly;
    }

    /**
     * {@return outputFile the XML file to write.}
     */
    public String outputFile()
    {
        return outputFile;
    }

    /**
     * @param outputFile the XML file to write.
     */
    public void setOutputFile(String outputFile)
    {
        this.outputFile = outputFile;
    }

    /**
     * {@return inputFile the XML file to read.}
     */
    public String inputFile()
    {
        return inputFile;
    }

    /**
     * @param inputFile the XML file to read.
     */
    public void setInputFile(String inputFile)
    {
        this.inputFile = inputFile;
    }

    /**
     * {@return Same as outputFile but for a full wiki directory.}
     */
    public String xmlReadDir()
    {
        return xmlReadDir;
    }

    /**
     * @param xmlReadDir Same as outputFile but for a full wiki directory.
     */
    public void setXmlReadDir(String xmlReadDir)
    {
        this.xmlReadDir = xmlReadDir;
    }

    /**
     * {@return Same as inputFile but for a full wiki directory.}
     */
    public String xmlWriteDir()
    {
        return xmlWriteDir;
    }

    /**
     * @param xmlWriteDir Same as inputFile but for a full wiki directory.
     */
    public void setXmlWriteDir(String xmlWriteDir)
    {
        this.xmlWriteDir = xmlWriteDir;
    }

    /**
     * {@return custom http HEADER to pass on the wiki requests.}
     */
    public Map<String, String> headers()
    {
        return headers;
    }

    /**
     * @param headers custom http HEADER to pass on the wiki requests.
     */
    public void setHeaders(Map<String, String> headers)
    {
        this.headers = headers;
    }

    /**
     * {@return the full url of the wiki.}
     */
    public String url()
    {
        return url;
    }

    /**
     * @param url the full url of the wiki.
     */
    public void setUrl(String url)
    {
        this.url = url;
    }

    /**
     * {@return user to authenticate to the wiki.}
     */
    public String user()
    {
        return user;
    }

    /**
     * @param user user to authenticate to the wiki.
     */
    public void setUser(String user)
    {
        this.user = user;
    }

    /**
     * {@return password to authenticate to the wiki.}
     */
    public String pass()
    {
        return pass;
    }

    /**
     * @param pass password to authenticate to the wiki.
     */
    public void setPass(String pass)
    {
        this.pass = pass;
    }

    /**
     * {@return content to set/get.}
     */
    public String content()
    {
        return content;
    }

    /**
     * @param content content to set/get.
     */
    public void setContent(String content)
    {
        this.content = content;
    }

    /**
     * {@return title to set/get.}
     */
    public String title()
    {
        return title;
    }

    /**
     * @param title title to set/get.
     */
    public void setTitle(String title)
    {
        this.title = title;
    }

    /**
     * {@return mountPath mount point for the FUSE filesystem.}
     */
    public String mountPath()
    {
        return mountPath;
    }

    /**
     * @param mountPath mount point for the FUSE filesystem.
     */
    public void setMountPath(String mountPath)
    {
        this.mountPath = mountPath;
    }

    /**
     * {@return syncPath target directory to sync all files.}
     */
    public String syncPath()
    {
        return syncPath;
    }

    /**
     * @param syncPath target directory to sync all files.
     */
    public void setSyncPath(String syncPath)
    {
        this.syncPath = syncPath;
    }

    /**
     * {@return source directory to ready all data for sync.}
     */
    public String syncDataSource()
    {
        return syncDataSource;
    }

    /**
     * @param syncDataSource source directory to ready all data for sync.
     */
    public void setSyncDataSource(String syncDataSource)
    {
        this.syncDataSource = syncDataSource;
    }

    /**
     * {@return mostly used for debug, show the full XML when we parse the XML file.}
     */
    public boolean printXML()
    {
        return printXML;
    }

    /**
     * @param printXML mostly used for debug, show the full XML when we parse the XML file.
     */
    public void setPrintXML(boolean printXML)
    {
        this.printXML = printXML;
    }

    /**
     * {@return add a specific extension to the temporary file.}
     */
    public String fileExtension()
    {
        return fileExtension;
    }

    /**
     * @param fileExtension add a specific extension to the temporary file.
     */
    public void setFileExtension(String fileExtension)
    {
        this.fileExtension = fileExtension;
    }

    /**
     * {@return add automatically a pom file to make easier the edition with an IDE.}
     */
    public boolean pom()
    {
        return pom;
    }

    /**
     * @param pom add automatically a pom file to make easier the edition with an IDE.
     */
    public void setPom(boolean pom)
    {
        this.pom = pom;
    }

    /**
     * {@return give the possibility to add new document.}
     */
    public boolean acceptNewDocument()
    {
        return acceptNewDocument;
    }

    /**
     * @param acceptNewDocument give the possibility to add new document.
     */
    public void setAcceptNewDocument(boolean acceptNewDocument)
    {
        this.acceptNewDocument = acceptNewDocument;
    }

    void print()
    {
        LOGGER.info("Action:        {}", action);
        LOGGER.info("Wiki:          {}", wiki);
        LOGGER.info("Page:          {}", page);
        LOGGER.info("Object Class:  {}", objectClass);
        LOGGER.info("Object Number: {}", objectNumber);
        LOGGER.info("Property:      {}", property);
        LOGGER.info("Wiki readonly: {}", wikiReadonly);
        LOGGER.info("Wiki writeonly:{}", wikiWriteonly);
        LOGGER.info("Input file:    {}", inputFile);
        LOGGER.info("Output file:   {}", outputFile);
        LOGGER.info("XML write dir: {}", xmlWriteDir);
        LOGGER.info("URL:           {}", url);
        LOGGER.info("User:          {}", user);
        LOGGER.info("Pass:          {}", given(pass));
        LOGGER.info("Content:       {}", given(content));
        LOGGER.info("Title:         {}", title);
        LOGGER.info("Accept New:    {}", acceptNewDocument);
        LOGGER.info("Mount Path:      {}", mountPath);
        LOGGER.info("Sync Path:       {}", syncPath);
        LOGGER.info("Sync data source:{}", syncDataSource);
        LOGGER.info("Used Doc URL:  {}", getDocURL());
        LOGGER.info("Log level:         {}",
            ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(Logger.ROOT_LOGGER_NAME).getLevel());
        LOGGER.info("printXML:   {}", printXML);
    }

    private static String value(String value)
    {
        if (value == null) {
            return LOGGER.isDebugEnabled() ? "(null)" : "";
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
