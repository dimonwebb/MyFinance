package ru.dmitry_shaposhnikov.myfinance;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.Map;
import java.util.List;

import com.github.jjobes.slidedatetimepicker.SlideDateTimeListener;
import com.github.jjobes.slidedatetimepicker.SlideDateTimePicker;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;

import android.text.TextUtils;
import java.util.Collections;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Created by dmitry on 20.03.17.
 */

public class TransactionsManage extends AppCompatActivity implements View.OnClickListener {

    private Map<Integer, String> items = new LinkedHashMap<Integer, String>();
    private Map<Integer, String> items_rev = new LinkedHashMap<Integer, String>();
    private Map<Integer, String> sources_map = new LinkedHashMap<Integer, String>();
    private Map<Integer, Integer> sources_currency = new LinkedHashMap<Integer, Integer>();
    private Map<Integer, Float> money_in_source = new LinkedHashMap<Integer, Float>();
    private Map<Integer, Integer> items_color = new LinkedHashMap<Integer, Integer>();
    private ListAdapter adapter;
    private RecyclerView recyclerView;
    private AlertDialog.Builder alertDialog;
    private Spinner trans_source;
    private EditText trans_datetime;
    private EditText trans_amount;
    private EditText trans_comment;
    private int edit_id;
    private View view;
    private boolean is_add = false;
    private Paint p = new Paint();
    private boolean is_sms_show = false;

    DBHelper dbHelper;
    SQLiteDatabase db;

