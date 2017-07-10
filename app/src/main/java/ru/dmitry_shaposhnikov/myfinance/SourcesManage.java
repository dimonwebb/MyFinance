package ru.dmitry_shaposhnikov.myfinance;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.view.ContextThemeWrapper;
import android.util.Log;
import android.view.ViewGroup;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.view.MotionEvent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.widget.EditText;
import java.util.ArrayList;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.database.sqlite.*;
import android.database.Cursor;
import java.util.LinkedHashMap;
import java.util.Map;
import android.content.ContentValues;

/**
 * Created by dmitry on 20.03.17.
 */

public class SourcesManage extends AppCompatActivity implements View.OnClickListener {

    private Map<Integer, String> items = new LinkedHashMap<Integer, String>();
    private Map<Integer, Integer> items_color = new LinkedHashMap<Integer, Integer>();
    private ListAdapter adapter;
    private RecyclerView recyclerView;
    private AlertDialog.Builder alertDialog;
    private EditText sources_name;
    private EditText sources_phone;
    private EditText sources_template;
    private Spinner sources_type;
    private Spinner sources_currency;
    private int edit_id;
    private View view;
    private boolean is_add = false;
    private Paint p = new Paint();

    DBHelper dbHelper;
    SQLiteDatabase db;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_layout);

        setTitle("Счета");

        dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();

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

        adapter = new ListAdapter(items, items_color);
        recyclerView.setAdapter(adapter);

        Cursor c = db.query("sources", new String[]{"id","name"}, null, null, null, null, null);

        if (c.moveToFirst()) {
            do {
                items.put(c.getInt(c.getColumnIndex("id")), c.getString(c.getColumnIndex("name")));
            } while (c.moveToNext());
        }

        c.close();

        adapter.notifyDataSetChanged();
        initSwipe();

    }

    private void showEditPanel(int id) {

        removeView();
        edit_id = id;
        is_add = false;
        alertDialog.setTitle("Редактировать");

        SpinnerLoad(sources_type, R.array.resource_type);
        SpinnerLoad(sources_currency, R.array.currency);

        Cursor c = db.query("sources", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
        c.moveToFirst();

        sources_name.setText(items.get(id));
        sources_type.setSelection(c.getInt(c.getColumnIndex("type")));
        sources_currency.setSelection(c.getInt(c.getColumnIndex("currency")));
        sources_phone.setText(c.getString(c.getColumnIndex("phone")));
        sources_template.setText(c.getString(c.getColumnIndex("template")));

        c.close();

        alertDialog.show();
        setupUI(view);
    }

    public void setupUI(View view2) {

        // Set up touch listener for non-text box views to hide keyboard.
        if (!(view2 instanceof EditText)) {
            view2.setOnTouchListener(new OnTouchListener() {
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
                final int position = viewHolder.getAdapterPosition();
                final int id = (new ArrayList<Integer>(items.keySet())).get(position);

                if (direction == ItemTouchHelper.LEFT){

                    // *** DELETE ***

                    //AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.myDialog));

                    new AlertDialog.Builder(SourcesManage.this)
                            .setMessage("Все транзакции этого счета удалятся. Вы уверены?")
                            .setCancelable(false)
                            .setPositiveButton("Да", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int i) {
                                    db.delete("sources", "id = ?", new String[]{String.valueOf(id)});
                                    db.delete("transactions", "source = ?", new String[]{String.valueOf(id)});
                                    db.delete("sms_logs", "source = ?", new String[]{String.valueOf(id)});
                                    adapter.removeItem(position);
                                }
                            })
                            .setNegativeButton("Нет", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int i) {
                                    adapter.notifyDataSetChanged();
                                }
                            })
                            .show();

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
        view = getLayoutInflater().inflate(R.layout.edit_sources_layout,null);
        alertDialog.setView(view);
        alertDialog.setPositiveButton("Сохранить", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                ContentValues cv = new ContentValues();
                cv.put("name", sources_name.getText().toString());
                cv.put("type", sources_type.getSelectedItemId());
                cv.put("currency", sources_currency.getSelectedItemId());
                cv.put("phone", sources_phone.getText().toString());
                cv.put("template", sources_template.getText().toString());

                if(is_add) {
                    // *** ADD ***
                    is_add = false;
                    if( !sources_name.getText().toString().equals("") ) {
                        int id = (int) db.insert("sources", null, cv);
                        adapter.addItem(id, sources_name.getText().toString());
                    }
                } else {
                    // *** UPDATE ***
                    db.update("sources",cv,"id = ?",new String[]{String.valueOf(edit_id)});
                    adapter.updateItem(edit_id, sources_name.getText().toString());
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

        sources_name = (EditText)view.findViewById(R.id.sources_name);
        sources_type = (Spinner)view.findViewById(R.id.sources_type);
        sources_currency = (Spinner)view.findViewById(R.id.sources_currency);
        sources_phone = (EditText)view.findViewById(R.id.sources_phone);
        sources_template = (EditText)view.findViewById(R.id.sources_template);
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()){
            case R.id.fab:
                // *** CLEAN ***

                removeView();
                is_add = true;

                alertDialog.setTitle("Добавить");

                sources_name.setText("");
                sources_phone.setText("");
                sources_template.setText("");

                SpinnerLoad(sources_type, R.array.resource_type);
                SpinnerLoad(sources_currency, R.array.currency);

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



}
