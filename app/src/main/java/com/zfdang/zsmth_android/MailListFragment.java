package com.zfdang.zsmth_android;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import com.zfdang.SMTHApplication;
import com.zfdang.zsmth_android.listeners.EndlessRecyclerOnScrollListener;
import com.zfdang.zsmth_android.listeners.OnMailInteractionListener;
import com.zfdang.zsmth_android.models.ComposePostContext;
import com.zfdang.zsmth_android.models.Mail;
import com.zfdang.zsmth_android.models.MailListContent;
import com.zfdang.zsmth_android.newsmth.AjaxResponse;
import com.zfdang.zsmth_android.newsmth.SMTHHelper;
import io.reactivex.ObservableSource;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnMailInteractionListener}
 * interface.
 */
public class MailListFragment extends Fragment implements View.OnClickListener {

  private static final String TAG = "MailListFragment";
  public static final String INBOX_LABEL = "inbox";
  private static final String OUTBOX_LABEL = "outbox";
  private static final String DELETED_LABEL = "deleted";
  public static final String AT_LABEL = "at";
  public static final String REPLY_LABEL = "reply";
  public static final String LIKE_LABEL = "like";

  private OnMailInteractionListener mListener;
  private RecyclerView recyclerView;
  private EndlessRecyclerOnScrollListener mScrollListener = null;

  private Button btInbox;
  private Button btOutbox;
  private Button btTrashbox;
  private Button btAt;
  private Button btReply;
  private Button btLike;

  private int colorNormal;
  private int colorBlue;

  private String currentFolder = INBOX_LABEL;
  private int currentPage;

