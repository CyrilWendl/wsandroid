package fi.bitrite.android.ws.ui.listadapter;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import androidx.annotation.NonNull;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import fi.bitrite.android.ws.R;
import fi.bitrite.android.ws.model.Message;
import fi.bitrite.android.ws.model.MessageThread;
import fi.bitrite.android.ws.model.User;
import fi.bitrite.android.ws.repository.MessageRepository;
import fi.bitrite.android.ws.repository.Repository;
import fi.bitrite.android.ws.repository.UserRepository;
import fi.bitrite.android.ws.ui.util.NavigationController;
import fi.bitrite.android.ws.ui.widget.UserCircleImageView;
import fi.bitrite.android.ws.util.LoggedInUserHelper;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class MessageThreadListAdapter extends
        DataBoundListAdapter<MessageThread, MessageThreadListAdapter.ItemBinding> {

    private final LoggedInUserHelper mLoggedInUserHelper;
    private final MessageRepository mMessageRepository;
    private final NavigationController mNavigationController;
    private final UserRepository mUserRepository;

    public MessageThreadListAdapter(
            LoggedInUserHelper loggedInUserHelper, MessageRepository messageRepository,
            NavigationController navigationController, UserRepository userRepository) {
        mUserRepository = userRepository;
        mMessageRepository = messageRepository;
        mLoggedInUserHelper = loggedInUserHelper;
        mNavigationController = navigationController;
    }

    @Override
    protected ItemBinding createBinding(ViewGroup parent) {
        return new ItemBinding(parent);
    }

    @Override
    protected void sort(List<MessageThread> threads) {
        Collections.sort(threads, (left, right) -> right.lastUpdated.compareTo(left.lastUpdated));
    }

    @Override
    protected boolean areItemsTheSame(MessageThread left, MessageThread right) {
        return left.id == right.id;
    }

    @Override
    protected boolean areContentsTheSame(MessageThread left, MessageThread right) {
        return left.id == right.id
               && left.subject.equals(right.subject)
               && left.started.equals(right.started)
               && left.isRead() == right.isRead()
               && left.lastUpdated.equals(right.lastUpdated)
               && left.messages.size() == right.messages.size();
    }

    class ItemBinding implements DataBoundListAdapter.ViewDataBinding<MessageThread> {

        @BindView(R.id.thread_icon) UserCircleImageView mIcon;
        @BindView(R.id.thread_lbl_participants) TextView mLblParticipants;
        @BindView(R.id.thread_lbl_last_updated) TextView mLblLastUpdated;
        @BindView(R.id.thread_lbl_subject) TextView mLblSubject;
        @BindView(R.id.thread_lbl_preview) TextView mLblPreview;

        private final View mRoot;
        private MessageThread mThread;
        private CompositeDisposable mDisposables = new CompositeDisposable();

        ItemBinding(ViewGroup parent) {
            mRoot = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_thread, parent, false);
            ButterKnife.bind(this, mRoot);

            mRoot.setOnCreateContextMenuListener(this::onCreateContextMenu);
        }

        @Override
        public View getRoot() {
            return mRoot;
        }

        @Override
        public void bind(@NonNull MessageThread thread) {
            mThread = thread;

            mDisposables.dispose();
            mDisposables = new CompositeDisposable();

            mRoot.setVisibility(View.GONE);

            // Bold if unread.
            int tf = thread.isRead() ? Typeface.NORMAL : Typeface.BOLD;
            mLblParticipants.setTypeface(null, tf);
            mLblLastUpdated.setTypeface(null, tf);
            mLblSubject.setTypeface(null, tf);

            mLblSubject.setText(thread.subject);

            mLblLastUpdated.setText(DateUtils.getRelativeTimeSpanString(
                    thread.lastUpdated.getTime(),
                    new Date().getTime(),
                    0,
                    DateUtils.FORMAT_NUMERIC_DATE
                    | DateUtils.FORMAT_SHOW_YEAR
                    | DateUtils.FORMAT_ABBREV_RELATIVE).toString());

            @SuppressLint("UseSparseArrays") Map<Integer, User> participants = new HashMap<>();
            mDisposables.add(Observable.merge(
                    mUserRepository.get(getParticipantIdsWithoutCurrentUser(thread),
                            Repository.ShouldSaveInDb.YES))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(userResource -> {
                        User user = userResource.data;
                        if (user == null) {
                            return;
                        }

                        participants.put(user.id, user);
                        mIcon.setUsers(participants.values());

                        List<String> names = new ArrayList<>(participants.size());
                        for (User participant : participants.values()) {
                            names.add(participant.getName());
                        }
                        mLblParticipants.setText(TextUtils.join(", ", names));

                        mRoot.setVisibility(View.VISIBLE);
                    }));

            // Shows a preview of the newest message.
            if (!thread.messages.isEmpty()) {
                // TODO(saemy): Show oldest unread message?
                Message newestMessage =
                        Collections.max(thread.messages, MessageListAdapter.COMPARATOR);
                String body = newestMessage.body.toString();

                // Recursively removes all "\n\n" by "\n". Therefore, "\n\n\n" becomes "\n", too.
                int lastLength;
                do {
                    lastLength = body.length();
                    body = body.replace("\n\n", "\n");
                } while (lastLength != body.length());

                // Replaces all remaining newlines by a single space.
                body = body.trim().replace("\n", " ");
                mLblPreview.setText(Html.fromHtml(body));

                if (!newestMessage.isPushed) {
                    mLblLastUpdated.setText("..."); // TODO(saemy): Add a hourglass icon?
                }
            }

            // Registers the onClick listener.
            mRoot.setOnClickListener(
                    view -> mNavigationController.navigateToMessageThread(thread.id));
        }

        /**
         * Gets the IDs of users participating in the message thread while excluding the currently
         * logged in user.
         *
         * @param thread Message thread whose participating users are to be returned
         * @return Set of User IDs participating, excluding currently logged in user
         */
        private Set<Integer> getParticipantIdsWithoutCurrentUser(MessageThread thread) {
            final Set<Integer> participantIds = new HashSet<>(thread.participantIds);
            final User loggedInUser = mLoggedInUserHelper.get();
            int loggedInUserId = loggedInUser != null ? loggedInUser.id : -1;
            participantIds.remove(loggedInUserId);

            if (participantIds.isEmpty()) {
                // No participants known -> search the messages.
                for (Message message : thread.messages) {
                    participantIds.add(message.authorId);
                }
                participantIds.remove(loggedInUserId); // Remove ourselves.

                if (participantIds.isEmpty() && loggedInUser != null) {
                    // Still no participants known -> just add ourselves.
                    participantIds.add(loggedInUserId);
                }
                // TODO(saemy): Should we handle the case where there is still no participant?
            }

            return participantIds;
        }

        private void onCreateContextMenu(ContextMenu menu, View v,
                                         ContextMenu.ContextMenuInfo menuInfo) {
            if (mThread == null) {
                return;
            }

            final MenuItem readStatus =
                    menu.add(Menu.NONE, v.getId(), Menu.NONE, mThread.isRead()
                            ? R.string.message_mark_unread
                            : R.string.message_mark_read);

            readStatus.setOnMenuItemClickListener(menuItem -> {
                if (mThread == null) {
                    return false;
                }

                (mThread.isRead()
                         ? mMessageRepository.markThreadAsUnread(mThread.id)
                         : mMessageRepository.markThreadAsRead(mThread.id))
                        .onErrorComplete() // TODO(saemy): Error handling.
                        .subscribe();

                return true;
            });

            final MenuItem showProfile = menu.add(Menu.NONE, v.getId(), Menu.NONE,
                    getParticipantIdsWithoutCurrentUser(mThread).size() == 1
                            ? R.string.menu_goto_profile
                            : R.string.menu_goto_profiles
            );

            showProfile.setOnMenuItemClickListener(menuItem -> {
                if (mThread == null) {
                    return false;
                }

                final ArrayList<Integer> participantIds =
                        new ArrayList<>(getParticipantIdsWithoutCurrentUser(mThread));

                if (participantIds.size() > 1) {
                    mNavigationController.navigateToUserList(participantIds);
                } else {
                    mNavigationController.navigateToUser(participantIds.get(0));
                }

                return true;
            });
        }
    }
}
