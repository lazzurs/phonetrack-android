package it.eneiluj.nextcloud.phonetrack.persistence;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.eneiluj.nextcloud.phonetrack.R;
import it.eneiluj.nextcloud.phonetrack.model.Category;
import it.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import it.eneiluj.nextcloud.phonetrack.model.Item;

public class LoadLogjobsListTask extends AsyncTask<Void, Void, List<Item>> {

    private final Context context;
    private final LogjobsLoadedListener callback;
    private final Category category;
    private final CharSequence searchQuery;
    public LoadLogjobsListTask(@NonNull Context context, @NonNull LogjobsLoadedListener callback, @NonNull Category category, @Nullable CharSequence searchQuery) {
        this.context = context;
        this.callback = callback;
        this.category = category;
        this.searchQuery = searchQuery;
    }

    @Override
    protected List<Item> doInBackground(Void... voids) {
        List<DBLogjob> logjobList;
        NoteSQLiteOpenHelper db = NoteSQLiteOpenHelper.getInstance(context);
        logjobList = db.searchLogjobs(searchQuery, null);

        return fillListTitle(logjobList);
        /*if (category.category == null) {
            return fillListByTime(logjobList);
        } else {
            return fillListByCategory(logjobList);
        }*/
    }

    private DBLogjob colorTheLogjob(DBLogjob dbLogjob) {
        if (!TextUtils.isEmpty(searchQuery)) {
            SpannableString spannableString = new SpannableString(dbLogjob.getTitle());
            Matcher matcher = Pattern.compile("(" + searchQuery + ")", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.primary_dark)),
                        matcher.start(), matcher.end(), 0);
            }

            dbLogjob.setTitle(Html.toHtml(spannableString));
            // TODO search by sub title
            /*spannableString = new SpannableString(dbLogjob.getCategory());
            matcher = Pattern.compile("(" + searchQuery + ")", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.primary_dark)),
                        matcher.start(), matcher.end(), 0);
            }

            dbLogjob.setCategory(Html.toHtml(spannableString));

            spannableString = new SpannableString(dbLogjob.getExcerpt());
            matcher = Pattern.compile("(" + searchQuery + ")", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.primary_dark)),
                        matcher.start(), matcher.end(), 0);
            }

            dbLogjob.setExcerptDirectly(Html.toHtml(spannableString));*/
        }

        return dbLogjob;
    }

    @NonNull
    @WorkerThread
    private List<Item> fillListTitle(@NonNull List<DBLogjob> logjobList) {
        List<Item> itemList = new ArrayList<>();
        for (DBLogjob logjob : logjobList) {
            itemList.add(colorTheLogjob(logjob));
        }
        return itemList;
    }

    /*@NonNull
    @WorkerThread
    private List<Item> fillListByCategory(@NonNull List<DBLogjob> noteList) {
        List<Item> itemList = new ArrayList<>();
        String currentCategory = category.category;
        for (DBLogjob logjob : noteList) {
            if (currentCategory != null && !currentCategory.equals(logjob.getCategory())) {
                itemList.add(new SectionItem(NoteUtil.extendCategory(logjob.getCategory())));
            }

            itemList.add(colorTheLogjob(logjob));
            currentCategory = logjob.getCategory();
        }
        return itemList;
    }*/

    /*@NonNull
    @WorkerThread
    private List<Item> fillListByTime(@NonNull List<DBLogjob> noteList) {
        List<Item> itemList = new ArrayList<>();
        // #12 Create Sections depending on Time
        boolean todaySet, yesterdaySet, weekSet, monthSet, earlierSet;
        todaySet = yesterdaySet = weekSet = monthSet = earlierSet = false;
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Calendar yesterday = Calendar.getInstance();
        yesterday.set(Calendar.DAY_OF_YEAR, yesterday.get(Calendar.DAY_OF_YEAR) - 1);
        yesterday.set(Calendar.HOUR_OF_DAY, 0);
        yesterday.set(Calendar.MINUTE, 0);
        yesterday.set(Calendar.SECOND, 0);
        yesterday.set(Calendar.MILLISECOND, 0);
        Calendar week = Calendar.getInstance();
        week.set(Calendar.DAY_OF_WEEK, week.getFirstDayOfWeek());
        week.set(Calendar.HOUR_OF_DAY, 0);
        week.set(Calendar.MINUTE, 0);
        week.set(Calendar.SECOND, 0);
        week.set(Calendar.MILLISECOND, 0);
        Calendar month = Calendar.getInstance();
        month.set(Calendar.DAY_OF_MONTH, 0);
        month.set(Calendar.HOUR_OF_DAY, 0);
        month.set(Calendar.MINUTE, 0);
        month.set(Calendar.SECOND, 0);
        month.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < noteList.size(); i++) {
            DBLogjob currentNote = noteList.get(i);
            if (currentNote.isFavorite()) {
                // don't show as new section
            } else if (!todaySet && currentNote.getModified().getTimeInMillis() >= today.getTimeInMillis()) {
                // after 00:00 today
                if (i > 0) {
                    itemList.add(new SectionItem(context.getResources().getString(R.string.listview_updated_today)));
                }
                todaySet = true;
            } else if (!yesterdaySet && currentNote.getModified().getTimeInMillis() < today.getTimeInMillis() && currentNote.getModified().getTimeInMillis() >= yesterday.getTimeInMillis()) {
                // between today 00:00 and yesterday 00:00
                if (i > 0) {
                    itemList.add(new SectionItem(context.getResources().getString(R.string.listview_updated_yesterday)));
                }
                yesterdaySet = true;
            } else if (!weekSet && currentNote.getModified().getTimeInMillis() < yesterday.getTimeInMillis() && currentNote.getModified().getTimeInMillis() >= week.getTimeInMillis()) {
                // between yesterday 00:00 and start of the week 00:00
                if (i > 0) {
                    itemList.add(new SectionItem(context.getResources().getString(R.string.listview_updated_this_week)));
                }
                weekSet = true;
            } else if (!monthSet && currentNote.getModified().getTimeInMillis() < week.getTimeInMillis() && currentNote.getModified().getTimeInMillis() >= month.getTimeInMillis()) {
                // between start of the week 00:00 and start of the month 00:00
                if (i > 0) {
                    itemList.add(new SectionItem(context.getResources().getString(R.string.listview_updated_this_month)));
                }
                monthSet = true;
            } else if (!earlierSet && currentNote.getModified().getTimeInMillis() < month.getTimeInMillis()) {
                // before start of the month 00:00
                if (i > 0) {
                    itemList.add(new SectionItem(context.getResources().getString(R.string.listview_updated_earlier)));
                }
                earlierSet = true;
            }
            itemList.add(colorTheLogjob(currentNote));
        }

        return itemList;
    }
    */

    @Override
    protected void onPostExecute(List<Item> ljItems) {
        callback.onLogjobsLoaded(ljItems, category.category == null);
    }

    public interface LogjobsLoadedListener {
        void onLogjobsLoaded(List<Item> ljItems, boolean showCategory);
    }
}
