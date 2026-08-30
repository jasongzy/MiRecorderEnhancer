package com.jasongzy.mirecorderenhancer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

@SuppressLint("DiscouragedApi")
final class FilterUi {
    private static final String TAG = "MiRecorderEnhancer";
    private static final String VIEW_TAG = "com.jasongzy.mirecorderenhancer.FILTER";
    private static final Uri RECORDS_URI = Uri.parse("content://records/records");
    private static final int MENU_LOCATION = 1;
    private static final int MENU_DURATION = 2;
    private static final int MENU_DATE = 3;
    private static final int MENU_TRANSCRIPTION = 4;
    private static final int MENU_CLEAR = 5;
    private static final int MAX_DURATION_SECONDS = 999_999;

    private FilterUi() {}

    static View wrapEndView(View actionBar, ApplicationInfo moduleInfo, View endView)
            throws PackageManager.NameNotFoundException {
        if (VIEW_TAG.equals(endView.getTag())) {
            return endView;
        }
        Context targetContext = actionBar.getContext();
        int settingsId = targetContext.getResources().getIdentifier("settings", "id", RecorderSymbols.PACKAGE);
        if (endView.findViewById(settingsId) == null) {
            return endView;
        }

        Resources moduleResources = targetContext.getPackageManager().getResourcesForApplication(moduleInfo);
        ImageButton button = createButton(targetContext, moduleResources);
        LinearLayout actions = new LinearLayout(targetContext);
        actions.setTag(VIEW_TAG);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        int buttonSize = dp(targetContext, 48);
        actions.addView(button, new LinearLayout.LayoutParams(buttonSize, buttonSize));
        actions.addView(endView);
        button.setOnClickListener(view -> showMenu(targetContext, moduleResources, view, actionBar));
        return actions;
    }

    private static ImageButton createButton(Context targetContext, Resources moduleResources) {
        ImageButton button = new ImageButton(targetContext);
        button.setContentDescription(moduleResources.getString(R.string.filter));
        int padding = dp(targetContext, 12);
        button.setPadding(padding, padding, padding, padding);
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);

