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

import static java.lang.System.out;
import static java.lang.System.err;

final class Main
{
    private Main()
    {
        throw new UnsupportedOperationException("Main cannot be instantiated");
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
        var i = 0;
        var cmd = new Command();
        cmd.headers = new HashMap<String, String>();
        while (i < args.length) {
            switch (args[i]) {
                case "-b":
                    cmd.base = Utils.ensureScheme(getNextParameter(args, i));
                    i++;
                    break;

                case "-p":
                    cmd.page = getNextParameter(args, i);
                    i++;
                    break;

                case "-o":
                    var objectParts = getNextParameter(args, i).split("/");
                    cmd.objectClass = objectParts[0];
                    if (objectParts.length == 2) {
                        cmd.objectNumber = objectParts[1];
                    } else if (objectParts.length != 1) {
                        throw new CommandException(
                            "Too many slashes in value "
                            + args[i + 1]
                            + " passed for option "
                            + args[i]);
                    }
                    i++;
                    break;

                case "-w":
                    cmd.wiki = getNextParameter(args, i);
                    i++;
                    break;

                case "-v":
                    cmd.value = getNextParameter(args, i);
                    i++;
                    break;

                case "-H":
                    String[] header = getNextParameter(args, i).split("\\s*:\\s*");
                    cmd.headers.put(header[0], header[1]);
                    break;

                case "--user":
                    cmd.user = getNextParameter(args, i);
                    i++;
                    break;

                case "--pass":
                    cmd.pass = getNextParameter(args, i);
                    i++;
                    break;

                case "--write-to-xml":
                    cmd.outputFile = getNextParameter(args, i);
                    i++;
                    break;

                case "--read-from-xml":
                    cmd.inputFile = getNextParameter(args, i);
                    i++;
                    break;

                case "--xml-file":
                    cmd.inputFile = getNextParameter(args, i);
                    cmd.outputFile = cmd.inputFile;
                    i++;
                    break;

                case "-u":
                case "--url":
                    cmd.url = getNextParameter(args, i);
                    i++;
                    break;

                case "--edit-page":
                    cmd.action = Command.Action.EDIT_PAGE;
                    break;

                case "--list-properties":
                    cmd.action = Command.Action.LIST_PROPERTIES;
                    break;

                case "--list-objects":
                    cmd.action = Command.Action.LIST_OBJECTS;
                    break;

                case "--get-content":
                    cmd.action = Command.Action.GET_CONTENT;
                    break;

                case "--get-title":
                    cmd.action = Command.Action.GET_TITLE;
                    break;

                case "--set-content":
                    cmd.content = getNextParameter(args, i);
                    i++;
                    cmd.action = Command.Action.SET_CONTENT;
                    break;

                case "--set-title":
                    cmd.title = getNextParameter(args, i);
                    i++;
                    cmd.action = Command.Action.SET_TITLE;
                    break;

                case "--get-property":
                    cmd.property = getNextParameter(args, i);
                    i++;
                    cmd.action = Command.Action.GET_PROPERTY_VALUE;
                    break;

                case "--set-property":
                    cmd.property = getNextParameter(args, i);
                    i++;
                    cmd.action = Command.Action.SET_PROPERTY_VALUE;
                    break;

                case "--debug":
                    cmd.debug = true;
                    break;

                case "--print-xml":
                    cmd.printXML = true;
                    break;

                case "--help":
                case "-help":
                case "-h":
                case "help":
                    cmd.action = Command.Action.HELP;
                    break;

                default:
                    throw new CommandException("Unknown option " + args[i] + ". Try --help.");
            }
            i++;
        }

        if (args.length == 0) {
            cmd.action = Command.Action.HELP;
        }

        if (cmd.action == null) {
            throw new CommandException("Please specify an action. Try --help for help.");
        }

        return cmd;
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

        if (cmd.debug) {
            cmd.print();
            out.println();
        }

        try {
            cmd.action.run(cmd);
        } catch (CancelledOperationDocException e) {
            out.println("Operation cancelled by the user.");
        } catch (MessageForUserDocException e) {
            out.println(e.getMessage());
        }
    }
}
