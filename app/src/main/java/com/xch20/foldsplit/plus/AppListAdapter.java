package com.xch20.foldsplit.plus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

final class AppListAdapter extends BaseAdapter {
    interface Binder {
        boolean isSelected(AppChoice app);

        String statusFor(AppChoice app);
    }

    private final LayoutInflater inflater;
    private final List<AppChoice> items;
    private final Binder binder;
    private final int selectedColor;

    AppListAdapter(LayoutInflater inflater, List<AppChoice> items, Binder binder) {
        this.inflater = inflater;
        this.items = items;
        this.binder = binder;
        this.selectedColor = inflater.getContext().getColor(R.color.selected_row);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public AppChoice getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_app, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        AppChoice app = items.get(position);
        boolean selected = binder.isSelected(app);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);
        holder.packageName.setText(app.packageName);
        String status = binder.statusFor(app);
        holder.status.setText(status);
        holder.status.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
        holder.check.setChecked(selected);
        convertView.setBackgroundColor(selected ? selectedColor : 0);
        return convertView;
    }

    private static final class ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView packageName;
        final TextView status;
        final CheckBox check;

        ViewHolder(View view) {
            icon = view.findViewById(R.id.appIcon);
            label = view.findViewById(R.id.appLabel);
            packageName = view.findViewById(R.id.appPackage);
            status = view.findViewById(R.id.appStatus);
            check = view.findViewById(R.id.appCheck);
        }
    }
}
