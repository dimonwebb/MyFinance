package ru.dmitry_shaposhnikov.myfinance;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.view.Gravity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.Legend.LegendPosition;
import com.github.mikephil.charting.components.XAxis.XAxisPosition;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarData;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Saldo extends AppCompatActivity {

    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private BarChart graph;
    DecimalFormat df;
    private Map<Integer, TreeMap<Long,Float>> stock_prices = new LinkedHashMap<Integer, TreeMap<Long,Float>>();
    private Map<Integer, Float> money_in_source = new LinkedHashMap<Integer, Float>();
    private Map<Integer, Integer> source_currency = new LinkedHashMap<Integer, Integer>();
    private Map<String, LinkedHashMap<String,Float>> money_in_month = new LinkedHashMap<String, LinkedHashMap<String, Float>>();
    SimpleDateFormat datetime_format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    SimpleDateFormat month_format = new SimpleDateFormat("yyyy-MM");
    SimpleDateFormat day_format = new SimpleDateFormat("yyyy-MM-dd");

    private boolean remove_replies = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.saldo);

        setTitle("Сальдо");

        dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();

        // Read stock prices

        read_stock_prices();

        // Numbers format

        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        df = new DecimalFormat("###,##0.00", symbols);

        // CheckBox

        ((CheckBox)findViewById(R.id.remove_replies)).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if( remove_replies ) {
                    remove_replies = false;
                    ((CheckBox)findViewById(R.id.remove_replies)).setChecked(false);
                } else {
                    ((CheckBox)findViewById(R.id.remove_replies)).setChecked(true);
                    remove_replies = true;
                }
                UpdateGraph();
            }
        });

        ((TextView)findViewById(R.id.remove_replies_text)).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                ((CheckBox)findViewById(R.id.remove_replies)).callOnClick();
            }
        });

        graph = (BarChart) findViewById(R.id.stats_graph);
    }

    @Override
    protected void onStart() {
        super.onStart();

        UpdateGraph();
    }

    public void UpdateGraph() {

        //----------------------------------------
        // Read sources

        Cursor cs = db.query("sources", new String[]{"id","name","currency"}, null, null, null, null, null);
        if (cs.moveToFirst()) {
            do {
                source_currency.put(cs.getInt(cs.getColumnIndex("id")),cs.getInt(cs.getColumnIndex("currency")));
            } while (cs.moveToNext());
        }
        cs.close();

        //----------------------------------------
        // Read transactions

        money_in_source.clear();
        money_in_month.clear();
        String last_month = "";
        String last_day = "";
        LinkedHashMap<String, Float> month_row = new LinkedHashMap<String, Float>();
        ArrayList<Float> intraday_money = new ArrayList<>();

        Cursor c = db.query("transactions", null, null, null, null, null, "datetime");

        if (c.moveToFirst()) {
            do {

                int source_id = c.getInt(c.getColumnIndex("source"));

                // Date

                Date date;

                try {
                    date = datetime_format.parse(c.getString(c.getColumnIndex("datetime")));
                } catch (ParseException e) {
                    date = new Date();
                }

                // Money delta

                float money_delta;
                float money_now = c.getFloat(c.getColumnIndex("amount"));

                if( source_currency.get(source_id) > 0 ) {
                    money_now = money_now * nearest_price(source_currency.get(source_id), date);
                }

                if( money_in_source.containsKey(source_id) ) {
                    float money_last = money_in_source.get(source_id);
                    money_delta = money_now - money_last;
                } else {
                    money_delta = money_now;
                }

                money_in_source.put(source_id, money_now);

                // Day grouping

                if( remove_replies ) {

                    String day = day_format.format(date);

                    if (!last_day.equals(day)) {
                        if( intraday_money.size() > 1 ) {
                            for (int i = 0; i < intraday_money.size(); i++) {
                                int j = intraday_money.indexOf(-intraday_money.get(i));
                                if (j >= 0) {
                                    month_row.put("incr", month_row.get("incr") - Math.abs(intraday_money.get(i)));
                                    month_row.put("decr", month_row.get("decr") + Math.abs(intraday_money.get(i)));
                                    intraday_money.set(i, 0f);
                                    intraday_money.set(j, 0f);
                                }
                            }
                        }
                        intraday_money.clear();
                        last_day = day;
                    }

                    intraday_money.add(money_delta);
                }

                // Month grouping

                String month = month_format.format(date);

                if( !last_month.equals(month) ) {
                    if( !last_month.equals("") )
                        money_in_month.put(last_month, new LinkedHashMap<String, Float>(month_row));
                    month_row.put("incr",0f);
                    month_row.put("decr",0f);
                    month_row.put("saldo",0f);
                    last_month = month;
                }

                if( money_delta > 0 )
                    month_row.put("incr", month_row.get("incr") + money_delta);

                if( money_delta < 0 )
                    month_row.put("decr", month_row.get("decr") + money_delta);

                month_row.put("saldo", month_row.get("saldo") + money_delta);

            } while (c.moveToNext());
        }
        c.close();

        money_in_month.put(last_month, month_row);

        //----------------------------------------
        // Get data

        List<String> entriesMonth = new ArrayList<>();
        List<BarEntry> entriesGroup1 = new ArrayList<>();
        List<BarEntry> entriesGroup2 = new ArrayList<>();
        List<BarEntry> entriesGroup3 = new ArrayList<>();

        int i = 0;

        for (Map.Entry<String, LinkedHashMap<String, Float>> month_r : money_in_month.entrySet()) {

            entriesMonth.add(month_r.getKey());
            entriesGroup1.add(new BarEntry(month_r.getValue().get("incr"),i));
            entriesGroup2.add(new BarEntry(month_r.getValue().get("decr"),i));
            entriesGroup3.add(new BarEntry(month_r.getValue().get("saldo"),i));

            i++;
        }

        //----------------------------------------
        // Update graph

        graph.clear();

        BarDataSet set1 = new BarDataSet(entriesGroup1, "Приход");
        BarDataSet set2 = new BarDataSet(entriesGroup2, "Расход");
        BarDataSet set3 = new BarDataSet(entriesGroup3, "Сальдо");

        set1.setColor(Color.rgb(0,128,0));
        set2.setColor(Color.rgb(128,0,0));
        set3.setColor(Color.rgb(0,0,128));

        set1.setDrawValues(false);
        set2.setDrawValues(false);
        set3.setDrawValues(false);

        List<BarDataSet> BarDataSets = new ArrayList<>();
        BarDataSets.add(set1);
        BarDataSets.add(set2);
        BarDataSets.add(set3);

        BarData data = new BarData(entriesMonth, BarDataSets);
        data.setGroupSpace(0.07f);

        graph.fitScreen();

        Legend l = graph.getLegend();
        l.setPosition(LegendPosition.ABOVE_CHART_CENTER);
        l.setForm(Legend.LegendForm.CIRCLE);
        //l.setYOffset(20f);

        graph.setDescription("");
        graph.getAxisRight().setEnabled(false);
        graph.getXAxis().setPosition(XAxisPosition.BOTTOM);

        graph.setData(data);
        graph.invalidate();

        //----------------------------------------
        // Update table

        TableLayout ll = (TableLayout) findViewById(R.id.budget_table);

        int chlds = ll.getChildCount();
        if( chlds > 1 ) {
            ll.removeViews(1,chlds-1);
        }

        TextView tv1, tv2, tv3, tv4;

        int cnt = set1.getEntryCount();

        for( i = 0; i < cnt; i++ ) {

            int j = cnt-i-1;

            TableRow row = new TableRow(this);
            TableRow.LayoutParams lp = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT);
            row.setLayoutParams(lp);

            tv1 = new TextView(this);
            tv1.setText(entriesMonth.get(j));
            tv1.setBackgroundResource(R.drawable.border);
            tv1.setPadding(5,5,5,5);
            tv1.setGravity(Gravity.CENTER);
            row.addView(tv1);

            tv2 = new TextView(this);
            tv2.setText(df.format(entriesGroup1.get(j).getVal()));
            tv2.setBackgroundResource(R.drawable.border);
            tv2.setPadding(5,5,5,5);
            tv2.setGravity(Gravity.RIGHT);
            row.addView(tv2);

            tv3 = new TextView(this);
            tv3.setText(df.format(entriesGroup2.get(j).getVal()));
            tv3.setBackgroundResource(R.drawable.border);
            tv3.setPadding(5,5,5,5);
            tv3.setGravity(Gravity.RIGHT);
            row.addView(tv3);

            tv4 = new TextView(this);
            tv4.setText(df.format(entriesGroup3.get(j).getVal()));
            tv4.setBackgroundResource(R.drawable.border);
            tv4.setPadding(5,5,5,5);
            tv4.setGravity(Gravity.RIGHT);
            row.addView(tv4);

            row.setBackgroundResource(R.drawable.border);
            row.setPadding(1,1,1,1);
            ll.addView(row,i+1);

        }

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
        Map.Entry<Long,Float> entry = stock_prices.get(stock_id).floorEntry(date.getTime());
        if( entry == null ) {
            return Float.valueOf(50);
        } else {
            return entry.getValue();
        }
    }
}
