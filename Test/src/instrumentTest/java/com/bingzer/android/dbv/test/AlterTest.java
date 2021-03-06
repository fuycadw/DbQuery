package com.bingzer.android.dbv.test;

import android.content.Context;
import android.test.AndroidTestCase;

import com.bingzer.android.dbv.DbQuery;
import com.bingzer.android.dbv.IDatabase;
import com.bingzer.android.dbv.ITable;
import com.bingzer.android.dbv.sqlite.SQLiteBuilder;

/**
 * Created by Ricky Tobing on 8/16/13.
 */
public class AlterTest extends AndroidTestCase {

    IDatabase db;

    @Override
    public void setUp(){
        db = DbQuery.getDatabase("AlterDb");
        db.open(1, new SQLiteBuilder() {
            @Override
            public Context getContext() {
                return AlterTest.this.getContext();
            }

            @Override
            public void onModelCreate(IDatabase database, IDatabase.Modeling modeling) {
                modeling.add("Person")
                        .addPrimaryKey("Id")
                        .addText("Name");
            }
        });
    }

    @Override
    public void tearDown(){
        db.close();
        getContext().deleteDatabase("AlterDb");
    }

    public void testRenameColumn(){
        assertNotNull(db.get("Person"));
        // alter
        ITable table = db.get("Person");
        table.alter().rename("PersonAltered");
        assertTrue(table.getName().equals("PersonAltered"));

        // re check person
        assertNull(db.get("Person"));
        // should not be null
        assertNotNull(db.get("PersonAltered"));

        // renamed to Person
        table.alter().rename("Person");
    }

    public void testAddColumn(){
        assertTrue(!db.get("Person").getColumns().contains("Address"));
        // we'll add address column
        db.get("Person").alter().addColumn("Address", "Text");

        assertTrue(db.get("Person").getColumns().contains("Address"));
    }

    public void testRemoveColumn(){
        try{
            // should throw exception
            db.get("Person").alter().removeColumn("Name");
            assertTrue(false);
        }
        catch (Exception e){
            assertTrue(true);
        }
    }
}
