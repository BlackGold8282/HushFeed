package app.morphe.extension.tiktok.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import app.morphe.extension.shared.GlobalLayoutHook;
import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ShareSheetToolsTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        ReflectionHelpers.setStaticField(Setting.class, "pausedForProcess", false);
        ShareModelFilter.surface(null);
        Settings.HIDE_SHARE_CONTACTS.save(false);
        ReflectionHelpers.setStaticField(ShareSheetTools.class, "windowGlobal", null);
        ReflectionHelpers.setStaticField(ShareSheetTools.class, "windowViewsReader", null);
        ReflectionHelpers.setStaticField(ShareSheetTools.class, "windowViewsUnavailable", false);
        ReflectionHelpers.setStaticField(ShareSheetTools.class, "applyPosted", false);
        Object cache = ReflectionHelpers.getStaticField(ShareSheetTools.class, "RESOURCE_IDS");
        ((ResourceIdCache) cache).clear();
        Map<String, Integer> ids = ReflectionHelpers.getField(cache, "ids");
        ids.put(context.getPackageName() + ":47.0.3:ip5", 0x7f000201);
        ids.put(context.getPackageName() + ":ibc", 0x7f000101);
        ids.put(context.getPackageName() + ":47.0.3:v3j", 0x7f000301);
        ids.put(context.getPackageName() + ":47.0.3:a5t", 0x7f000401);
    }

    @After public void tearDown() {
        ReflectionHelpers.setStaticField(Setting.class, "pausedForProcess", false);
        Settings.HIDE_SHARE_CONTACTS.resetToDefault();
        Settings.SHARE_HIDDEN_ITEMS.resetToDefault();
        ((GlobalLayoutHook) ReflectionHelpers.getStaticField(ShareSheetTools.class, "LAYOUT_HOOK")).detach();
        ((GlobalLayoutHook) ReflectionHelpers.getStaticField(ShareSheetTools.class, "SHEET_LAYOUT_HOOK")).detach();
        ReflectionHelpers.setStaticField(ShareSheetTools.class, "activityReference", new WeakReference<>(null));
        ((ResourceIdCache) ReflectionHelpers.getStaticField(ShareSheetTools.class, "RESOURCE_IDS")).clear();
        ((Map<?, ?>) ReflectionHelpers.getStaticField(ShareSheetTools.class, "ORIGINAL_WIDTHS")).clear();
        ReflectionHelpers.setStaticField(ShareSheetTools.class, "windowGlobal", null);
        ReflectionHelpers.setStaticField(ShareSheetTools.class, "windowViewsReader", null);
        ReflectionHelpers.setStaticField(ShareSheetTools.class, "windowViewsUnavailable", false);
        ReflectionHelpers.setStaticField(ShareSheetTools.class, "applyPosted", false);
        ShareModelFilter.surface(null);
    }

    @Test public void recycledCellsRestoreTheirOriginalWidthAfterHiding() {
        FrameLayout cell = new FrameLayout(context);
        cell.setLayoutParams(new FrameLayout.LayoutParams(120, 48));

        ShareSheetTools.setCellHidden(cell, true);
        assertEquals(View.GONE, cell.getVisibility());
        assertEquals(0, cell.getLayoutParams().width);

        ShareSheetTools.setCellHidden(cell, false);
        assertEquals(View.VISIBLE, cell.getVisibility());
        assertEquals(120, cell.getLayoutParams().width);

        cell.getLayoutParams().width = 64;
        ShareSheetTools.setCellHidden(cell, true);
        ShareSheetTools.setCellHidden(cell, false);
        assertEquals("the recycled cell returns to its first measured width", 120,
                cell.getLayoutParams().width);
    }

    /**
     * One walk per root finds what findViewById finds: the first view with the id in pre-order,
     * never one inside a nested window root, where findViewById doesn't look.
     */
    @Test public void oneWalkPerRootFindsWhatFindViewByIdFinds() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout root = new FrameLayout(activity);
            FrameLayout branch = new FrameLayout(activity);
            View deepFirst = new View(activity);
            deepFirst.setId(0x7f000501);
            branch.addView(deepFirst);
            View shallowLater = new View(activity);
            shallowLater.setId(0x7f000501);
            root.addView(branch);
            root.addView(shallowLater);
            // A dialog's window root, not shown, so it has no parent yet: the framework marks it
            // a root namespace, and findViewById from above never enters it.
            android.app.Dialog dialog = new android.app.Dialog(activity);
            ViewGroup decor = (ViewGroup) dialog.getWindow().getDecorView();
            View inside = new View(activity);
            inside.setId(0x7f000502);
            decor.addView(inside);
            root.addView(decor);
            View other = new View(activity);
            other.setId(0x7f000503);
            root.addView(other);

            java.util.Set<Integer> ids = java.util.Set.of(0x7f000501, 0x7f000502, 0x7f000503, 0x7f000504);
            java.util.List<Map<Integer, View>> index = ShareSheetTools.indexRoots(java.util.List.of(root), ids);
            assertSame("the first view in pre-order", deepFirst, root.findViewById(0x7f000501));
            assertNull("the control: findViewById skips the nested window root", root.findViewById(0x7f000502));
            for (int id : ids) {
                assertSame(Integer.toHexString(id), root.findViewById(id), index.get(0).get(id));
            }
        }
    }

    @Test public void theHiddenListSplitsOnLineBreaksAsWellAsCommas() {
        assertEquals(java.util.List.of("sam", "whatsapp", "copy link", "repost"),
                ShareSheetTools.entries("Sam\nWhatsApp, Copy link\r\n,,Repost\n"));
        assertTrue(ShareSheetTools.entries(null).isEmpty());
    }

    @Test public void current47ContactsSectionWinsWhenOlderResourceStillResolves() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout root = new FrameLayout(activity);
            FrameLayout currentSection = new FrameLayout(activity);
            currentSection.setId(0x7f000201);
            root.addView(currentSection);
            activity.setContentView(root);

            Settings.HIDE_SHARE_CONTACTS.save(true);
            ReflectionHelpers.setStaticField(ShareSheetTools.class, "activityReference",
                    new WeakReference<>(activity));
            ReflectionHelpers.callStaticMethod(ShareSheetTools.class, "apply");

            assertEquals(View.GONE, currentSection.getVisibility());
        }
    }

    /**
     * 47.0.3 builds the panel in a window of its own, which the activity's layout listener doesn't
     * see being laid out, so a contact bind is what runs the hiding pass there.
     */
    @Test public void aContactBindRunsTheHidingPassOnTheSendToRow() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout root = new FrameLayout(activity);
            FrameLayout contacts = new FrameLayout(activity);
            contacts.setId(0x7f000301);
            FrameLayout alice = new FrameLayout(activity);
            alice.setContentDescription("  Alice  ");
            alice.setLayoutParams(new FrameLayout.LayoutParams(120, 48));
            FrameLayout bob = new FrameLayout(activity);
            bob.setContentDescription("Bob");
            bob.setLayoutParams(new FrameLayout.LayoutParams(120, 48));
            contacts.addView(alice);
            contacts.addView(bob);
            root.addView(contacts);
            activity.setContentView(root);
            ReflectionHelpers.setStaticField(ShareSheetTools.class, "activityReference",
                    new WeakReference<>(activity));
            Settings.SHARE_HIDDEN_ITEMS.save("alice");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("the control: nothing has run the pass yet", View.VISIBLE, alice.getVisibility());

            ShareSheetTools.contactBound();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertEquals("Alice", ShareSheetTools.labelOf(alice));
            assertEquals(View.GONE, alice.getVisibility());
            assertEquals(View.VISIBLE, bob.getVisibility());
        }
    }

    /** The native action delegate publishes the bound child title, not a View description. */
    @Test public void nativeActionTitlesHideWithNullOrBlankRootDescriptions() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout actions = actionRow(activity);
            NativeActionCell report = new NativeActionCell(activity, 137, "  Report  ");
            NativeActionCell translated = new NativeActionCell(activity, 143, "Melden");
            translated.setContentDescription("  ");
            NativeActionCell repost = new NativeActionCell(activity, 129, "Repost");
            NativeActionCell copy = new NativeActionCell(activity, 131, "Copy link");
            int[] clicks = {0};
            copy.setOnClickListener(view -> clicks[0]++);
            actions.addView(report);
            actions.addView(translated);
            actions.addView(repost);
            actions.addView(copy);
            activity.setContentView(actions);
            ReflectionHelpers.setStaticField(ShareSheetTools.class, "activityReference",
                    new WeakReference<>(activity));
            Settings.SHARE_HIDDEN_ITEMS.save("report, melden");

            ReflectionHelpers.callStaticMethod(ShareSheetTools.class, "apply");

            assertNull("reading the native title does not copy it onto the View", report.getContentDescription());
            assertEquals("Report", ShareSheetTools.labelOf(report));
            assertEquals("Melden", ShareSheetTools.labelOf(translated));
            assertEquals(View.GONE, report.getVisibility());
            assertEquals(0, report.getLayoutParams().width);
            assertEquals(View.GONE, translated.getVisibility());
            assertEquals(View.VISIBLE, repost.getVisibility());
            assertEquals(View.VISIBLE, copy.getVisibility());
            assertEquals("reading labels cannot activate a share action", 0, clicks[0]);
            copy.performClick();
            assertEquals("an allowed cell keeps its listener", 1, clicks[0]);
        }
    }

    @Test public void anExcludedDescendantDoesNotLabelItsContainingGroup() {
        FrameLayout group = new FrameLayout(context);
        group.addView(new NativeActionCell(context, 137, "Report"));

        assertNull("the native vertical row can contain several choices", ShareSheetTools.labelOf(group));
    }

    /** The activity observer cannot deliver a later layout in TikTok's separate panel window. */
    @Test public void latePanelLabelsAndRecycledCellsUseThePanelsOwnLayouts() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Activity activity = controller.get();
            activity.setContentView(new FrameLayout(activity));
            ShareSheetTools.install(activity);
            idle();
            NativeActionCell cell = new NativeActionCell(activity, 137, "");
            NativeActionCell copy = new NativeActionCell(activity, 131, "Copy link");
            Dialog dialog = showActions(activity, cell, copy);
            try {
                Settings.SHARE_HIDDEN_ITEMS.save("report");
                ShareSheetTools.contactBound();
                idle();
                assertNull(ShareSheetTools.labelOf(cell));
                assertEquals(View.VISIBLE, cell.getVisibility());

                cell.title.setText("Report");
                panelLayout(dialog);
                assertEquals(View.GONE, cell.getVisibility());
                assertEquals(0, cell.getLayoutParams().width);
                assertEquals(View.VISIBLE, copy.getVisibility());
                assertEquals(131, copy.getLayoutParams().width);

                cell.title.setText("Repost");
                cell.getLayoutParams().width = 64;
                panelLayout(dialog);
                assertEquals(View.VISIBLE, cell.getVisibility());
                assertEquals("a native rebind cannot replace the original width", 137,
                        cell.getLayoutParams().width);

                cell.title.setText("Report");
                panelLayout(dialog);
                assertEquals(View.GONE, cell.getVisibility());
                ReflectionHelpers.setStaticField(Setting.class, "pausedForProcess", true);
                panelLayout(dialog);
                assertEquals("Pause restores the row", View.VISIBLE, cell.getVisibility());
                assertEquals(137, cell.getLayoutParams().width);
                assertEquals("Pause keeps the exclusion", "report", Settings.SHARE_HIDDEN_ITEMS.savedValue());

                ReflectionHelpers.setStaticField(Setting.class, "pausedForProcess", false);
                panelLayout(dialog);
                assertEquals(View.GONE, cell.getVisibility());
                Settings.SHARE_HIDDEN_ITEMS.save("");
                panelLayout(dialog);
                assertEquals("clearing the exclusion restores the row", View.VISIBLE, cell.getVisibility());
                assertEquals(137, cell.getLayoutParams().width);
            } finally {
                dialog.dismiss();
            }
        }
    }

    @Test public void aReopenedPanelObservesItsNewLateBoundActionCells() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Activity activity = controller.get();
            activity.setContentView(new FrameLayout(activity));
            ShareSheetTools.install(activity);
            idle();
            Settings.SHARE_HIDDEN_ITEMS.save("report");
            NativeActionCell first = new NativeActionCell(activity, 137, "Report");
            Dialog original = showActions(activity, first);
            try {
                ShareSheetTools.contactBound();
                idle();
                assertEquals(View.GONE, first.getVisibility());
            } finally {
                original.dismiss();
            }
            idle();
            activity.findViewById(android.R.id.content).getViewTreeObserver().dispatchOnGlobalLayout();
            idle();

            NativeActionCell reopened = new NativeActionCell(activity, 149, "");
            Dialog next = showActions(activity, reopened);
            try {
                ShareSheetTools.contactBound();
                idle();
                assertEquals(View.VISIBLE, reopened.getVisibility());
                reopened.title.setText("Report");
                panelLayout(next);
                assertEquals(View.GONE, reopened.getVisibility());
                assertEquals(0, reopened.getLayoutParams().width);
                reopened.title.setText("Copy link");
                panelLayout(next);
                assertEquals(View.VISIBLE, reopened.getVisibility());
                assertEquals("a new cell owns its own original width", 149,
                        reopened.getLayoutParams().width);
            } finally {
                next.dismiss();
            }
        }
    }

    private static FrameLayout actionRow(Context context) {
        FrameLayout actions = new FrameLayout(context);
        actions.setId(0x7f000401);
        return actions;
    }

    private static Dialog showActions(Activity activity, NativeActionCell... cells) {
        FrameLayout actions = actionRow(activity);
        for (NativeActionCell cell : cells) actions.addView(cell);
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(actions);
        dialog.show();
        return dialog;
    }

    private static void panelLayout(Dialog dialog) {
        dialog.getWindow().getDecorView().getViewTreeObserver().dispatchOnGlobalLayout();
        idle();
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    /** The holder/delegate shape shared by both native action adapters on every declared host. */
    private static final class NativeActionCell extends FrameLayout {
        final TextView title;

        NativeActionCell(Context context, int width, String label) {
            super(context);
            setLayoutParams(new FrameLayout.LayoutParams(width, 48));
            title = new TextView(context);
            title.setText(label);
            addView(title);
            setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo node) {
                    super.onInitializeAccessibilityNodeInfo(host, node);
                    node.setContentDescription(title.getText());
                    node.setClassName(android.widget.Button.class.getName());
                }
            });
        }
    }

    public static final class TestActivity extends Activity {
    }
}
