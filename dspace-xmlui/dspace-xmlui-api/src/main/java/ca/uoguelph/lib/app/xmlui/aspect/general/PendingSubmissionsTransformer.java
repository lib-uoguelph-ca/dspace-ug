package ca.uoguelph.lib.app.xmlui.aspect.general;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.Row;
import org.dspace.app.xmlui.wing.element.Table;
import org.dspace.eperson.EPerson;

// TODO: JavaDoc
public class PendingSubmissionsTransformer extends AbstractDSpaceTransformer
{
    public void addBody(Body body) throws WingException, SQLException
    {
        EPerson currentUser = context.getCurrentUser();

        if (currentUser != null)
        {
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

            Connection connection = context.getDBConnection();
            Statement statement =
                connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
                                           ResultSet.CONCUR_UPDATABLE);

            ResultSet dcTitleField = statement.executeQuery(title_field_query);
            dcTitleField.next();
            String dcTitleID = dcTitleField.getString("metadata_field_id");

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

            if (rowCount > 0)
            {
                // Add the pending-submissions div and table.
                Division div = body.addDivision("pending-submissions");
                div.setHead("Your pending submissions");
                div.addPara("The following submissions are awaiting approval.");

                Table table =
                    div.addTable("pending-submissions", rowCount + 1, 2);
                Row header = table.addRow(Row.ROLE_HEADER);
                header.addCellContent("Item");
                header.addCellContent("Collection");

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