  /**
   * Mandatory empty constructor for the fragment manager to instantiate the
   * fragment (e.g. upon screen orientation changes).
   */
  public MailListFragment() {
  }

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // http://stackoverflow.com/questions/8308695/android-options-menu-in-fragment
    setHasOptionsMenu(true);
  }

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_mail_list, container, false);

    recyclerView = (RecyclerView) view.findViewById(R.id.recyclerview_mail_contents);
    Context context = view.getContext();
    LinearLayoutManager linearLayoutManager = new WrapContentLinearLayoutManager(context);
    recyclerView.setLayoutManager(linearLayoutManager);
    recyclerView.setItemAnimator(new DefaultItemAnimator());
    recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), LinearLayoutManager.VERTICAL, 0));
    recyclerView.setAdapter(new MailRecyclerViewAdapter(MailListContent.MAILS, mListener));

    // enable endless loading
    mScrollListener = new EndlessRecyclerOnScrollListener(linearLayoutManager) {
      @Override public void onLoadMore(int current_page) {
        // do something...
        LoadMoreMails();
      }
    };
    recyclerView.addOnScrollListener(mScrollListener);

    // enable swipe to delete mail
    initItemHelper();

    btInbox = (Button) view.findViewById(R.id.mail_button_inbox);
    btInbox.setOnClickListener(this);
    btOutbox = (Button) view.findViewById(R.id.mail_button_outbox);
    btOutbox.setOnClickListener(this);
    btTrashbox = (Button) view.findViewById(R.id.mail_button_trashbox);
    btTrashbox.setOnClickListener(this);
    btAt = (Button) view.findViewById(R.id.mail_button_at);
    btAt.setOnClickListener(this);
    btReply = (Button) view.findViewById(R.id.mail_button_reply);
    btReply.setOnClickListener(this);
    btLike = (Button) view.findViewById(R.id.mail_button_like);
    btLike.setOnClickListener(this);

    colorNormal = getResources().getColor(R.color.status_text_night);
    colorBlue = getResources().getColor(R.color.blue_text_night);

    if (MailListContent.MAILS.size() == 0) {
      LoadMailsFromBeginning();
    }

    // highlight the current folder
    highlightCurrentFolder();

    return view;
  }

  public void initItemHelper() {
    //0则不执行拖动或者滑动
    ItemTouchHelper.Callback mCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

      @Override public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        return false;
      }

      @Override public void onSwiped(final RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();
        Mail mail = MailListContent.MAILS.get(position);
        if (!mail.isCategory) {
          String type = "mail";
          String mailId = mail.getMailIDFromURL();
          if (mail.referIndex != null && mail.referIndex.length() > 0) {
            type = "refer";
            mailId = mail.referIndex;
          }

          Map<String, String> mails = new HashMap<String, String>();
          String mailKey = String.format("m_%s", mailId);
          mails.put(mailKey, "on");

          SMTHHelper helper = SMTHHelper.getInstance();
          helper.wService.deleteMailOrReferPost(type, currentFolder, mails)
              .subscribeOn(Schedulers.io())
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe(new Observer<AjaxResponse>() {
                @Override public void onSubscribe(@NonNull Disposable disposable) {

                }

                @Override public void onNext(@NonNull AjaxResponse ajaxResponse) {
                  // Log.d(TAG, "onNext: " + ajaxResponse.toString());
                  if (ajaxResponse.getAjax_st() == AjaxResponse.AJAX_RESULT_OK) {
                    MailListContent.MAILS.remove(viewHolder.getAdapterPosition());
                    recyclerView.getAdapter().notifyItemRemoved(viewHolder.getAdapterPosition());
                  }
                  Toast.makeText(getActivity(), ajaxResponse.getAjax_msg(), Toast.LENGTH_SHORT).show();
                }

                @Override public void onError(@NonNull Throwable e) {
                  Toast.makeText(SMTHApplication.getAppContext(), "删除邮件失败!\n" + e.toString(), Toast.LENGTH_LONG).show();
                }

                @Override public void onComplete() {

                }
              });
        }
      }
    };
    ItemTouchHelper itemTouchHelper = new ItemTouchHelper(mCallback);
    itemTouchHelper.attachToRecyclerView(recyclerView);
  }

  public void setCurrentFolder(String folder) {
    if (TextUtils.equals(folder, INBOX_LABEL)) {
      currentFolder = INBOX_LABEL;
    } else if (TextUtils.equals(folder, AT_LABEL)) {
      currentFolder = AT_LABEL;
    } else if (TextUtils.equals(folder, REPLY_LABEL)) {
      currentFolder = REPLY_LABEL;
    } else if (TextUtils.equals(folder, LIKE_LABEL)) {
      currentFolder = LIKE_LABEL;
    }
    //        Log.d(TAG, "setCurrentFolder: " + folder);
  }

  public void highlightCurrentFolder() {
    if (TextUtils.equals(currentFolder, INBOX_LABEL)) {
      btInbox.setTextColor(colorBlue);
      btOutbox.setTextColor(colorNormal);
      btTrashbox.setTextColor(colorNormal);
      btAt.setTextColor(colorNormal);
      btReply.setTextColor(colorNormal);
      btLike.setTextColor(colorNormal);
    } else if (TextUtils.equals(currentFolder, OUTBOX_LABEL)) {
      btInbox.setTextColor(colorNormal);
      btOutbox.setTextColor(colorBlue);
      btTrashbox.setTextColor(colorNormal);
      btAt.setTextColor(colorNormal);
      btReply.setTextColor(colorNormal);
      btLike.setTextColor(colorNormal);
    } else if (TextUtils.equals(currentFolder, DELETED_LABEL)) {
      btInbox.setTextColor(colorNormal);
      btOutbox.setTextColor(colorNormal);
      btTrashbox.setTextColor(colorBlue);
      btAt.setTextColor(colorNormal);
      btReply.setTextColor(colorNormal);
      btLike.setTextColor(colorNormal);
    } else if (TextUtils.equals(currentFolder, AT_LABEL)) {
      btInbox.setTextColor(colorNormal);
      btOutbox.setTextColor(colorNormal);
      btTrashbox.setTextColor(colorNormal);
      btAt.setTextColor(colorBlue);
      btReply.setTextColor(colorNormal);
      btLike.setTextColor(colorNormal);
    } else if (TextUtils.equals(currentFolder, REPLY_LABEL)) {
      btInbox.setTextColor(colorNormal);
      btOutbox.setTextColor(colorNormal);
      btTrashbox.setTextColor(colorNormal);
      btAt.setTextColor(colorNormal);
      btReply.setTextColor(colorBlue);
      btLike.setTextColor(colorNormal);
    } else if (TextUtils.equals(currentFolder, LIKE_LABEL)) {
      btInbox.setTextColor(colorNormal);
      btOutbox.setTextColor(colorNormal);
      btTrashbox.setTextColor(colorNormal);
      btAt.setTextColor(colorNormal);
      btReply.setTextColor(colorNormal);
      btLike.setTextColor(colorBlue);
    }
  }

  public void LoadMoreMails() {
    // LoadMore will be re-enabled in clearLoadingHints.
    // if we return here, loadMore will not be triggered again

    MainActivity activity = (MainActivity) getActivity();
    if (activity != null && activity.pDialog != null && activity.pDialog.isShowing()) {
      // loading in progress, do nothing
      return;
    }

    if (currentPage >= MailListContent.totalPages) {
      // reach the last page, do nothing
      Mail mail = new Mail(".END.");
      MailListContent.addItem(mail);

      recyclerView.getAdapter().notifyItemChanged(MailListContent.MAILS.size() - 1);
      return;
    }

    currentPage += 1;
    LoadMailsOrReferPosts();
  }

  public void LoadMailsFromBeginning() {
    currentPage = 1;
    MailListContent.clear();
    recyclerView.getAdapter().notifyDataSetChanged();

    showLoadingHints();
    LoadMailsOrReferPosts();
  }

  public void LoadMailsOrReferPosts() {
    if (TextUtils.equals(currentFolder, INBOX_LABEL) || TextUtils.equals(currentFolder, OUTBOX_LABEL) || TextUtils.equals(currentFolder,
        DELETED_LABEL)) {
      // Load mails
      LoadMails();
    } else {
      // Load refer posts
      LoadReferPosts();
    }
  }

  public void LoadReferPosts() {
    // Log.d(TAG, "LoadReferPosts: " + currentPage);
    SMTHHelper helper = SMTHHelper.getInstance();

    helper.wService.getReferPosts(currentFolder, Integer.toString(currentPage)).flatMap(new Function<ResponseBody, ObservableSource<Mail>>() {
      @Override public ObservableSource<Mail> apply(@NonNull ResponseBody responseBody) throws Exception {
        try {
          String response = responseBody.string();
          List<Mail> results = SMTHHelper.ParseMailsFromWWW(response);
          return Observable.fromIterable(results);
        } catch (Exception e) {
          Toast.makeText(SMTHApplication.getAppContext(), "加载文章提醒失败\n" + e.toString(), Toast.LENGTH_LONG).show();
        }
        return null;
      }
    }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Mail>() {
      @Override public void onSubscribe(@NonNull Disposable disposable) {

      }

      @Override public void onNext(@NonNull Mail mail) {
        // Log.d(TAG, "onNext: " + mail.toString());
        MailListContent.addItem(mail);
        recyclerView.getAdapter().notifyItemChanged(MailListContent.MAILS.size() - 1);
      }

      @Override public void onError(@NonNull Throwable e) {
        clearLoadingHints();
        Toast.makeText(getActivity(), "加载相关文章失败！\n" + e.toString(), Toast.LENGTH_LONG).show();

      }

      @Override public void onComplete() {
        clearLoadingHints();
        recyclerView.smoothScrollToPosition(0);
      }
    });
  }

  public void LoadMails() {
    // Log.d(TAG, "LoadMails: " + currentPage);
    SMTHHelper helper = SMTHHelper.getInstance();

    helper.wService.getUserMails(currentFolder, Integer.toString(currentPage)).flatMap(new Function<ResponseBody, ObservableSource<Mail>>() {
      @Override public ObservableSource<Mail> apply(@NonNull ResponseBody responseBody) throws Exception {
        try {
          String response = responseBody.string();
          List<Mail> results = SMTHHelper.ParseMailsFromWWW(response);
          return Observable.fromIterable(results);
        } catch (Exception e) {
          Toast.makeText(SMTHApplication.getAppContext(), "加载邮件错误\n" + e.toString(), Toast.LENGTH_SHORT).show();
        }
        return null;
      }
    }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Mail>() {
      @Override public void onSubscribe(@NonNull Disposable disposable) {

      }

      @Override public void onNext(@NonNull Mail mail) {
        // Log.d(TAG, "onNext: " + mail.toString());
        MailListContent.addItem(mail);
        recyclerView.getAdapter().notifyItemChanged(MailListContent.MAILS.size() - 1);
      }

      @Override public void onError(@NonNull Throwable e) {
        clearLoadingHints();
        Toast.makeText(SMTHApplication.getAppContext(), "加载邮件列表失败！\n" + e.toString(), Toast.LENGTH_LONG).show();
      }

      @Override public void onComplete() {
        clearLoadingHints();
        recyclerView.smoothScrollToPosition(0);
      }
    });
  }

  public void showLoadingHints() {
    MainActivity activity = (MainActivity) getActivity();
    if (activity != null) activity.showProgress("加载信件中...");
  }

  public void clearLoadingHints() {
    // disable progress bar
    MainActivity activity = (MainActivity) getActivity();
    if (activity != null) {
      activity.dismissProgress();
    }

    // re-enable endless load
    if (mScrollListener != null) {
      mScrollListener.setLoading(false);
    }
  }

  public void markMailAsReaded(final int position) {
    if (position >= 0 && position < MailListContent.MAILS.size()) {
      final Mail mail = MailListContent.MAILS.get(position);
      if (!mail.isNew) return;

      // only referred post need explicit marking. for mails, there is no need to mark
      if (TextUtils.equals(currentFolder, INBOX_LABEL) || TextUtils.equals(currentFolder, OUTBOX_LABEL) || TextUtils.equals(currentFolder,
          DELETED_LABEL)) {
        mail.isNew = false;
        recyclerView.getAdapter().notifyItemChanged(position);
        return;
      }

      // mark it as read in remote and local
      SMTHHelper helper = SMTHHelper.getInstance();
      helper.wService.readReferPosts(currentFolder, mail.referIndex)
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(new Observer<AjaxResponse>() {
            @Override public void onSubscribe(@NonNull Disposable disposable) {

            }

            @Override public void onNext(@NonNull AjaxResponse ajaxResponse) {
              // Log.d(TAG, "onNext: " + ajaxResponse.toString());
              if (ajaxResponse.getAjax_st() == AjaxResponse.AJAX_RESULT_OK) {
                // succeed to mark the post as read in remote
                mail.isNew = false;
                recyclerView.getAdapter().notifyItemChanged(position);
              } else {
                // mark remote failed, show the response message
                Toast.makeText(getActivity(), ajaxResponse.getAjax_msg(), Toast.LENGTH_SHORT).show();
              }
            }

            @Override public void onError(@NonNull Throwable e) {
              Toast.makeText(SMTHApplication.getAppContext(), "设置已读标记失败!\n" + e.toString(), Toast.LENGTH_LONG).show();
            }

            @Override public void onComplete() {

            }
          });
    }
  }

  @Override public void onAttach(Context context) {
    super.onAttach(context);
    if (context instanceof OnMailInteractionListener) {
      mListener = (OnMailInteractionListener) context;
    } else {
      throw new RuntimeException(context.toString() + " must implement OnMailInteractionListener");
    }
  }

  @Override public void onDetach() {
    super.onDetach();
    mListener = null;
  }

  @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    MenuItem item = menu.findItem(R.id.mail_list_fragment_newmail);
    item.setVisible(true);
    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.mail_list_fragment_newmail) {
      // write new mail
      ComposePostContext postContext = new ComposePostContext();
      postContext.setComposingMode(ComposePostContext.MODE_NEW_MAIL);
      Intent intent = new Intent(getActivity(), ComposePostActivity.class);
      intent.putExtra(SMTHApplication.COMPOSE_POST_CONTEXT, postContext);
      startActivity(intent);
      return true;
    } else if (id == R.id.main_action_refresh) {
      LoadMailsFromBeginning();
    }

    return super.onOptionsItemSelected(item);
  }

  @Override public void onClick(View v) {
    if (v == btInbox) {
      if (TextUtils.equals(currentFolder, INBOX_LABEL)) return;
      currentFolder = INBOX_LABEL;
    } else if (v == btOutbox) {
      if (TextUtils.equals(currentFolder, OUTBOX_LABEL)) return;
      currentFolder = OUTBOX_LABEL;
    } else if (v == btTrashbox) {
      if (TextUtils.equals(currentFolder, DELETED_LABEL)) return;
      currentFolder = DELETED_LABEL;
    } else if (v == btAt) {
      if (TextUtils.equals(currentFolder, AT_LABEL)) return;
      currentFolder = AT_LABEL;
    } else if (v == btReply) {
      if (TextUtils.equals(currentFolder, REPLY_LABEL)) return;
      currentFolder = REPLY_LABEL;
    } else if (v == btLike) {
      if (TextUtils.equals(currentFolder, LIKE_LABEL)) return;
      currentFolder = LIKE_LABEL;
    }

    highlightCurrentFolder();
    LoadMailsFromBeginning();
  }
}
