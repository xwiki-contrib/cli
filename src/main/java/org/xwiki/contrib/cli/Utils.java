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
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Namespace;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

/**
 * Utils.
 * @version $Id$
 */
public final class Utils
{
    private static final String SINGLE_QUOTE = "'";

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
                    c = page.charAt(i);
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
        return internalHttpRequest(cmd, HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", mimetype == null ? "text/plain; charset=utf8" : mimetype)
            .PUT(BodyPublishers.ofString(content)), HttpResponse.BodyHandlers.ofString());
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
    public static HttpResponse<String> httpPut(Command cmd, String url, byte[] content, String mimetype)
        throws DocException
    {
        return internalHttpRequest(cmd, HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", mimetype == null ? "text/plain; charset=utf8" : mimetype)
            .PUT(BodyPublishers.ofByteArray(content)), HttpResponse.BodyHandlers.ofString());
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
        return internalHttpRequest(cmd, HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Perform a GET request.
     * @param cmd the Command produced by parsing arguments from the cli.
     *            It contains authentication and custom headers to use.
     * @param url the URL to use.
     * @return the HTTP reponse.
     */
    public static HttpResponse<byte[]> httpGetBytes(Command cmd, String url) throws DocException
    {
        return internalHttpRequest(cmd, HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static <T> HttpResponse<T> internalHttpRequest(Command cmd, HttpRequest.Builder url,
        HttpResponse.BodyHandler<T> bodyHandler) throws DocException
    {
        var client = getHTTPClient(cmd);
        var request = setHeadersFromCommand(
            cmd,
            url
        ).build();

        try {
            return client.send(request, bodyHandler);
        } catch (IOException | InterruptedException e) {
            throw new DocException(e);
        }
    }

    /**
     * Encode a string with URL-encoding.
     *
     * @param string the input to encode
     * @return the encoded result
     */
    public static String encodeURLPart(Object string)
    {
        String encodedURL = null;
        if (string != null) {
            encodedURL = URLEncoder.encode(String.valueOf(string), StandardCharsets.UTF_8);
            // The previous call will convert " " into "+" (and "+" into "%2B") so we need to convert "+" into "%20"
            // It's ok since %20 is allowed in both the URL path and the query string (and anchor).
            encodedURL = encodedURL.replace("+", "%20");
        }
        return encodedURL;
    }

    /**
     * Parse an XML document into a DOM.
     *
     * @param xml the XML to parse
     * @return the parsed DOM
     * @throws DocException if there is an error parsing the XML
     */
    public static Document parseXML(String xml) throws DocException
    {
        Document dom = null;

        if (xml != null) {
            var reader = new SAXReader();
            try {
                reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (SAXException e) {
                throw new DocException(e);
            }

            try {
                dom = reader.read(new StringReader(xml));
                dom.getRootElement().add(new Namespace("xwiki", "http://www.xwiki.org"));
            } catch (DocumentException e) {
                throw new DocException(e);
            }
        }

        return dom;
    }

    /**
     * Escape the given string to be used as a string in an XPath expression.
     *
     * @param input the input to escape
     * @return the escaped string
     */
    public static String escapeXPathString(String input)
    {
        if (input.contains(SINGLE_QUOTE)) {
            String[] elements = input.split(SINGLE_QUOTE);

            return "concat("
                + Arrays.stream(elements)
                .map(element -> SINGLE_QUOTE + element + SINGLE_QUOTE)
                .collect(Collectors.joining(", \"'\", "))
                + ")";
        } else {
            return SINGLE_QUOTE + input + SINGLE_QUOTE;
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
            .replace("&", "&amp;")
            .replace(SINGLE_QUOTE, "&apos;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
