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

import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MockXWikiInstance
{
    private static final String BASE = "/xwikibase";

    private HttpServer httpServer;

    private final Collection<HttpHandler> expectedCalls = new HashSet<>();
    private final StringBuffer unexpectedRequests = new StringBuffer();

    MockXWikiInstance() throws IOException
    {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/", r -> {
            unexpectedRequests.append("*** Unexpected request: ");
            unexpectedRequests.append(r.getRequestMethod());
            unexpectedRequests.append(" ");
            unexpectedRequests.append(r.getRequestURI());
            unexpectedRequests.append("\n");
            unexpectedRequests.append(new String(r.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            unexpectedRequests.append("\n");
            sendResponse(r, "", HttpURLConnection.HTTP_INTERNAL_ERROR);
        });
    }

    void once(String path, HttpHandler handler)
    {
        expectedCalls.add(handler);
        HttpContext c = httpServer.createContext(BASE + path);
        HttpHandler wrappedHandler = e -> {
            handler.handle(e);
            expectedCalls.remove(handler);
            httpServer.removeContext(c);
        };
        c.setHandler(wrappedHandler);
    }

    void answer(String path, HttpHandler handler)
    {
        httpServer.createContext(BASE + path, handler);
    }

    static void sendResponse(HttpExchange exchange, String content, int httpCode) throws IOException
    {
        byte[] response = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/xml; charset=utf-8");
        exchange.sendResponseHeaders(httpCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    String getXWikiBaseURL()
    {
        InetSocketAddress a = httpServer.getAddress();
        return "http://localhost:" + a.getPort() + BASE;
    }

    void doneConfiguring()
    {
        httpServer.start();
    }

    void done() throws IOException
    {
        httpServer.stop(0);
        assertEquals(0, expectedCalls.size(), "There were expected requests that did not happen");
        assertEquals(0, unexpectedRequests.length(), "There were unexpected requests:\n" + unexpectedRequests);
    }
}
