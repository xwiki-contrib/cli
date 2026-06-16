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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

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
        Command cmd = new Command();
        try {
            parseArgs(args, cmd);
            endArgs(args, cmd);
        } catch (CommandException e) {
            err.println(e.getMessage());
            if (e.getCause() != null) {
                err.println(e.getCause().getMessage());
            }
            return;
        }

        cmd.print();

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

    private static void readConfigFile(String path, Command cmd) throws IOException, CommandException
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

    private static void parseArgs(String[] args, Command cmd) throws IOException, CommandException
    {
        var i = 0;
        Map<String, String> headers = cmd.headers();
        if (headers == null) {
             headers = new HashMap<>();
             cmd.setHeaders(headers);
        }
        while (i < args.length) {
            switch (args[i]) {
                case "-c", "--configuration" ->  {
                    try {
                        readConfigFile(getNextParameter(args, i++), cmd);
                    } catch (IOException e) {
                        throw new CommandException("Could not read config file", e);
                    }
                }
                case "-p" -> cmd.setPage(getNextParameter(args, i++));
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
                case "-w" -> cmd.setWiki(getNextParameter(args, i++));
                case "-v" -> cmd.setValue(getNextParameter(args, i++));
                case "--editor" -> cmd.setEditor(getNextParameter(args, i++));
                case "--pom" -> cmd.setPom(true);
                case "-H" -> {
                    String[] header = HEADER_SPLIT_PATTERN.split(getNextParameter(args, i));
                    headers.put(header[0], header[1]);
                }
                case "--user" -> cmd.setUser(getNextParameter(args, i++));
                case "--pass" -> cmd.setPass(getNextParameter(args, i++));
                case "--wiki-readonly" -> cmd.setWikiReadonly(true);
                case "--wiki-writeonly" -> cmd.setWikiWriteonly(true);
                case "--write-to-xml" -> cmd.setOutputFile(getNextParameter(args, i++));
                case "--read-from-xml" -> cmd.setInputFile(getNextParameter(args, i++));
                case "--write-to-mvn-repository" -> cmd.setXmlWriteDir(getNextParameter(args, i++));
                case "--xml-file" -> {
                    cmd.setInputFile(getNextParameter(args, i++));
                    cmd.setOutputFile(cmd.inputFile());
                }
                case "--sync-data-source" -> cmd.setSyncDataSource(getNextParameter(args, i++));
                case "-u", "--url" -> cmd.setUrl(getNextParameter(args, i++));
                case "--edit-page" -> cmd.setAction(Command.Action.EDIT_PAGE);
                case "--edit-content" -> cmd.setAction(Command.Action.EDIT_CONTENT);
                case "--edit-macro" -> {
                    cmd.setMacro(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.EDIT_MACRO);
                }
                case "--list-properties" -> cmd.setAction(Command.Action.LIST_PROPERTIES);
                case "--list-objects" -> cmd.setAction(Command.Action.LIST_OBJECTS);
                case "--list-attachments" -> cmd.setAction(Command.Action.LIST_ATTACHMENTS);
                case "--get-content" -> cmd.setAction(Command.Action.GET_CONTENT);
                case "--get-title" -> cmd.setAction(Command.Action.GET_TITLE);
                case "--set-content" -> {
                    cmd.setContent(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.SET_CONTENT);
                }
                case "--set-title" -> {
                    cmd.setTitle(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.SET_TITLE);
                }
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
                case "--property" -> cmd.setProperty(getNextParameter(args, i++));
                case "--mount" -> {
                    cmd.setMountPath(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.MOUNT);
                }
                case "--sync" -> {
                    cmd.setSyncPath(getNextParameter(args, i++));
                    cmd.setAction(Command.Action.SYNC);
                }
                case "--ext" -> cmd.setFileExtension(getNextParameter(args, i++));
                case "--loglevel" -> cmd.setLogLevel(getNextParameter(args, i++));
                case "--print-xml" -> cmd.setPrintXML(true);
                case "--help", "-help", "-h", "help" -> cmd.setAction(Command.Action.HELP);
                case "-n", "--new" -> cmd.setAcceptNewDocument(true);

                default -> throw new CommandException("Unknown option " + args[i] + ". Try --help.");
            }
            i++;
        }
    }

    private static void endArgs(String[] args, Command cmd) throws CommandException
    {
        if (cmd.action() == Command.Action.SYNC) {
            cmd.setXmlWriteDir(cmd.syncDataSource());
        }

        if (args.length == 0) {
            cmd.setAction(Command.Action.HELP);
        }

        if (cmd.action() == null) {
            throw new CommandException("Please specify an action. Try --help for help.");
        }

        if (cmd.logLevel() == null) {
            cmd.setLogLevel(Level.WARN.toString());
        }
        var ctx = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.valueOf(cmd.logLevel()));
    }
}
