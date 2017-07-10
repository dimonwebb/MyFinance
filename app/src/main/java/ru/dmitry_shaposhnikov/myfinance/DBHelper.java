package ru.dmitry_shaposhnikov.myfinance;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

class DBHelper extends SQLiteOpenHelper {

    public DBHelper(Context context) {
        super(context, "myDB", null, 4);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL("create table sources (id integer primary key autoincrement," +
                "name text," +
                "type int," +
                "currency int," +
                "phone text," +
                "template text);");

        db.execSQL("create table transactions (id integer primary key autoincrement," +
                "source int," +
                "auto int," +
                "datetime datetime," +
                "amount real," +
                "comment text);");

        db.execSQL("create index tr_source_inx on transactions (source);");
        db.execSQL("create index tr_datetime_inx on transactions (datetime);");

        db.execSQL("create table sms_logs (id integer primary key autoincrement," +
                "phone text," +
                "template text," +
                "source int," +
                "date long);");

        db.execSQL("create table stocks (id integer primary key autoincrement," +
                "currency int," +
                "price real," +
                "date long);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        if( oldVersion <= 1 ) {
            db.execSQL("delete from sms_logs;");
            db.execSQL("delete from transactions where source in (select id from sources where type = 1);");
            db.execSQL("alter table sms_logs add template text after phone;");
        }

        if( oldVersion <= 2 ) {
            db.execSQL("delete from sms_logs;");
            db.execSQL("delete from transactions where source in (select id from sources where type = 1);");
            db.execSQL("alter table sms_logs add source int after template;");
        }

        if( oldVersion <= 3 ) {
            db.execSQL("create index tr_source_inx on transactions (source);");
            db.execSQL("create index tr_datetime_inx on transactions (datetime);");
        }

    }
}
