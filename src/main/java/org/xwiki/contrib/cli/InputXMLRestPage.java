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

class InputXMLRestPage extends AbstractXMLDoc implements InputDoc
{
    private final String url;

    InputXMLRestPage(Command cmd) throws DocException
    {
        super(cmd);
        url = Utils.getDocRestURLFromCommand(cmd, true);

        var response = Utils.httpGet(cmd, url);
        var status = response.statusCode();
        if (status == 200) {
            var body = response.body();
            setXML(body, true);
        } else {
            throw new MessageForUserDocException(
                "Unexpected status "
                + status
                + ". "
                + (cmd.debug
                    ? "Body: " + response.body()
                    : " Use --debug to print the body of the HTTP request")
            );
        }
    }

    @Override
    public String getFriendlyName()
    {
        return "the page at [" + url + "]";
    }
}
