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

import static java.lang.System.err;
import static java.lang.System.out;
import static org.xwiki.contrib.cli.Arguments.endArgs;
import static org.xwiki.contrib.cli.Arguments.parseArgs;

final class Main
{
    private Main()
    {
        throw new UnsupportedOperationException("Main cannot be instantiated");
    }

    public static void main(String[] args) throws Exception
    {
        Command cmd = new Command();
        try {
            parseArgs(args, cmd);
            endArgs(cmd);
        } catch (CommandException e) {
            err.println(e.getMessage());
            if (e.getCause() != null) {
                err.println(e.getCause().getMessage());
            }
            return;
        }

        runCommand(cmd);
    }

    static void runCommand(Command cmd) throws Exception
    {
        cmd.print();

        try {
            cmd.action().run(cmd);
        } catch (CancelledOperationDocException e) {
            out.println("Operation cancelled by the user.");
        } catch (MessageForUserDocException e) {
            out.println(e.getMessage());
        }
    }
}
