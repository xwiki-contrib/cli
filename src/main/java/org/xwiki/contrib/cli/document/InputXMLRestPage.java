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

package org.xwiki.contrib.cli.document;

import java.net.http.HttpResponse;

import org.xwiki.contrib.cli.Command;
import org.xwiki.contrib.cli.DocException;
import org.xwiki.contrib.cli.MessageForUserDocException;
import org.xwiki.contrib.cli.Utils;

class InputXMLRestPage extends AbstractXMLDoc implements InputDoc
{
    protected final String wiki;

    protected final String page;

    private final String url;

    InputXMLRestPage(Command cmd, String wiki, String page) throws DocException
    {
        super(cmd);

        this.page = page;
        this.wiki = wiki;
        url = Utils.getDocRestURLFromCommand(cmd, wiki, page, true, true, true);

        var response = Utils.httpGet(cmd, url);
        var status = response.statusCode();
        if (status == 200) {
            handleResponse(response);
        } else if (status == 404 && cmd.acceptNewDocument()) {
            // 404 : Document not found, we assume it's a document we would like to create
            response = Utils.httpPut(cmd, url, "", null);
            status = response.statusCode();
            if (status == 201) {
                // 201 : New Document Created
                handleResponse(response);
            } else {
                handleUnexpectedStatus(status, cmd, response);
            }
        } else {
            handleUnexpectedStatus(status, cmd, response);
        }
    }

    public String getWiki()
    {
        return wiki;
    }

    public String getPage()
    {
        return page;
    }

    @Override
    public byte[] getAttachment(String attachmentName) throws DocException
    {

        String attachmentURL = Utils.getAttachmentRestURLFromCommand(cmd, wiki, page, attachmentName);
        return Utils.httpGetBytes(cmd, attachmentURL).body();
    }

    @Override
    public String getFriendlyName()
    {
        return "the page at [" + url + "]";
    }

    private void handleResponse(HttpResponse<String> response)
    {
        var body = response.body();
        setXML(body, true);
    }

    private void handleUnexpectedStatus(int status, Command cmd, HttpResponse<String> response) throws DocException
    {
        throw new MessageForUserDocException(
            "Unexpected status "
                + status
                + ". "
                + (cmd.debug()
                ? "Body: " + response.body()
                : " Use --debug to print the body of the HTTP request")
        );
    }
}
