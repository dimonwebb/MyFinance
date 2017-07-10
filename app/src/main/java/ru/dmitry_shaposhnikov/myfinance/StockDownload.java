package ru.dmitry_shaposhnikov.myfinance;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.HttpURLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;
import java.net.URLConnection;
import android.os.AsyncTask;
import android.text.format.DateUtils;


/**
 * Created by dmitry on 20.03.17.
 */

public class StockDownload extends AppCompatActivity {

    DBHelper dbHelper;
    SQLiteDatabase db;
    private Map<Integer, Long> last_stock_read = new LinkedHashMap<Integer, Long>();
    private Map<Integer, String> stock_em = new LinkedHashMap<Integer, String>();

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if( !this.isOnline() ) {
            finish();
            return;
        }

        dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();

        // Read last stock download date

        Cursor c = db.rawQuery("SELECT currency, MAX(date) as date FROM stocks GROUP BY currency", null);
        if (c.moveToFirst()) {
            do {
                last_stock_read.put(c.getInt(c.getColumnIndex("currency")), c.getLong(c.getColumnIndex("date")));
            } while (c.moveToNext());
        }

        stock_em.put(1,"182456");
        stock_em.put(2,"182441");

        new MyDownloadTask().execute();

        finish();
    }

    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnectedOrConnecting();
    }

    class MyDownloadTask extends AsyncTask<Void,Void,Void> {

        @Override
        protected Void doInBackground(Void... params) {

            for( int stock_id = 1; stock_id < 3; stock_id++ ) {

                Date fdate = start_date(stock_id);
                Date tdate = new Date();

                if( date_in_int(fdate) < date_in_int(tdate) ) {

                    String url = "http://export.finam.ru/X.txt?market=45&apply=0&p=8&f=X&e=.txt&dtf=1&tmf=1&MSOR=1&mstime=on&mstimever=1&sep=1&sep2=1&datf=4" +
                            "&em=" + stock_em.get(stock_id) +
                            "&df=" + new SimpleDateFormat("d").format(fdate) +
                            "&mf=" + String.valueOf(Integer.valueOf(new SimpleDateFormat("M").format(fdate))-1) +
                            "&yf=" + new SimpleDateFormat("yyyy").format(fdate) +
                            "&dt=" + new SimpleDateFormat("d").format(tdate) +
                            "&mt=" + String.valueOf(Integer.valueOf(new SimpleDateFormat("M").format(tdate))-1) +
                            "&yt=" + new SimpleDateFormat("yyyy").format(tdate);

                    //Log.d("WWW","GET STOCKS FROM URL: "+url);

                    String data = file_get_contents(url);
                    upload_stocks(data, stock_id);

                }
            }

            return null;
        }

        private Integer date_in_int(Date date) {
            return Integer.valueOf(new SimpleDateFormat("yyyyMMdd").format(date));
        }

        private Date start_date(int stock_id) {
            if( last_stock_read.containsKey(stock_id) ) {
                return new Date(last_stock_read.get(stock_id) + DateUtils.DAY_IN_MILLIS);
            } else {
                return new Date(new Date().getTime() - DateUtils.YEAR_IN_MILLIS);
            }
        }

        private String file_get_contents(String url) {

            try {

                URLConnection connection = (new URL(url)).openConnection();
                connection.connect();

                InputStream in = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder html = new StringBuilder();
                for (String line; (line = reader.readLine()) != null; ) {
                    html.append(line+"\n");
                }
                in.close();

                return html.toString();

            } catch (IOException e) {
                //
            }

            return null;
        }

        private void upload_stocks(String data, int stock_id) {

            String[] rows = data.split("\n");

            for( int i = 0; i < rows.length; i++ ) {
                String[] row = rows[i].split(",");
                if(row.length > 0 ) {
                    try {

                        ContentValues cv = new ContentValues();

                        cv.put("currency", stock_id);
                        cv.put("price", Float.parseFloat(row[4]));
                        cv.put("date", new SimpleDateFormat("yyyyMMdd").parse(row[2]).getTime());

                        db.insert("stocks", null, cv);

                    } catch (ParseException e) {
                        //
                    }
                }
            }
        }

    }


}