        TypedValue value = new TypedValue();
        if (targetContext.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
                && value.resourceId != 0) {
            button.setBackgroundResource(value.resourceId);
        }
        Drawable icon = moduleResources.getDrawable(R.drawable.ic_filter_list, null).mutate();
        if (targetContext.getTheme().resolveAttribute(android.R.attr.textColorPrimary, value, true)) {
            ColorStateList colors = value.resourceId == 0
                    ? ColorStateList.valueOf(value.data)
                    : targetContext.getColorStateList(value.resourceId);
            icon.setTintList(colors);
        }
        button.setImageDrawable(icon);
        return button;
    }

    private static void showMenu(Context targetContext, Resources resources, View anchor, View actionBar) {
        FilterState state = FilterController.get();
        int[] itemIds = state.isEmpty()
                ? new int[] {MENU_LOCATION, MENU_DURATION, MENU_DATE, MENU_TRANSCRIPTION}
                : new int[] {MENU_LOCATION, MENU_DURATION, MENU_DATE, MENU_TRANSCRIPTION, MENU_CLEAR};
        String[] labels = {
            resources.getString(R.string.filter_location, resources.getString(state.location().label())),
            resources.getString(R.string.filter_duration, durationDescription(resources, state)),
            resources.getString(R.string.filter_date, dateDescription(resources, state)),
            resources.getString(
                    R.string.filter_transcription, resources.getString(state.transcription().label())),
            resources.getString(R.string.clear_filters)
        };
        showMiuixPopupMenu(targetContext, resources, anchor, actionBar, itemIds, labels);
    }

    private static void showMiuixPopupMenu(
            Context context,
            Resources resources,
            View anchor,
            View actionBar,
            int[] itemIds,
            String[] labels) {
        try {
            ClassLoader loader = context.getClassLoader();
            Object actionPresenter = actionBar.getClass()
                    .getField(RecorderSymbols.ACTION_BAR_PRESENTER_FIELD)
                    .get(actionBar);
            if (actionPresenter == null) {
                throw new IllegalStateException("Action bar presenter is unavailable");
            }
            Context popupContext = (Context) actionPresenter.getClass()
                    .getField(RecorderSymbols.MENU_PRESENTER_CONTEXT_FIELD)
                    .get(actionPresenter);
            View popupHost = (View) actionPresenter.getClass()
                    .getField(RecorderSymbols.MENU_PRESENTER_HOST_FIELD)
                    .get(actionPresenter);
            Class<?> menuClass = Class.forName(
                    RecorderSymbols.MIUIX_MENU_BUILDER_CLASS, false, loader);
            Object menu = menuClass.getConstructor(Context.class).newInstance(popupContext);
            for (int i = 0; i < itemIds.length; i++) {
                int itemId = itemIds[i];
                MenuItem item = (MenuItem) menuClass
                        .getMethod("add", int.class, int.class, int.class, CharSequence.class)
                        .invoke(menu, 0, itemId, i, labels[i]);
                item.setOnMenuItemClickListener(
                        ignored -> handleMenuItem(context, resources, itemId));
            }

            Class<?> presenterClass = Class.forName(
                    RecorderSymbols.MIUIX_MENU_PRESENTER_CLASS, false, loader);
            Object presenter = presenterClass
                    .getConstructor(Context.class, menuClass, View.class, View.class, boolean.class)
                    .newInstance(popupContext, menu, anchor, popupHost, true);
            int itemLayout = context.getResources().getIdentifier(
                    "miuix_appcompat_overflow_popup_menu_item_layout", "layout", RecorderSymbols.PACKAGE);
            if (itemLayout != 0) {
                presenterClass.getField(RecorderSymbols.POPUP_ITEM_LAYOUT_FIELD).setInt(presenter, itemLayout);
            }
            presenterClass.getMethod(RecorderSymbols.POPUP_SHOW_METHOD).invoke(presenter);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            Log.e(TAG, "Unable to show MIUIX popup menu", ignored);
            showMiuixMenuDialog(context, resources, itemIds, labels);
        }
    }

    private static void showMiuixMenuDialog(
            Context context, Resources resources, int[] itemIds, String[] labels) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        Dialog[] shown = new Dialog[1];
        for (int i = 0; i < itemIds.length; i++) {
            int itemId = itemIds[i];
            TextView row = createMiuixTextView(context);
            row.setText(labels[i]);
            row.setTextColor(resolveColor(context, android.R.attr.textColorPrimary, 0xff000000));
            row.setTextSize(18);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(context, 56));
            row.setPadding(dp(context, 24), 0, dp(context, 24), 0);
            row.setBackgroundResource(resolveSelectableBackground(context));
            row.setOnClickListener(view -> {
                shown[0].dismiss();
                handleMenuItem(context, resources, itemId);
            });
            content.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        shown[0] = showMiuixContentDialog(context, resources.getString(R.string.filter), content);
    }

    private static int resolveSelectableBackground(Context context) {
        TypedValue value = new TypedValue();
        return context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                ? value.resourceId
                : 0;
    }

    private static boolean handleMenuItem(Context context, Resources resources, int itemId) {
        switch (itemId) {
            case MENU_LOCATION -> showLocationDialog(context, resources);
            case MENU_DURATION -> showDurationDialog(context, resources);
            case MENU_DATE -> showDateDialog(context, resources);
            case MENU_TRANSCRIPTION -> showTranscriptionDialog(context, resources);
            case MENU_CLEAR -> applyState(context, FilterState.EMPTY);
            default -> throw new IllegalStateException("Unknown filter item: " + itemId);
        }
        return true;
    }

    private static void showLocationDialog(Context targetContext, Resources resources) {
        FilterState state = FilterController.get();
        int[] selected = {state.location().ordinal()};
        FilterState.Location[] locations = FilterState.Location.values();
        String[] labels = new String[locations.length];
        for (int i = 0; i < locations.length; i++) {
            labels[i] = resources.getString(locations[i].label());
        }
        showMiuixSingleChoiceDialog(
                targetContext,
                resources.getString(R.string.location),
                labels,
                selected[0],
                (dialog, which) -> selected[0] = which,
                resources.getString(R.string.apply),
                (dialog, which) -> applyState(
                        targetContext, FilterController.get().withLocation(locations[selected[0]])),
                resources.getString(R.string.cancel));
    }

    private static void showDurationDialog(Context targetContext, Resources resources) {
        FilterState state = FilterController.get();
        int[] selected = {state.durationOperator().ordinal()};
        TextView[] operators = createSegments(
                targetContext,
                resources.getString(R.string.filter_all),
                resources.getString(R.string.duration_less_than),
                resources.getString(R.string.duration_at_least));
        LinearLayout operatorGroup = createSegmentGroup(targetContext, operators, selected);

        EditText seconds = createMiuixEditText(targetContext);
        seconds.setHint(resources.getString(R.string.duration_seconds_hint));
        seconds.setInputType(InputType.TYPE_CLASS_NUMBER);
        seconds.setFilters(new InputFilter[] {new InputFilter.LengthFilter(6)});
        seconds.setSelectAllOnFocus(true);
        seconds.setSingleLine(true);
        seconds.setText(String.valueOf(Math.max(1, Math.min(state.durationSeconds(), MAX_DURATION_SECONDS))));
        seconds.setPadding(
                seconds.getPaddingLeft(),
                seconds.getPaddingTop(),
                dp(targetContext, 64),
                seconds.getPaddingBottom());

        TextView unit = createMiuixTextView(targetContext);
        unit.setText(resources.getString(R.string.seconds_unit));
        unit.setTextColor(resolveColor(targetContext, android.R.attr.textColorSecondary, 0xff666666));
        unit.setTextSize(16);
        unit.setGravity(Gravity.CENTER);
        FrameLayout input = new FrameLayout(targetContext);
        input.addView(seconds, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams unitParams = new FrameLayout.LayoutParams(
                dp(targetContext, 56), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END);
        input.addView(unit, unitParams);
        input.setVisibility(selected[0] == FilterState.DurationOperator.ALL.ordinal()
                ? View.GONE
                : View.VISIBLE);
        for (int i = 0; i < operators.length; i++) {
            int index = i;
            operators[i].setOnClickListener(view -> {
                selected[0] = index;
                updateSegmentStyles(targetContext, operators, index);
                updateDurationInputVisibility(
                        targetContext,
                        input,
                        seconds,
                        index != FilterState.DurationOperator.ALL.ordinal());
            });
        }

        LinearLayout container = new LinearLayout(targetContext);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(targetContext, 24);
        int verticalPadding = dp(targetContext, 8);
        container.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        container.addView(operatorGroup);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(targetContext, 12);
        container.addView(input, inputParams);

        Dialog dialog = showMiuixDialog(
                targetContext,
                resources.getString(R.string.duration),
                container,
                resources.getString(R.string.apply),
                (ignored, which) -> {
                    long value = parseSeconds(seconds.getText().toString(), state.durationSeconds());
                    applyState(
                            targetContext,
                            FilterController.get().withDuration(
                                    FilterState.DurationOperator.values()[selected[0]], value));
                },
                resources.getString(R.string.cancel));
        if (selected[0] != FilterState.DurationOperator.ALL.ordinal()) {
            seconds.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            seconds.post(() -> showKeyboard(targetContext, seconds));
        }
    }

    private static void updateDurationInputVisibility(
            Context context, View input, EditText seconds, boolean visible) {
        input.setVisibility(visible ? View.VISIBLE : View.GONE);
        InputMethodManager keyboard = context.getSystemService(InputMethodManager.class);
        if (visible) {
            seconds.requestFocus();
            seconds.post(() -> showKeyboard(context, seconds));
        } else {
            seconds.clearFocus();
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(seconds.getWindowToken(), 0);
            }
        }
    }

    private static void showKeyboard(Context context, EditText input) {
        InputMethodManager keyboard = context.getSystemService(InputMethodManager.class);
        if (keyboard != null) {
            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private static void showTranscriptionDialog(Context targetContext, Resources resources) {
        FilterState state = FilterController.get();
        int[] selected = {state.transcription().ordinal()};
        FilterState.Transcription[] statuses = FilterState.Transcription.values();
        String[] labels = new String[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            labels[i] = resources.getString(statuses[i].label());
        }
        showMiuixSingleChoiceDialog(
                targetContext,
                resources.getString(R.string.transcription),
                labels,
                selected[0],
                (dialog, which) -> selected[0] = which,
                resources.getString(R.string.apply),
                (dialog, which) -> applyState(
                        targetContext, FilterController.get().withTranscription(statuses[selected[0]])),
                resources.getString(R.string.cancel));
    }

    private static void showDateDialog(Context targetContext, Resources resources) {
        FilterState state = FilterController.get();
        long today = currentDayStart();
        long initialStart = state.startTime() == null ? today : state.startTime();
        long initialEnd = state.endTime() == null ? initialStart : previousDay(state.endTime());
        long[] range = {Math.min(initialStart, initialEnd), Math.max(initialStart, initialEnd)};
        int[] selected = {0};
        TextView[] endpoints = createSegments(targetContext, "", "");
        updateDateLabels(resources, endpoints, range);
        View picker = createDatePicker(targetContext, range[0]);
        LinearLayout endpointGroup = createSegmentGroup(targetContext, endpoints, selected);
        for (int i = 0; i < endpoints.length; i++) {
            int index = i;
            endpoints[i].setOnClickListener(view -> {
                storeSelectedDate(picker, range, selected[0]);
                updateDateLabels(resources, endpoints, range);
                selected[0] = index;
                updateSegmentStyles(targetContext, endpoints, index);
                setDatePickerDate(picker, range[index]);
            });
        }

        FrameLayout pickerCard = new FrameLayout(targetContext);
        pickerCard.setBackground(roundedBackground(
                withAlpha(resolveColor(targetContext, android.R.attr.textColorPrimary, 0xff000000), 12),
                dp(targetContext, 20)));
        pickerCard.setClipToOutline(true);
        pickerCard.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        pickerCard.setPadding(0, dp(targetContext, 8), 0, 0);
        pickerCard.addView(picker, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        TextView reset = createMiuixTextView(targetContext);
        reset.setText(resources.getString(R.string.clear_date_filter));
        reset.setGravity(Gravity.END);
        reset.setPadding(0, dp(targetContext, 8), 0, dp(targetContext, 8));
        TypedValue accent = new TypedValue();
        if (targetContext.getTheme().resolveAttribute(android.R.attr.colorAccent, accent, true)) {
            reset.setTextColor(accent.resourceId == 0 ? accent.data : targetContext.getColor(accent.resourceId));
        }

        LinearLayout container = new LinearLayout(targetContext);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(targetContext, 24);
        container.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        container.addView(endpointGroup);
        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pickerParams.topMargin = dp(targetContext, 12);
        container.addView(pickerCard, pickerParams);
        container.addView(reset, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(targetContext);
        scroll.setFillViewport(true);
        scroll.addView(container, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        Dialog[] shown = new Dialog[1];
        shown[0] = showMiuixDialog(
                targetContext,
                resources.getString(R.string.date),
                scroll,
                resources.getString(R.string.apply),
                (ignored, which) -> {
                    storeSelectedDate(picker, range, selected[0]);
                    long start = Math.min(range[0], range[1]);
                    long end = Math.max(range[0], range[1]);
                    applyState(targetContext, FilterController.get().withDate(start, nextDay(end)));
                },
                resources.getString(R.string.cancel));
        observeDatePicker(picker, resources, endpoints, range, selected, shown[0]);
        reset.setOnClickListener(view -> {
            applyState(targetContext, FilterController.get().withDate(null, null));
            shown[0].dismiss();
        });
    }

    private static long parseSeconds(String text, long fallback) {
        long safeFallback = Math.max(1, Math.min(fallback, MAX_DURATION_SECONDS));
        if (text.isBlank()) {
            return safeFallback;
        }
        try {
            return Math.max(1, Math.min(Long.parseLong(text.trim()), MAX_DURATION_SECONDS));
        } catch (NumberFormatException exception) {
            Log.w(TAG, "Invalid duration input", exception);
            return safeFallback;
        }
    }

    private static TextView[] createSegments(Context context, String... labels) {
        TextView[] buttons = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView button = createMiuixTextView(context);
            button.setText(labels[i]);
            button.setTextSize(16);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10));
            buttons[i] = button;
        }
        return buttons;
    }

    private static LinearLayout createSegmentGroup(Context context, TextView[] buttons, int[] selected) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
        group.setBackground(roundedBackground(
                withAlpha(resolveColor(context, android.R.attr.textColorPrimary, 0xff000000), 12),
                dp(context, 16)));
        for (int i = 0; i < buttons.length; i++) {
            int index = i;
            group.addView(buttons[i], new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            buttons[i].setOnClickListener(view -> {
                selected[0] = index;
                updateSegmentStyles(context, buttons, index);
            });
        }
        updateSegmentStyles(context, buttons, selected[0]);
        return group;
    }

    private static void updateSegmentStyles(Context context, TextView[] buttons, int selected) {
        int accent = resolveColor(context, android.R.attr.colorAccent, 0xff3482ff);
        int primary = resolveColor(context, android.R.attr.textColorPrimary, 0xff000000);
        for (int i = 0; i < buttons.length; i++) {
            boolean active = i == selected;
            buttons[i].setBackground(active
                    ? roundedBackground(withAlpha(accent, 36), dp(context, 12))
                    : null);
            buttons[i].setTextColor(active ? accent : primary);
            buttons[i].setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private static void updateDateLabels(Resources resources, TextView[] endpoints, long[] range) {
        endpoints[0].setText(resources.getString(R.string.date_start_value, formatDate(resources, range[0])));
        endpoints[1].setText(resources.getString(R.string.date_end_value, formatDate(resources, range[1])));
    }

    private static View createDatePicker(Context context, long date) {
        try {
            Class<?> pickerClass = Class.forName(
                    RecorderSymbols.MIUIX_CALENDAR_PICKER_CLASS, false, context.getClassLoader());
            View picker = (View) pickerClass
                    .getConstructor(Context.class, android.util.AttributeSet.class)
                    .newInstance(context, null);
            pickerClass.getMethod("setDate", long.class).invoke(picker, date);
            return picker;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            Log.w(TAG, "MIUIX calendar unavailable; using Android DatePicker", exception);
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(date);
            DatePicker picker = new DatePicker(context);
            picker.updateDate(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            return picker;
        }
    }

    private static void setDatePickerDate(View picker, long date) {
        if (picker instanceof DatePicker datePicker) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(date);
            datePicker.updateDate(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            return;
        }
        try {
            picker.getClass().getMethod("setDate", long.class).invoke(picker, date);
        } catch (ReflectiveOperationException exception) {
            Log.e(TAG, "Unable to update MIUIX calendar date", exception);
        }
    }

    private static boolean storeSelectedDate(View picker, long[] range, int selected) {
        if (picker instanceof DatePicker datePicker) {
            range[selected] = dayStart(
                    datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth());
            normalizeRange(range, selected);
            return true;
        }
        try {
            int year = (int) picker.getClass().getMethod("getYear").invoke(picker);
            int month = (int) picker.getClass().getMethod("getMonth").invoke(picker);
            int day = (int) picker.getClass().getMethod("getDayOfMonth").invoke(picker);
            range[selected] = dayStart(year, month, day);
            normalizeRange(range, selected);
            return true;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            Log.e(TAG, "Unable to read MIUIX calendar date", exception);
            return false;
        }
    }

    private static void normalizeRange(long[] range, int selected) {
        if (selected == 0) {
            range[1] = Math.max(range[0], range[1]);
        } else {
            range[0] = Math.min(range[0], range[1]);
        }
    }

    private static void observeDatePicker(
            View picker,
            Resources resources,
            TextView[] endpoints,
            long[] range,
            int[] selected,
            Dialog dialog) {
        if (picker instanceof DatePicker datePicker) {
            datePicker.init(
                    datePicker.getYear(),
                    datePicker.getMonth(),
                    datePicker.getDayOfMonth(),
                    (view, year, month, day) -> {
                        range[selected[0]] = dayStart(year, month, day);
                        normalizeRange(range, selected[0]);
                        updateDateLabels(resources, endpoints, range);
                    });
            return;
        }

        boolean[] readable = {true};
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            if (!readable[0]) {
                return true;
            }
            long oldStart = range[0];
            long oldEnd = range[1];
            readable[0] = storeSelectedDate(picker, range, selected[0]);
            if (range[0] != oldStart || range[1] != oldEnd) {
                updateDateLabels(resources, endpoints, range);
            }
            return true;
        };
        picker.getViewTreeObserver().addOnPreDrawListener(listener);
        dialog.setOnDismissListener(ignored -> {
            ViewTreeObserver observer = picker.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(listener);
            }
        });
    }

    private static Drawable roundedBackground(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private static TextView createMiuixTextView(Context context) {
        try {
            Class<?> viewClass = Class.forName(
                    RecorderSymbols.MIUIX_TEXT_VIEW_CLASS, false, context.getClassLoader());
            return (TextView) viewClass
                    .getConstructor(Context.class, android.util.AttributeSet.class)
                    .newInstance(context, null);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return new TextView(context);
        }
    }

    private static EditText createMiuixEditText(Context context) {
        try {
            Class<?> viewClass = Class.forName(
                    RecorderSymbols.MIUIX_EDIT_TEXT_CLASS, false, context.getClassLoader());
            return (EditText) viewClass
                    .getConstructor(Context.class, android.util.AttributeSet.class)
                    .newInstance(context, null);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return new EditText(context);
        }
    }

    private static int resolveColor(Context context, int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            return context.getColorStateList(value.resourceId).getDefaultColor();
        }
        return value.data;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private static void showMiuixSingleChoiceDialog(
            Context context,
            String title,
            String[] labels,
            int checked,
            DialogInterface.OnClickListener itemListener,
            String positive,
            DialogInterface.OnClickListener positiveListener,
            String negative) {
        try {
            int itemLayout = context.getResources().getIdentifier(
                    "miuix_appcompat_select_dialog_singlechoice", "layout", RecorderSymbols.PACKAGE);
            ListAdapter adapter = new ArrayAdapter<>(context, itemLayout, android.R.id.text1, labels);
            Class<?> builderClass = Class.forName(
                    RecorderSymbols.MIUIX_DIALOG_BUILDER_CLASS, false, context.getClassLoader());
            Object builder = builderClass.getConstructor(Context.class).newInstance(context);
            builderClass.getMethod(RecorderSymbols.DIALOG_TITLE_METHOD, CharSequence.class).invoke(builder, title);
            builderClass
                    .getMethod(
                            RecorderSymbols.DIALOG_SINGLE_CHOICE_METHOD,
                            ListAdapter.class,
                            int.class,
                            DialogInterface.OnClickListener.class)
                    .invoke(builder, adapter, checked, itemListener);
            builderClass
                    .getMethod(
                            RecorderSymbols.DIALOG_POSITIVE_METHOD,
                            String.class,
                            DialogInterface.OnClickListener.class)
                    .invoke(builder, positive, positiveListener);
            builderClass
                    .getMethod(
                            RecorderSymbols.DIALOG_NEGATIVE_METHOD,
                            String.class,
                            DialogInterface.OnClickListener.class)
                    .invoke(builder, negative, null);
            builderClass.getMethod(RecorderSymbols.DIALOG_SHOW_METHOD).invoke(builder);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.w(TAG, "MIUIX single-choice dialog unavailable; using Android dialog", exception);
            new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setSingleChoiceItems(labels, checked, itemListener)
                    .setPositiveButton(positive, positiveListener)
                    .setNegativeButton(negative, null)
                    .show();
        }
    }

    private static Dialog showMiuixDialog(
            Context context,
            String title,
            View content,
            String positive,
            DialogInterface.OnClickListener listener,
            String negative) {
        try {
            Class<?> builderClass = Class.forName(
                    RecorderSymbols.MIUIX_DIALOG_BUILDER_CLASS, false, context.getClassLoader());
            Object builder = builderClass.getConstructor(Context.class).newInstance(context);
            builderClass.getMethod(RecorderSymbols.DIALOG_TITLE_METHOD, CharSequence.class).invoke(builder, title);
            builderClass.getMethod(RecorderSymbols.DIALOG_CONTENT_METHOD, View.class).invoke(builder, content);
            builderClass
                    .getMethod(
                            RecorderSymbols.DIALOG_POSITIVE_METHOD,
                            String.class,
                            DialogInterface.OnClickListener.class)
                    .invoke(builder, positive, listener);
            builderClass
                    .getMethod(
                            RecorderSymbols.DIALOG_NEGATIVE_METHOD,
                            String.class,
                            DialogInterface.OnClickListener.class)
                    .invoke(builder, negative, null);
            return (Dialog) builderClass.getMethod(RecorderSymbols.DIALOG_SHOW_METHOD).invoke(builder);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            Log.w(TAG, "MIUIX content dialog unavailable; using Android dialog", exception);
            return new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setView(content)
                    .setPositiveButton(positive, listener)
                    .setNegativeButton(negative, null)
                    .show();
        }
    }

    private static Dialog showMiuixContentDialog(Context context, String title, View content) {
        try {
            Class<?> builderClass = Class.forName(
                    RecorderSymbols.MIUIX_DIALOG_BUILDER_CLASS, false, context.getClassLoader());
            Object builder = builderClass.getConstructor(Context.class).newInstance(context);
            builderClass.getMethod(RecorderSymbols.DIALOG_TITLE_METHOD, CharSequence.class).invoke(builder, title);
            builderClass.getMethod(RecorderSymbols.DIALOG_CONTENT_METHOD, View.class).invoke(builder, content);
            return (Dialog) builderClass.getMethod(RecorderSymbols.DIALOG_SHOW_METHOD).invoke(builder);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            Log.w(TAG, "MIUIX menu dialog unavailable; using Android dialog", exception);
            return new AlertDialog.Builder(context).setTitle(title).setView(content).show();
        }
    }

    private static String durationDescription(Resources resources, FilterState state) {
        int seconds = Math.toIntExact(state.durationSeconds());
        return switch (state.durationOperator()) {
            case ALL -> resources.getString(R.string.filter_all);
            case LESS_THAN -> resources.getQuantityString(R.plurals.duration_value_less_than, seconds, seconds);
            case AT_LEAST -> resources.getQuantityString(R.plurals.duration_value_at_least, seconds, seconds);
        };
    }

    private static String dateDescription(Resources resources, FilterState state) {
        Long start = state.startTime();
        Long end = state.endTime() == null ? null : previousDay(state.endTime());
        if (start != null && end != null) {
            if (start.equals(end)) {
                return formatShortDate(start);
            }
            return resources.getString(
                    R.string.date_range,
                    formatShortDate(start),
                    isSameYear(start, end) ? formatShortMonthDay(end) : formatShortDate(end));
        }
        if (start != null) {
            return resources.getString(R.string.date_from, formatShortDate(start));
        }
        if (end != null) {
            return resources.getString(R.string.date_until, formatShortDate(end));
        }
        return resources.getString(R.string.date_any);
    }

    private static String formatShortDate(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        return String.format(
                Locale.ROOT,
                "%04d/%d/%d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    private static String formatShortMonthDay(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        return String.format(
                Locale.ROOT,
                "%d/%d",
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    private static boolean isSameYear(long first, long second) {
        Calendar firstDate = Calendar.getInstance();
        Calendar secondDate = Calendar.getInstance();
        firstDate.setTimeInMillis(first);
        secondDate.setTimeInMillis(second);
        return firstDate.get(Calendar.YEAR) == secondDate.get(Calendar.YEAR);
    }

    private static String formatDate(Resources resources, long time) {
        DateFormat format = DateFormat.getDateInstance(
                DateFormat.MEDIUM, resources.getConfiguration().getLocales().get(0));
        return format.format(new Date(time));
    }

    private static long dayStart(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day);
        return calendar.getTimeInMillis();
    }

    private static long currentDayStart() {
        Calendar calendar = Calendar.getInstance();
        return dayStart(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    private static long nextDay(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTimeInMillis();
    }

    private static long previousDay(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        return calendar.getTimeInMillis();
    }

    private static void applyState(Context context, FilterState state) {
        FilterController.set(state);
        context.getContentResolver().notifyChange(RECORDS_URI, null, ContentResolver.NOTIFY_UPDATE);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
