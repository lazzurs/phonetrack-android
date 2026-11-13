package net.eneiluj.nextcloud.phonetrack.model;

import android.graphics.Color;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

//import butterknife.BindView;
//import butterknife.ButterKnife;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.util.ThemeUtils;

public class NavigationAdapter extends RecyclerView.Adapter<NavigationAdapter.ViewHolder> {

    @DrawableRes
    public static final int ICON_FOLDER = R.drawable.ic_folder_grey600_24dp;
    @DrawableRes
    public static final int ICON_NOFOLDER = R.drawable.ic_folder_open_grey600_24dp;
    @DrawableRes
    public static final int ICON_SUB_FOLDER = R.drawable.ic_folder_grey600_18dp;
    @DrawableRes
    public static final int ICON_MULTIPLE = R.drawable.ic_create_new_folder_grey600_24dp;
    @DrawableRes
    public static final int ICON_MULTIPLE_OPEN = R.drawable.ic_folder_grey600_24dp;
    @DrawableRes
    public static final int ICON_SUB_MULTIPLE = R.drawable.ic_create_new_folder_grey600_18dp;

    public static class NavigationItem {
        @NonNull
        public String id;
        @NonNull
        public String label;
        @DrawableRes
        public int icon;
        @Nullable
        public Integer count;

        public NavigationItem(@NonNull String id, @NonNull String label, @Nullable Integer count, @DrawableRes int icon) {
            this.id = id;
            this.label = label;
            this.count = count;
            this.icon = icon;
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        @NonNull
        private final View view;

        TextView name;
        TextView count;
        ImageView icon;

        private NavigationItem currentItem;
        private final ClickListener clickListener;
        private int iconActionId = View.NO_ID;

        ViewHolder(@NonNull View itemView, @NonNull final ClickListener clickListener) {
            super(itemView);
            view = itemView;
            name = view.findViewById(R.id.navigationItemLabel);
            count = view.findViewById(R.id.navigationItemCount);
            icon = view.findViewById(R.id.navigationItemIcon);
            this.clickListener = clickListener;
            icon.setFocusable(false);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickListener.onItemClick(currentItem);
                }
            });
        }

        void assignItem(@NonNull NavigationItem item) {
            currentItem = item;
            boolean isSelected = item.id.equals(selectedItem);
            name.setText(item.label);
            count.setVisibility(item.count == null ? View.GONE : View.VISIBLE);
            count.setText(String.valueOf(item.count));
            if (item.icon > 0) {
                icon.setImageDrawable(
                        //icon.getResources().getDrawable(item.icon)
                        ContextCompat.getDrawable(icon.getContext(), item.icon)
                );
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }
            int textColor = ContextCompat.getColor(view.getContext(), R.color.fg_default);

            name.setTextColor(textColor);
            count.setTextColor(textColor);
            icon.setColorFilter(isSelected ? textColor : 0);

            // recycled views must not keep the bold flag of the item they showed before
            int boldFlag = isSelected ? Paint.FAKE_BOLD_TEXT_FLAG : 0;
            name.setPaintFlags((name.getPaintFlags() & ~Paint.FAKE_BOLD_TEXT_FLAG) | boldFlag);
            count.setPaintFlags((count.getPaintFlags() & ~Paint.FAKE_BOLD_TEXT_FLAG) | boldFlag);
            ViewCompat.setStateDescription(view,
                    isSelected ? view.getContext().getString(R.string.a11y_nav_selected) : null);

            // The icon is only a separate touch target for items where it does something other than
            // the row (expand a folder, show or hide a track). It is not a TalkBack focus stop: that
            // action is offered on the row instead.
            int actionLabel = iconActionLabel(item);
            if (iconActionId != View.NO_ID) {
                ViewCompat.removeAccessibilityAction(view, iconActionId);
                iconActionId = View.NO_ID;
            }
            if (actionLabel != 0) {
                icon.setOnClickListener(v -> clickListener.onIconClick(currentItem));
                iconActionId = ViewCompat.addAccessibilityAction(view, view.getContext().getString(actionLabel),
                        (v, args) -> {
                            clickListener.onIconClick(currentItem);
                            return true;
                        });
            } else {
                icon.setOnClickListener(null);
                icon.setClickable(false);
            }
            //view.setBackgroundColor(isSelected ? ContextCompat.getColor(view.getContext(), R.color.bg_highlighted) : Color.TRANSPARENT);
            view.setSelected(isSelected);
        }
    }

    /** String resource describing what tapping the icon does, or 0 when it does the same as the row. */
    @StringRes
    static int iconActionLabel(@NonNull NavigationItem item) {
        if (item.icon == ICON_MULTIPLE || item.icon == ICON_MULTIPLE_OPEN) {
            return R.string.a11y_nav_folder_action;
        }
        if (item.icon == R.drawable.ic_device_check_24 || item.icon == R.drawable.ic_phone_android_grey_24dp) {
            return R.string.a11y_nav_track_action;
        }
        return 0;
    }

    public interface ClickListener {
        void onItemClick(NavigationItem item);

        void onIconClick(NavigationItem item);
    }

    @NonNull
    private List<NavigationItem> items = new ArrayList<>();
    private String selectedItem = null;
    @NonNull
    private ClickListener clickListener;

    public NavigationAdapter(@NonNull ClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_navigation, parent, false);
        return new ViewHolder(v, clickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.assignItem(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setItems(@NonNull List<NavigationItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void setSelectedItem(String id) {
        selectedItem = id;
        notifyDataSetChanged();
    }

    public String getSelectedItem() {
        return selectedItem;
    }
}
