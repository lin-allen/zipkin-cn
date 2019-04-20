/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.storage.cassandra.internal.call;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.exceptions.DriverInternalError;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.Test;
import zipkin2.Call;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class DeduplicatingCallTest {
  Function<String, ListenableFuture<ResultSet>> delegate =
    s -> immediateFuture(mock(ResultSet.class));
  TestDeduplicatingCall.Factory callFactory = new TestDeduplicatingCall.Factory(delegate);

  @Test
  public void exceptionArentCached_immediateFuture() throws Exception {
    AtomicBoolean first = new AtomicBoolean(true);
    callFactory =
        new TestDeduplicatingCall.Factory(
            s -> {
              if (first.getAndSet(false)) {
                return immediateFailedFuture(new IllegalArgumentException());
              }
              return immediateFuture(null);
            });
    exceptionsArentCached();
  }

  @Test
  public void exceptionArentCached_deferredFuture() throws Exception {
    ListeningExecutorService exec = listeningDecorator(Executors.newSingleThreadExecutor());
    AtomicBoolean first = new AtomicBoolean(true);
    try {
      callFactory =
          new TestDeduplicatingCall.Factory(
              s -> {
                if (first.getAndSet(false)) {
                  return exec.submit(
                      () -> {
                        Thread.sleep(50);
                        throw new IllegalArgumentException();
                      });
                }
                return immediateFuture(null);
              });
      exceptionsArentCached();
    } finally {
      exec.shutdownNow();
    }
  }

  @Test
  public void exceptionArentCached_creatingFuture() throws Exception {
    AtomicBoolean first = new AtomicBoolean(true);

    callFactory =
        new TestDeduplicatingCall.Factory(
            s -> {
              if (first.getAndSet(false)) {
                throw new IllegalArgumentException();
              }
              return immediateFuture(null);
            });
    exceptionsArentCached();
  }

  void exceptionsArentCached() throws Exception {
    // Intentionally not dereferencing the future. We need to ensure that dropped failed
    // futures still purge!
    Call<ResultSet> firstFoo = callFactory.create("foo");

    Thread.sleep(100); // wait a bit for the future to execute and cache to purge the entry

    // doesn't cache exception
    assertThat(callFactory.create("foo")).isNotEqualTo(firstFoo);

    // sanity check the first future actually failed
    try {
      firstFoo.execute();
      fail();
    } catch (IllegalArgumentException e) {
    } catch (DriverInternalError e) {
      assertThat(e).hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }

  static class TestDeduplicatingCall extends DeduplicatingCall<String> {

    static class Factory extends DeduplicatingCall.Factory<String, TestDeduplicatingCall> {
      final Function<String, ListenableFuture<ResultSet>> delegate;

      Factory(Function<String, ListenableFuture<ResultSet>> delegate) {
        super(1000, 1000);
        this.delegate = delegate;
      }

      @Override protected TestDeduplicatingCall newCall(String string) {
        return new TestDeduplicatingCall(this, string);
      }
    }

    @Override protected ListenableFuture<ResultSet> newFuture() {
      return ((Factory) factory).delegate.apply(input);
    }

    @Override public Call<ResultSet> clone() {
      return new TestDeduplicatingCall(((Factory) factory), input);
    }

    TestDeduplicatingCall(Factory factory, String key) {
      super(factory, key);
    }
  }
}
