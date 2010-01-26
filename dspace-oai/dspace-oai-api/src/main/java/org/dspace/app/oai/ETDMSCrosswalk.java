/*
 * ETDMSCrosswalk.java
 *
 * Version: $Revision: 1.0 $
 *
 * Date: $Date: 2004/11/17 15:01:11 $
 *
 *
 * =============================================
 * Notes for external developers
 * =============================
 *
 * Here are some general tips for developers using
 * this code for their own sites
 *
 * 1) Our Dublin Core records have a fields for degree info
 *    called degree:discipline and degree:level. these 2 fields
 *    are used in this crosswalk for degree info. If you use
 *    something different, you'll have to change the code where
 *    its been labelled.
 *
 * 2) There's other code in the DSpaceOAICatalog (i think, its been
 *    a while) that doesn't allow our restricted items to be harvested
 *    because having direct links to the PDF would not be a good thing.
 *
 * 3) Theres some data you have to add to your oaicat.properties file
 *    so LAC can read your data properly. Read the harvesting requirements
 *    and it should be in there. If its not there, email me at
 *    robyj@cc.umanitoba.ca and i'll help you sort it out.
 *
 */

package org.dspace.app.oai;

import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.dspace.core.LogManager;

import java.sql.SQLException;

import ORG.oclc.oai.server.crosswalk.Crosswalk;
import ORG.oclc.oai.server.verb.CannotDisseminateFormatException;

import org.dspace.authorize.AuthorizeManager;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DCValue;
import org.dspace.content.Item;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;

import org.dspace.eperson.Group;

import org.dspace.search.HarvestedItemInfo;


/**
 * An ETDMS Crosswalk implementation that extracts unqualified Dublin Core
 * from DSpace items into the ETD-ms format.
 *
 * @author  Jonathan Roby
 * @version $Revision: 1.0 $
 */
public class ETDMSCrosswalk extends Crosswalk
{
    private static Logger log = Logger.getLogger(DSpaceRecordFactory.class);


    /**
     * Constructor for the ETDms crosswalk class. it currently just sets the
     * header information for the spec.
     *
     * @param properties instance of properties for this application
     */
    public ETDMSCrosswalk(Properties properties)
    {
    super("http://www.ndltd.org/standards/metadata/etdms/1.0/ http://www.ndltd.org/standards/metadata/etdms/1.0/etdms.xsd");
    }

    /**
     * removes certain XML character codes (<,>,&) in a string.
     * yes, this was borrowed from the DC crosswalk
     *
     * @param string input string to "fix"
     */
    private String fixString( String string )
    {
        // Escape XML chars <, > and &
        String value = string;

        // First do &'s - need to be careful not to replace the
        // & in "&amp;" again!
        int c = -1;
        while ((c = value.indexOf("&", c + 1)) > -1)
        {
            value = value.substring(0, c) +
                    "&amp;" +
                    value.substring(c + 1);
        }

        while ((c = value.indexOf("<")) > -1)
        {
            value = value.substring(0, c) +
                    "&lt;" +
                    value.substring(c + 1);
        }

        while ((c = value.indexOf(">")) > -1)
        {
            value = value.substring(0, c) +
                    "&gt;" +
                    value.substring(c + 1);
        }
        return value;
    }

    /**
     * This has to be here as its a member of the Super class and is
     * called by the ClassFactory or another higher level class.
     *
     * @param item The item to check.
     */
    public boolean isAvailableFor(Object nativeItem)
    {
        // We have DC for everything
        return true;
    }

    /**
     * createMetadata is the workhorse of this class and creates the
     * string that represents the ETDms record.
     *
     * @param item The item to convert.
     * @return String a string containing the ETD-ms record
     */

