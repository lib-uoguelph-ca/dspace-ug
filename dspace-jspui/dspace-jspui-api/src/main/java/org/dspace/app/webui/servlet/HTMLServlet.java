/*
 * HTMLServlet.java
 *
 * Version: $Revision: 4430 $
 *
 * Date: $Date: 2009-10-10 17:21:30 +0000 (Sat, 10 Oct 2009) $
 *
 * Copyright (c) 2002-2005, Hewlett-Packard Company and Massachusetts
 * Institute of Technology.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Hewlett-Packard Company nor the name of the
 * Massachusetts Institute of Technology nor the names of their
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */
package org.dspace.app.webui.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.dspace.app.webui.util.JSPManager;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.core.Utils;
import org.dspace.handle.HandleManager;
import org.dspace.services.model.Event;
import org.dspace.usage.UsageEvent;
import org.dspace.utils.DSpace;

/**
 * Servlet for HTML bitstream support.
 * <P>
 * If we receive a request like this:
 * <P>
 * <code>http://dspace.foo.edu/html/123.456/789/foo/bar/index.html</code>
 * <P>
 * we first check for a bitstream with the *exact* filename
 * <code>foo/bar/index.html</code>. Otherwise, we strip the path information
 * (up to three levels deep to prevent infinite URL spaces occurring) and see if
 * we have a bitstream with the filename <code>index.html</code> (with no
 * path). If this exists, it is served up. This is because if an end user
 * uploads a composite HTML document with the submit UI, we will not have
 * accurate path information, and so we assume that if the browser is requesting
 * foo/bar/index.html but we only have index.html, that this is the desired file
 * but we lost the path information on upload.
 * 
 * @author Austin Kim, Robert Tansley
 * @version $Revision: 4430 $
 */
public class HTMLServlet extends DSpaceServlet
{
    /** log4j category */
    private static Logger log = Logger.getLogger(HTMLServlet.class);

    /**
     * Default maximum number of path elements to strip when testing if a
     * bitstream called "foo.html" should be served when "xxx/yyy/zzz/foo.html"
     * is requested.
     */
    private int maxDepthGuess;

    /**
     * Create an HTML Servlet
     */
    public HTMLServlet()
    {
        super();

        if (ConfigurationManager.getProperty("webui.html.max-depth-guess") != null)
        {
            maxDepthGuess = ConfigurationManager
                    .getIntProperty("webui.html.max-depth-guess");
        }
        else
        {
            maxDepthGuess = 3;
        }
    }
    
    // Return bitstream whose name matches the target bitstream-name
    // bsName, or null if there is no match.  Match must be exact.
    // NOTE: This does not detect duplicate bitstream names, just returns first.
    private static Bitstream getItemBitstreamByName(Item item, String bsName)
    						throws SQLException
    {
        Bundle[] bundles = item.getBundles();

        for (int i = 0; i < bundles.length; i++)
        {
            Bitstream[] bitstreams = bundles[i].getBitstreams();

            for (int k = 0; k < bitstreams.length; k++)
            {
                if (bsName.equals(bitstreams[k].getName()))
                    return bitstreams[k];
            }
        }
        return null;
    }

    // On the surface it doesn't make much sense for this servlet to
    // handle POST requests, but in practice some HTML pages which
    // are actually JSP get called on with a POST, so it's needed.
    protected void doDSPost(Context context, HttpServletRequest request,
                HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
        doDSGet(context, request, response);
    }

    protected void doDSGet(Context context, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException,
            SQLException, AuthorizeException
    {
        Item item = null;
        Bitstream bitstream = null;

        String idString = request.getPathInfo();
        String filenameNoPath = null;
        String fullpath = null;
        String handle = null;

        // Parse URL
        if (idString != null)
        {
            // Remove leading slash
            if (idString.startsWith("/"))
            {
                idString = idString.substring(1);
            }

            // Get handle and full file path
            int slashIndex = idString.indexOf('/');
            if (slashIndex != -1)
            {
                slashIndex = idString.indexOf('/', slashIndex + 1);
                if (slashIndex != -1)
                {
                    handle = idString.substring(0, slashIndex);
                    fullpath = URLDecoder.decode(idString
                            .substring(slashIndex + 1),
                            Constants.DEFAULT_ENCODING);

                    // Get filename with no path
                    slashIndex = fullpath.indexOf('/');
                    if (slashIndex != -1)
                    {
                        String[] pathComponents = fullpath.split("/");
                        if (pathComponents.length <= maxDepthGuess + 1)
                        {
                            filenameNoPath = pathComponents[pathComponents.length - 1];
                        }
                    }
                }
            }
        }

        if (handle != null && fullpath != null)
        {
            // Find the item
            try
            {
                /*
                 * If the original item doesn't have a Handle yet (because it's
                 * in the workflow) what we actually have is a fake Handle in
                 * the form: db-id/1234 where 1234 is the database ID of the
                 * item.
                 */
                if (handle.startsWith("db-id"))
                {
                    String dbIDString = handle
                            .substring(handle.indexOf('/') + 1);
                    int dbID = Integer.parseInt(dbIDString);
                    item = Item.find(context, dbID);
                }
                else
                {
                    item = (Item) HandleManager
                            .resolveToObject(context, handle);
                }
            }
            catch (NumberFormatException nfe)
            {
                // Invalid ID - this will be dealt with below
            }
        }

        if (item != null)
        {
            // Try to find bitstream with exactly matching name + path
            bitstream = getItemBitstreamByName(item, fullpath);
            
            if (bitstream == null && filenameNoPath != null)
            {
                // No match with the full path, but we can try again with
                // only the filename
                bitstream = getItemBitstreamByName(item, filenameNoPath);
            }
        }

        // Did we get a bitstream?
        if (bitstream != null)
        {
            log.info(LogManager.getHeader(context, "view_html", "handle="
                    + handle + ",bitstream_id=" + bitstream.getID()));
            
            new DSpace().getEventService().fireEvent(
            		new UsageEvent(
            				UsageEvent.Action.VIEW,
            				request, 
            				context, 
            				bitstream));
            
            //new UsageEvent().fire(request, context, AbstractUsageEvent.VIEW,
			//		Constants.BITSTREAM, bitstream.getID());

            // Set the response MIME type
            response.setContentType(bitstream.getFormat().getMIMEType());

            // Response length
            response.setHeader("Content-Length", String.valueOf(bitstream
                    .getSize()));

            // Pipe the bits
            InputStream is = bitstream.retrieve();

            Utils.bufferedCopy(is, response.getOutputStream());
            is.close();
            response.getOutputStream().flush();
        }
        else
        {
            // No bitstream - we got an invalid ID
            log.info(LogManager.getHeader(context, "view_html",
                    "invalid_bitstream_id=" + idString));

            JSPManager.showInvalidIDError(request, response, idString,
                    Constants.BITSTREAM);
        }
    }
}
