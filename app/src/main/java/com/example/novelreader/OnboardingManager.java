package com.example.novelreader;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists one-time onboarding states without tying them to the book database. */
public final class OnboardingManager {
    private static final String PREFS = "onboarding";
    private static final String KEY_WELCOME_DONE = "welcomeDone";
    private static final String KEY_READER_DONE = "readerDone";
    private static final String KEY_ONLINE_VISITED = "onlineVisited";
    private static final String KEY_CHECKLIST_DISMISSED = "checklistDismissed";

    private OnboardingManager() {
    }

    public static boolean shouldShowWelcome(Context context) {
        return !prefs(context).getBoolean(KEY_WELCOME_DONE, false);
    }

    public static void completeWelcome(Context context) {
        prefs(context).edit().putBoolean(KEY_WELCOME_DONE, true).apply();
    }

    public static boolean shouldShowReaderGuide(Context context) {
        return !prefs(context).getBoolean(KEY_READER_DONE, false);
    }

    public static void completeReaderGuide(Context context) {
        prefs(context).edit().putBoolean(KEY_READER_DONE, true).apply();
    }

    public static boolean hasVisitedOnlineSearch(Context context) {
        return prefs(context).getBoolean(KEY_ONLINE_VISITED, false);
    }

    public static void markOnlineSearchVisited(Context context) {
        prefs(context).edit().putBoolean(KEY_ONLINE_VISITED, true).apply();
    }

    public static boolean isChecklistDismissed(Context context) {
        return prefs(context).getBoolean(KEY_CHECKLIST_DISMISSED, false);
    }

    public static void dismissChecklist(Context context) {
        prefs(context).edit().putBoolean(KEY_CHECKLIST_DISMISSED, true).apply();
    }

    public static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