    public String createMetadata(Object nativeItem)
        throws CannotDisseminateFormatException
    {
        Item item = ((HarvestedItemInfo) nativeItem).item;

        // Get all the DC fields in the DC record
        DCValue[] allDC = item.getDC(Item.ANY, Item.ANY, Item.ANY);

        //the result string buffer to write out
        StringBuffer metadata = new StringBuffer();

        //a whole bunch of StringBuffers used to store the ETDms elements
        //before we write them out in the correct order into the metadata
        //buffer (order specified by the ETDms spec).
        StringBuffer titleBuffer = new StringBuffer();
        StringBuffer creatorBuffer = new StringBuffer();
        StringBuffer degreelevelBuffer = new StringBuffer();
        StringBuffer languageBuffer = new StringBuffer();
        StringBuffer typeBuffer = new StringBuffer();
        StringBuffer formatBuffer = new StringBuffer();
        StringBuffer dateBuffer = new StringBuffer();
        StringBuffer hdlIDBuffer = new StringBuffer();
        StringBuffer degreedisciplineBuffer = new StringBuffer();
        StringBuffer pdfIDBuffer = new StringBuffer();
        StringBuffer publisherBuffer = new StringBuffer();
        StringBuffer TCIDBuffer = new StringBuffer();
        StringBuffer degreegrantorBuffer = new StringBuffer();
        StringBuffer subjectBuffer = new StringBuffer();
        StringBuffer abstractBuffer = new StringBuffer();
        StringBuffer contributorBuffer = new StringBuffer();
        StringBuffer formatfileBuffer = new StringBuffer();

        //JR - 17/11/2004 make a stringbuffer to store thesis fields in
        //to keep them out of the main string buffer until the end
        StringBuffer degreedata = new StringBuffer();
        boolean noWrite = false;
        boolean noDegreeWrite = false;

        String AddedHeader = "";
        String ID = "";
        String Template = "hdl.handle.net";
        String Handle = "";

        String currValue="";

        //record header added to the record output first.
        //Messy, but it works
        metadata.append("<oai_etdms:thesis xmlns=\"http://www.ndltd.org/standards/metadata/etdms/1.0/\" ")
            .append("xmlns:oai_etdms=\"http://www.ndltd.org/standards/metadata/etdms/1.0/\" ")
            .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
            .append("xsi:schemaLocation=\"http://www.ndltd.org/standards/metadata/etdms/1.0/ http://www.ndltd.org/standards/metadata/etdms/1.0/etdms.xsd\">");

        degreedata.append("<degree>");

        for (int i = 0; i < allDC.length; i++)
        {
            // Do not include description.provenance
            boolean provenance = allDC[i].qualifier != null &&
                                 allDC[i].qualifier.equals("provenance");

            if (!provenance)
            {
                String element = allDC[i].element;

                //this is the title element
                if (allDC[i].element.equals("title"))
                {
                    currValue = fixString(allDC[i].value);
                    titleBuffer.append("<title>");
                    titleBuffer.append(currValue);
                    titleBuffer.append("</title>");
                }

                // contributor.author exposed as 'creator'
                if (allDC[i].element.equals("contributor") &&
                      allDC[i].qualifier != null)
                {
                    currValue = fixString(allDC[i].value);

                    //the author has different tags than other people
                    //who are usually defined as "Supervisor" or "marking board"
                    //or whatever we want to use
            if (allDC[i].qualifier.equals("author"))
            {
                        creatorBuffer.append("<creator>")
                                     .append(currValue)
                                     .append("</creator>");
                    }
                    else
                    {
                        contributorBuffer.append("<contributor>");
                        contributorBuffer.append(currValue);
                        contributorBuffer.append("</contributor>");
                    }
                }

                //JR - 17/11/2004
                //check if this is a degree field. if it is,
                //don't write it out now, save it in the degreebuffer
                //store to be written out at the end
                //
                // MODIFY THIS ACCORDING TO YOUR DUBLIN CORE FIELDS!!!!
                //
                if ( element.equals("degree") )
                {
                    element = allDC[i].qualifier;
                    if (element.equals("name"))
                    {
                        element = "name";
                        String Value = fixString(allDC[i].value);
                        String Level = "";

                        if (Value.indexOf("Master") != -1)
                        {
                            Level = "master's";
                        }
                        if ((Value.indexOf("Ph.D") != -1) ||
                            (Value.indexOf("Doctor") != -1))
                        {
                            Level = "doctoral";
                        }

                        //get the name of the program, which is is brackets
                        if (Value.indexOf("(") != -1)
                        {
                            int start = Value.indexOf("(");
                            int end = Value.indexOf(")");
                            currValue = Value.substring(start+1,end);
                            degreelevelBuffer.append("\t<name>")
                                             .append(currValue)
                                             .append("</name>");
                        }
                        degreelevelBuffer.append("\t<level>")
                                         .append(Level)
                                         .append("</level>");
                    }
                    else if (element.equals("programme"))
                    {
                        //this is the area of study (i.e. English Literature)
                        currValue=fixString(allDC[i].value);
                        degreedisciplineBuffer.append("\t<discipline>")
                                              .append(currValue)
                                              .append("</discipline>");
                    }
                }

                if (element.equals("subject"))
                {
                    currValue = fixString(allDC[i].value);
                    subjectBuffer.append("<subject>")
                                 .append(currValue)
                                 .append("</subject>");
                }

                //this is the abstract - direct copy
                if (element.equals("description") && allDC[i].qualifier.equals("abstract"))
                {
                    currValue = fixString(allDC[i].value);
                    abstractBuffer.append("<description>");
                    abstractBuffer.append(currValue);
                    abstractBuffer.append("</description>");
                }

                //language has to be modified to work - this could be better
                if (element.equals("language"))
                {
                    String Value = fixString(allDC[i].value);
                    if (Value.length() > 2)
                    {
                      Value = Value.substring(0,2);
                    }
                    languageBuffer.append("<language>")
                                  .append(Value)
                                  .append("</language>");
                }

                //this is either a string on the format of the file or the file size in bytes
                if (element.equals("format"))
                {
                    String Value = fixString(allDC[i].value);
                    if (Value.indexOf("bytes") != -1)
                    {
                        formatBuffer.append("<format>")
                                    .append(Value)
                                    .append("</format>");
                    }
                    else
                    {
                        formatfileBuffer.append("<format>")
                                        .append(Value)
                                        .append("</format>");
                    }
                }

                //this should be set to "Thesis or Dissertation" by default in other code
                //in the submission area.
                if (element.equals("type"))
                {
                    currValue = fixString(allDC[i].value);
                    typeBuffer.append("<type>")
                              .append(currValue)
                              .append("</type>");
                }

                //mess about with the date to make the correct format
                if (element.equals("date"))
                {
                    if (allDC[i].qualifier.equals("available"))
                    {
                        //don't write out the dates - only the available date
                        currValue = allDC[i].value;
                        currValue = currValue.substring(0,10); //the YYYY-MM-DD part
                        dateBuffer.append("<date>");
                        dateBuffer.append(currValue);
                        dateBuffer.append("</date>");
                    }
                }

                //this is our handle ID string. we need the item ID (1993/14, for example)
                if (element.equals("identifier"))
                {
                  int Index = allDC[i].value.indexOf(Template);
                  if (Index != -1)
                  {
                    //this is our string - parse the last value for the ID
                    ID = allDC[i].value.substring(allDC[i].value.lastIndexOf('/')+1);
                       Handle = allDC[i].value.substring(Index+Template.length()+1);
                       currValue = fixString(allDC[i].value);
                       hdlIDBuffer.append("<identifier>");
                       hdlIDBuffer.append(currValue);
                       hdlIDBuffer.append("</identifier>");
                  }
                }
                    AddedHeader = "";
            }
        }

        String Filename = "";
        boolean Found = false;
        int BundleIdx = 1;

        //JR - 01/12/2004 - hack to make direct link to PDF
        //this is why we restrict restricted items
        try
        {
            Bundle[] Bundles = item.getBundles();
            for (int BundleSize=0; BundleSize < Bundles.length && (Found == false); BundleSize++)
            {
                Bitstream[] Bitstreams = Bundles[BundleSize].getBitstreams();
                for (int StreamCount=0; StreamCount < Bitstreams.length; StreamCount++)
                {
                    String Name = Bitstreams[StreamCount].getName();
                    if (Name.indexOf(".pdf") != -1)
                    {
                        Filename = Name;
                        BundleIdx = Bitstreams[StreamCount].getSequenceID();//StreamCount+1;
                        Found = false;
                    }
                    log.info(LogManager.getHeader(null,
                                                  "oai_stuff",
                                                  "Filename = " + Name));

                }
            }
        }
        catch (SQLException e)
        {
            // Do nothing for now
        }

        String URL = ConfigurationManager.getProperty("dspace.url");
        log.info(LogManager.getHeader(null,
            "oai_stuff",
            "BundleIdx = " + BundleIdx));
        URL += "/bitstream/" + Handle + "/" + BundleIdx + "/" + Filename;
        pdfIDBuffer.append("<identifier>")
                   .append(URL)
                   .append("</identifier>");

        //JR - 01/12/2004 - hack to allow a publisher field
        publisherBuffer.append("<publisher>University of Guelph</publisher>");

        //JR - 01/12/2004 - hack to add the TC field
        TCIDBuffer.append("<identifier>TC-OGU-")
                  .append(ID)
                  .append("</identifier>");

        //JR - 21/01/2005 - put it all together in the correct order
        metadata.append(titleBuffer);
        metadata.append(creatorBuffer);
        metadata.append(subjectBuffer);
        metadata.append(abstractBuffer);
        metadata.append(publisherBuffer);
        metadata.append(contributorBuffer);
        metadata.append(dateBuffer);
        metadata.append(typeBuffer);
        metadata.append(formatBuffer);
        metadata.append("<format>text/html</format>");
        metadata.append(formatfileBuffer);
        metadata.append(hdlIDBuffer);
        metadata.append(pdfIDBuffer);
        metadata.append(TCIDBuffer);
        metadata.append(languageBuffer);
        metadata.append("<degree>");
        metadata.append(degreelevelBuffer);
        metadata.append(degreedisciplineBuffer);

        //JR - 01/12/2004 - hack to add degree:grantor field to degree data
        metadata.append("\t<grantor>University of Guelph</grantor>");

        //JR 17/11/2004
        //finish off the degreebuffer buffer and append to the metadata buffer
        metadata.append("</degree>");
        metadata.append("</oai_etdms:thesis>");

        return metadata.toString();
    }
}
