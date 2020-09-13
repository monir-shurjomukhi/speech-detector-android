package com.shurjomukhi.speechdetector;

import android.animation.Animator;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.airbnb.lottie.LottieAnimationView;
import com.shurjomukhi.speechdetector.eventbus.SpeechEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class ListeningDialogFragment extends DialogFragment {
  public static final String TAG = "ListeningDialogFragment";
  public static final String ITEM_NAME = "item-name";
  public static final String ITEM_IMAGE = "item-image";

  private LottieAnimationView animationView;
  private TextView listeningTextView;
  private ImageView itemImageView;

  public static ListeningDialogFragment newInstance(String item, int itemImage) {
    ListeningDialogFragment fragment = new ListeningDialogFragment();
    Bundle args = new Bundle();
    args.putString(ITEM_NAME, item);
    args.putInt(ITEM_IMAGE, itemImage);
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_listening_dialog, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    animationView = view.findViewById(R.id.animationView);
    listeningTextView = view.findViewById(R.id.listeningTextView);
    String itemName = getArguments().getString(ITEM_NAME).substring(0, 1).toUpperCase() +
        getArguments().getString(ITEM_NAME).substring(1);
    listeningTextView.setText(String.format("Playing %s", itemName));
    //animateTextViewTextChange(listeningTextView, 500, String.format("Playing %s", itemName));
    itemImageView = view.findViewById(R.id.itemImageView);
    itemImageView.setImageResource(getArguments().getInt(ITEM_IMAGE));

    playSound(itemName);
  }

  private void playSound(String fileName) {
    switch (fileName) {
      case "Apple":
        play(R.raw.apple);
        break;
      case "Bird":
        play(R.raw.bird);
        break;
      case "Cat":
        play(R.raw.cat);
        break;
      case "Dog":
        play(R.raw.dog);
        break;
    }
  }

  private void play(int resourceId) {
    try {
      //AssetFileDescriptor descriptor = getActivity().getAssets().openFd(fileName + "wav");
      MediaPlayer player = MediaPlayer.create(getContext(), resourceId);
      //player.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
      //player.prepare();
      player.start();
      player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
          String itemName = getArguments().getString(ITEM_NAME).substring(0, 1).toUpperCase() +
              getArguments().getString(ITEM_NAME).substring(1);
          //listeningTextView.setText(String.format("Speak %s", itemName));
          animateTextViewTextChange(listeningTextView, 500, String.format("Speak %s", itemName));
          animationView.setAnimation(R.raw.listening);
          animationView.setSpeed(2.0f);
          animationView.playAnimation();
          try {
            ((SpeechActivity) getActivity()).startSpeechRecognition();
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      });
    } catch (Exception e) {
      Log.e(TAG, "play: ", e);
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    getDialog().getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT);
    EventBus.getDefault().register(this);
  }

  @Override
  public void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
    try {
      ((SpeechActivity) getActivity()).stopSpeechRecognition();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onSpeechEvent(SpeechEvent event) {
    //Toast.makeText(getActivity(), event.toString(), Toast.LENGTH_SHORT).show();
    Log.d(TAG, "onSpeechEvent: event = " + event);
    if (event.getText().equalsIgnoreCase(getArguments().getString(ITEM_NAME)) && (event.getScore() * 100) >= 70.0f) {
      animationView.setAnimation(R.raw.trophy);
      animationView.setSpeed(2.0f);
      animationView.playAnimation();
      String score = ((event.getScore() * 100) + "").substring(0, 5) + "%";
      String text = "Congratulations! You are <b>" + score + "</b> correct.";
      //listeningTextView.setText(Html.fromHtml(text));
      animateTextViewTextChange(listeningTextView, 500, Html.fromHtml(text).toString());
      try {
        ((SpeechActivity) getActivity()).stopSpeechRecognition();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static void animateTextViewTextChange(final TextView textView,
                                               final int duration, final String newText) {
    textView.animate().alpha(0f).setDuration(duration)
        .setListener(new Animator.AnimatorListener() {
          @Override
          public void onAnimationEnd(Animator animation) {
            textView.setText(newText);
            textView.animate().alpha(1f).setDuration(duration)
                .start();
          }

          @Override
          public void onAnimationStart(Animator animation) {
          }

          @Override
          public void onAnimationCancel(Animator animation) {
          }

          @Override
          public void onAnimationRepeat(Animator animation) {
          }
        }).start();
  }
}
