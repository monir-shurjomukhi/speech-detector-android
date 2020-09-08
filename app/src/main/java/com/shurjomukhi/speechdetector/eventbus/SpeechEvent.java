package com.shurjomukhi.speechdetector.eventbus;

public class SpeechEvent {
  private String text;
  private String score;

  public SpeechEvent(String text, String score) {
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

  public String getScore() {
    return score;
  }

  public void setScore(String percentage) {
    this.score = score;
  }
}
