package com.shurjomukhi.speechdetector;

import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.shurjomukhi.speechdetector.eventbus.SpeechEvent;
import com.shurjomukhi.speechdetector.util.Constants;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.greenrobot.eventbus.EventBus;
import org.tensorflow.lite.Interpreter;

/**
 * An activity that listens for audio and then uses a TensorFlow model to detect particular classes,
 * by default a small set of action words.
 */
public class SpeechActivity extends AppCompatActivity
    implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

  private static final String TAG = "SpeechActivity";

  // Constants that control the behavior of the recognition code and model
  // settings. See the audio recognition tutorial for a detailed explanation of
  // all these, but you should customize them to match your training settings if
  // you are running your own model.
  private static final int SAMPLE_RATE = 16000;
  private static final int SAMPLE_DURATION_MS = 1000;
  private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
  private static final long AVERAGE_WINDOW_DURATION_MS = 1000;
  private static final float DETECTION_THRESHOLD = 0.50f;
  private static final int SUPPRESSION_MS = 1500;
  private static final int MINIMUM_COUNT = 3;
  private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 30;
  private static final String LABEL_FILENAME = "file:///android_asset/conv_actions_labels.txt";
  private static final String MODEL_FILENAME = "file:///android_asset/conv_actions_frozen.tflite";

  // UI elements.
  private static final int REQUEST_RECORD_AUDIO = 13;
  private static final String LOG_TAG = SpeechActivity.class.getSimpleName();

  // Working variables.
  short[] recordingBuffer = new short[RECORDING_LENGTH];
  int recordingOffset = 0;
  boolean shouldContinue = true;
  private Thread recordingThread;
  boolean shouldContinueRecognition = true;
  private Thread recognitionThread;
  private final ReentrantLock recordingBufferLock = new ReentrantLock();

  private List<String> labels = new ArrayList<String>();
  private List<String> displayedLabels = new ArrayList<>();
  private RecognizeCommands recognizeCommands = null;
  private LinearLayout bottomSheetLayout;
  private LinearLayout gestureLayout;
  private BottomSheetBehavior<LinearLayout> sheetBehavior;

  private Interpreter tfLite;
  private ImageView bottomSheetArrowImageView;

  private TextView yesTextView,
      noTextView,
      upTextView,
      downTextView,
      leftTextView,
      rightTextView,
      onTextView,
      offTextView,
      stopTextView,
      goTextView,
      aTextView,
      bTextView,
      cTextView,
      dTextView,
      pTextView;
  private TextView sampleRateTextView, inferenceTimeTextView;
  private ImageView plusImageView, minusImageView;
  private SwitchCompat apiSwitchCompat;
  private TextView threadsTextView;
  private long lastProcessingTimeMs;
  private Handler handler = new Handler();
  private TextView selectedTextView = null;
  private HandlerThread backgroundThread;
  private Handler backgroundHandler;

  private boolean isRecognitionActive;

  /**
   * Memory-map the model file in Assets.
   */
  private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
      throws IOException {
    AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // Set up the UI.
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_speech);

    // Load the labels for the model, but only display those that don't start
    // with an underscore.
    String actualLabelFilename = LABEL_FILENAME.split("file:///android_asset/", -1)[1];
    Log.i(LOG_TAG, "Reading labels from: " + actualLabelFilename);
    BufferedReader br = null;
    try {
      br = new BufferedReader(new InputStreamReader(getAssets().open(actualLabelFilename)));
      String line;
      while ((line = br.readLine()) != null) {
        labels.add(line);
        if (line.charAt(0) != '_') {
          displayedLabels.add(line.substring(0, 1).toUpperCase() + line.substring(1));
        }
      }
      br.close();
    } catch (IOException e) {
      throw new RuntimeException("Problem reading label file!", e);
    }

    // Set up an object to smooth recognition results to increase accuracy.
    recognizeCommands =
        new RecognizeCommands(
            labels,
            AVERAGE_WINDOW_DURATION_MS,
            DETECTION_THRESHOLD,
            SUPPRESSION_MS,
            MINIMUM_COUNT,
            MINIMUM_TIME_BETWEEN_SAMPLES_MS);

    String actualModelFilename = MODEL_FILENAME.split("file:///android_asset/", -1)[1];
    try {
      tfLite = new Interpreter(loadModelFile(getAssets(), actualModelFilename));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    tfLite.resizeInput(0, new int[]{RECORDING_LENGTH, 1});
    tfLite.resizeInput(1, new int[]{1});

    sampleRateTextView = findViewById(R.id.sample_rate);
    inferenceTimeTextView = findViewById(R.id.inference_info);
    bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
    gestureLayout = findViewById(R.id.gesture_layout);
    sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
    bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);

    threadsTextView = findViewById(R.id.threads);
    plusImageView = findViewById(R.id.plus);
    minusImageView = findViewById(R.id.minus);
    apiSwitchCompat = findViewById(R.id.api_info_switch);

    yesTextView = findViewById(R.id.yes);
    noTextView = findViewById(R.id.no);
    upTextView = findViewById(R.id.up);
    downTextView = findViewById(R.id.down);
    leftTextView = findViewById(R.id.left);
    rightTextView = findViewById(R.id.right);
    onTextView = findViewById(R.id.on);
    offTextView = findViewById(R.id.off);
    stopTextView = findViewById(R.id.stop);
    goTextView = findViewById(R.id.go);
    aTextView = findViewById(R.id.a);
    bTextView = findViewById(R.id.b);
    cTextView = findViewById(R.id.c);
    dTextView = findViewById(R.id.d);
    pTextView = findViewById(R.id.p);

    apiSwitchCompat.setOnCheckedChangeListener(this);

    ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
    vto.addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            int height = gestureLayout.getMeasuredHeight();

            sheetBehavior.setPeekHeight(height);
          }
        });
    sheetBehavior.setHideable(false);

    sheetBehavior.addBottomSheetCallback(
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            switch (newState) {
              case BottomSheetBehavior.STATE_HIDDEN:
                break;
              case BottomSheetBehavior.STATE_EXPANDED: {
                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
              }
              break;
              case BottomSheetBehavior.STATE_COLLAPSED: {
                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
              }
              break;
              case BottomSheetBehavior.STATE_DRAGGING:
                break;
              case BottomSheetBehavior.STATE_SETTLING:
                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                break;
            }
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {
          }
        });

    plusImageView.setOnClickListener(this);
    minusImageView.setOnClickListener(this);

    sampleRateTextView.setText(SAMPLE_RATE + " Hz");
  }

  private void requestMicrophonePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requestPermissions(
          new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == REQUEST_RECORD_AUDIO
        && grantResults.length > 0
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      startRecording();
      startRecognition();
    }
  }

  public synchronized void startRecording() {
    if (recordingThread != null) {
      return;
    }
    shouldContinue = true;
    recordingThread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                record();
              }
            });
    recordingThread.start();
  }

  public synchronized void stopRecording() {
    if (recordingThread == null) {
      return;
    }
    shouldContinue = false;
    recordingThread = null;
  }

  private void record() {
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

    // Estimate the buffer size we'll need for this device.
    int bufferSize =
        AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
      bufferSize = SAMPLE_RATE * 2;
    }
    short[] audioBuffer = new short[bufferSize / 2];

    AudioRecord record =
        new AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize);

    if (record.getState() != AudioRecord.STATE_INITIALIZED) {
      Log.e(LOG_TAG, "Audio Record can't initialize!");
      return;
    }

    record.startRecording();

    Log.v(LOG_TAG, "Start recording");

    // Loop, gathering audio data and copying it to a round-robin buffer.
    while (shouldContinue) {
      int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
      int maxLength = recordingBuffer.length;
      int newRecordingOffset = recordingOffset + numberRead;
      int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
      int firstCopyLength = numberRead - secondCopyLength;
      // We store off all the data for the recognition thread to access. The ML
      // thread will copy out of this buffer into its own, while holding the
      // lock, so this should be thread safe.
      recordingBufferLock.lock();
      try {
        System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
        System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
        recordingOffset = newRecordingOffset % maxLength;
      } finally {
        recordingBufferLock.unlock();
      }
    }

    record.stop();
    record.release();
  }

  public synchronized void startRecognition() {
    if (recognitionThread != null) {
      return;
    }
    shouldContinueRecognition = true;
    recognitionThread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                recognize();
              }
            });
    recognitionThread.start();
  }

  public synchronized void stopRecognition() {
    if (recognitionThread == null) {
      return;
    }
    shouldContinueRecognition = false;
    recognitionThread = null;
  }

  private void recognize() {

    Log.v(LOG_TAG, "Start recognition");

    short[] inputBuffer = new short[RECORDING_LENGTH];
    float[][] floatInputBuffer = new float[RECORDING_LENGTH][1];
    float[][] outputScores = new float[1][labels.size()];
    int[] sampleRateList = new int[]{SAMPLE_RATE};

    // Loop, grabbing recorded data and running the recognition model on it.
    while (shouldContinueRecognition) {
      long startTime = new Date().getTime();
      // The recording thread places data in this round-robin buffer, so lock to
      // make sure there's no writing happening and then copy it to our own
      // local version.
      recordingBufferLock.lock();
      try {
        int maxLength = recordingBuffer.length;
        int firstCopyLength = maxLength - recordingOffset;
        int secondCopyLength = recordingOffset;
        System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
        System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
      } finally {
        recordingBufferLock.unlock();
      }

      // We need to feed in float values between -1.0f and 1.0f, so divide the
      // signed 16-bit inputs.
      for (int i = 0; i < RECORDING_LENGTH; ++i) {
        floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f;
      }

      Object[] inputArray = {floatInputBuffer, sampleRateList};
      Map<Integer, Object> outputMap = new HashMap<>();
      outputMap.put(0, outputScores);

      // Run the model.
      tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

      // Use the smoother to figure out if we've had a real recognition event.
      long currentTime = System.currentTimeMillis();
      final RecognizeCommands.RecognitionResult result =
          recognizeCommands.processLatestResults(outputScores[0], currentTime);
      lastProcessingTimeMs = new Date().getTime() - startTime;
      runOnUiThread(
          new Runnable() {
            @Override
            public void run() {

              inferenceTimeTextView.setText(lastProcessingTimeMs + " ms");

              /*Log.d(TAG, "run: result.foundCommand = " + result.foundCommand +
                  " result.score = " + (result.score * 100) + "%");*/

              // If we do have a new command, highlight the right list entry.
              if (!result.foundCommand.startsWith("_") && result.isNewCommand) {
                int labelIndex = -1;
                for (int i = 0; i < labels.size(); ++i) {
                  if (labels.get(i).equals(result.foundCommand)) {
                    labelIndex = i;
                  }
                }

                switch (labelIndex - 2) {
                  /*case 0:
                    selectedTextView = yesTextView;
                    break;
                  case 1:
                    selectedTextView = noTextView;
                    break;
                  case 2:
                    selectedTextView = upTextView;
                    break;
                  case 3:
                    selectedTextView = downTextView;
                    break;
                  case 4:
                    selectedTextView = leftTextView;
                    break;
                  case 5:
                    selectedTextView = rightTextView;
                    break;
                  case 6:
                    selectedTextView = onTextView;
                    break;
                  case 7:
                    selectedTextView = offTextView;
                    break;
                  case 8:
                    selectedTextView = stopTextView;
                    break;
                  case 9:
                    selectedTextView = goTextView;
                    break;*/
                  case 0:
                    selectedTextView = aTextView;
                    break;
                  case 1:
                    selectedTextView = bTextView;
                    break;
                  case 2:
                    selectedTextView = cTextView;
                    break;
                  case 3:
                    selectedTextView = dTextView;
                    break;
                  case 4:
                    selectedTextView = pTextView;
                    break;
                }

                if (selectedTextView != null) {
                  //selectedTextView.setBackgroundResource(R.drawable.round_corner_text_bg_selected);
                  String score = Math.round(result.score * 100) + "%";
                  Log.d(TAG, "run: command = " + result.foundCommand + " score  = " + score);

                  EventBus.getDefault().post(new SpeechEvent(result.foundCommand, result.score));
                  //stopSpeechRecognition();

                  /*selectedTextView.setText(selectedTextView.getText() + "\n" + score);
                  selectedTextView.setTextColor(
                      getResources().getColor(android.R.color.holo_orange_light));
                  handler.postDelayed(
                      new Runnable() {
                        @Override
                        public void run() {
                          String originalString =
                              selectedTextView.getText().toString().replace(score, "").trim();
                          selectedTextView.setText(originalString);
                          *//*selectedTextView.setBackgroundResource(
                              R.drawable.round_corner_text_bg_unselected);*//*
                          selectedTextView.setTextColor(
                              getResources().getColor(android.R.color.darker_gray));
                        }
                      },
                      750);*/
                }
              }
            }
          });
      try {
        // We don't need to run too frequently, so snooze for a bit.
        Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS);
      } catch (InterruptedException e) {
        // Ignore
      }
    }

    Log.v(LOG_TAG, "End recognition");
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.plus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      numThreads++;
      threadsTextView.setText(String.valueOf(numThreads));
      //            tfLite.setNumThreads(numThreads);
      int finalNumThreads = numThreads;
      backgroundHandler.post(() -> tfLite.setNumThreads(finalNumThreads));
    } else if (v.getId() == R.id.minus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads == 1) {
        return;
      }
      numThreads--;
      threadsTextView.setText(String.valueOf(numThreads));
      tfLite.setNumThreads(numThreads);
      int finalNumThreads = numThreads;
      backgroundHandler.post(() -> tfLite.setNumThreads(finalNumThreads));
    }
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    backgroundHandler.post(() -> tfLite.setUseNNAPI(isChecked));
    if (isChecked) apiSwitchCompat.setText("NNAPI");
    else apiSwitchCompat.setText("TFLITE");
  }

  private static final String HANDLE_THREAD_NAME = "CameraBackground";

  private void startBackgroundThread() {
    backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
  }

  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;
    } catch (InterruptedException e) {
      Log.e("amlan", "Interrupted when stopping background thread", e);
    }
  }

  @Override
  protected void onResume() {
    Log.d(TAG, "onResume: ");
    super.onResume();
    startBackgroundThread();
    /*if (isRecognitionActive) {
      stopSpeechRecognition();
    }*/
  }

  @Override
  protected void onPause() {
    super.onPause();
    Log.d(TAG, "onPause: ");
  }

  @Override
  protected void onStop() {
    Log.d(TAG, "onStop: ");
    super.onStop();
    stopBackgroundThread();
  }

  @Override
  protected void onStart() {
    super.onStart();
    Log.d(TAG, "onStart: ");
  }

  public void startSpeechRecognition() {
    // Start the recording and recognition threads.
    requestMicrophonePermission();
    startRecording();
    startRecognition();
    isRecognitionActive = true;
  }

  public void stopSpeechRecognition() {
    stopRecording();
    stopRecognition();
    isRecognitionActive = false;
  }

  public void appleClick(View view) {
    ListeningDialogFragment.newInstance(Constants.APPLE, R.drawable.ic_apple).show(getSupportFragmentManager(),
        ListeningDialogFragment.TAG);
    //startSpeechRecognition();
  }

  public void birdClick(View view) {
    ListeningDialogFragment.newInstance(Constants.BIRD, R.drawable.ic_bird).show(getSupportFragmentManager(),
        ListeningDialogFragment.TAG);
    //startSpeechRecognition();
  }

  public void catClick(View view) {
    ListeningDialogFragment.newInstance(Constants.CAT, R.drawable.ic_cat).show(getSupportFragmentManager(),
        ListeningDialogFragment.TAG);
    //startSpeechRecognition();
  }

  public void dogClick(View view) {
    ListeningDialogFragment.newInstance(Constants.DOG, R.drawable.ic_dog).show(getSupportFragmentManager(),
        ListeningDialogFragment.TAG);
    //startSpeechRecognition();
  }

  public void pythonClick(View view) {
    ListeningDialogFragment.newInstance(Constants.PYTHON, R.drawable.ic_python).show(getSupportFragmentManager(),
        ListeningDialogFragment.TAG);
    //startSpeechRecognition();
  }
}
