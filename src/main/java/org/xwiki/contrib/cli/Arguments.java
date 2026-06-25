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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * Class dedicated to handle command arguments, parameter from config file and to check that everything is correct.
 *
 * @version $Id$
 */
public final class Arguments
{
    private static final Pattern HEADER_SPLIT_PATTERN = Pattern.compile("\\s*:\\s*");

    private Arguments()
    {
        // Nothing to do
    }

    static void parseArgs(String[] args, Command cmd) throws CommandException
    {
        var i = 0;
        Map<String, String> headers = cmd.headers();
        if (headers == null) {
            headers = new HashMap<>();
            cmd.setHeaders(headers);
        }
        while (i < args.length) {
            switch (args[i]) {
                case "--help", "-help", "-h", "help" -> cmd.setAction(Command.Action.HELP);
                case "--repl", "--interactive", "-i" -> cmd.setAction(Command.Action.REPL);
                case "-c", "--configuration" -> {
                    try {
                        readConfigFile(getNextParameter(args, i++), cmd);
                    } catch (IOException e) {
                        throw new CommandException("Could not read config file", e);
                    }
                }
                case "--edit-doc" -> cmd.setAction(Command.Action.EDIT_DOCUMENT);
                case "--get-content" -> cmd.setAction(Command.Action.GET_CONTENT);
                case "--set-content" -> {
                    cmd.setContent(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.SET_CONTENT);
                }
                case "--edit-content" -> cmd.setAction(Command.Action.EDIT_CONTENT);
                case "--edit-macro" -> {
                    cmd.setMacro(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.EDIT_MACRO);
                }
                case "--get-title" -> cmd.setAction(Command.Action.GET_TITLE);
                case "--set-title" -> {
                    cmd.setTitle(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.SET_TITLE);
                }
                case "--list-properties" -> cmd.setAction(Command.Action.LIST_PROPERTIES);
                case "--list-objects" -> cmd.setAction(Command.Action.LIST_OBJECTS);
                case "--get-property" -> {
                    cmd.setProperty(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.GET_PROPERTY_VALUE);
                }
                case "--set-property" -> {
                    cmd.setProperty(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.SET_PROPERTY_VALUE);
                }
                case "--edit-property" -> {
                    cmd.setProperty(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.EDIT_PROPERTY);
                }
                case "--list-attachments" -> cmd.setAction(Command.Action.LIST_ATTACHMENTS);
                case "--mount" -> {
                    cmd.setMountPath(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.MOUNT);
                }
                case "--edit-tree" -> {
                    cmd.setAction(Command.Action.EDIT_TREE);
                }
                case "--pull-doc" -> {
                    cmd.setPullReference(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.PULL_DOCUMENT);
                }
                case "--push-doc" -> {
                    cmd.setPushReference(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.PUSH_DOCUMENT);
                }
                case "--pull-all-docs" -> {
                    cmd.setAction(Command.Action.PULL_ALL_DOCUMENTS);
                }
                case "--push-all-docs" -> {
                    cmd.setAction(Command.Action.PUSH_ALL_DOCUMENTS);
                }

                case "--log-level" -> cmd.setLogLevel(getNextParameter(args, i++));
                case "--print-xml" -> cmd.setPrintXML(true);
                case "-H" -> {
                    String[] header = HEADER_SPLIT_PATTERN.split(getNextParameter(args, i));
                    headers.put(header[0], header[1]);
                }
                case "-u", "--url" -> cmd.setUrl(getNextParameter(args, i++));
                case "-w" -> cmd.setWiki(getNextParameter(args, i++));

                case "--editor" -> cmd.setEditor(getNextParameter(args, i++));
                case "-r", "--ref" -> cmd.setReference(getNextParameter(args, i++));
                case "-o" -> {
                    var objectParts = getNextParameter(args, i).split("/");
                    cmd.setObjectClass(objectParts[0]);
                    if (objectParts.length == 2) {
                        cmd.setObjectNumber(objectParts[1]);
                    } else if (objectParts.length != 1) {
                        throw new CommandException(
                            "Too many slashes in value "
                                + args[i + 1]
                                + " passed for option "
                                + args[i]);
                    }
                    i++;
                }
                case "-v" -> cmd.setValue(getNextParameter(args, i++));
                case "--property" -> cmd.setProperty(getNextParameter(args, i++));
                case "--read-from-xml" -> cmd.setInputFile(getNextParameter(args, i++));
                case "--write-to-xml" -> cmd.setOutputFile(getNextParameter(args, i++));
                case "--xml-file" -> {
                    cmd.setInputFile(getNextParameter(args, i++));
                    cmd.setOutputFile(cmd.inputFile());
                }
                case "-n", "--new" -> cmd.setAcceptNewDocument(true);
                case "--ext" -> cmd.setFileExtension(getNextParameter(args, i++));
                case "--no-write-wiki" -> cmd.setNoWriteWiki(true);
                case "--no-mvn-repo-write" -> cmd.setNoMvnRepoWrite(true);
                case "--first-sync-from" -> {
                    var value = getNextParameter(args, i++);
                    if ("wiki".equalsIgnoreCase(value)) {
                        cmd.setFirstSyncFrom(Command.FirstSyncFrom.WIKI);
                    } else if ("mvn".equalsIgnoreCase(value)) {
                        cmd.setFirstSyncFrom(Command.FirstSyncFrom.MVN);
                    } else {
                        throw new CommandException("Invalid first sync from " + value);
                    }
                }
                case "--working-directory" -> cmd.setWorkingDirectory(getNextParameter(args, i++));
                case "--mvn-repo" -> cmd.setMvnRepo(getNextParameter(args, i++));
                case "--spaces" -> cmd.spaces().add(getNextParameter(args, i++));

                case "--user" -> cmd.setUser(getNextParameter(args, i++));
                case "--pass" -> cmd.setPass(getNextParameter(args, i++));

                default -> throw new CommandException("Unknown option " + args[i] + ". Try --help.");
            }
            i++;
        }
    }