    Date trans_datetime_date;
    SimpleDateFormat trans_datetime_format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    SimpleDateFormat trans_name_format = new SimpleDateFormat("dd.MM");
    DecimalFormat DigitsFormat;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_layout);

        setTitle("Транзакции");

        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        DigitsFormat = new DecimalFormat("###,###.00", symbols);


        dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();

        // Read sources

        Cursor c = db.query("sources", new String[]{"id","name","currency"}, null, null, null, null, null);
        if (c.moveToFirst()) {
            do {
                sources_map.put(c.getInt(c.getColumnIndex("id")), c.getString(c.getColumnIndex("name")));
                sources_currency.put(c.getInt(c.getColumnIndex("id")), c.getInt(c.getColumnIndex("currency")));
            } while (c.moveToNext());
        }
        c.close();

        initViews();
        initDialog();

    }

    private void initViews(){
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(this);
        recyclerView = (RecyclerView)findViewById(R.id.card_recycler_view);
        recyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(layoutManager);

        adapter = new ListAdapter(items,items_color);
        recyclerView.setAdapter(adapter);

        items_rev.clear();
        items.clear();
        money_in_source.clear();
        items_color.clear();

        Cursor c;
        if( is_sms_show ) {
            c = db.query("transactions", null, null, null, null, null, "datetime");
        } else {
            c = db.query("transactions", null, "auto = 0", null, null, null, "datetime");
        }

        if (c.moveToFirst()) {
            do {

                // FORM NAME STRING

                int id = c.getInt(c.getColumnIndex("id"));
                int source_id = c.getInt(c.getColumnIndex("source"));
                String amount_str = DigitsFormat.format(c.getFloat(c.getColumnIndex("amount")));
                String currency_str = getResources().getStringArray(R.array.currency)[sources_currency.get(source_id)];

                // Money

                if( money_in_source.containsKey(source_id) ) {
                    float money_last = money_in_source.get(source_id);
                    float money_delta = c.getFloat(c.getColumnIndex("amount")) - money_last;
                    if( money_delta > 0 ) {
                        amount_str = "+"+DigitsFormat.format(money_delta);
                        items_color.put(id, Color.rgb(0,128,0));
                    }
                    if( money_delta < 0 ) {
                        amount_str = DigitsFormat.format(money_delta);
                        items_color.put(id, Color.rgb(128,0,0));
                    }
                }
                money_in_source.put(source_id, c.getFloat(c.getColumnIndex("amount")));

                // Date
                Date date;
                try {
                    date = trans_datetime_format.parse(c.getString(c.getColumnIndex("datetime")));
                } catch (ParseException e) {
                    date = new Date();
                }
                String item_name = trans_name_format.format(date)+" "+sources_map.get(source_id) + ": " + amount_str + " " + currency_str;

                items_rev.put(id, item_name);

            } while (c.moveToNext());
        }

        c.close();

        // Reverse

        ArrayList<Integer> items_keys = new ArrayList<Integer> (items_rev.keySet());

        for( int i = items_keys.size() - 1; i >= 0; i-- ) {
            int key = items_keys.get(i);
            items.put(key, items_rev.get(key));
        }

        adapter.notifyDataSetChanged();
        initSwipe();

    }

    private void showEditPanel(int id) {

        removeView();
        edit_id = id;
        is_add = false;
        alertDialog.setTitle("Редактировать");

        SpinnerFromMap(trans_source, sources_map);

        Cursor c = db.query("transactions", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
        c.moveToFirst();

        trans_source.setSelection((new ArrayList<Integer>(sources_map.keySet())).indexOf(c.getInt(c.getColumnIndex("source"))));
        trans_datetime.setText(c.getString(c.getColumnIndex("datetime")));
        trans_amount.setText(c.getString(c.getColumnIndex("amount")));
        trans_comment.setText(c.getString(c.getColumnIndex("comment")));

        EnableDateTimePicker(trans_datetime);

        c.close();

        alertDialog.show();
        setupUI(view);
    }

    public void setupUI(View view2) {

        // Set up touch listener for non-text box views to hide keyboard.
        if (!(view2 instanceof EditText)) {
            view2.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    return false;
                }
            });
        }

    }

    private void initSwipe(){
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                int id = (new ArrayList<Integer>(items.keySet())).get(position);

                if (direction == ItemTouchHelper.LEFT){
                    // *** DELETE ***
                    db.delete("transactions", "id = ?", new String[]{String.valueOf(id)});
                    adapter.removeItem(position);
                } else {
                    // *** SHOW EDIT PANEL ***
                    showEditPanel(id);
                }
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {

                Bitmap icon;
                if(actionState == ItemTouchHelper.ACTION_STATE_SWIPE){

                    View itemView = viewHolder.itemView;
                    float height = (float) itemView.getBottom() - (float) itemView.getTop();
                    float width = height / 3;

                    if(dX > 0){
                        p.setColor(Color.parseColor("#388E3C"));
                        RectF background = new RectF((float) itemView.getLeft(), (float) itemView.getTop(), dX,(float) itemView.getBottom());
                        c.drawRect(background,p);
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_edit_white);
                        RectF icon_dest = new RectF((float) itemView.getLeft() + width ,(float) itemView.getTop() + width,(float) itemView.getLeft()+ 2*width,(float)itemView.getBottom() - width);
                        c.drawBitmap(icon,null,icon_dest,p);
                    } else {
                        p.setColor(Color.parseColor("#D32F2F"));
                        RectF background = new RectF((float) itemView.getRight() + dX, (float) itemView.getTop(),(float) itemView.getRight(), (float) itemView.getBottom());
                        c.drawRect(background,p);
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_delete_white);
                        RectF icon_dest = new RectF((float) itemView.getRight() - 2*width ,(float) itemView.getTop() + width,(float) itemView.getRight() - width,(float)itemView.getBottom() - width);
                        c.drawBitmap(icon,null,icon_dest,p);
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        recyclerView.addOnItemTouchListener(
                new RecyclerItemClickListener(this, recyclerView, new RecyclerItemClickListener.OnItemClickListener() {
                    @Override public void onItemClick(View view, int position) {
                        int id = (new ArrayList<Integer>(items.keySet())).get(position);
                        showEditPanel(id);
                    }
                    @Override public void onLongItemClick(View view, int position) {
                        // do whatever
                    }
                })
        );
    }

    private void removeView(){
        if(view.getParent()!=null) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private void initDialog(){
        alertDialog = new AlertDialog.Builder(this);
        view = getLayoutInflater().inflate(R.layout.edit_transactions_layout,null);
        alertDialog.setView(view);
        alertDialog.setPositiveButton("Сохранить", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                int source_id = (new ArrayList<Integer>(sources_map.keySet())).get((int) trans_source.getSelectedItemId());
                String currency_str = getResources().getStringArray(R.array.currency)[sources_currency.get(source_id)];

                Date date;
                try {
                    date = trans_datetime_format.parse(trans_datetime.getText().toString());
                } catch (ParseException e) {
                    date = new Date();
                }
                String item_name = trans_name_format.format(date)+" "+sources_map.get(source_id) + ": " + trans_amount.getText().toString() + " " + currency_str;

                ContentValues cv = new ContentValues();

                cv.put("source", source_id);
                cv.put("auto", 0);
                cv.put("datetime", trans_datetime.getText().toString());
                cv.put("amount", trans_amount.getText().toString());
                cv.put("comment", trans_comment.getText().toString());

                if(is_add) {
                    // *** ADD ***
                    is_add = false;
                    if( !trans_amount.getText().toString().equals("") ) {
                        int id = (int) db.insert("transactions", null, cv);
                        items_color.put(id, Color.rgb(0,0,128));
                        adapter.addItem(id, item_name);
                    }
                } else {
                    // *** UPDATE ***
                    items_color.put(edit_id, Color.rgb(0,0,128));
                    db.update("transactions",cv,"id = ?",new String[]{String.valueOf(edit_id)});
                    adapter.updateItem(edit_id, item_name);
                }

                dialog.dismiss();
            }
        });

        alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface dialog) {
                removeView();
                adapter.notifyDataSetChanged();
            }
        });

        trans_source = (Spinner)view.findViewById(R.id.trans_source);
        trans_datetime = (EditText)view.findViewById(R.id.trans_datetime);
        trans_amount = (EditText)view.findViewById(R.id.trans_amount);
        trans_comment = (EditText)view.findViewById(R.id.trans_comment);
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()){
            case R.id.fab:
                // *** CLEAN ***

                removeView();
                is_add = true;

                alertDialog.setTitle("Добавить");

                trans_datetime.setText(trans_datetime_format.format(new Date()));
                trans_amount.setText("");
                trans_comment.setText("");

                EnableDateTimePicker(trans_datetime);

                SpinnerFromMap(trans_source, sources_map);

                alertDialog.show();
                setupUI(view);
                break;
        }
    }

    private Spinner SpinnerLoad(Spinner spinner, int ArrayId) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, ArrayId, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private Spinner SpinnerFromMap(Spinner spinner, Map<Integer, String> ArrayMap) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, new ArrayList<String>(ArrayMap.values()));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void EnableDateTimePicker(EditText elem) {

        elem.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    BuildSlideDateTimePicker();
                }
            }
        });

        elem.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                BuildSlideDateTimePicker();
            }
        });

    }

    public void BuildSlideDateTimePicker() {

        try {
            trans_datetime_date = trans_datetime_format.parse(trans_datetime.getText().toString());
        } catch (ParseException e) {
            trans_datetime_date = new Date();
        }

        new SlideDateTimePicker.Builder(getSupportFragmentManager())
                .setListener(listener)
                .setInitialDate(trans_datetime_date)
                .setIs24HourTime(true)
                .build()
                .show();
    }

    private SlideDateTimeListener listener = new SlideDateTimeListener() {

        @Override
        public void onDateTimeSet(Date date)
        {
            trans_datetime.setText(trans_datetime_format.format(date));
        }

        @Override
        public void onDateTimeCancel()
        {
            // Overriding onDateTimeCancel() is optional.
        }
    };

    public String makePlaceholders(int len) {
        return TextUtils.join(",", Collections.nCopies(len, "?"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_transactions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_update_trans:
                is_sms_show = false;
                initViews();
                return true;
            case R.id.menu_show_sms:
                is_sms_show = true;
                initViews();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
