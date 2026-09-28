package app.morphe.extension.tiktok.comment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * A commenter blocked from one TikTok account isn't shown as blocked to the next account signed
 * in, where the first tap would have sent that account an unblock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CommentBlockAccountsTest {
    /** Stands in for a comment: the tools read its user's uid by name. */
    public static final class Comment {
        public final User user;

        Comment(String uid) {
            user = new User(uid);
        }
    }

    public static final class User {
        public final String uid;

        User(String uid) {
            this.uid = uid;
        }
    }

    @After public void tearDown() throws Exception {
        blocked().clear();
        CommentTools.signedInUserIdForTests = null;
    }

    @Test public void aBlockBelongsToTheAccountThatMadeIt() throws Exception {
        CommentTools.signedInUserIdForTests = "account_a";
        blocked().add(blockKey("commenter"));
        assertTrue(isBlocked(new Comment("commenter")));

        CommentTools.signedInUserIdForTests = "account_b";
        assertFalse("another account saw the first account's block", isBlocked(new Comment("commenter")));

        CommentTools.signedInUserIdForTests = "account_a";
        assertTrue("switching back lost the block", isBlocked(new Comment("commenter")));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> blocked() throws Exception {
        Field field = CommentTools.class.getDeclaredField("BLOCKED_UIDS");
        field.setAccessible(true);
        return (Set<String>) field.get(null);
    }

    private static String blockKey(String uid) throws Exception {
        Method method = CommentTools.class.getDeclaredMethod("blockKey", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, uid);
    }

    private static boolean isBlocked(Object comment) throws Exception {
        Method method = CommentTools.class.getDeclaredMethod("isBlocked", Object.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, comment);
    }
}
