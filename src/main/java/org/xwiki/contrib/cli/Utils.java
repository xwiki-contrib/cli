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
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Namespace;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

/**
 * Utils.
 *
 * @version $Id$
 */
public final class Utils
{
    private static final String CONTENT_TYPE = "Content-Type";

    private static final String TEXT_PLAIN_CHARSET_UTF_8 = "text/plain; charset=utf8";

    private static final String SINGLE_QUOTE = "'";

    private static final String XWIKI = "xwiki";

    private Utils()
    {
        // Intentionally left blank.
    }

    /**
     * @param reference the page, in dotted notation.
     * @return the maven repos path to reach the given page. Example: Main.WebHome -> Main/WebHome
     */
    public static String fromReferenceToMvnReposPath(String reference)
    {
        // TODO unit test
        return fromReferenceToMvnReposPath(deserialize(reference));
    }

    public static String fromReferenceToMvnReposPath(PageReference reference)
    {
        // TODO unit test
        return String.join("/", reference.spaces()) + "/" + reference.page();
    }

    /**
     * Convert a page reference to a path in the XFF format. Note we use this format for the REST API and also for the
     * synced dir path.
     *
     * @param reference the page, in dotted notation.
     * @return the page path in the XFF format. Example: Main.WebHome -> /spaces/Main/pages/WebHome
     */
    public static String fromReferenceToXFFPath(String reference)
    {
        // TODO unit test
        return fromReferenceToXFFPath(deserialize(reference));
    }

    public static String fromReferenceToXFFPath(PageReference reference)
    {
        // TODO unit test
        return "/spaces/" + String.join("/spaces/", reference.spaces()) + "/pages/" + reference.page();
    }

    public static PageReference deserialize(String reference)
    {
        var spaces = new ArrayList<String>();
        var currentSpace = new StringBuilder(reference.length());
        final var len = reference.length();
        var i = 0;

        while (i < len) {
            char c = reference.charAt(i);
            switch (c) {
                case '.':
                    spaces.add(currentSpace.toString());
                    currentSpace = new StringBuilder(reference.length());
                    break;
                case '\\':
                    if (i + 1 < len) {
                        i++;
                    }
                    c = reference.charAt(i);
                    currentSpace.append(c);
                    break;

                default:
                    currentSpace.append(c);
            }
            i++;
        }
        var page = currentSpace.toString();
        return new PageReference(spaces, page);
    }

    /**
     * Test if the string value is present.
     *
     * @param param the value to check
     * @return if the param is not null and is not empty
     */
    public static boolean present(String param)
    {
        return param != null && !param.isEmpty();
    }

