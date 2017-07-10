package ru.dmitry_shaposhnikov.myfinance;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.XAxis.XAxisPosition;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.Legend.LegendPosition;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class HistoryGraph extends AppCompatActivity {

    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private Map<Integer, TreeMap<Long,Float>> stock_prices = new LinkedHashMap<Integer, TreeMap<Long,Float>>();
    private Map<Integer, Float> amount_by_source = new LinkedHashMap<Integer, Float>();
    private SimpleDateFormat dayf = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat datef;
    private LineChart graph;
    private ArrayList<Integer> colors_palette = new ArrayList<>();

    private int days_count = 365;
    private boolean group_by_days = true;
    private boolean show_detailed = true;
    private boolean show_month = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history_graph);

        setTitle("История");

        dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();

        // Read stock prices

        read_stock_prices();

        // Read first transactions

        // If we want to determine the first day
        //Cursor cd = db.rawQuery("SELECT julianday('now') - julianday( MIN(datetime) ) as days FROM transactions", null);
        //if( cd.moveToFirst() ) {
        //    days_count = cd.getInt(cd.getColumnIndex("days")) + 2;
        //}

        Cursor ctr = db.rawQuery("SELECT *, MAX(datetime) as max FROM transactions WHERE datetime < datetime('now', '-"+String.valueOf(days_count-1)+" days') GROUP BY source", null);
        if (ctr.moveToFirst()) {
            do {
                amount_by_source.put(ctr.getInt(ctr.getColumnIndex("source")), ctr.getFloat(ctr.getColumnIndex("amount")));
            } while (ctr.moveToNext());
        }
        ctr.close();

        // Generate colors palette

        generate_colors_palette();

        // Buttons

        ((Button)findViewById(R.id.group_by_day)).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                group_by_days = true;
                show_month = true;
                UpdateGraph();
            }
        });

        ((Button)findViewById(R.id.group_by_month)).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                group_by_days = false;
                show_month = false;
                UpdateGraph();
            }
        });

        ((Button)findViewById(R.id.show_month)).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                show_month = true;
                UpdateGraph();
            }
        });

        ((Button)findViewById(R.id.show_year)).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                show_month = false;
                UpdateGraph();
            }
        });

        ((Button)findViewById(R.id.details_all)).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                show_detailed = true;
                UpdateGraph();
            }
        });

        ((Button)findViewById(R.id.details_sum)).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                show_detailed = false;
                UpdateGraph();
            }
        });

        graph = (LineChart) findViewById(R.id.history_graph);
    }

    @Override
    protected void onStart() {
        super.onStart();

        UpdateGraph();
    }

    public void UpdateGraph() {

        // String grouping format

        String strftime;
        if( group_by_days )  {
            datef = new SimpleDateFormat("yyyy-MM-dd");
            strftime = "%Y-%m-%d";
        } else {
            datef = new SimpleDateFormat("yyyy-MM");
            strftime = "%Y-%m";
        }

        // Compile Budget

        graph.clear();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1-days_count);

        // Read sources

        ArrayList<Integer> source_ids = new ArrayList<>();
        ArrayList<String> source_names = new ArrayList<>();
        ArrayList<Integer> source_currency = new ArrayList<>();

        Cursor cs = db.query("sources", new String[]{"id","name","currency"}, null, null, null, null, null);
        if (cs.moveToFirst()) {
            do {
                source_ids.add(cs.getInt(cs.getColumnIndex("id")));
                source_names.add(cs.getString(cs.getColumnIndex("name")));
                source_currency.add(cs.getInt(cs.getColumnIndex("currency")));
            } while (cs.moveToNext());
        }
        cs.close();

        // Whole budget

        Map<String, Float> whole_budget = new LinkedHashMap<String, Float>();

        // Generate Dates

        ArrayList xDates = new ArrayList<>();

        for( int d = 0; d < days_count; d++ ) {

            Date cur_date = calendar.getTime();
            String day_str = dayf.format(cur_date);
            xDates.add(day_str);

            calendar.add(Calendar.DATE, 1);
        }

        calendar.add(Calendar.DATE, -days_count);
        LineData lineData = new LineData(xDates);

        // Read transactions

        for(int s = 0; s < source_ids.size(); s++ ) {

            Map<String, Float> amount_by_day = new LinkedHashMap<String, Float>();

            // Read transactions

            Cursor ctr = db.rawQuery("SELECT *, strftime('"+strftime+"', datetime) as day, MAX(datetime) as max FROM transactions WHERE datetime > "+datef.format(calendar.getTime())+" AND source = "+String.valueOf(source_ids.get(s))+" GROUP BY strftime('"+strftime+"', datetime)", null);
            if (ctr.moveToFirst()) {
                do {
                    amount_by_day.put(ctr.getString(ctr.getColumnIndex("day")), ctr.getFloat(ctr.getColumnIndex("amount")));
                } while (ctr.moveToNext());
            }
            ctr.close();

            // Plot Graph

            ArrayList<Entry> series = new ArrayList<Entry>();

            float cur_price = -1;

            if( amount_by_source.containsKey(source_ids.get(s)) ) {
                cur_price = amount_by_source.get(source_ids.get(s));
            }

            for( int d = 0; d < days_count; d++ ) {

                Date cur_date = calendar.getTime();
                String day_str = dayf.format(cur_date);
                String date_str = datef.format(cur_date);

                if( amount_by_day.containsKey(date_str) ) {
                    cur_price = amount_by_day.get(date_str);
                }

                if( cur_price >= 0 ) {

                    float res_price = cur_price;

                    if( source_currency.get(s) > 0 ) {
                        res_price = res_price * nearest_price(source_currency.get(s), cur_date);
                    }

                    if( show_detailed ) {
                        series.add(new Entry(res_price, d));
                    }

                    if( whole_budget.containsKey(day_str) ) {
                        whole_budget.put(day_str,whole_budget.get(day_str)+res_price);
                    } else {
                        whole_budget.put(day_str,res_price);
                    }

                }

                calendar.add(Calendar.DATE, 1);
            }

            calendar.add(Calendar.DATE, -days_count);

            if( !series.isEmpty() ) {
                LineDataSet dataSet = new LineDataSet(series, source_names.get(s));
                dataSet.setColor(get_color(s));
                dataSet.setDrawCircles(false);
                dataSet.setDrawValues(false);
                lineData.addDataSet(dataSet);
            }
        }

        // Whole budget series

        ArrayList<Entry> series = new ArrayList<Entry>();

        float cur_price = -1;

        for( int d = 0; d < days_count; d++ ) {

            Date cur_date = calendar.getTime();
            String day_str = dayf.format(cur_date);

            if( whole_budget.containsKey(day_str) ) {
                cur_price = whole_budget.get(day_str);
            }

            if( cur_price >= 0 ) {
                series.add(new Entry(cur_price, d));
            }

            calendar.add(Calendar.DATE, 1);
        }

        if( !series.isEmpty() ) {
            LineDataSet dataSet = new LineDataSet(series, "Сумма");
            dataSet.setColor(Color.BLACK);
            dataSet.setDrawCircles(false);
            dataSet.setDrawValues(false);
            lineData.addDataSet(dataSet);
        }

        // Graph settings

        graph.fitScreen();

        if( show_month ) {
            graph.zoom(10f,1f,0f,0f);
        }

        Legend l = graph.getLegend();
        l.setPosition(LegendPosition.LEFT_OF_CHART_INSIDE);
        l.setXOffset(55f);
        l.setForm(Legend.LegendForm.CIRCLE);

        graph.moveViewToX(Float.valueOf(days_count));
        graph.setDescription("");
        graph.getAxisRight().setEnabled(false);
        graph.getXAxis().setPosition(XAxisPosition.BOTTOM);

        graph.setData(lineData);
        graph.invalidate();

    }

    public void read_stock_prices() {

        for( int stock_id = 1; stock_id < 3; stock_id++ ) {

            stock_prices.put(stock_id, new TreeMap<Long,Float>());

            String year_ago_str = String.valueOf(new Date().getTime() - DateUtils.YEAR_IN_MILLIS);
            Cursor cs = db.query("stocks", null, "currency = "+String.valueOf(stock_id)+" AND date > "+year_ago_str, null, null, null, null);
            if (cs.moveToFirst()) {
                do {
                    stock_prices.get(stock_id).put(cs.getLong(cs.getColumnIndex("date")), cs.getFloat(cs.getColumnIndex("price")));
                } while (cs.moveToNext());
            } else {
                stock_prices.get(stock_id).put(new Date().getTime(),Float.valueOf(50));
            }
            cs.close();

        }

    }

    public Float nearest_price(int stock_id, Date date) {
        return stock_prices.get(stock_id).floorEntry(date.getTime()).getValue();
    }

    public void generate_colors_palette() {
        colors_palette.clear();
        colors_palette.add(Color.rgb(63,222,0));
        colors_palette.add(Color.rgb(10,0,222));
        colors_palette.add(Color.rgb(226,0,0));
        colors_palette.add(Color.rgb(222,214,0));
        colors_palette.add(Color.rgb(222,0,183));
        colors_palette.add(Color.rgb(222,120,0));
        colors_palette.add(Color.rgb(80,234,229));
        colors_palette.add(Color.rgb(123,123,123));
        colors_palette.add(Color.rgb(171,54,159));
        colors_palette.add(Color.rgb(217,194,47));
    }

    public int get_color(int i) {
        return colors_palette.get(i % colors_palette.size());
    }


}
