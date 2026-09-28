package io.github.rsliyu.foldflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

final class AppListAdapter extends BaseAdapter {
    interface Binder {
        boolean isSelected(AppChoice app);

        CharSequence tagsFor(AppChoice app);
    }

    private final LayoutInflater inflater;
    private final List<AppChoice> items;
    private final Binder binder;

    AppListAdapter(LayoutInflater inflater, List<AppChoice> items, Binder binder) {
        this.inflater = inflater;
        this.items = items;
        this.binder = binder;
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
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);
        holder.packageName.setText(app.packageName);
        CharSequence tags = binder.tagsFor(app);
        holder.tags.setText(tags);
        holder.tags.setVisibility(tags.length() == 0 ? View.GONE : View.VISIBLE);
        convertView.setActivated(binder.isSelected(app));
        return convertView;
    }

    private static final class ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView packageName;
        final TextView tags;

        ViewHolder(View view) {
            icon = view.findViewById(R.id.appIcon);
            label = view.findViewById(R.id.appLabel);
            packageName = view.findViewById(R.id.appPackage);
            tags = view.findViewById(R.id.appTags);
        }
    }
}
