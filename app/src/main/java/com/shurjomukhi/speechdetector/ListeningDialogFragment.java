package com.shurjomukhi.speechdetector;

import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.airbnb.lottie.LottieAnimationView;
import com.shurjomukhi.speechdetector.eventbus.SpeechEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Locale;

public class ListeningDialogFragment extends DialogFragment {
  public static final String TAG = "ListeningDialogFragment";
  public static final String ITEM = "item";
  public static final String ITEM_IMAGE = "item-image";

  private LottieAnimationView animationView;
  private TextView listeningTextView;
  private ImageView itemImageView;

  public static ListeningDialogFragment newInstance(String item, int itemImage) {
    ListeningDialogFragment fragment = new ListeningDialogFragment();
    Bundle args = new Bundle();
    args.putString(ITEM, item);
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
    itemImageView = view.findViewById(R.id.itemImageView);
    itemImageView.setImageResource(getArguments().getInt(ITEM_IMAGE));
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
    ((SpeechActivity) getActivity()).stopSpeechRecognition();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onSpeechEvent(SpeechEvent event) {
    //Toast.makeText(getActivity(), event.toString(), Toast.LENGTH_SHORT).show();
    Log.d(TAG, "onSpeechEvent: event = " + event);
    if (event.getText().equalsIgnoreCase(getArguments().getString(ITEM)) && (event.getScore() * 100) >= 70.0f) {
      animationView.setAnimation(R.raw.trophy);
      animationView.setSpeed(2.0f);
      animationView.playAnimation();
      String score = (event.getScore() * 100) + "%";
      String text = "Congratulations! You are <b>" + score + "</b> correct.";
      listeningTextView.setText(Html.fromHtml(text));
      ((SpeechActivity) getActivity()).stopSpeechRecognition();
    }
  }
}
