package com.shurjomukhi.speechdetector;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

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
  public static final String ITEM = "item";

  private LottieAnimationView animationView;

  public static ListeningDialogFragment newInstance(String item) {
    ListeningDialogFragment fragment = new ListeningDialogFragment();
    Bundle args = new Bundle();
    args.putString(ITEM, item);
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
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onSpeechEvent(SpeechEvent event) {
    //Toast.makeText(getActivity(), event.toString(), Toast.LENGTH_SHORT).show();
    if (event.getText().equalsIgnoreCase(getArguments().getString(ITEM))) {
      animationView.setAnimation(R.raw.trophy);
      animationView.setSpeed(2.0f);
      animationView.playAnimation();
    }
  }
}
