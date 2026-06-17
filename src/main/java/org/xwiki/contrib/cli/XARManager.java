package org.xwiki.contrib.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.primitives.Bytes;

/**
 * Class dedicated to import and export XAR from XWiki instance.
 */
public class XARManager
{
    private final Command command;

    public XARManager(Command cmd)
    {
        command = cmd;
    }

    public Map<String, String> getXarOfPages(List<String> references) throws DocException, IOException
    {
        var csrf = Utils.getCSRF(command);
        var xarPath = Utils.getUrlAction(command) + "export/cli-export?format=xar";
        var contentToSend = new StringBuilder("form_token=" + csrf);
        for (var r : references) {
            contentToSend.append("&page=").append(URLEncoder.encode(r, StandardCharsets.UTF_8));
        }
        var response = Utils.httpPost(command, xarPath, contentToSend.toString(), "application/x-www-form-urlencoded");

        var zipStream = new ZipInputStream(new ByteArrayInputStream(response.body().getBytes(StandardCharsets.UTF_8)));
        var result = new HashMap<String, String>();
        ZipEntry entry;
        byte[] buffer = new byte[1024];
        while ((entry = zipStream.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            ArrayList<Byte> bytes = new ArrayList<>(1024);
            int len = 0;
            while ((len = zipStream.read(buffer)) > 0) {
                bytes.addAll(Bytes.asList(buffer).subList(0, len).stream().toList());
            }
            var contentStr = new String(Bytes.toArray(bytes));
            zipStream.closeEntry();
            result.put(entry.getName(), contentStr);
        }
        return result;
    }

    public void putXarOfPages(List<String> pages)
    {




    }
}
