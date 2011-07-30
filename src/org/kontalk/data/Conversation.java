package org.kontalk.data;

import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.ui.MessagingNotification;

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;


/**
 * A class represeting a conversation thread.
 * @author Daniele Ricci
 * @version 1.0
 */
public class Conversation {

    private static final String[] ALL_THREADS_PROJECTION = {
        Threads._ID,
        Threads.PEER,
        Threads.DIRECTION,
        Threads.COUNT,
        Threads.UNREAD,
        Threads.MIME,
        Threads.CONTENT,
        Threads.TIMESTAMP,
        Threads.STATUS
    };

    private final Context mContext;

    private long mThreadId;
    private Contact mContact;

    private String mRecipient;
    private long mDate;
    private int mMessageCount;
    private String mSubject;
    private int mUnreadCount;
    private int mStatus;

    private Conversation(Context context) {
        mContext = context;
        mThreadId = 0;
    }

    private Conversation(Context context, Cursor c) {
        mContext = context;
        synchronized (this) {
            mThreadId = c.getLong(c.getColumnIndex(Threads._ID));
            mDate = c.getLong(c.getColumnIndex(Threads.TIMESTAMP));

            mRecipient = c.getString(c.getColumnIndex(Threads.PEER));
            mSubject = c.getString(c.getColumnIndex(Threads.CONTENT));

            mUnreadCount = c.getInt(c.getColumnIndex(Threads.UNREAD));
            mMessageCount = c.getInt(c.getColumnIndex(Threads.COUNT));
            mStatus = c.getInt(c.getColumnIndex(Threads.STATUS));

            loadContact();
        }
    }

    public static Conversation createNew(Context context) {
        return new Conversation(context);
    }

    public static Conversation createFromCursor(Context context, Cursor cursor) {
        return new Conversation(context, cursor);
    }

    public static Conversation loadFromUserId(Context context, String userId) {
        Conversation cv = null;
        Cursor cp = context.getContentResolver().query(Threads.CONTENT_URI,
                null, Threads.PEER + " = ?", new String[] { userId }, null);
        if (cp.moveToFirst())
            cv = createFromCursor(context, cp);

        cp.close();
        return cv;
    }

    public static Conversation loadFromId(Context context, long id) {
        Conversation cv = null;
        Cursor cp = context.getContentResolver().query(
                ContentUris.withAppendedId(Threads.CONTENT_URI, id),
                null, null, null, null);
        if (cp.moveToFirst())
            cv = createFromCursor(context, cp);

        cp.close();
        return cv;
    }

    private void loadContact() {
        mContact = Contact.findByUserId(mContext, mRecipient);
    }

    public Contact getContact() {
        return mContact;
    }

    public long getDate() {
        return mDate;
    }

    public void setDate(long mDate) {
        this.mDate = mDate;
    }

    public String getSubject() {
        return mSubject;
    }

    public void setSubject(String mSubject) {
        this.mSubject = mSubject;
    }

    public String getRecipient() {
        return mRecipient;
    }

    public void setRecipient(String mRecipient) {
        this.mRecipient = mRecipient;
        // reload contact
        loadContact();
    }

    public int getMessageCount() {
        return mMessageCount;
    }

    public int getUnreadCount() {
        return mUnreadCount;
    }

    public long getThreadId() {
        return mThreadId;
    }

    public int getStatus() {
        return mStatus;
    }

    public static void startQuery(AsyncQueryHandler handler, int token) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null, Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, null, null, Threads.DEFAULT_SORT_ORDER);
    }

    public static void startQuery(AsyncQueryHandler handler, int token, long threadId) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null, Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, Threads._ID + " = " + threadId, null, Threads.DEFAULT_SORT_ORDER);
    }

    public void markAsRead() {
        if (mThreadId > 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (MessagesProvider.getThreadUnreadCount(mContext, mThreadId) > 0) {
                        MessagesProvider.markThreadAsRead(mContext, mThreadId);
                    }

                    MessagingNotification.updateMessagesNotification(mContext.getApplicationContext(), false);
                }
            }).start();
        }
    }
}