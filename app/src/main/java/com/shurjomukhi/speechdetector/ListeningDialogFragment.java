package com.shurjomukhi.speechdetector;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.airbnb.lottie.LottieAnimationView;

public class ListeningDialogFragment extends DialogFragment {
  public static final String TAG = "ListeningDialogFragment";

  public static ListeningDialogFragment newInstance() {
    ListeningDialogFragment fragment = new ListeningDialogFragment();
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

    LottieAnimationView animationView = view.findViewById(R.id.animationView);
    animationView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        animationView.setAnimation(R.raw.trophy);
        animationView.playAnimation();
        animationView.setSpeed(2.0f);
      }
    });
  }

  @Override
  public void onStart() {
    super.onStart();
    getDialog().getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT);
  }
}
