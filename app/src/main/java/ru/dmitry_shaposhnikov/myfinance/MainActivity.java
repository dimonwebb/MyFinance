package ru.dmitry_shaposhnikov.myfinance;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.graphics.Color;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class MainActivity extends AppCompatActivity {

    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private Map<Integer, TreeMap<Long,Float>> stock_prices = new LinkedHashMap<Integer, TreeMap<Long,Float>>();
    private ArrayList<Integer> colors_palette = new ArrayList<>();
    PieChart pieChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();

        // Load activities

        //Intent i1 = new Intent(getApplicationContext(),SMSRead.class);
        //startActivity(i1);

        Intent i2 = new Intent(getApplicationContext(),StockDownload.class);
        startActivityForResult(i2, 2);

        // Read stock prices

        read_stock_prices();

        // Generate colors palette

        generate_colors_palette();

        pieChart = (PieChart) findViewById(R.id.chart);

    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent i1 = new Intent(getApplicationContext(),SMSRead.class);
        startActivityForResult(i1, 1);

        UpdateGraph();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        UpdateGraph();
    }

    public void UpdateGraph() {

        // Read data

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

        //

        Map<Integer, Float> amount_by_source = new LinkedHashMap<Integer, Float>();

        Cursor ctr = db.rawQuery("SELECT *, MAX(datetime) as max FROM transactions GROUP BY source", null);
        if (ctr.moveToFirst()) {
            do {
                amount_by_source.put(ctr.getInt(ctr.getColumnIndex("source")), ctr.getFloat(ctr.getColumnIndex("amount")));
            } while (ctr.moveToNext());
        }
        ctr.close();

        // Plot pie chart

        Float whole_budget = 0f;

        pieChart.clear();

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<String>();

        for( int s = 0; s < source_ids.size(); s++ ) {
            if( amount_by_source.containsKey(source_ids.get(s)) ) {

                Float amount = amount_by_source.get(source_ids.get(s));

                if( source_currency.get(s) > 0 ) {
                    amount = amount * nearest_price(source_currency.get(s), new Date());
                }

                whole_budget += amount;
                entries.add(new Entry(amount, s));
                labels.add(source_names.get(s));
            }
        }

        PieDataSet dataset = new PieDataSet(entries, "");
        PieData data = new PieData(labels, dataset);

        pieChart.setDescription("");
        dataset.setColors(colors_palette);
        //pieChart.setHoleRadius(20f);

        Legend l = pieChart.getLegend();
        //l.setPosition(LegendPosition.RIGHT_OF_CHART);
        l.setEnabled(false);

        pieChart.setData(data);
        pieChart.invalidate();

        // Print the SUM

        TextView text_budget = (TextView) findViewById(R.id.text_budget);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("###,##0.00", symbols);

        if( source_ids.size() == 0 ) {
            text_budget.setTextSize(20f);
            text_budget.setText("Добавьте счета");
            text_budget.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
            text_budget.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(getApplicationContext(),SourcesManage.class));
                }
            });
        } else if( amount_by_source.size() == 0 ) {
            text_budget.setTextSize(18f);
            text_budget.setText("Добавьте транзакции");
            text_budget.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
            text_budget.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(getApplicationContext(),TransactionsManage.class));
                }
            });
        } else {
            text_budget.setTextSize(32f);
            text_budget.setText(df.format(whole_budget) + " р");
            text_budget.setTextColor(getResources().getColor(R.color.colorTextView));
            text_budget.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    return;
                }
            });
        }

        pieChart.setCenterText(df.format(whole_budget)+" р");

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sources:
                startActivity(new Intent(getApplicationContext(),SourcesManage.class));
                return true;
            case R.id.menu_transactions:
                startActivity(new Intent(getApplicationContext(),TransactionsManage.class));
                return true;
            case R.id.menu_history_graph:
                startActivity(new Intent(getApplicationContext(),HistoryGraph.class));
                return true;
            case R.id.menu_stats:
                startActivity(new Intent(getApplicationContext(),Saldo.class));
                return true;
            case R.id.menu_rescan_sms:
                db.execSQL("delete from sms_logs");
                db.execSQL("delete from transactions where source in (select id from sources where type = 1);");
                Intent i1 = new Intent(getApplicationContext(),SMSRead.class);
                startActivity(i1);
            case R.id.menu_reload_stocks:
                Intent i2 = new Intent(getApplicationContext(),StockDownload.class);
                startActivity(i2);
            default:
                return super.onOptionsItemSelected(item);
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