    /**
     * Perform a PUT request.
     *
     * @param cmd the Command produced by parsing arguments from the cli. It contains authentication and custom
     *     headers to use.
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
            .header(CONTENT_TYPE, mimetype == null ? TEXT_PLAIN_CHARSET_UTF_8 : mimetype)
            .PUT(BodyPublishers.ofString(content)), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Perform a PUT request.
     *
     * @param cmd the Command produced by parsing arguments from the cli. It contains authentication and custom
     *     headers to use.
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
            .header(CONTENT_TYPE, mimetype == null ? TEXT_PLAIN_CHARSET_UTF_8 : mimetype)
            .PUT(BodyPublishers.ofByteArray(content)), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Perform a GET request.
     *
     * @param cmd the Command produced by parsing arguments from the cli. It contains authentication and custom
     *     headers to use.
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
     *
     * @param cmd the Command produced by parsing arguments from the cli. It contains authentication and custom
     *     headers to use.
     * @param url the URL to use.
     * @return the HTTP reponse.
     */
    public static HttpResponse<byte[]> httpGetBytes(Command cmd, String url) throws DocException
    {
        return internalHttpRequest(cmd, HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET(), HttpResponse.BodyHandlers.ofByteArray());
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
                dom.getRootElement().add(new Namespace(XWIKI, "http://www.xwiki.org"));
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
     * @param base the base to use.
     * @return the base, with an HTTP scheme if it's missing. Uses https://, except of IPv4 or localhost addresses.
     */
    public static String ensureScheme(String base)
    {
        if (base.startsWith("http:") || base.startsWith("https:")) {
            return base;
        }

        if (base.equals("localhost") || base.startsWith("localhost:")
            || base.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")
        )
        {
            return "http://" + base;
        }

        return "https://" + base;
    }

    /**
     * @param cmd the Command to use.
     * @param withObjects define if we need to get the document with objects.
     * @return the REST document URL specified by the given user-provided command.
     */
    public static String getDocRestURLFromCommand(Command cmd, String wiki, String page, boolean withObjects,
        boolean withAttchments, boolean withClass)
        throws DocException
    {
        if (wiki == null || wiki.isEmpty()) {
            wiki = XWIKI;
        }

        if (page == null) {
            throw new MessageForUserDocException("Please specify a page, e.g. -p Main.WebHome");
        }

        var url = new StringBuilder(cmd.url());
        url.append("/rest/wikis/").append(wiki);
        url.append(Utils.fromReferenceToXFFPath(page));
        if (withObjects  || withAttchments || withClass) {
            var params = new String[] {
                withObjects ? "objects=true":"",
                withAttchments ? "attachments=true":"",
                withClass ? "class=true":"",
            };
            url.append("?");
            url.append(String.join("&", params));
        }
        return  url.toString();
    }

    public static String getAttachmentRestURLFromCommand(Command cmd, String wiki, String page, String attachmentName)
        throws MessageForUserDocException
    {
        if (wiki == null || wiki.isEmpty()) {
            wiki = XWIKI;
        }

        if (page == null) {
            throw new MessageForUserDocException("Please specify a page, e.g. -p Main.WebHome");
        }

        return cmd.url()
            + "/rest/wikis/" + wiki
            + Utils.fromReferenceToXFFPath(page)
            + "/attachments/" + attachmentName;
    }

    /**
     * @param v the value to test.
     * @return the given value is null or empty.
     */
    public static boolean isEmpty(String v)
    {
        return v == null || v.isEmpty();
    }

    /**
     * Escape string for XML encoding.
     *
     * @param str string to escape
     * @return the escaped value for XML content
     */
    public static String escapeXML(String str)
    {
        return str
            .replace("&", "&amp;")
            .replace(SINGLE_QUOTE, "&apos;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    public static List<String> getContentScriptingLanguage(String content) throws DocException
    {
        // For now we only support velocity, groovy, python, php macro
        // We can at any time extends this list
        final Map<String, String> macroRegex = Map.ofEntries(
            Map.entry("velocity", "vm"),
            Map.entry("groovy", "groovy"),
            Map.entry("python", "py"),
            Map.entry("php", "php")
        );
        var res = new ArrayList<String>(macroRegex.size());
        for (var macro : macroRegex.entrySet()) {
            if (content.contains("{{/" + macro.getKey() + "}}")) {
                res.add(macro.getValue());
            }
        }
        return res;
    }

    /**
     * Give all the file extension for script files of object properties.
     *
     * @param properties map of all properties of the object. Needed for some object to deduce the extension.
     * @param objectClass class of the object.
     * @param property properties for which we need to return the extension.
     * @return the extension if it's a scripting file.
     */
    public static Optional<String> getScriptLangFromObjectInfo(HashMap<String, String> properties, String objectClass,
        String property)
    {
        if (objectClass.equals("XWiki.StyleSheetExtension") && property.equals("code")) {
            return Optional.of("less");
        } else if (objectClass.equals("XWiki.JavaScriptExtension") && property.equals("code")) {
            return Optional.of("js");
        } else if (objectClass.equals("XWiki.XWikiSkinFileOverrideClass") && property.equals("content")) {
            return Optional.of("vm");
        } else if (objectClass.equals("XWiki.ScriptComponentClass") && property.equals("script_content")) {
            var scriptLanguage = properties.get("script_language");
            switch (scriptLanguage) {
                case "ruby" -> Optional.of("rb");
                case "velocity" -> Optional.of("vm");
                case "python" -> Optional.of("py");
                // php, groovy
                default -> Optional.of(scriptLanguage);
            }
        }
        return Optional.empty();
    }

    private static HttpClient getHTTPClient(Command cmd)
    {
        return HttpClient.newBuilder().build();
    }

    private static HttpRequest.Builder setHeadersFromCommand(Command cmd, HttpRequest.Builder builder)
    {
        for (var header : cmd.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        if (present(cmd.user()) && present(cmd.pass())) {
            builder.header("Authorization",
                "Basic " + Base64.getEncoder().encodeToString((cmd.user() + ":" + cmd.pass()).getBytes())
            );
        }
        return builder;
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
}
