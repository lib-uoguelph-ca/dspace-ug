/**
 * PendingSubmissionsTransformer.java
 *
 * This file is released under the same license as DSpace itself.
 *
 * @author Chris Charles ccharles@uoguelph.ca
 */

package ca.uoguelph.lib.app.xmlui.aspect.general;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.Row;
import org.dspace.app.xmlui.wing.element.Table;
import org.dspace.eperson.EPerson;


/**
 * Add a table containing pending submissions to the DRI document.
 *
 * Pending submissions are items which have been submitted but have not yet
 * been accepted into any collections due to workflow restrictions.
 */
public class PendingSubmissionsTransformer extends AbstractDSpaceTransformer
{
    private static final Message T_your_pending_submissions =
        message("xmlui.general.pending_submissions.title");
    private static final Message T_pending_submissions_intro =
        message("xmlui.general.pending_submissions.intro");
    private static final Message T_pending_submissions_item_title =
        message("xmlui.general.pending_submissions.item_title");
    private static final Message T_pending_submissions_collection_title =
        message("xmlui.general.pending_submissions.collection_title");

    /**
     * Add a new pending-submissions div and table to the DRI body and
     * populate from the database.
     *
     * @param body The DRI document's body element.
     */
    public void addBody(Body body) throws WingException, SQLException
    {
        EPerson currentUser = context.getCurrentUser();

        if (currentUser != null)
        {
            Connection connection = context.getDBConnection();
            Statement statement =
                connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
                                           ResultSet.CONCUR_UPDATABLE);

            // Find out which metadata_field_id represents dc.title
            String title_field_query =
                "SELECT metadata_field_id" +
                "    FROM metadatafieldregistry AS fields," +
                "            metadataschemaregistry AS schemas" +
                "    WHERE fields.metadata_schema_id" +
                "            = schemas.metadata_schema_id" +
                "        AND schemas.short_id = 'dc'" +
                "        AND fields.element = 'title'" +
                "        AND fields.qualifier IS NULL" +
                "    LIMIT 1;";

            ResultSet dcTitleField = statement.executeQuery(title_field_query);
            dcTitleField.next();
            String dcTitleID = dcTitleField.getString("metadata_field_id");

            // Retrieve a list of pending submissions
            String submissions_query =
                "SELECT metadatavalue.text_value AS item_title," +
                "        collection.name AS collection_name" +
                "    FROM workflowitem, item, collection, metadatavalue" +
                "    WHERE workflowitem.item_id = item.item_id" +
                "        AND workflowitem.collection_id" +
                "            = collection.collection_id" +
                "        AND item.item_id = metadatavalue.item_id" +
                "        AND metadatavalue.metadata_field_id = " + dcTitleID +
                "        AND item.submitter_id = " + currentUser.getID() +
                "    ORDER BY collection_name, item_title;";

            ResultSet submissions = statement.executeQuery(submissions_query);

            // Unfortunately ResultSets do not have a method to show the
            // number of records returned so we have to fake it.
            submissions.last();
            int rowCount = submissions.getRow();
            submissions.beforeFirst();

            // Add the pending-submissions div and table if pending
            // submissions exist.
            if (rowCount > 0)
            {
                Division div = body.addDivision("pending-submissions");
                div.setHead(T_your_pending_submissions);
                div.addPara(T_pending_submissions_intro);

                Table table =
                    div.addTable("pending-submissions", rowCount + 1, 2);
                Row header = table.addRow(Row.ROLE_HEADER);
                header.addCellContent(T_pending_submissions_item_title);
                header.addCellContent(T_pending_submissions_collection_title);

                while (submissions.next())
                {
                    Row row = table.addRow(Row.ROLE_DATA);
                    row.addCellContent(submissions.getString("item_title"));
                    row.addCellContent(
                        submissions.getString("collection_name"));
                }
            }
        }
    }
}
