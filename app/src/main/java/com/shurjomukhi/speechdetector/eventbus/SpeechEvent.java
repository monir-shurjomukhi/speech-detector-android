package com.shurjomukhi.speechdetector.eventbus;

public class SpeechEvent {
  private String text;
  private float score;

  public SpeechEvent(String text, float score) {
    this.text = text;
    this.score = score;
  }

  @Override
  public String toString() {
    return "SpeechEvent{" +
        "text='" + text + '\'' +
        ", percentage='" + score + '\'' +
        '}';
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public float getScore() {
    return score;
  }

  public void setScore(float score) {
    this.score = score;
  }
}
