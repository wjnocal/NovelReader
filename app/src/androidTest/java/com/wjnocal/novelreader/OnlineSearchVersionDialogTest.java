package com.wjnocal.novelreader;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.wjnocal.novelreader.online.OnlineBookResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
public class OnlineSearchVersionDialogTest {
    @Test
    public void longVersionListCanScrollToLastSourceAndBack() {
        verifyVersionDialog(19);
    }

    @Test
    public void singleVersionAndCloseButtonRemainVisible() {
        verifyVersionDialog(1);
    }

    private void verifyVersionDialog(int count) {
        try (ActivityScenario<OnlineSearchActivity> scenario = ActivityScenario.launch(OnlineSearchActivity.class)) {
            scenario.onActivity(activity -> populateResults(activity, count));
            onView(withText("滚动测试小说\n" + count + " 个书源版本")).perform(scrollTo(), click());
            onView(withText("关闭")).check(matches(isDisplayed()));
            onView(withText(sourceText(1))).check(matches(isDisplayed()));
            if (count > 1) {
                onView(withText(sourceText(count))).check(matches(not(isDisplayed())));
                onView(withText(sourceText(count))).perform(scrollTo()).check(matches(isDisplayed()));
                onView(withText("关闭")).check(matches(isDisplayed()));
                onView(withText(sourceText(1))).perform(scrollTo()).check(matches(isDisplayed()));
            }
            onView(withText("关闭")).perform(click());
        }
    }

    @SuppressWarnings("unchecked")
    private static void populateResults(OnlineSearchActivity activity, int count) {
        try {
            Class<?> groupClass = Class.forName(OnlineSearchActivity.class.getName() + "$ResultGroup");
            Constructor<?> constructor = groupClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object group = constructor.newInstance();
            Field title = groupClass.getDeclaredField("title");
            title.setAccessible(true);
            title.set(group, "滚动测试小说");
            Field versionsField = groupClass.getDeclaredField("versions");
            versionsField.setAccessible(true);
            List<OnlineBookResult> versions = (List<OnlineBookResult>) versionsField.get(group);
            for (int i = 1; i <= count; i++) {
                OnlineBookResult result = new OnlineBookResult();
                result.sourceName = "测试书源" + i;
                result.author = "测试作者";
                result.latestChapter = "第一章";
                versions.add(result);
            }
            Method renderResults = OnlineSearchActivity.class.getDeclaredMethod("renderResults", List.class);
            renderResults.setAccessible(true);
            renderResults.invoke(activity, Collections.singletonList(group));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法创建书源版本测试数据", e);
        }
    }

    private static String sourceText(int index) {
        return "测试书源" + index + "\n测试作者 · 第一章";
    }
}