    static void endArgs(Command cmd) throws CommandException
    {
        setLogLevel(cmd);

        if (cmd.action() == null) {
            System.out.println("No action has been provided, opening an interactive session. Use --help for help.");
            cmd.setAction(Command.Action.REPL);
        }

        if (cmd.action() == Command.Action.EDIT_TREE && StringUtils.isEmpty(cmd.url())) {
            throw new CommandException(
                "XWiki instance URL is required for sync mode.");
        }

        if (((cmd.action() == Command.Action.EDIT_TREE && cmd.firstSyncFrom() == Command.FirstSyncFrom.MVN)
            || cmd.action() == Command.Action.PUSH_ALL_DOCUMENTS
            || cmd.action() == Command.Action.PULL_ALL_DOCUMENTS
            || cmd.action() == Command.Action.PUSH_DOCUMENT
            || cmd.action() == Command.Action.PULL_DOCUMENT)
            && StringUtils.isEmpty(cmd.mvnRepo()))
        {
            throw new CommandException("This action requires providing a Maven repository.");
        }

    }

    private static void setLogLevel(Command cmd)
    {
        if (cmd.logLevel() == null) {
            cmd.setLogLevel(Level.INFO.toString());
        }

        var ctx = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.valueOf(cmd.logLevel()));
    }

    private static String getNextParameter(String[] args, int i) throws CommandException
    {
        if (i + 1 >= args.length) {
            throw new CommandException("Expected a parameter for " + args[i]);
        }
        return args[i + 1];
    }

    static void readConfigFile(String path, Command cmd) throws IOException, CommandException
    {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // We parse lines that look like:
                // --parameter value
                // or
                // --parameter
                parseArgs(line.split("\\s+", 2), cmd);
            }
        }
    }
}
