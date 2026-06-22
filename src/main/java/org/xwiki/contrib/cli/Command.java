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

import java.io.Console;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.cli.document.MultipleDoc;
import org.xwiki.contrib.cli.document.element.MacroInstance;
import org.xwiki.contrib.cli.document.element.ObjectInfo;

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
        Actions:
            -h, --help                   Show the help
            -c, --configuration <PATH>   Read this configuration file. One --parameter value pair or item per line.
                                         This overwrites previously passed parameters and is overwritten by following
                                         parameters
            --repl, --interactive        Run in interactive mode

            --edit-page                  Edit a complete XWiki document
            --get-content                Get the content of a XWiki document
            --set-content <CONTENT>      Set the content of a XWiki document
            --edit-content               Edit the content of a XWiki document with a text editor
            --edit-macro <NAME[/NUMBER]> Edit a macro in the content of a XWiki document or a property value
                                         with a text editor, where NUMBER is the nth macro with this name (0-indexed).
                                         If NUMBER is not given, 0 (the first macro) is assumed.
            --get-title                  Get the title of a XWiki document
            --set-title <TITLE>          Set the title of a XWiki document
            --list-properties            List the document's properties, optionally from the given object
            --list-objects               List the document's objects, optionally from the given class
            --get-property <PROPERTY>    Get the value of the given property, optionally from the given object
            --set-property <PROPERTY>    Set the value of the given property, optionally from the given object
                                         (see -v to provide a value)
            --edit-property <PROPERTY>   Edit the content of a given property with a text editor
            --list-attachments           List attachments of a given XWiki document
            --mount <PATH>               Mount a FUSE filesystem with the wiki contents at PATH
            --edit-tree                  Provide a working directory from which the instance and the maven repository
                                         can be updated
            --push-page <REFERENCE>      Push a page from the maven repository to a XWiki instance
            --pull-page <REFERENCE>      Pull a page from a XWiki instance to the maven repository
            --push-all-pages             Push all pages from the maven repository to the XWiki instance
            --pull-all-pages             Update all pages present in the %aven repository from the XWiki instance
        
        General Parameters:
            --log-level                  Define the log level. Default warn.
            --print-xml                  Print received XML code (for debugging)

            -H 'Header-Name: Val'        Add a custom HTTP header (repeat to have several ones)
            -u, --url <URL>              Specify the page's URL
            -w <WIKI>                    Specify the wiki. Default is 'xwiki' (the main wiki).

        Parameters for single field or page edition:
            --editor <EDITOR>            Use this editor (necessary if environment variable EDITOR is not set)
            -p <PAGE>                    Specify the page (dotted notation)
            -o <CLASS[/NUMBER]>          Specify the class and optionally the number of the object to consider
            -v <VALUE>                   The value to use
            -property <PROPERTY>         Define the property to work on
            --read-from-xml <FILE>       Read the document from the given file
            --write-to-xml <FILE>        Write the document to the given file
            --xml-file FILE              Same as --write-to-xml FILE --read-from-xml FILE
            -n, --new                    Allow creation of a document using --edit-content (and no input file given)
            --ext <EXT>                  Use this as a file extension when editing a file

        Parameters for --edit-tree:
            --no-write-wiki              Don't read from the wiki instance
            --no-mvn-repo-write          Don't write on the maven repository
            --first-sync-from <mvn|wiki> Specify the source for the working directory generation.
                                          - 'mvn' will use the maven repository.
                                          - 'wiki' will use the XWiki instance.
                                         The default is 'mvn'.

            --working-directory <DIR>    Directory which will have the XFF tree and the auto created Maven project
            --mvn-repo <DIR>             Path to the maven repository
            --spaces <SPACE>             The XWiki space to use as the source. Note this parameter could be provided
                                         multiple times.

        Authentication:
            --user <USERNAME>            The XWiki username to use.
            --pass <PASS>                The XWiki user’s password.
        """;

    enum Action
    {
        REPL {
            @Override
            void run(Command cmd) throws Exception
            {
                // We don't want the action to be set, the real action shall be defined by the user in during the
                // interactive session.
                cmd.setAction(null);

                Console console = System.console();
                console.printf("Welcome to XWiki-CLI's interactive session!\n");
                printPrelude();

                String line;
                while ( (line = console.readLine("> ")) != null) {
                    switch (line.trim()) {
                        case "run":
                            Arguments.endArgs(cmd);
                            Main.runCommand(cmd);
                            break;
                        case "guide":
                            guide(console, cmd);
                            return;
                        case "help", "-h", "--help":
                            console.printf(HELP_TEXT);
                            break;
                        case "":
                            break;
                        default:
                            try {
                                Arguments.parseArgs(line.split("\\s", 2), cmd);
                            } catch (CommandException e) {
                                console.printf("%s\n", e.getMessage());
                            }
                    }
                }
            }

            private static void printPrelude()
            {
                System.console().printf("""
                    If you want to be guided, type 'guide'.
                    Otherwise, you can type parameters to use, one for each line, like this:
    
                    --paramater
    
                    or
    
                    --paramater VALUE
    
                    Type 'help' for parameter usage.
                    And then when you are ready to run your action, type 'run'.
                    (Note: we don't yet have proper command editing in the interactive mode, but you can use something
                    like rlwrap for a better command editing experience.)
                    """);
            }

            private void guide(Console console, Command cmd) throws Exception
            {
                String origUser = cmd.user();
                String origPass = cmd.pass();
                String origURL = cmd.url();
                String origMvnRepo = cmd.mvnRepo();
                String origCliDir = cmd.workingDirectory();

                // TODO: allow working without a maven project
                console.printf("First, we need to define a few things like which Maven project to work on "
                   + "and which XWiki instance to use.\n");
                askMavenDirectory(console, cmd);
                askInstance(console, cmd);
                askWhatToDo(console, cmd);
                askWhetherToSave(console, cmd, origURL, origUser, origPass, origMvnRepo, origCliDir);
                Arguments.endArgs(cmd);
                Main.runCommand(cmd);
            }

            private void askWhatToDo(Console console, Command cmd)
            {
                String answer = getAnswerWithDefault(console, "1", """
                        XWiki-cli provides different ways of working. This guide lets you:
                        
                        [1] Use a temporary working directory from which you can edit everything,
                            and your Maven repository as well as the XWiki instance will be updated in real time
                        [2] Edit a single value (page, object property, content, macro).
                            This will open an editor and your Maven repository as well as the XWiki instance will be
                            updated each time you save the file opened in your editor
                        [3] Push a page present in the maven project to the XWiki instance
                        [4] Push all pages in the maven project to the XWiki instance
                        [5] Pull a page present from the XWiki instance to the maven project
                        [6] Pull (update) all pages present in the Maven project from the XWiki instance

                        What do you want to do?
                        """.trim());

                switch (answer.trim()) {
                    case "1":
                        askSyncDirectory(console, cmd);
                        cmd.setAction(EDIT_TREE);
                        break;
                    case "2":
                        askEditAction(console, cmd);
                        break;
                    case "3":
                        cmd.setAction(PUSH_PAGE);
                        askDocumentToEdit(console, cmd);
                        break;
                    case "4":
                        cmd.setAction(PUSH_ALL_PAGES);
                        break;
                    case "5":
                        cmd.setAction(PULL_PAGE);
                        askDocumentToEdit(console, cmd);
                        break;
                    case "6":
                        cmd.setAction(PULL_ALL_PAGES);
                        break;
                    default:
                        couldNotUnderstandAnswer(console);
                        askWhatToDo(console, cmd);
                }
            }

            private static void couldNotUnderstandAnswer(Console console)
            {
                console.printf("Could not understand this answer, let's retry.\n");
            }

            private void askEditAction(Console console, Command cmd)
            {
                String answer = getAnswerWithDefault(console, "1", """
                        You can edit:
                        
                        [1] A whole document
                        [2] The content of a document
                        [3] The macro in a document
                        [4] The value of a property
                        
                        What do you want to do?
                        """.trim());
                switch (answer.trim()) {
                    case "1":
                        cmd.setAction(EDIT_PAGE);
                        askDocumentToEdit(console, cmd);
                        break;
                    case "2":
                        cmd.setAction(EDIT_CONTENT);
                        askDocumentToEdit(console, cmd);
                        if (!askYesNo(console,
                                "Is the macro in the content of the document? (as opposed to in a property)")
                        ) {
                            askPropertyToEdit(console, cmd);
                        }
                        break;
                    case "3":
                        cmd.setAction(EDIT_MACRO);
                        askDocumentToEdit(console, cmd);
                        break;
                    case "4":
                        cmd.setAction(EDIT_PROPERTY);
                        askDocumentToEdit(console, cmd);
                        askPropertyToEdit(console, cmd);
                        break;
                    default:
                        couldNotUnderstandAnswer(console);
                        askEditAction(console, cmd);
                }
            }

            private void askPropertyToEdit(Console console, Command cmd)
            {
                String propertyName = askNonEmptyThing(console,
                        "Please give the name of the property to edit (e.g. script_content)");
                cmd.setProperty(propertyName);
                String objectClass = console.readLine(
                        "You can specify the class name of the object to edit (or leave empty for auto-detection): ");
                if (StringUtils.isNotEmpty(objectClass)) {
                    cmd.setObjectClass(objectClass);
                    String objectNumber = console.readLine(
                            "You can specify the object number (or leave empty to use the first available): ");
                    if (StringUtils.isNotEmpty(objectNumber)) {
                        cmd.setObjectNumber(objectNumber);
                    }
                }
            }

            private void askDocumentToEdit(Console console, Command cmd)
            {
                String ref = askNonEmptyThing(console,
                    "Please give the reference of the document to modify "
                            + "(e.g. Main.WebHome) or "
                            + "the path to the XML file of the document "
                            + "(e.g. /home/user/Work/XWiki/my-project-ui/src/main/resources/Main/WebHome.xml)\n> ");

                if (new File(ref).exists()) {
                    cmd.setInputFile(ref);
                    cmd.setOutputFile(ref);
                } else {
                    cmd.setPage(ref);
                }
            }

            private static String askNonEmptyThing(Console console, String msg)
            {
                String answer = "";
                while (answer.isEmpty()) {
                    answer = console.readLine(msg).trim();
                }
                return answer;
            }

            private void askWhetherToSave(Console console, Command cmd, String origURL, String origUser,
                  String origPass, String origMvnRepo, String origCliDir) throws IOException
            {
                File currentDirectory = new File("").getAbsoluteFile();
                File configFile = new File(currentDirectory, "xwikicli.config");
                String path = configFile.getAbsolutePath();
                if (!(Objects.equals(cmd.url(), origURL)
                        && Objects.equals(cmd.user(), origUser)
                        && Objects.equals(cmd.pass(), origPass)
                        && Objects.equals(cmd.mvnRepo(), origMvnRepo)
                        && Objects.equals(cmd.workingDirectory(), origCliDir)
                    ) && askYesNo(console,
                        "Do you want to save common parameters as configuration in %s for next time?", path)
                ) {
                    FileWriter fileWriter = new FileWriter(configFile);
                    try (PrintWriter printWriter = new PrintWriter(fileWriter)) {
                        printParameterNotEmpty(printWriter, "--url", cmd.url());
                        printParameterNotEmpty(printWriter, "--user", cmd.user());
                        printParameterNotEmpty(printWriter, "--pass", cmd.pass());
                        printParameterNotEmpty(printWriter, "--mvn-repo", cmd.mvnRepo());
                        printParameterNotEmpty(printWriter, "--working-directory", cmd.workingDirectory());
                    }
                }
            }

            private void printParameterNotEmpty(PrintWriter printWriter, String paramName, String value)
            {
                if (StringUtils.isNotEmpty(value)) {
                    printWriter.printf("%s %s\n", paramName, value);
                }
            }

            private void askInstance(Console console, Command cmd)
            {
                if (!askYesNo(console, "Do you want to work with an XWiki instance?")) {
                    cmd.setUrl(null);
                    return;
                }

                String defaultURL = ObjectUtils.firstNonNull(cmd.url(), "http://localhost:8080/xwiki");
                cmd.setUrl(getAnswerWithDefault(console, defaultURL, "Please provide the URL of your instance"));
                String defaultUser = ObjectUtils.firstNonNull(cmd.user(), "Admin");
                String defaultPassword = ObjectUtils.firstNonNull(cmd.pass(), "admin");

                cmd.setUser(getAnswerWithDefault(console, defaultUser, "Please provide the XWiki user to use"));
                cmd.setPass(getAnswerWithDefault(console, defaultPassword, "Please provide the user password"));
            }

            private void askSyncDirectory(Console console, Command cmd)
            {
                String userHome = System.getProperty("user.home");
                String mvnRepo = cmd.mvnRepo();
                File mvnRepoFile = new File(mvnRepo);
                String mvnRepoName = mvnRepoFile.getName();
                cmd.setWorkingDirectory(
                    getAnswerWithDefault(
                        console,
                        ObjectUtils.firstNonNull(cmd.workingDirectory(), userHome + "/Work/XWiki/cli/" + mvnRepoName),
                        "You will edit files in a 'sync' directory (following the XFF format).\n"
                            + "Where do you want to work?"));
            }

            private String getAnswerWithDefault(Console console, String def, String msg)
            {
                String answer = console.readLine(msg + " (Default: %s): ", def);
                if (answer.isEmpty()) {
                    answer = def;
                }
                return answer;
            }

            private void askMavenDirectory(Console console, Command cmd)
            {
                // We first check if we are in a maven project
                File currentDirectory = new File("").getAbsoluteFile();
                File projectDirectory;
                if (new File(currentDirectory, "pom.xml").exists()) {
                    projectDirectory =
                        askYesNo(console, "Do you want to work on the Maven project there? %s ", currentDirectory)
                            ? currentDirectory
                            : askProjectPath(console);
                } else {
                    projectDirectory = askProjectPath(console);
                }
                cmd.setMvnRepo(projectDirectory.getAbsolutePath());
            }

            private static File askProjectPath(Console console)
            {
                File projectDirectory;
                String path = console.readLine("Please provide the path to your project: ");
                while (true) {
                    projectDirectory = new File(path);
                    File pomFile = new File(projectDirectory, "pom.xml");
                    if (pomFile.exists()) {
                        return projectDirectory;
                    } else {
                        // TODO suggest creating a new Maven project?
                        path =
                            console.readLine("There's no pom.xml file here. Please provide the path to your project: ");
                    }
                }
            }

            private boolean askYesNo(Console console, String msg, Object... parameters)
            {
                String answer = console.readLine(msg + " [Y/n]: ", parameters).trim();
                return switch (answer) {
                    case "", "y", "Y", "yes", "YES" -> true;
                    default -> false;
                };
            }
        },
        EDIT_CONTENT {
            @Override
            void run(Command cmd) throws Exception
            {
                var editing = new Editing();
                var doc = new MultipleDoc(cmd);
                editing.editValue(cmd, doc.getContent(), EDIT_PREFIX_CONTENT, XWIKI_FILE_EXTENSION, newValue -> {
                    try {
                        doc.setContent(newValue);
                        doc.save();
                    } catch (DocException e) {
                        // FIXME we can't really print stuff here, it will mess up any terminal editor.
                        LOGGER.error(ERROR_COULD_NOT_SAVE_DOCUMENT, e);
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
                var doc = new MultipleDoc(cmd);
                String macroContent = editing.getMacroContent(doc, cmd.objectClass, cmd.objectNumber(), cmd.property,
                    MacroInstance.fromString(cmd.macro));
                String filePrefix = cmd.macro.replace('/', '-');
                String fileEx = Editing.getFileExtensionForMacroSpec(MacroInstance.fromString(cmd.macro));
                editing.editValue(cmd, macroContent, filePrefix, fileEx, newValue -> {
                    try {
                        editing.setMacro(doc, cmd.objectClass, cmd.objectNumber(), cmd.property,
                            MacroInstance.fromString(cmd.macro), newValue);
                        doc.save();
                    } catch (Exception e) {
                        // FIXME we can't really print stuff here, it will mess up any terminal editor.
                        LOGGER.error(ERROR_COULD_NOT_SAVE_DOCUMENT, e);
                    }
                });
            }
        },
        EDIT_PAGE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
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
                        LOGGER.error(ERROR_COULD_NOT_SAVE_DOCUMENT, e);
                    }
                });
            }
        },
        EDIT_PROPERTY {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                var val = doc.getValue(cmd.objectClass(), cmd.objectNumber(), cmd.property);
                if (val.isEmpty()) {
                    throw new MessageForUserDocException("This property does not exist");
                }
                var oClass = cmd.objectClass();
                if (StringUtils.isEmpty(oClass)) {
                    Optional<ObjectInfo> first = doc.getObjects(cmd.objectClass(), cmd.objectNumber(), cmd.property)
                                                         .stream().findFirst();
                    if (first.isPresent()) {
                        oClass = first.get().objectClass();
                    } else {
                        oClass = "";
                    }
                }

                String ext = StringUtils.isNotEmpty(cmd.fileExtension())
                    ? '.' + cmd.fileExtension()
                    : cmd.getFileExtension(oClass, cmd.property());
                var editing = new Editing();
                editing.editValue(cmd, val.get(), "property-", ext, newValue -> {
                    try {
                        doc.setValue(cmd.objectClass(), cmd.objectNumber(), cmd.property(), newValue);
                        doc.save();
                    } catch (DocException e) {
                        // FIXME we can't really print stuff here, it will mess up any terminal editor.
                        LOGGER.error(ERROR_COULD_NOT_SAVE_DOCUMENT, e);
                    }
                });
            }
        },
        GET_CONTENT {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                System.console().printf(value(doc.getContent()) + "\n");
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
                System.console().printf(value(doc.getTitle()) + "\n");
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
                System.console().printf(value(doc.getValue(cmd.objectClass, cmd.objectNumber(), cmd.property).orElse("empty")) + "\n");
            }
        },
        SET_PROPERTY_VALUE {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                if (cmd.value == null) {
                    LOGGER.error("--set-property: please provide a value to set with -v VALUE");
                }
                doc.setValue(cmd.objectClass, cmd.objectNumber(), cmd.property, cmd.value);
                doc.save();
            }
        },
        LIST_OBJECTS {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                for (var object : doc.getObjects(cmd.objectClass, cmd.objectNumber(), cmd.property)) {
                    System.console().printf(object.objectClass() + '/' + object.number() + "\n");
                }
            }
        },
        LIST_PROPERTIES {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                for (var obj : doc.getObjects(cmd.objectClass, cmd.objectNumber(), cmd.property)) {
                    for (var prop : obj.properties()) {
                        var val = prop.value();
                        if (val == null) {
                            val = "\u001B[33m(missing value)\u001B[0m";
                        } else if (val.isEmpty()) {
                            val = "\u001B[32m(empty)\u001B[0m";
                        } else if (severalLines(val)) {
                            val = LINE + '\n' + val + LINE;
                        }
                        System.console().printf(prop.name() + " = " + val + "\n");
                    }
                }
            }
        },
        MOUNT {
            @Override
            void run(Command cmd)
            {
                XWikiFS fs = new XWikiFS(cmd);
                try {
                    fs.mount(Path.of(cmd.mountPath()), true);
                } finally {
                    fs.umount();
                }
            }
        },
        EDIT_TREE {
            @Override
            void run(Command cmd)
            {
                LOGGER.info("Preparing the working directory at [{}] on Maven repository [{}]",
                        cmd.workingDirectory(), cmd.mvnRepo());
                XWikiDirAutoSync ds = new XWikiDirAutoSync(cmd);
                try {
                    ds.doFirstSync();
                    LOGGER.info("Monitoring the working directory for changes...");
                    ds.monitor();
                } catch (Exception e) {
                    LOGGER.error("Something wrong happened", e);
                } finally {
                    // TODO
                }
            }
        },
        PULL_PAGE {
            @Override
            void run(Command cmd) throws DocException, IOException
            {
                var pageSync = new PageSync(cmd);
                pageSync.pullPage();
            }
        },
        PUSH_PAGE {
            @Override
            void run(Command cmd) throws DocException, IOException
            {
                var pageSync = new PageSync(cmd);
                pageSync.pushPage();
            }
        },
        PULL_ALL_PAGES {
            @Override
            void run(Command cmd) throws DocException, IOException
            {
                var pageSync = new PageSync(cmd);
                pageSync.pullPages();
            }
        },
        PUSH_ALL_PAGES {
            @Override
            void run(Command cmd) throws DocException, IOException
            {
                var pageSync = new PageSync(cmd);
                pageSync.pushPages();
            }
        },
        LIST_ATTACHMENTS {
            @Override
            void run(Command cmd) throws Exception
            {
                var doc = new MultipleDoc(cmd);
                for (var attachment : doc.getAttachments()) {
                    System.console().printf(attachment.name() + " (size: " + attachment.size() + ")\n");
                }
            }
        },
        HELP {
            @Override
            void run(Command cmd)
            {
                System.console().printf("xwiki-cli JAVA\n\n%s", HELP_TEXT);
            }
        };

        void run(Command cmd) throws Exception
        {
            LOGGER.error("No action was specified");
        }
    }

    enum FirstSyncFrom
    {
        MVN,
        WIKI
    }

    private Action action;

    private String title;

    private String macro;

    private String mountPath;

    private String pushReference;

    private String pullReference;

    private String logLevel;

    private boolean printXML;

    private Map<String, String> headers;

    private String url;

    private String wiki = "xwiki";

    private String editor;

    private String page;

    private String objectClass;

    private String objectNumber;

    private String value;

    private String property;

    private String outputFile;

    private String inputFile;

    private String content;

    private boolean acceptNewDocument;

    private String fileExtension;

    private boolean noMvnRepoWrite;

    private boolean noWriteWiki;

    private FirstSyncFrom firstSyncFrom = FirstSyncFrom.MVN;

    private String cliDir;

    private String mvnRepo;

    private List<String> spaces = new ArrayList<>(5);

    private String user;

    private String pass;

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
     * @param macro the name of the targeted macro
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

    /**
     * {@return the document reference to push to the XWiki instance.}
     */
    public String pushReference()
    {
        return pushReference;
    }

    /**
     * @param pushReference the document reference to push to the XWiki instance.
     */
    public void setPushReference(String pushReference)
    {
        this.pushReference = pushReference;
    }

    /**
     * {@return the document reference to pull from the XWiki instance}
     */
    public String pullReference()
    {
        return pullReference;
    }

    /**
     * @param pullReference the document reference to pull from the XWiki instance.
     */
    public void setPullReference(String pullReference)
    {
        this.pullReference = pullReference;
    }

    /**
     * {@return true, if we don't want to write into the maven repos}
     */
    public boolean noMvnRepoWrite()
    {
        return noMvnRepoWrite;
    }

    /**
     * @param noMvnRepoWrite true, if we don't want to write into the maven repos.
     */
    public void setNoMvnRepoWrite(boolean noMvnRepoWrite)
    {
        this.noMvnRepoWrite = noMvnRepoWrite;
    }

    /**
     * {@return true, if we don't want to write into the XWiki instance.}
     */
    public boolean noWriteWiki()
    {
        return noWriteWiki;
    }

    /**
     * @param noWriteWiki true, if we don't want to write into the XWiki instance.
     */
    public void setNoWriteWiki(boolean noWriteWiki)
    {
        this.noWriteWiki = noWriteWiki;
    }

    public FirstSyncFrom firstSyncFrom()
    {
        return firstSyncFrom;
    }

    public void setFirstSyncFrom(FirstSyncFrom firstSyncFrom)
    {
        this.firstSyncFrom = firstSyncFrom;
    }

    /**
     * @return the directory where XWiki CLI will create a hierarchy which is easily editable.
     */
    public String workingDirectory()
    {
        return cliDir;
    }

    /**
     * @param cliDir the directory where XWiki CLI will create a hierarchy which is easily editable.
     */
    public void setWorkingDirectory(String cliDir)
    {
        this.cliDir = cliDir;
    }

    /**
     * {@return the maven repository where there are the XAR project. It's generally a git repository, but it's not
     *         mandatory.}
     */
    public String mvnRepo()
    {
        return mvnRepo;
    }

    /**
     * @param mvnRepo the maven repository where there are the XAR project. It's generally a git repository, but
     *     it's not mandatory.
     */
    public void setMvnRepo(String mvnRepo)
    {
        this.mvnRepo = mvnRepo;
    }

    public List<String> spaces()
    {
        return spaces;
    }

    void print()
    {
        // TODO log all parameters ??
        LOGGER.info("Action:        {}", action);
        LOGGER.info("Wiki:          {}", wiki);
        LOGGER.info("Page:          {}", page);
        LOGGER.info("Object Class:  {}", objectClass);
        LOGGER.info("Object Number: {}", objectNumber);
        LOGGER.info("Property:      {}", property);
        LOGGER.info("Input file:    {}", inputFile);
        LOGGER.info("Output file:   {}", outputFile);
        LOGGER.info("URL:           {}", url);
        LOGGER.info("User:          {}", user);
        LOGGER.info("Pass:          {}", given(pass));
        LOGGER.info("Content:       {}", given(content));
        LOGGER.info("Title:         {}", title);
        LOGGER.info("Accept New:    {}", acceptNewDocument);
        LOGGER.info("Mount Path:    {}", mountPath);
        LOGGER.info("Sync Path:     {}", cliDir);
        LOGGER.info("Used Doc URL:  {}", getDocURL());
        LOGGER.info("Log level:     {}",
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

        if (objectClass.equals("XWiki.ScriptComponentClass") && property.equals("script_content")) {
            return ".groovy";
        }

        return XWIKI_FILE_EXTENSION;
    }
}