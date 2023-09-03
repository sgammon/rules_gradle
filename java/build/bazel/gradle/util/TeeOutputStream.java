package build.bazel.gradle.util;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.io.OutputStream;

public class TeeOutputStream extends OutputStream {
    private final ImmutableList<OutputStream> delegates;

    public TeeOutputStream(ImmutableList<OutputStream> delegates) {
        this.delegates = delegates;
    }

    public TeeOutputStream(OutputStream... delegates) {
        this(ImmutableList.copyOf(delegates));
    }

    @Override
    public void write(int b) throws IOException {
        for (OutputStream delegate : delegates) {
            delegate.write(b);
        }
    }
    @Override
    public void write(byte[] b) throws IOException {
        for (OutputStream delegate : delegates) {
            delegate.write(b);
        }
    }
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        for (OutputStream delegate : delegates) {
            delegate.write(b, off, len);
        }
    }
    @Override
    public void flush() throws IOException {
        for (OutputStream delegate : delegates) {
            delegate.flush();
        }
    }
    @Override
    public void close() throws IOException {
        for (OutputStream delegate : delegates) {
            delegate.close();
        }
    }
}
