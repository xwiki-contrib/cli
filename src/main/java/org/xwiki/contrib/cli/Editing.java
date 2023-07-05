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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;

class Editing
{
    interface EditingCallback
    {
      void run(String newValue);
    }

    public static void editValue(Command cmd, String oldValue, File folder, File file, EditingCallback callback)
        throws IOException, InterruptedException
    {
        try (final var writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(oldValue);
        }

        var filename = file.getAbsolutePath();
        var editor = getEditor(cmd);

        if (Utils.isEmpty(editor)) {
            System.out.println("Please select an editor with --editor or set an EDITOR environnement variable");
            return;
        }

        new ProcessBuilder(editor, filename).inheritIO().start();
        final var path = FileSystems.getDefault().getPath(folder.getAbsolutePath());

        try (final var watchService = FileSystems.getDefault().newWatchService()) {
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            while (true) {
                //TODO: catch if a ENTRY_MODIFY and ENTRY_CREATE are fired together
                final var wk = watchService.take();
                for (var event : wk.pollEvents()) {
                    //we only register "ENTRY_MODIFY" so the context is always a Path.
                    final var changed = (Path) event.context();
                    if (filename.endsWith(changed.toString())) {
                        callback.run(Files.readString(Path.of(filename)));
                    }
                }
                wk.reset();
            }
        }
    }

    public static String getEditor(Command cmd) {
        var editor = "";
            if (!Utils.isEmpty(cmd.editor)) {
                editor = cmd.editor;
            } else {
                try {
                    editor = System.getenv("EDITOR");
                } catch (Exception e) {
                    //Variable either doesn't exist or a security manager has denied access to it
                }
            }
        return editor;
    }
}
