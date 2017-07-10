package ru.dmitry_shaposhnikov.myfinance;
 
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ViewHolder> {

    private Map<Integer, String> items;
    private Map<Integer, Integer> items_color;

    public ListAdapter(Map<Integer, String> items, Map<Integer, Integer> items_color) {
        this.items = items;
        this.items_color = items_color;
    }

    @Override
    public ListAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.row_layout, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int i) {
        int id = (new ArrayList<Integer>(items.keySet())).get(i);
        viewHolder.tv_row.setText(items.get(id));

        if( items_color.containsKey(id) ) {
            viewHolder.tv_row.setTextColor(items_color.get(id));
        } else {
            viewHolder.tv_row.setTextColor(Color.rgb(115,115,115));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void addItem(int id, String name) {
        items.put(id, name);
        notifyItemInserted(items.size());
    }

    public void updateItem(int id, String name) {
        items.put(id, name);
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        int id = (new ArrayList<Integer>(items.keySet())).get(position);
        items.remove(id);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, items.size());
    }

    public class ViewHolder extends RecyclerView.ViewHolder{
        TextView tv_row;
        public ViewHolder(View view) {
            super(view);
            tv_row = (TextView)view.findViewById(R.id.tv_row);
        }
    }

}