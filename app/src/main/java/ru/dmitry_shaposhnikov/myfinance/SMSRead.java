package ru.dmitry_shaposhnikov.myfinance;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.net.Uri;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import java.util.Date;
import java.text.SimpleDateFormat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import android.support.v4.content.ContextCompat;
import android.content.pm.PackageManager;
import android.Manifest;
import android.support.v4.app.ActivityCompat;


/**
 * Created by dmitry on 20.03.17.
 */

public class SMSRead extends AppCompatActivity {

    DBHelper dbHelper;
    SQLiteDatabase db;
    private Map<Integer, Long> last_sms_date = new LinkedHashMap<Integer, Long>();
    private Map<Integer, String> last_sms_phone = new LinkedHashMap<Integer, String>();
    private Map<Integer, String> last_sms_template = new LinkedHashMap<Integer, String>();
    private final int MY_PERMISSIONS_REQUEST_READ_SMS = 0;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(this,Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_SMS}, MY_PERMISSIONS_REQUEST_READ_SMS);
            return;
        } else {
            ReadSMS();
        }

        //LinearLayout linearLayout = new LinearLayout(this);
        //TextView valueTV = new TextView(this);
        //linearLayout.addView(valueTV);
        //setContentView(linearLayout);

        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_SMS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ReadSMS();
                    finish();
                } else {
                    // permission denied
                }
                return;
            }
        }
    }

    public void ReadSMS() {

        dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();

        // Last update

        Cursor clg = db.rawQuery("SELECT source, phone, template, MAX(date) as date FROM sms_logs GROUP BY source, phone, template", null);
        if (clg.moveToFirst()) {
            do {
                last_sms_date.put(clg.getInt(clg.getColumnIndex("source")), clg.getLong(clg.getColumnIndex("date")));
                last_sms_phone.put(clg.getInt(clg.getColumnIndex("source")), clg.getString(clg.getColumnIndex("phone")));
                last_sms_template.put(clg.getInt(clg.getColumnIndex("source")), clg.getString(clg.getColumnIndex("template")));
            } while (clg.moveToNext());
        }

        // Read sources from database

        Cursor cdb = db.query("sources", null, "type = 1", null, null, null, null);
        if (cdb.moveToFirst()) {
            do {
                int source_id = cdb.getInt(cdb.getColumnIndex("id"));
                String phone = cdb.getString(cdb.getColumnIndex("phone"));
                String template = cdb.getString(cdb.getColumnIndex("template"));

                // Write to log

                ContentValues cv0 = new ContentValues();

                cv0.put("phone", phone);
                cv0.put("template", template);
                cv0.put("source", source_id);
                cv0.put("date", new Date().getTime());

                db.insert("sms_logs", null, cv0);

                // Read SMSs

                Uri uri = Uri.parse("content://sms/inbox");

                Cursor c;
                if( last_sms_date.containsKey(source_id) && phone.equals(last_sms_phone.get(source_id)) && template.equals(last_sms_template.get(source_id)) ) {
                    c = getContentResolver().query(uri, new String[]{"date", "body"}, "address = ? AND date > ?", new String[]{phone, String.valueOf(last_sms_date.get(source_id))}, null);
                } else {
                    c = getContentResolver().query(uri, new String[]{"date", "body"}, "address = ?", new String[]{phone}, null);
                }

                String body;
                Date date;

                if( c.moveToFirst() ) {
                    do {

                        date = new Date(c.getLong(c.getColumnIndex("date")));
                        body = c.getString(c.getColumnIndex("body"));

                        // Matcher
                        Matcher m = Pattern.compile(template+"([\\s\\d,.]+)").matcher(body);
                        while( m.find() ) {
                            try {

                                float amount = Float.parseFloat(m.group(1).replace(",", ".").replace(" ", ""));

                                // Save in Database

                                ContentValues cv = new ContentValues();

                                cv.put("source", source_id);
                                cv.put("auto", 1);
                                cv.put("datetime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date));
                                cv.put("amount", amount);
                                cv.put("comment", body);

                                db.insert("transactions", null, cv);

                            } catch (NumberFormatException e){
                                //
                            }
                        }

                    } while (c.moveToNext());

                }
                c.close();

            } while (cdb.moveToNext());
        }
        cdb.close();

    }

}
