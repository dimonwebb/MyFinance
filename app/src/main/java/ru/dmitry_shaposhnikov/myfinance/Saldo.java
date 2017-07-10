package ru.dmitry_shaposhnikov.myfinance;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
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
import java.util.ArrayList;
import java.util.List;

public class Saldo extends AppCompatActivity {

    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private BarChart graph;
    DecimalFormat df;

    private boolean group_by_days = true;
    private boolean group_by_source = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.saldo);

        setTitle("Сальдо");

        dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();

        // Numbers format

        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        df = new DecimalFormat("###,##0.00", symbols);

        // CheckBox

        ((CheckBox)findViewById(R.id.group_by_day)).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if( group_by_days ) {
                    group_by_days = false;
                    ((CheckBox)findViewById(R.id.group_by_day)).setChecked(false);
                } else {
                    ((CheckBox)findViewById(R.id.group_by_day)).setChecked(true);
                    group_by_days = true;
                }
                UpdateGraph();
            }
        });

        ((TextView)findViewById(R.id.group_by_day_text)).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                ((CheckBox)findViewById(R.id.group_by_day)).callOnClick();
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
        // Query

        String gr_str1, gr_str2, gr_str3 = "";

        if( group_by_days ) {
            gr_str1 = "strftime('%Y-%m-%d',t3.datetime)";
            gr_str2 = "tr6.day";
        } else {
            gr_str1 = "t3.id";
            gr_str2 = "tr6.id";
        }

        if( group_by_source ) {
            gr_str3 = "tr4.source, ";
        }

        Cursor ctr = db.rawQuery("SELECT tr4.source, tr4.month,\n" +
                        "  SUM( CASE WHEN tr4.diff > 0 THEN tr4.diff ELSE 0 END ) as incr,\n" +
                        "  SUM( CASE WHEN tr4.diff < 0 THEN tr4.diff ELSE 0 END ) as decr,\n" +
                        "  SUM( CASE WHEN tr4.diff > 0 THEN tr4.diff ELSE 0 END ) + SUM( CASE WHEN tr4.diff < 0 THEN tr4.diff ELSE 0 END ) as saldo\n" +
                        "FROM (\n" +
                        "  SELECT tr6.source,\n" +
                        "    tr6.month,\n" +
                        "    tr6.day,\n" +
                        "    SUM( CASE\n" +
                        "      WHEN tr6.currency = 0 THEN tr6.diff\n" +
                        "      WHEN k2.price IS NULL THEN tr6.diff*50\n" +
                        "      ELSE tr6.diff*k2.price\n" +
                        "    END ) as diff\n" +
                        "  FROM (\n" +
                        "    SELECT MAX(k.date) as maxdate,\n" +
                        "    tr5.* FROM (\n" +
                        "      SELECT s.currency, t3.*,\n" +
                        "        SUM( t3.amount - t4.amount) as diff,\n" +
                        "        strftime('%Y-%m-%d',t3.datetime) as day,\n" +
                        "        strftime('%Y-%m',t3.datetime) as month\n" +
                        "      FROM\n" +
                        "      (SELECT t1.*,\n" +
                        "        MAX(t2.datetime) as maxdt\n" +
                        "        FROM transactions as t1\n" +
                        "        LEFT JOIN transactions as t2\n" +
                        "        ON t1.datetime > t2.datetime AND t1.source = t2.source\n" +
                        "        WHERE t2.id IS NOT NULL\n" +
                        "        GROUP BY t1.id\n" +
                        "      ) t3\n" +
                        "      LEFT JOIN transactions as t4 ON t4.datetime = t3.maxdt AND t4.source = t3.source\n" +
                        "      LEFT JOIN sources as s on s.id = t3.source\n" +
                        "      group by t3.source, " + gr_str1 + "\n" +
                        "      order by t3.datetime\n" +
                        "    ) as tr5\n" +
                        "    LEFT JOIN stocks as k on k.currency = tr5.currency and datetime(k.date,'unixepoch') <= tr5.datetime\n" +
                        "    GROUP BY tr5.source, tr5.id\n" +
                        "  ) tr6\n" +
                        "  LEFT JOIN stocks as k2 on k2.currency = tr6.currency and k2.date = tr6.maxdate\n" +
                        "  GROUP BY " + gr_str2 + "\n" +
                        ") tr4\n" +
                        "GROUP BY " + gr_str3 + " tr4.month\n" +
                        "ORDER BY tr4.month",null);

        //----------------------------------------
        // Get data

        List<String> entriesMonth = new ArrayList<>();
        List<BarEntry> entriesGroup1 = new ArrayList<>();
        List<BarEntry> entriesGroup2 = new ArrayList<>();
        List<BarEntry> entriesGroup3 = new ArrayList<>();

        int i = 0;

        if (ctr.moveToFirst()) {
            do {

                entriesMonth.add(ctr.getString(ctr.getColumnIndex("month")));
                entriesGroup1.add(new BarEntry(ctr.getFloat(ctr.getColumnIndex("incr")),i));
                entriesGroup2.add(new BarEntry(ctr.getFloat(ctr.getColumnIndex("decr")),i));
                entriesGroup3.add(new BarEntry(ctr.getFloat(ctr.getColumnIndex("saldo")),i));

                i++;

            } while (ctr.moveToNext());
        }
        ctr.close();

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


}
