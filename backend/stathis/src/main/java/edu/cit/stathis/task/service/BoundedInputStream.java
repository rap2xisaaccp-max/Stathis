package edu.cit.stathis.task.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Stops after a fixed number of bytes so a range response cannot over-read. */
class BoundedInputStream extends FilterInputStream {

  private long remaining;

  BoundedInputStream(InputStream in, long limit) {
    super(in);
    this.remaining = Math.max(0, limit);
  }

  @Override
  public int read() throws IOException {
    if (remaining <= 0) {
      return -1;
    }
    int value = super.read();
    if (value >= 0) {
      remaining--;
    }
    return value;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if (remaining <= 0) {
      return -1;
    }
    int allowed = (int) Math.min(length, remaining);
    int read = super.read(buffer, offset, allowed);
    if (read > 0) {
      remaining -= read;
    }
    return read;
  }
}
