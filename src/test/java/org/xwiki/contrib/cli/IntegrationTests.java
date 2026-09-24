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

import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xwiki.contrib.cli.MockXWikiInstance.sendResponse;

public class IntegrationTests
{
    @FunctionalInterface
    private static interface RunScript
    {
        abstract void run(MockXWikiInstance mockXWikiInstance) throws IOException;
    }

    private final PrintStream standardOut = System.out;
    private final PrintStream standardErr = System.err;
    private ByteArrayOutputStream outputStreamCaptor;
    private ByteArrayOutputStream errStreamCaptor;

    @BeforeEach
    public void setUp() {
        outputStreamCaptor = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStreamCaptor));

        errStreamCaptor = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errStreamCaptor));
    }

    @ParameterizedTest
    @ValueSource(strings = { "title", "content" })
    public void testGetTitleAndContent(String element) throws Exception
    {
        run(r -> {
            r.once("/rest/wikis/xwiki/spaces/Main/pages/WebHome", e -> {
                sendResponse(e, getFileContent("Main.WebHome.xml"), HttpURLConnection.HTTP_OK);
            });
        }, "--get-" + element, "-r", "Main.WebHome");
        outputEquals("My example " + element);
    }

    @ParameterizedTest
    @ValueSource(strings = { "title", "content" })
    public void testSetTitleAndContent(String element) throws Exception
    {
        run(r -> {
            r.answer("/rest/wikis/xwiki/spaces/Main/pages/WebHome", e -> {
                if ("GET".equals(e.getRequestMethod())) {
                    sendResponse(e, getFileContent("Main.WebHome.xml"), HttpURLConnection.HTTP_OK);
                } else if ("PUT".equals(e.getRequestMethod())) {
                    // FIXME this check is not ideal, but better might require that we use a real XWiki instance
                    assertTrue(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                        .contains("<" + element + ">new " + element + "</" + element + ">"));
                    sendResponse(e, "", HttpURLConnection.HTTP_OK);
                }
            });
        }, "--set-" + element, "new " + element + "", "-r", "Main.WebHome");
        outputEquals("");
    }

    String getFileContent(String filename) throws IOException
    {
        InputStream s = getClass().getClassLoader().getResourceAsStream(filename);
        return new String(s.readAllBytes(), StandardCharsets.UTF_8);
    }

    void outputEquals(String expected)
    {
        String actual = outputStreamCaptor.toString();
        if (actual.endsWith("\n")) {
            actual = actual.substring(0, actual.length() - 1);
        }
        assertEquals(expected, actual);
    }

    void run(RunScript script, String... commandArgs)
    {
        try {
            MockXWikiInstance r = new MockXWikiInstance();

            String[] hardcodedArgs = new String[] { "--url", r.getXWikiBaseURL() };
            String[] args = new String[hardcodedArgs.length + commandArgs.length];
            int i = -1;
            for (String arg : hardcodedArgs) {
                args[++i] = arg;
            }
            for (String arg : commandArgs) {
                args[++i] = arg;
            }
            script.run(r);
            r.doneConfiguring();
            Main.main(args);
            r.done();
            // There's no error that was printed
            assertEquals("", errStreamCaptor.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
