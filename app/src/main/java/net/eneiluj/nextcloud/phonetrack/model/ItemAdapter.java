package net.eneiluj.nextcloud.phonetrack.model;

import android.content.SharedPreferences;
//import android.preference.PreferenceManager;
import android.graphics.PorterDuff;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SwitchCompat;
import android.content.Context;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

//import butterknife.BindView;
//import butterknife.ButterKnife;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.util.ThemeUtils;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

public class ItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = ItemAdapter.class.getSimpleName();

    private static final int section_type = 0;
    private static final int logjob_type = 1;
    private final LogjobClickListener logjobClickListener;
    private List<Item> itemList;
    private boolean showCategory = true;
    private List<Integer> selected;
    private PhoneTrackSQLiteOpenHelper db;
    private SharedPreferences prefs;

    public ItemAdapter(@NonNull LogjobClickListener logjobClickListener, PhoneTrackSQLiteOpenHelper db) {
        this.itemList = new ArrayList<>();
        this.selected = new ArrayList<>();
        this.logjobClickListener = logjobClickListener;
        this.db = db;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(db.getContext());
    }

    /**
     * Updates the item list and notifies respective view to update.
     *
     * @param itemList List of items to be set
     */
    public void setItemList(@NonNull List<Item> itemList) {
        this.itemList = itemList;
        notifyDataSetChanged();
    }

    /**
     * Adds the given logjob to the top of the list.
     *
     * @param logjob log job that should be added.
     */
    public void add(@NonNull DBLogjob logjob) {
        itemList.add(0, logjob);
        notifyItemInserted(0);
        notifyItemChanged(0);
    }

    /**
     * Replaces a logjob with an updated version
     *
     * @param logjob     log job with the changes.
     * @param position position in the list of the node
     */
    public void replace(@NonNull DBLogjob logjob, int position) {
        itemList.set(position, logjob);
        notifyItemChanged(position);
    }

    /**
     * Removes all items from the adapter.
     */
    public void removeAll() {
        itemList.clear();
        notifyDataSetChanged();
    }

    // Create new views (invoked by the layout manager)
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;
        if (viewType == section_type) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_logjobs_list_section_item, parent, false);
            return new SectionViewHolder(v);
        } else {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_logjobs_list_logjob_item, parent, false);
            return new LogjobViewHolder(v);
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        Item item = itemList.get(position);
        if (item.isSection()) {
            SectionItem section = (SectionItem) item;
            ((SectionViewHolder) holder).sectionTitle.setText(section.geTitle());
        } else {
            final DBLogjob mylogjob = (DBLogjob) item;
            final DBLogjob logjob = db.getLogjob(mylogjob.getId());
            final LogjobViewHolder nvHolder = ((LogjobViewHolder) holder);
            nvHolder.logjobSwipeable.setAlpha(1.0f);
            nvHolder.logjobTitle.setText(Html.fromHtml(logjob.getTitle()));
            if (!logjob.getDeviceName().isEmpty()) {
                nvHolder.logjobSubtitle.setText(Html.fromHtml(
                        logjob.getDeviceName() + " => " + logjob.getUrl())
                );
            } else {
                nvHolder.logjobSubtitle.setText(Html.fromHtml(logjob.getUrl()));
            }

            Log.d(TAG, "[UUUUUUUUUUUUUUPDATE logjob enabled : ]"+logjob.isEnabled());
            nvHolder.logjobEnabled.setChecked(logjob.isEnabled());
            // name the switch and the menu button after the job, otherwise TalkBack reads "Switch, on"
            final String jobTitle = Html.fromHtml(logjob.getTitle()).toString();
            nvHolder.logjobEnabled.setContentDescription(jobTitle);
            nvHolder.moreButton.setContentDescription(
                    db.getContext().getString(R.string.a11y_logjob_more_options, jobTitle));
            if (nvHolder.logjobEnabled.isChecked()) {
                nvHolder.logjobEnabled.getTrackDrawable().setColorFilter(ThemeUtils.primaryDarkColor(db.getContext()), PorterDuff.Mode.SRC_IN);
                nvHolder.logjobEnabled.getThumbDrawable().setColorFilter(ThemeUtils.primaryColor(db.getContext()), PorterDuff.Mode.MULTIPLY);
            } else {
                nvHolder.logjobEnabled.getTrackDrawable().setColorFilter(ContextCompat.getColor(db.getContext(), R.color.fg_default_low), PorterDuff.Mode.SRC_IN);
                nvHolder.logjobEnabled.getThumbDrawable().setColorFilter(ContextCompat.getColor(db.getContext(), R.color.fg_default_high), PorterDuff.Mode.MULTIPLY);
            }
            nvHolder.logjobEnabled.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    logjobClickListener.onLogjobEnabledClick(holder.getAdapterPosition(), view);
                    if (nvHolder.logjobEnabled.isChecked()) {
                        nvHolder.logjobEnabled.getTrackDrawable().setColorFilter(ThemeUtils.primaryDarkColor(db.getContext()), PorterDuff.Mode.SRC_IN);
                        nvHolder.logjobEnabled.getThumbDrawable().setColorFilter(ThemeUtils.primaryColor(db.getContext()), PorterDuff.Mode.MULTIPLY);
                    }
                    else {
                        nvHolder.logjobEnabled.getTrackDrawable().setColorFilter(ContextCompat.getColor(db.getContext(), R.color.fg_default_low), PorterDuff.Mode.SRC_IN);
                        nvHolder.logjobEnabled.getThumbDrawable().setColorFilter(ContextCompat.getColor(db.getContext(), R.color.fg_default_high), PorterDuff.Mode.MULTIPLY);
                    }
                }
            });

            nvHolder.moreButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    logjobClickListener.onLogjobMoreButtonClick(holder.getAdapterPosition(), view);
                }
            });

            // nb not synced (pending)
            int nb = db.getLogjobLocationNotSyncedCount(logjob.getId());
            String nbTxt = (nb == 0) ? "" : String.valueOf(nb);
            nvHolder.nbNotSync.setText(nbTxt);
            int visible = (nb == 0) ? View.INVISIBLE : View.VISIBLE;
            nvHolder.nosyncIcon.setVisibility(visible);

            int nbSentShown = 0;
            if (prefs.getBoolean(db.getContext().getString(R.string.pref_key_shownbsynced), false)) {
                int nbSent = db.getNbSync(logjob.getId());
                nbSentShown = nbSent;
                nbTxt = (nbSent == 0) ? "" : String.valueOf(nbSent);
                if (LoggerService.DEBUG) {
                    Log.d(TAG, "[onBind : " + nbSent + " nbSync]");
                }
                nvHolder.nbSync.setText(nbTxt);
                visible = (nbSent == 0) ? View.INVISIBLE : View.VISIBLE;
                nvHolder.syncIcon.setVisibility(visible);
                nvHolder.nbSync.setVisibility(visible);
            }
            else {
                nvHolder.syncIcon.setVisibility(View.GONE);
                nvHolder.nbSync.setVisibility(View.GONE);
            }

            // icon to show if there was a location fix since last activation
            Context ctx = db.getContext();
            List<String> summary = new ArrayList<>();
            summary.add(jobTitle);
            summary.add(ctx.getString(logjob.isEnabled() ? R.string.a11y_logjob_enabled : R.string.a11y_logjob_disabled));
            if (!logjob.isEnabled()) {
                nvHolder.locSuccessIcon.setVisibility(View.GONE);
            }
            else {
                nvHolder.locSuccessIcon.setVisibility(View.VISIBLE);
                // select the icon
                if (db.getLastLocTimestamp(logjob.getId()) >= db.getLastActivationGpsTimestamp(logjob.getId())) {
                    nvHolder.locSuccessIcon.setImageResource(R.drawable.ic_location_found_grey_24dp);
                    summary.add(ctx.getString(R.string.a11y_logjob_fix_found));
                }
                else {
                    nvHolder.locSuccessIcon.setImageResource(R.drawable.ic_location_searching_grey_24dp);
                    summary.add(ctx.getString(R.string.a11y_logjob_fix_searching));
                }
            }
            if (nb > 0) {
                summary.add(ctx.getResources().getQuantityString(R.plurals.a11y_logjob_pending, nb, nb));
            }
            if (nbSentShown > 0) {
                summary.add(ctx.getResources().getQuantityString(R.plurals.a11y_logjob_sent, nbSentShown, nbSentShown));
            }
            // cleared by the next successful upload
            SyncError uploadError = db.getLastSyncError(logjob.getId());
            if (uploadError.isFailing()) {
                String reason = uploadError.getMessage() == null ? "" : uploadError.getMessage();
                String errorText = ctx.getString(R.string.logjob_upload_failing, reason);
                nvHolder.uploadError.setText(errorText);
                nvHolder.uploadError.setVisibility(View.VISIBLE);
                summary.add(errorText);
            } else {
                nvHolder.uploadError.setVisibility(View.GONE);
            }
            // The row sums up its state; the icons and counters are hidden from TalkBack in the layout.
            nvHolder.itemView.setContentDescription(TextUtils.join(", ", summary));
        }
    }

    public boolean select(Integer position) {
        return !selected.contains(position) && selected.add(position);
    }

    public void clearSelection() {
        selected.clear();
    }

    @NonNull
    public List<Integer> getSelected() {
        return selected;
    }

    public boolean deselect(Integer position) {
        for (int i = 0; i < selected.size(); i++) {
            if (selected.get(i).equals(position)) {
                //position was selected and removed
                selected.remove(i);
                return true;
            }
        }
        // position was not selected
        return false;
    }

    public Item getItem(int logjobPosition) {
        if (logjobPosition >= 0 && logjobPosition < itemList.size()) {
            if (LoggerService.DEBUG) { Log.d(TAG, "[GETITEM " + logjobPosition + "/"+itemList.size()+"]"); }
            return itemList.get(logjobPosition);
        }
        else {
            return null;
        }
    }

    public void remove(@NonNull Item item) {
        itemList.remove(item);
        notifyDataSetChanged();
    }

    public void setShowCategory(boolean showCategory) {
        this.showCategory = showCategory;
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isSection() ? section_type : logjob_type;
    }

    public interface LogjobClickListener {
        void onLogjobClick(int position, View v);

        void onLogjobEnabledClick(int position, View v);

        void onLogjobMoreButtonClick(int position, View v);

        boolean onLogjobLongClick(int position, View v);

        /** The row's "Delete" accessibility action (the swipe gesture has no TalkBack equivalent). */
        void onLogjobDeleteAction(int position);
    }

    public class LogjobViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener, View.OnClickListener {
        //@BindView(R.id.logjobSwipeable)
        public View logjobSwipeable;
        View logjobSwipeFrame;
        TextView logjobTextToggleLeft;
        ImageView logjobDeleteRight;
        TextView logjobTitle;
        TextView logjobSubtitle;
        ImageView nosyncIcon;
        ImageView syncIcon;
        ImageView locSuccessIcon;
        SwitchCompat logjobEnabled;
        TextView nbNotSync;
        TextView nbSync;
        TextView uploadError;
        ImageButton moreButton;

        private LogjobViewHolder(View v) {
            super(v);
            this.logjobSwipeFrame = v.findViewById(R.id.logjobSwipeFrame);
            this.logjobSwipeable = v.findViewById(R.id.logjobSwipeable);
            this.logjobTextToggleLeft = v.findViewById(R.id.logjobTextToggleLeft);
            this.logjobDeleteRight = v.findViewById(R.id.logjobDeleteRight);
            this.logjobTitle = v.findViewById(R.id.logjobTitle);
            this.logjobSubtitle = v.findViewById(R.id.logjobExcerpt);
            this.nosyncIcon = v.findViewById(R.id.nosyncIcon);
            this.syncIcon = v.findViewById(R.id.syncIcon);
            this.locSuccessIcon = v.findViewById(R.id.locSuccessIcon);
            this.logjobEnabled = v.findViewById(R.id.logjobEnabled);
            this.nbNotSync = v.findViewById(R.id.nbNotSync);
            this.nbSync = v.findViewById(R.id.nbSync);
            this.uploadError = v.findViewById(R.id.logjobUploadError);
            this.moreButton = v.findViewById(R.id.moreButton);
            v.setOnClickListener(this);
            v.setOnLongClickListener(this);
            // the swipe gesture has no TalkBack equivalent, so offer it as an action on the row
            ViewCompat.addAccessibilityAction(v, v.getContext().getString(R.string.a11y_logjob_toggle_action),
                    (view, args) -> logjobEnabled.performClick());
            ViewCompat.addAccessibilityAction(v, v.getContext().getString(R.string.menu_delete), (view, args) -> {
                final int adapterPosition = getAdapterPosition();
                if (adapterPosition == NO_POSITION) {
                    return false;
                }
                logjobClickListener.onLogjobDeleteAction(adapterPosition);
                return true;
            });
        }

        @Override
        public void onClick(View v) {
            final int adapterPosition = getAdapterPosition();
            if (adapterPosition != NO_POSITION) {
                logjobClickListener.onLogjobClick(adapterPosition, v);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            return logjobClickListener.onLogjobLongClick(getAdapterPosition(), v);
        }

        public void showSwipe(boolean left) {
            logjobTextToggleLeft.setVisibility(left ? View.VISIBLE : View.INVISIBLE);
            logjobDeleteRight.setVisibility(left ? View.INVISIBLE : View.VISIBLE);
            logjobSwipeFrame.setBackgroundResource(left ? R.color.bg_warning : R.color.bg_attention);
        }
    }

    public static class SectionViewHolder extends RecyclerView.ViewHolder {
        //@BindView(R.id.sectionTitle)
        TextView sectionTitle;

        private SectionViewHolder(View view) {
            super(view);
            //ButterKnife.bind(this, view);
        }
    }
}
