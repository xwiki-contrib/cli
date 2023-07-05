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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.Base64;

/**
 * Utils.
 * @version $Id$
 */
public final class Utils
{
    private Utils()
    {
        // Intentionally left blank.
    }


    /**
     * @return the part of a rest url to reach the given page. Example: Main.WebHome -> /spaces/Main/pages/WebHome
     * @param page the page, in dotted notation.
     */
    public static String pageToRestURLPart(String page)
    {
        var urlPart = "";
        var lastPart = "";
        var i = 0;
        var len = page.length();

        while (i < len) {
            char c = page.charAt(i);
            switch (c) {
                case '.':
                    urlPart += "/spaces/" + lastPart;
                    lastPart = "";
                    break;

                case '\\':
                    if (i + 1 < len) {
                        i++;
                    }
                    lastPart += c;
                    break;

                default:
                    lastPart += c;
            }

            i++;
        }

        return urlPart + "/pages/" + lastPart;
    }

    private static HttpClient getHTTPClient(Command cmd)
    {
        return HttpClient.newBuilder().build();
    }

    public static boolean present(String param)
    {
        return param != null && !param.isEmpty();
    }


    private static HttpRequest.Builder setHeadersFromCommand(Command cmd, HttpRequest.Builder builder)
    {
        for (var header : cmd.headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        if (present(cmd.user) && present(cmd.pass)) {
            builder.header("Authorization",
                "Basic " + Base64.getEncoder().encodeToString((cmd.user + ":" + cmd.pass).getBytes())
            );
        }
        return builder;
    }

    /**
     * Perform a PUT request.
     * @param cmd the Command produced by parsing arguments from the cli.
     *            It contains authentication and custom headers to use.
     * @param url the URL to use.
     * @param content the content to set.
     * @param mimetype the mimetype of the content to set. null to use the default "text/plain; charset=utf8".
     * @return the HTTP reponse.
     */
    public static HttpResponse<String> httpPut(Command cmd, String url, String content, String mimetype)
        throws DocException
    {
        var client = getHTTPClient(cmd);
        var request = setHeadersFromCommand(
            cmd,
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", mimetype == null ? "text/plain; charset=utf8" : mimetype)
                .PUT(BodyPublishers.ofString(content))
        ).build();

        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new DocException(e);
        }
    }

    /**
     * Perform a GET request.
     * @param cmd the Command produced by parsing arguments from the cli.
     *            It contains authentication and custom headers to use.
     * @param url the URL to use.
     * @return the HTTP reponse.
     */
    public static HttpResponse<String> httpGet(Command cmd, String url) throws DocException
    {
        var client = getHTTPClient(cmd);
        var request = setHeadersFromCommand(
            cmd,
            HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
        ).build();

        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new DocException(e);
        }
    }

    /**
     * @return the base, with an HTTP scheme if it's missing.
     *         Uses https://, except of IPv4 or localhost addresses.
     * @param base the base to use.
     */
    public static String ensureScheme(String base)
    {
        if (base.startsWith("http:") || base.startsWith("https:")) {
            return base;
        }

        if (base.equals("localhost") || base.startsWith("localhost:")
            || base.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")
        ) {
            return "http://" + base;
        }

        return "https://" + base;
    }

    /**
     * @return the REST document URL specified by the given user-provided command.
     * @param cmd the Command to use.
     */
    public static String getDocRestURLFromCommand(Command cmd, boolean withObjects) throws DocException
    {
        var wiki = cmd.wiki;
        if (wiki == null || wiki.isEmpty()) {
            wiki = "xwiki";
        }

        if (cmd.page == null) {
            throw new MessageForUserDocException("Please specify a page, e.g. -p Main.WebHome");
        }

        return cmd.base
            + "/xwiki/rest/wikis/" + wiki
            +  Utils.pageToRestURLPart(cmd.page)
            + (withObjects ? "?objects=true&attachments=true" : "");
    }

    /**
     * @return the given value is null or empty.
     * @param v the value to test.
     */
    public static boolean isEmpty(String v) {
        return v == null || v.isEmpty();
    }

    public static String escapeXML(String str)
    {
        return str
            .replaceAll("&", "&amp;")
            .replaceAll("'", "&apos;")
            .replaceAll("\"", "&quote;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;");
    }
}
