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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static java.lang.System.err;
import static java.lang.System.out;

final class Main
{
    private static final Pattern HEADER_SPLIT_PATTERN = Pattern.compile("\\s*:\\s*");

    private Main()
    {
        throw new UnsupportedOperationException("Main cannot be instantiated");
    }

    public static void main(String[] args) throws Exception
    {
        Command cmd;
        try {
            cmd = parseArgs(args);
        } catch (CommandException e) {
            err.println(e.getMessage());
            return;
        }

        if (cmd.debug()) {
            cmd.print();
            out.println();
        }

        try {
            cmd.action().run(cmd);
        } catch (CancelledOperationDocException e) {
            out.println("Operation cancelled by the user.");
        } catch (MessageForUserDocException e) {
            out.println(e.getMessage());
        }
    }

    private static String getNextParameter(String[] args, int i) throws CommandException
    {
        if (i + 1 >= args.length) {
            throw new CommandException("Expected a parameter for " + args[i]);
        }
        return args[i + 1];
    }

    private static Command parseArgs(String[] args) throws CommandException
    {
        Command.Action action = null;
        String wiki = null;
        String page = null;
        String objectClass = null;
        String objectNumber = null;
        String property = null;
        String value = null;
        String editor = null;
        boolean wikiReadonly = false;
        boolean wikiWriteonly = false;
        String outputFile = null;
        String inputFile = null;
        String xmlReadDir = null;
        String xmlWriteDir = null;
        Map<String, String> headers = new HashMap<>();
        String url = null;
        String user = null;
        String pass = null;
        String content = null;
        String title = null;
        String mountPath = null;
        String syncPath = null;
        String syncDataSource = null;
        boolean printXML = false;
        String fileExtension = null;
        boolean debug = false;
        boolean pom = false;
        boolean acceptNewDocument = false;

        var i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "-p" -> page = getNextParameter(args, i++);
                case "-o" -> {
                    var objectParts = getNextParameter(args, i).split("/");
                    objectClass = objectParts[0];
                    if (objectParts.length == 2) {
                        objectNumber = objectParts[1];
                    } else if (objectParts.length != 1) {
                        throw new CommandException(
                            "Too many slashes in value "
                                + args[i + 1]
                                + " passed for option "
                                + args[i]);
                    }
                    i++;
                }
                case "-w" -> wiki = getNextParameter(args, i++);
                case "-v" -> value = getNextParameter(args, i++);
                case "--editor" -> editor = getNextParameter(args, i++);
                case "--pom" -> pom = true;
                case "-H" -> {
                    String[] header = HEADER_SPLIT_PATTERN.split(getNextParameter(args, i));
                    headers.put(header[0], header[1]);
                }
                case "--user" -> user = getNextParameter(args, i++);
                case "--pass" -> pass = getNextParameter(args, i++);
                case "--wiki-readonly" -> wikiReadonly = true;
                case "--wiki-writeonly" -> wikiWriteonly = true;
                case "--write-to-xml" -> outputFile = getNextParameter(args, i++);
                case "--read-from-xml" -> inputFile = getNextParameter(args, i++);
                case "--write-to-mvn-repository" -> xmlWriteDir = getNextParameter(args, i++);
                case "--xml-file" -> {
                    inputFile = getNextParameter(args, i++);
                    outputFile = inputFile;
                }
                case "--sync-data-source" -> syncDataSource = getNextParameter(args, i++);
                case "-u", "--url" -> url = getNextParameter(args, i++);
                case "--edit-page" -> action = Command.Action.EDIT_PAGE;
                case "--edit-content" -> action = Command.Action.EDIT_CONTENT;
                case "--list-properties" -> action = Command.Action.LIST_PROPERTIES;
                case "--list-objects" -> action = Command.Action.LIST_OBJECTS;
                case "--list-attachments" -> action = Command.Action.LIST_ATTACHMENTS;
                case "--get-content" -> action = Command.Action.GET_CONTENT;
                case "--get-title" -> action = Command.Action.GET_TITLE;
                case "--set-content" -> {
                    content = getNextParameter(args, i++);
                    action = Command.Action.SET_CONTENT;
                }
                case "--set-title" -> {
                    title = getNextParameter(args, i++);
                    action = Command.Action.SET_TITLE;
                }
                case "--get-property" -> {
                    property = getNextParameter(args, i++);
                    action = Command.Action.GET_PROPERTY_VALUE;
                }
                case "--set-property" -> {
                    property = getNextParameter(args, i++);
                    action = Command.Action.SET_PROPERTY_VALUE;
                }
                case "--edit-property" -> {
                    property = getNextParameter(args, i++);
                    action = Command.Action.EDIT_PROPERTY;
                }
                case "--mount" -> {
                    mountPath = getNextParameter(args, i++);
                    action = Command.Action.MOUNT;
                }
                case "--sync" -> {
                    syncPath = getNextParameter(args, i++);
                    action = Command.Action.SYNC;
                }
                case "--ext" -> fileExtension = getNextParameter(args, i++);
                case "--debug" -> debug = true;
                case "--print-xml" -> printXML = true;
                case "--help", "-help", "-h", "help" -> action = Command.Action.HELP;
                case "-n", "--new" -> acceptNewDocument = true;

                default -> throw new CommandException("Unknown option " + args[i] + ". Try --help.");
            }
            i++;
        }
        if (action == Command.Action.SYNC) {
            xmlWriteDir = syncDataSource;
        }

        if (args.length == 0) {
            action = Command.Action.HELP;
        }

        var cmd = new Command(
            action, wiki, page, objectClass, objectNumber, property, value, editor, wikiReadonly,
            wikiWriteonly, outputFile, inputFile, xmlReadDir, xmlWriteDir, headers, url, user, pass, content, title,
            mountPath, syncPath, syncDataSource, printXML, fileExtension, debug, pom, acceptNewDocument);

        if (cmd.action() == null) {
            throw new CommandException("Please specify an action. Try --help for help.");
        }

        return cmd;
    }
}
